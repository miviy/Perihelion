package perihelion.integration.minecraft189.event;

import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

/**
 * Observational facts from a fresh client-thread PLAY send; never intercepts or retains a packet.
 */
public final class ClientSendFactEvent {
  public enum Action {
    WALK,
    USE_ENTITY,
    ATTACK,
    DIG,
    RELEASE_USE_ITEM,
    USE_ITEM,
    PLACE,
    SLOT,
    SWING,
    INVENTORY
  }

  private final Action action;
  private final ItemStack stack;

  private ClientSendFactEvent(Action action, ItemStack stack) {
    this.action = action;
    this.stack = stack == null ? null : stack.copy();
  }

  public Action action() {
    return action;
  }

  public ItemStack stack() {
    return stack == null ? null : stack.copy();
  }

  public static ClientSendFactEvent from(Packet<?> packet) {
    if (packet instanceof C03PacketPlayer) return new ClientSendFactEvent(Action.WALK, null);
    if (packet instanceof C02PacketUseEntity)
      return new ClientSendFactEvent(
          ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK
              ? Action.ATTACK
              : Action.USE_ENTITY,
          null);
    if (packet instanceof C09PacketHeldItemChange)
      return new ClientSendFactEvent(Action.SLOT, null);
    if (packet instanceof C0APacketAnimation) return new ClientSendFactEvent(Action.SWING, null);
    if (packet instanceof C0EPacketClickWindow
        || packet instanceof C0DPacketCloseWindow
        || packet instanceof C16PacketClientStatus
            && ((C16PacketClientStatus) packet).getStatus()
                == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)
      return new ClientSendFactEvent(Action.INVENTORY, null);
    if (packet instanceof C07PacketPlayerDigging)
      return new ClientSendFactEvent(
          ((C07PacketPlayerDigging) packet).getStatus()
                  == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
              ? Action.RELEASE_USE_ITEM
              : Action.DIG,
          null);
    if (packet instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement use = (C08PacketPlayerBlockPlacement) packet;
      return new ClientSendFactEvent(
          use.getPlacedBlockDirection() == 255 ? Action.USE_ITEM : Action.PLACE, use.getStack());
    }
    return null;
  }
}
