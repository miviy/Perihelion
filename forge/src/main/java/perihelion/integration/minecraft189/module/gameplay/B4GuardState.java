package perihelion.integration.minecraft189.module.gameplay;

/** B4 AutoBlock default hold/cooldown transition table: 150ms hold, 250ms cooldown. */
final class B4GuardState {
  enum Action { NONE, PRESS, RELEASE }
  private int holdTicks, cooldownTicks;

  Action update(boolean block, boolean manual) {
    if (cooldownTicks > 0) --cooldownTicks;
    if (holdTicks > 0) {
      --holdTicks;
      if (!block || holdTicks == 0) {
        holdTicks = 0;
        if (!manual) cooldownTicks = 5;
        return Action.RELEASE;
      }
    } else if (block && !manual && cooldownTicks == 0) {
      holdTicks = 3;
      return Action.PRESS;
    }
    return Action.NONE;
  }

  boolean knockback() {
    if (holdTicks == 0) return false;
    holdTicks = 0;
    cooldownTicks = 5;
    return true;
  }

  void reset() { holdTicks = cooldownTicks = 0; }
}
