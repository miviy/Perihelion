// SPDX-License-Identifier: AGPL-3.0-only
// Packet categories adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def.
package perihelion.integration.minecraft189.network;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

/** Raw-thread-safe classification only; retains no packets, player, settings or world objects. */
public final class OutgoingPacketFacts {
  private final ActionPacketWindow window = new ActionPacketWindow();
  private final SentPosition position = new SentPosition();

  /** Called after cancellation/defer decisions, including accepted silent buffer replay. */
  public void accepted(Packet<?> packet) {
    if (packet instanceof C03PacketPlayer) {
      C03PacketPlayer movement = (C03PacketPlayer) packet;
      if (movement.isMoving())
        position.record(movement.getPositionX(), movement.getPositionY(), movement.getPositionZ());
    }
  }

  public SentPosition.Point position() {
    return position.point();
  }

  public void record(Packet<?> packet) {
    ActionPacketWindow.Kind kind = ActionPacketWindow.Kind.OTHER;
    if (packet instanceof C03PacketPlayer) kind = ActionPacketWindow.Kind.WALK;
    else if (packet instanceof C09PacketHeldItemChange
        || packet instanceof C0APacketAnimation
        || packet instanceof C02PacketUseEntity
        || packet instanceof C08PacketPlayerBlockPlacement
        || packet instanceof C07PacketPlayerDigging
        || packet instanceof C0EPacketClickWindow
        || packet instanceof C0DPacketCloseWindow
        || packet instanceof C16PacketClientStatus
            && ((C16PacketClientStatus) packet).getStatus()
                == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)
      kind = ActionPacketWindow.Kind.ACTION;
    window.record(kind);
  }

  public boolean bad() {
    return window.bad();
  }

  public void reset() {
    window.reset();
    position.reset();
  }
}
