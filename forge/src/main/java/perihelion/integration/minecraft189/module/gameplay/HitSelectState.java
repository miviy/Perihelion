package perihelion.integration.minecraft189.module.gameplay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Activation-local combo history. Times use the module's monotonic clock. */
final class HitSelectState {
  private final Map<Integer, Long> firstHits = new HashMap<>();
  private int combo, lastTarget = -1, lastSlot = -1, previousTargetHurt, previousPlayerHurt;
  private long lastAttack, damagedAt = -1;

  void tick(
      long now,
      int slot,
      int playerHurt,
      int maxPlayerHurt,
      int targetHurt,
      int maxTargetHurt,
      boolean tracked,
      boolean countCombo,
      boolean resetOnSwap,
      boolean resetOnDamage) {
    if (lastSlot != -1 && lastSlot != slot && resetOnSwap) combo = 0;
    lastSlot = slot;
    if (playerHurt > 0 && playerHurt == maxPlayerHurt && playerHurt > previousPlayerHurt) {
      combo = 0;
      if (resetOnDamage) damagedAt = now;
    }
    previousPlayerHurt = playerHurt;
    if (tracked) {
      if (countCombo
          && targetHurt > 0
          && targetHurt == maxTargetHurt
          && targetHurt > previousTargetHurt
          && now - lastAttack < 750) combo++;
      previousTargetHurt = targetHurt;
    } else previousTargetHurt = 0;
    if (combo > 0 && now - lastAttack > 1250) combo = 0;
    // Donor entries expire after ten seconds. Retire them rather than retain every entity ID.
    Iterator<Long> entries = firstHits.values().iterator();
    while (entries.hasNext()) if (now - entries.next() > 10000) entries.remove();
  }

  int lastTarget() {
    return lastTarget;
  }

  int combo() {
    return combo;
  }

  boolean hold(
      long now,
      int target,
      int hurt,
      int pause,
      int minimumCombo,
      boolean healthEscape,
      boolean jumpEscape,
      boolean criticalHold,
      boolean forceFirst) {
    if (healthEscape
        || jumpEscape
        || combo < minimumCombo
        || damagedAt >= 0 && now - damagedAt < 170) return false;
    boolean hold = pause > 0 && hurt > 10 - pause / 50 || criticalHold;
    if (hold && forceFirst) {
      Long previous = firstHits.put(target, now);
      if (previous == null || now - previous > 10000) hold = false;
    }
    return hold;
  }

  void attacked(int target, long now, boolean countCombo) {
    if (!countCombo) return;
    if (lastTarget != target) previousTargetHurt = 0;
    lastTarget = target;
    lastAttack = now;
  }
}
