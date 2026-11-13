package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.*;
import perihelion.core.control.ControlRequest;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.network.PacketBufferScope;

final class CombatTimingModules {
  private CombatTimingModules() {}

  static EntityLivingBase resolve(AuraModule aura, boolean onlyAura) {
    if (onlyAura) return aura.target();
    return aura.candidates(6).stream()
        .min(
            java.util.Comparator.comparingDouble(
                entity ->
                    net.minecraft.client.Minecraft.getMinecraft()
                        .thePlayer
                        .getDistanceToEntity(entity)))
        .orElse(null);
  }

  static final class TickBase extends GameplayModule {
    private final AuraModule aura;
    private final Setting<Integer> delay;
    private final Setting<Boolean> onlyAura;

    TickBase(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "tickbase",
          "Tick Base",
          "Burst and repay client ticks around the target range.",
          ModuleCategory.COMBAT);
      this.aura = aura;
      delay = integer("delay", "Delay", 20, 0, 200);
      onlyAura = bool("only-with-kill-aura", "Only With Kill Aura", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Float> timer = c.controls().timerSpeed(60);
      TickBaseTiming timing = new TickBaseTiming();
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            EntityLivingBase target = resolve(aura, onlyAura.get());
            float speed =
                timing.update(
                    onlyAura.get(),
                    active("aura"),
                    target == null ? null : (double) mc().thePlayer.getDistanceToEntity(target),
                    delay.get());
            if (speed == 1) timer.clear();
            else timer.setForTicks(speed, 1);
          });
    }
  }

  static final class LagRange extends GameplayModule {
    enum Mode {
      LAG,
      BLINK,
      B4
    }

    private final AuraModule aura;
    private final Setting<Mode> mode;
    private final Setting<Double> min, max;
    private final Setting<Integer> minDelay, maxDelay;
    private final Setting<Boolean> onlyAura, teleports, velocity, entities;
    private final Setting<Boolean> renderPosition;
    private B4LagRange b4;
    private perihelion.integration.minecraft189.network.SentPosition.Point position;

    LagRange(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "lagrange",
          "Lag Range",
          "Buffer selected traffic in a target distance band.",
          ModuleCategory.COMBAT);
      this.aura = aura;
      mode = mode("mode", "Mode", Mode.LAG, Mode.class);
      min = number("min-range", "Min Range", 3.5, 1, 6, 0.1);
      max = number("max-range", "Max Range", 4.2, 1, 6, 0.1);
      minDelay = integer("min-delay", "Min Delay (ms)", 50, 0, 5000);
      maxDelay = integer("max-delay", "Max Delay (ms)", 200, 0, 5000);
      onlyAura = bool("only-with-kill-aura", "Only With Kill Aura", true);
      teleports = bool("delay-teleports", "Delay Teleports", true);
      velocity = bool("delay-velocity", "Delay Velocity", true);
      entities = bool("delay-entity-movements", "Delay Entity Movements", true);
      renderPosition = bool("render-lag-pos", "Render Lag Pos", true);
      min.visibleWhen(() -> mode.get() != Mode.B4);
      max.visibleWhen(() -> mode.get() != Mode.B4);
      minDelay.visibleWhen(() -> mode.get() != Mode.B4);
      maxDelay.visibleWhen(() -> mode.get() != Mode.B4);
      onlyAura.visibleWhen(() -> mode.get() != Mode.B4);
      teleports.visibleWhen(() -> mode.get() != Mode.B4);
      velocity.visibleWhen(() -> mode.get() != Mode.B4);
      entities.visibleWhen(() -> mode.get() != Mode.B4);
      renderPosition.visibleWhen(() -> mode.get() != Mode.B4);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      b4 = new B4LagRange(this, c, mode);
      c.onCleanup(() -> b4 = null);
      LagRangeTiming timing = new LagRangeTiming();
      java.util.function.IntSupplier delays =
          () -> LagRangeTiming.sampleDelay(minDelay.get(), maxDelay.get());
      position = null;
      c.onCleanup(() -> position = null);
      PacketPositionMarker marker = new PacketPositionMarker(runtime);
      c.listen(
          perihelion.core.event.WorldRenderEvent.class,
          e -> {
            if (mode.get() == Mode.B4 || !renderPosition.get() || !timing.lagging()) return;
            marker.draw(
                mode.get() == Mode.BLINK
                    ? position
                    : perihelion.integration.minecraft189.HookBridge.gameControls()
                        .outgoingFacts()
                        .position());
          });
      PacketBufferScope buffer =
          new PacketBufferScope(
              runtime,
              c,
              (p, outbound) ->
                  mode.get() != Mode.B4 && (mode.get() == Mode.BLINK
                      ? outbound
                      : p instanceof C03PacketPlayer
                          || PacketModules.regular(p)
                          || teleports.get() && PacketModules.teleport(p)
                          || velocity.get() && PacketModules.velocity(p)
                          || entities.get() && PacketModules.entity(p)),
              () -> mode.get() == Mode.BLINK ? Long.MAX_VALUE : timing.delay() + 1L);
      buffer.collecting(false);
      c.observe(
          mode,
          (s, before, after) -> {
            buffer.collecting(false);
            buffer.flush(true);
            timing.reset();
            position = null;
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (mode.get() == Mode.B4) return;
            LagRangeTiming.Action action;
            if (e == PlayerControlEvent.BEFORE_MOTION) {
              boolean wasLagging = timing.lagging();
              EntityLivingBase target =
                  onlyAura.get() && !active("aura") ? null : resolve(aura, onlyAura.get());
              action =
                  timing.pre(
                      now(),
                      GameActions.moving(mc().thePlayer),
                      target == null ? null : (double) mc().thePlayer.getDistanceToEntity(target),
                      mode.get() == Mode.BLINK,
                      min.get(),
                      max.get(),
                      delays);
              if (!wasLagging && timing.lagging() && mode.get() == Mode.BLINK)
                position =
                    new perihelion.integration.minecraft189.network.SentPosition.Point(
                        mc().thePlayer.posX, mc().thePlayer.posY, mc().thePlayer.posZ);
            } else if (e == PlayerControlEvent.AFTER_MOTION) {
              action = timing.post(now(), mode.get() == Mode.BLINK);
            } else return;
            if (action == LagRangeTiming.Action.COLLECT) buffer.collecting(true);
            else if (action == LagRangeTiming.Action.FLUSH) {
              buffer.collecting(false);
              buffer.flush(true);
            }
          });
    }
  }

  static final class BackTrack extends GameplayModule {
    private final AuraModule aura;
    private final Setting<Double> min, max;
    private final Setting<Integer> delay;
    private final Setting<Boolean> onlyAura;

    BackTrack(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "backtrack",
          "Back Track",
          "Temporarily delay entity movement around a combat target.",
          ModuleCategory.COMBAT);
      this.aura = aura;
      min = number("min-range", "Min Range", 2, 0, 6, 0.1);
      max = number("max-range", "Max Range", 4, 0, 10, 0.1);
      delay = integer("max-delay", "Max Delay (ms)", 400, 40, 2000);
      onlyAura = bool("only-kill-aura", "Only Kill Aura", true);
    }

    private volatile TrackSnapshot snapshot;

    private boolean track(net.minecraft.network.Packet<?> packet, boolean outbound) {
      TrackSnapshot tracked = snapshot;
      if (outbound || tracked == null) return false;
      if (packet instanceof S14PacketEntity) {
        S14PacketEntity move = (S14PacketEntity) packet;
        try {
          if (GameActions.movementEntityId(move) == tracked.id)
            tracked.move(
                move.func_149062_c() / 32.0,
                move.func_149061_d() / 32.0,
                move.func_149064_e() / 32.0);
        } catch (java.io.IOException failure) {
          throw new IllegalStateException(failure);
        }
        return true;
      }
      if (packet instanceof S18PacketEntityTeleport) {
        S18PacketEntityTeleport move = (S18PacketEntityTeleport) packet;
        if (move.getEntityId() == tracked.id)
          tracked.teleport(move.getX() / 32.0, move.getY() / 32.0, move.getZ() / 32.0);
        return true;
      }
      return false;
    }

    @Override
    protected void onEnable(ModuleContext c) {
      snapshot = null;
      PacketBufferScope buffer =
          new PacketBufferScope(runtime, c, this::track, () -> Long.MAX_VALUE);
      buffer.collecting(false);
      long[] start = {now()};
      c.onCleanup(
          () -> {
            snapshot = null;
          });
      c.listenNetwork(
          PacketReceiveEvent.class,
          100,
          false,
          e -> {
            TrackSnapshot tracked = snapshot;
            if (e.packet() instanceof S08PacketPlayerPosLook
                || tracked != null
                    && e.packet() instanceof S12PacketEntityVelocity
                    && ((S12PacketEntityVelocity) e.packet()).getEntityID() == tracked.localId)
              c.deferForWorld(
                  () -> {
                    buffer.collecting(false);
                    buffer.flush(true);
                    snapshot = null;
                    start[0] = now();
                  });
          });
      c.listen(
          PlayerAttackEvent.class,
          -100,
          false,
          e -> {
            TrackSnapshot tracked = snapshot;
            net.minecraft.util.Vec3 pending = tracked == null ? null : tracked.position;
            if (tracked != null && pending != null && e.target().getEntityId() == tracked.id)
              e.target().setPosition(pending.xCoord, pending.yCoord, pending.zCoord);
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            EntityLivingBase target = resolve(aura, onlyAura.get());
            double distance =
                target == null
                    ? -1
                    : mc().thePlayer.getPositionEyes(1).distanceTo(target.getPositionVector());
            boolean spoof =
                distance > min.get() && distance < max.get() && now() - start[0] < delay.get();
            if (snapshot != null && (target == null || snapshot.id != target.getEntityId())) {
              buffer.collecting(false);
              buffer.flush(true);
            }
            if (!spoof) snapshot = null;
            else if (snapshot == null || snapshot.id != target.getEntityId())
              snapshot = new TrackSnapshot(target, mc().thePlayer.getEntityId());
            buffer.collecting(spoof);
            if (!spoof) {
              buffer.flush(true);
              start[0] = now();
            }
          });
    }

    private static final class TrackSnapshot {
      final int id, localId;
      final double x, y, z;
      volatile net.minecraft.util.Vec3 position;

      synchronized void move(double dx, double dy, double dz) {
        net.minecraft.util.Vec3 previous = position;
        position =
            previous == null
                ? new net.minecraft.util.Vec3(x + dx, y + dy, z + dz)
                : previous.addVector(dx, dy, dz);
      }

      synchronized void teleport(double x, double y, double z) {
        position = new net.minecraft.util.Vec3(x, y, z);
      }

      TrackSnapshot(EntityLivingBase target, int localId) {
        id = target.getEntityId();
        this.localId = localId;
        x = target.posX;
        y = target.posY;
        z = target.posZ;
      }
    }
  }
}
