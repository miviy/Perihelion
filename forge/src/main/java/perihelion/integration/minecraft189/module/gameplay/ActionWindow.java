package perihelion.integration.minecraft189.module.gameplay;

import perihelion.core.module.ModuleContext;
import perihelion.integration.minecraft189.event.ClientSendFactEvent;

/** Activation-owned packet facts since the last walking packet. */
final class ActionWindow {
  private boolean slot, attack, swing, block, inventory;

  ActionWindow(ModuleContext context) {
    context.listen(ClientSendFactEvent.class, 2000, false, event -> record(event.action()));
  }

  private synchronized void record(ClientSendFactEvent.Action action) {
    switch (action) {
      case WALK:
        slot = attack = swing = block = inventory = false;
        break;
      case SLOT:
        slot = true;
        break;
      case ATTACK:
      case USE_ENTITY:
        attack = true;
        break;
      case SWING:
        swing = true;
        break;
      case USE_ITEM:
      case PLACE:
      case DIG:
      case RELEASE_USE_ITEM:
        block = true;
        break;
      case INVENTORY:
        inventory = true;
        break;
      default:
        break;
    }
  }

  synchronized boolean bad() {
    return slot || attack || swing || block || inventory;
  }

  synchronized boolean block() {
    return block;
  }

  synchronized boolean combat() {
    return attack || swing || block;
  }
}
