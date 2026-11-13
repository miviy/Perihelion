package perihelion.integration.minecraft189.event;

import net.minecraft.item.ItemStack;

/** Local consumption completed; item is the consumed stack, not its replacement container. */
public final class ItemUseFinishedEvent {
  private final ItemStack item;

  public ItemUseFinishedEvent(ItemStack item) {
    this.item = item == null ? null : item.copy();
  }

  public ItemStack item() {
    return item == null ? null : item.copy();
  }
}
