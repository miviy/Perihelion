// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import perihelion.core.event.HudRenderEvent;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.MinecraftTimerAccess;
import perihelion.integration.minecraft189.event.PacketReceiveEvent;
import perihelion.integration.minecraft189.event.PacketSendEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.network.OrderedPacketQueue;
import perihelion.integration.minecraft189.network.PacketSnapshot;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Yuri 1.3.1 Back Track behavior with activation-owned, encoded inbound traffic. */
final class BackTrackModule extends GameplayModule {
  private final AuraModule aura;
  private final Setting<BackTrackState.Mode> mode;
  private final Setting<BackTrackState.Style> style;
  private final Setting<Double> minRange, maxRange, delay;
  private final Setting<Boolean> onlyAura;

  BackTrackModule(ClientRuntime runtime, AuraModule aura) {
    super(
        runtime,
        "backtrack",
        "Back Track",
        "Delay server updates around a combat target.",
        ModuleCategory.COMBAT);
    this.aura = aura;
    mode = mode("mode", "Mode", BackTrackState.Mode.BASIC, BackTrackState.Mode.class);
    style =
        mode("style", "Style", BackTrackState.Style.NEW, BackTrackState.Style.class)
            .visibleWhen(() -> mode.get() == BackTrackState.Mode.BASIC);
    minRange =
        number("min-range", "Min Range", 2, 0, 6, 0.1)
            .visibleWhen(() -> mode.get() != BackTrackState.Mode.MMC);
    maxRange =
        number("max-range", "Max Range", 4, 0, 10, 0.1)
            .visibleWhen(() -> mode.get() != BackTrackState.Mode.MMC);
    // Retain the existing config key while exposing the donor's Delay label and 10ms step.
    delay =
        number("max-delay", "Delay", 400, 40, 2000, 10)
            .visibleWhen(() -> mode.get() == BackTrackState.Mode.BASIC);
    onlyAura = bool("only-kill-aura", "Only Kill Aura", true);
  }

  private BackTrackState.Policy policy() {
    return new BackTrackState.Policy(
        mode.get(), style.get(), delay.get().longValue(), minRange.get(), maxRange.get());
  }

  @Override
  protected void onEnable(ModuleContext context) {
    Session session = context.own(new Session(policy()));
    context.observe(mode, (s, before, after) -> session.policy(policy()));
    context.observe(style, (s, before, after) -> session.policy(policy()));
    context.observe(delay, (s, before, after) -> session.policy(policy()));
    context.observe(minRange, (s, before, after) -> session.policy(policy()));
    context.observe(maxRange, (s, before, after) -> session.policy(policy()));
    context.listenNetwork(
        PacketReceiveEvent.class,
        100,
        false,
        event -> {
          if (session.capture(event.source(), event.packet())) event.cancel();
        });
    context.listenNetwork(
        PacketSendEvent.class,
        0,
        false,
        event -> {
          if (!session.acceptsSource(event.source())
              || !(event.packet() instanceof C02PacketUseEntity)) return;
          // Read only serialized identifiers in raw callbacks, even for a foreign sending thread.
          int id = useEntityId((C02PacketUseEntity) event.packet());
          BackTrackState.AttackPosition position = session.attackPosition(id);
          if (position == null) return;
          Runnable update = () -> session.silentPosition(position);
          if (runtime.tasks().isClientThread()) update.run();
          else context.deferForWorld(update);
        });
    context.listen(
        PlayerControlEvent.class,
        event -> {
          if (event == PlayerControlEvent.BEFORE_INPUT) session.tick();
        });
    context.listen(HudRenderEvent.class, event -> session.renderRelease());
    RenderState rendering = new RenderState();
    context.listen(WorldRenderEvent.class, event -> session.render(rendering));
  }

  private static int useEntityId(C02PacketUseEntity packet) throws IOException {
    PacketBuffer bytes = new PacketBuffer(Unpooled.buffer(16));
    try {
      packet.writePacketData(bytes);
      return bytes.readVarIntFromBuffer();
    } finally {
      bytes.release();
    }
  }

  private final class Session implements AutoCloseable {
    private final long generation = runtime.tasks().generation();
    private final NetHandlerPlayClient connection = mc().getNetHandler();
    private final NetworkManager manager =
        connection == null ? null : connection.getNetworkManager();
    private final Object world = mc().theWorld;
    private final OrderedPacketQueue<PacketSnapshot> held =
        new OrderedPacketQueue<>(8192, 16 * 1024 * 1024);
    private final BackTrackState state;
    private EntityLivingBase trackedTarget;
    private boolean closed, overflow;

    Session(BackTrackState.Policy policy) {
      state = new BackTrackState(policy);
    }

    synchronized void policy(BackTrackState.Policy policy) {
      runtime.tasks().checkThread();
      if (closed || overflow) return;
      // A legacy head has no deadline. It cannot remain ahead of a new timed queue.
      if (state.changePolicy(policy)) {
        trackedTarget = null;
        release(true);
      }
    }

    boolean acceptsSource(NetworkManager source) {
      return source == null || source == manager;
    }

    // Existing reflection fixtures deliberately post packet-only synthetic captures.
    synchronized boolean capture(Packet<?> packet) throws IOException {
      return capture(null, packet);
    }

