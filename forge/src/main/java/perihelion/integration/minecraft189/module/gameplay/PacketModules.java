package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.network.PacketBufferScope;

final class PacketModules {
  private PacketModules() {}

  static boolean regular(Packet<?> p) {
    return p instanceof C0FPacketConfirmTransaction
        || p instanceof C00PacketKeepAlive
        || p instanceof S1CPacketEntityMetadata;
  }

  static boolean velocity(Packet<?> p) {
    return p instanceof S12PacketEntityVelocity || p instanceof S27PacketExplosion;
  }

  static boolean teleport(Packet<?> p) {
    return p instanceof S08PacketPlayerPosLook
        || p instanceof S39PacketPlayerAbilities
        || p instanceof S09PacketHeldItemChange;
  }

  static boolean entity(Packet<?> p) {
    return p instanceof S13PacketDestroyEntities
        || p instanceof S14PacketEntity
        || p instanceof S18PacketEntityTeleport
        || p instanceof S20PacketEntityProperties
        || p instanceof S19PacketEntityHeadLook;
  }

  static final class Blink extends GameplayModule {
    enum Release {
      ATTACK,
      INVENTORY,
      MINE,
      PLACE
    }

    enum Mode {
      CONSTANT,
      PULSE
    }

    private final Setting<Mode> mode;
    private final Setting<Integer> delay;
    private final Setting<Boolean> received;
    private final Setting<Boolean> renderPosition;
    private final Setting<java.util.Set<Release>> release;
    private volatile java.util.Set<Release> releaseSnapshot = java.util.Collections.emptySet();
    private perihelion.integration.minecraft189.network.SentPosition.Point position;

    Blink(ClientRuntime r) {
      super(
          r,
          "blink",
          "Blink",
          "Queue packets until disabled or a pulse expires.",
          ModuleCategory.MISC);
      mode = mode("mode", "Mode", Mode.CONSTANT, Mode.class);
      delay = integer("delay", "Delay", 20, 0, 100).visibleWhen(() -> mode.get() == Mode.PULSE);
      received = bool("cancel-received-packets", "Cancel Received Packets", true);
      renderPosition = bool("render-blink-pos", "Render Blink Pos", true);
      release =
          setting(
              Setting.multiSelect(
                  "release-on",
                  "Release On",
                  "Stop Blink after the selected queued interactions.",
                  java.util.EnumSet.noneOf(Release.class),
                  Release.class,
                  guard));
    }

    @Override
    protected void onEnable(ModuleContext c) {
      position = null;
      c.onCleanup(() -> position = null);
      PacketPositionMarker marker = new PacketPositionMarker(runtime);
      c.listen(
          perihelion.core.event.WorldRenderEvent.class,
          e -> marker.draw(renderPosition.get() ? position : null));
      releaseSnapshot = release.get();
      c.observe(release, (s, before, after) -> releaseSnapshot = after);
      java.util.concurrent.atomic.AtomicBoolean releasing =
          new java.util.concurrent.atomic.AtomicBoolean();
      PacketBufferScope buffer =
          new PacketBufferScope(
              runtime,
              c,
              (packet, outbound) -> {
                // This filter runs only for Blink's own eligible capture. A deferred
                // event observed later could belong entirely to another packet scope.
                // Capture stays locked until this triggering packet is enqueued, so
                // client-thread disable cannot flush before the trigger is owned.
                if (outbound
                    && releases(releaseSnapshot, packet)
                    && releasing.compareAndSet(false, true)) {
                  if (!c.deferForWorld(c::requestDisable)) releasing.set(false);
                }
                return outbound || received.get();
              },
              () -> Long.MAX_VALUE);
      buffer.collecting(false);
      long[] start = {now()};
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_MOTION && position == null)
              position =
                  new perihelion.integration.minecraft189.network.SentPosition.Point(
                      mc().thePlayer.posX, mc().thePlayer.posY, mc().thePlayer.posZ);
            if (e != PlayerControlEvent.AFTER_MOTION) return;
            if (mode.get() == Mode.PULSE && now() - start[0] >= delay.get() * 10L) {
              buffer.collecting(false);
              buffer.flush(true);
              start[0] = now();
            } else buffer.collecting(true);
          });
    }

    static boolean releases(java.util.Set<Release> options, Packet<?> packet) {
      if (packet instanceof C02PacketUseEntity)
        return options.contains(Release.ATTACK)
            && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK;
      if (packet instanceof C0EPacketClickWindow) return options.contains(Release.INVENTORY);
      if (packet instanceof C07PacketPlayerDigging) {
        C07PacketPlayerDigging.Action action = ((C07PacketPlayerDigging) packet).getStatus();
        return options.contains(Release.MINE)
            && (action == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
                || action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK);
      }
      if (packet instanceof C08PacketPlayerBlockPlacement) {
        C08PacketPlayerBlockPlacement placed = (C08PacketPlayerBlockPlacement) packet;
        return options.contains(Release.PLACE)
            && placed.getStack() != null
            && placed.getStack().getItem() instanceof net.minecraft.item.ItemBlock
            && placed.getPlacedBlockDirection() != 255;
      }
      return false;
    }
  }

  static final class FakeLag extends GameplayModule {
    private final Setting<Integer> delay;
    private final Setting<Boolean> teleports, velocities, entities;

    FakeLag(ClientRuntime r) {
      super(
          r,
          "fakelag",
          "Fake Lag",
          "Delay movement and selected play packets.",
          ModuleCategory.MISC);
      delay = integer("delay", "Delay (ms)", 200, 50, 2000);
      teleports = bool("delay-teleports", "Delay Teleports", true);
      velocities = bool("delay-velocity", "Delay Velocity", true);
      entities = bool("delay-entity-movements", "Delay Entity Movements", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      new PacketBufferScope(
          runtime,
          c,
          (p, outbound) ->
              regular(p)
                  || p instanceof C03PacketPlayer
                  || velocities.get() && velocity(p)
                  || teleports.get() && teleport(p)
                  || entities.get() && entity(p),
          () -> delay.get());
    }
  }
}
