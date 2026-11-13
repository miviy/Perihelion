package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;

/** Donor alert transitions, permanent purchase flags and the fixed two-second alert interval. */
final class B4BedWarsState {
  private static final String[] START_LINES = {
    "  Protect your bed", "  Select an ultimate", "  Collect Lucky Blocks",
    "  Become a Soul Collector", "  All generators are maxed!", "  Destroy the enemy bed and",
    "  Every few seconds brings a new"
  };

  /** ClickGuiMod's leading-space/type gate followed by BedWarsModule's exact start text. */
  static boolean gameStart(String stripped, int type) {
    if (type == 2 || stripped == null || stripped.isEmpty() || stripped.charAt(0) != ' ') return false;
    for (String line : START_LINES) if (stripped.contains(line)) return true;
    return false;
  }

  enum Alert {
    BLOCK_ZAPPER("is holding Block Zapper", false), BOW("is holding Bow", false),
    BOW_POWER("is holding Power Bow", false), BOW_PUNCH("is holding Punch Bow", false),
    CHARLIE_UNICORN("is holding Charlie Kirk", false), DIA_ARMOR("has purchased Diamond Armor", true),
    DIA_PICKAXE("is holding Diamond Pickaxe", false), DREAM_DEFENDER("is holding Dream Defender", false),
    ENCHANT_PROT("has purchased Protection", true), ENCHANT_SHARPNESS("has purchased Sharpness", true),
    ENDERPEARL("is holding Ender Pearl", false), FIREBALL("is holding Fireball", false),
    ICE_BRIDGE("is holding Ice Bridge", false), INVISIBLE("is now Invisible", false),
    KB_STICK("is holding Knockback Stick", false), MAGIC_MILK("is holding Magic Milk", false),
    OBSIDIAN_HELD("is holding Obsidian", false), OBSIDIAN_PLACED("placed Obsidian", false),
    POTION_INVIS("is holding Invis Potion", false), POTION_JUMP("is holding Jump Potion", false),
    POTION_SPEED("is holding Speed Potion", false), SLEEPING_DUST("is holding Sleeping Dust", false),
    WATER_BUCKET("is holding Water Bucket", false);
    final String text;
    final boolean permanent;
    Alert(String text, boolean permanent) { this.text = text; this.permanent = permanent; }
  }

  private final Map<Integer, EnumSet<Alert>> flags = new LinkedHashMap<>();
  private final Map<UUID, EnumMap<Alert, Long>> last = new LinkedHashMap<>();
  private int milkTicks;

  boolean transition(int entity, UUID player, Alert alert, boolean present, long now) {
    EnumSet<Alert> set = flags.get(entity);
    if (set == null) {
      if (!present) return false;
      if (flags.size() == 512) flags.remove(flags.keySet().iterator().next());
      flags.put(entity, set = EnumSet.noneOf(Alert.class));
    }
    if (set.contains(alert)) {
      if (!alert.permanent && !present) set.remove(alert);
      return false;
    }
    if (!present) return false;
    set.add(alert);
    return permit(player, alert, now);
  }

  boolean permit(UUID player, Alert alert, long now) {
    EnumMap<Alert, Long> map = last.get(player);
    if (map == null) {
      if (last.size() == 512) last.remove(last.keySet().iterator().next());
      last.put(player, map = new EnumMap<>(Alert.class));
    }
    Long previous = map.get(alert);
    if (previous != null && Math.abs(now - previous) <= 2000) return false;
    map.put(alert, now);
    return true;
  }

  void milkFinished() { milkTicks = 600; }
  int milkTicks() { return milkTicks; }
  void tickMilk() {
    if (milkTicks > 0) { if (--milkTicks == 0) milkTicks = -40; }
    else if (milkTicks < 0) milkTicks++;
  }
  void reset() { flags.clear(); last.clear(); milkTicks = 0; }
}