    synchronized boolean capture(NetworkManager source, Packet<?> packet) throws IOException {
      if (closed
          || overflow
          || generation != runtime.tasks().generation()
          || !acceptsSource(source)) return false;
      boolean movement =
          packet instanceof S14PacketEntity || packet instanceof S18PacketEntityTeleport;
      boolean flushPacket =
          packet instanceof S08PacketPlayerPosLook
              || packet instanceof S12PacketEntityVelocity
                  && ((S12PacketEntityVelocity) packet).getEntityID() == state.localId;
      if (!state.captures(movement, flushPacket)) return false;
      PacketSnapshot copy = PacketSnapshot.copy(packet, false);
      if (!held.offer(copy, state.releaseAt(now(), flushPacket), copy.size())) {
        overflow = true;
        state.stop();
        held.close();
        return true;
      }
      if (packet instanceof S14PacketEntity) {
        S14PacketEntity move = (S14PacketEntity) packet;
        state.move(
            GameActions.movementEntityId(move),
            move.func_149062_c(),
            move.func_149061_d(),
            move.func_149064_e());
      } else if (packet instanceof S18PacketEntityTeleport) {
        S18PacketEntityTeleport move = (S18PacketEntityTeleport) packet;
        state.teleport(move.getEntityId(), move.getX(), move.getY(), move.getZ());
      }
      state.captured(flushPacket);
      return true;
    }

    private boolean current() {
      runtime.tasks().checkThread();
      return !closed
          && !overflow
          && runtime.isRunning()
          && generation == runtime.tasks().generation()
          && world != null
          && mc().theWorld == world
          && connection != null
          && mc().getNetHandler() == connection
          && manager != null
          && connection.getNetworkManager() == manager
          && manager.isChannelOpen();
    }

    private boolean ready() {
      if (overflow)
        throw new IllegalStateException("Back Track capacity exceeded; backlog discarded");
      if (current() && mc().thePlayer != null) return true;
      discard();
      return false;
    }

    synchronized void tick() {
      if (!ready()) return;
      if (state.flush) stop();
      EntityLivingBase target =
          onlyAura.get()
              ? active("aura") ? aura.target() : null
              : CombatTimingModules.resolve(aura, false);
      if (target == null
          || !mc().thePlayer.isEntityAlive()
          || !target.isEntityAlive()
          || mc().theWorld.getEntityByID(target.getEntityId()) != target
          || !inRange(target)) {
        stop();
        return;
      }
      if (state.expired(now())) {
        stop();
        return;
      }
      if (!state.holding || state.targetId != target.getEntityId() || trackedTarget != target) {
        release(true);
        if (!current()) return;
        state.begin(
            target.getEntityId(),
            mc().thePlayer.getEntityId(),
            target.serverPosX,
            target.serverPosY,
            target.serverPosZ,
            now());
        trackedTarget = target;
      }
      release(false);
    }

    private boolean inRange(EntityLivingBase target) {
      BackTrackState.Position tracked =
          state.holding && state.targetId == target.getEntityId() ? state.position() : null;
      Vec3 position =
          tracked == null
              ? new Vec3(
                  target.serverPosX / 32.0, target.serverPosY / 32.0, target.serverPosZ / 32.0)
              : new Vec3(tracked.x, tracked.y, tracked.z);
      float partial = ((MinecraftTimerAccess) mc()).perihelion$timer().renderPartialTicks;
      return state.policy.inRange(mc().thePlayer.getPositionEyes(partial).distanceTo(position));
    }

    synchronized void renderRelease() {
      if (!ready()) return;
      if (state.flush) stop();
      else release(false);
    }

    synchronized BackTrackState.AttackPosition attackPosition(int id) {
      if (closed
          || overflow
          || generation != runtime.tasks().generation()
          || !state.policy.legacy()
          || state.targetId != id) return null;
      return state.attackPosition(id);
    }

    synchronized void silentPosition(BackTrackState.AttackPosition pending) {
      if (!current()
          || !state.accepts(pending)
          || trackedTarget == null
          || mc().theWorld.getEntityByID(pending.targetId) != trackedTarget) return;
      BackTrackState.Position position = pending.position;
      trackedTarget.setPosition(position.x, position.y, position.z);
    }

    synchronized void render(RenderState rendering) {
      if (!current() || mc().thePlayer == null) return;
      BackTrackState.Position position = state.position();
      if (position == null) return;
      double x = position.x - mc().getRenderManager().viewerPosX;
      double y = position.y - mc().getRenderManager().viewerPosY;
      double z = position.z - mc().getRenderManager().viewerPosZ;
      AxisAlignedBB bounds = new AxisAlignedBB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
      int accent =
          ((ColorValue) runtime.modules().require("hud").module().settings().get("accent").get())
                  .resolvedArgb(System.currentTimeMillis())
              & 0xffffff;
      try (RenderState ignored = rendering.open()) {
        RenderStyle.lines(true, .5F, 60 << 24 | accent);
        VisualDraw.fill(bounds);
        RenderStyle.color(40 << 24 | accent);
        VisualDraw.box(bounds);
      }
    }

    private void stop() {
      trackedTarget = null;
      state.stop();
      release(true);
    }

    private void release(boolean all) {
      if (!current()) {
        discard();
        return;
      }
      do {
        for (PacketSnapshot copy : held.drain(now(), all)) {
          if (!current()) {
            discard();
            return;
          }
          try {
            process(copy.decode());
          } catch (IOException failure) {
            throw new IllegalStateException("Back Track packet decode failed", failure);
          }
        }
      } while (all && current() && held.size() > 0);
    }

    @SuppressWarnings("unchecked")
    private void process(Packet<?> packet) {
      ((Packet<INetHandlerPlayClient>) packet).processPacket(connection);
    }

    private void discard() {
      closed = true;
      trackedTarget = null;
      state.stop();
      held.close();
    }

    @Override
    public synchronized void close() {
      runtime.tasks().checkThread();
      try {
        state.stop();
        if (current()) release(true);
      } finally {
        discard();
      }
    }
  }
}
