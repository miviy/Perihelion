package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

final class InventoryProtection {
  private String slotText = "", itemText = "";
  private final Set<Integer> slots = new HashSet<>();
  private final Set<String> items = new HashSet<>();

  void configure(String slotText, String itemText) {
    if (!this.slotText.equals(slotText)) {
      this.slotText = slotText;
      slots.clear();
      for (String token : slotText.split("[,\\s]+")) {
        try {
          int slot = Integer.parseInt(token);
          if (slot >= 1 && slot <= 36) slots.add(slot);
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (!this.itemText.equals(itemText)) {
      this.itemText = itemText;
      items.clear();
      for (String token : itemText.toLowerCase(Locale.ROOT).split("[,\\s]+"))
        if (!token.isEmpty()) items.add(token.contains(":") ? token : "minecraft:" + token);
    }
  }

  boolean protects(int containerSlot, ItemStack stack, boolean named) {
    if (slots.contains(containerSlot >= 36 ? containerSlot - 35 : containerSlot + 1)) return true;
    if (stack == null) return false;
    if (InventoryPolicy.protectedItem(stack)) return true;
    if (named
        && (stack.hasDisplayName()
            || stack.hasTagCompound()
                && stack.getTagCompound().getCompoundTag("display").hasKey("Lore", 9))) return true;
    return items.contains(String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())));
  }
}
