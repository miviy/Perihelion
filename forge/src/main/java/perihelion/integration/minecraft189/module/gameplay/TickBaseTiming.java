package perihelion.integration.minecraft189.module.gameplay;

/** Native timer policy; preserves Yuri's float range edges and Aura-disabled pause. */
final class TickBaseTiming {
  private int cooldown;
  private float speed = 1;
  private boolean slow;

  float update(boolean auraRequired, boolean auraEnabled, Double distance, int delay) {
    if (auraRequired && !auraEnabled) return normal();
    if (distance != null && cooldown == 0) {
      if (slow) {
        speed = .1f;
        slow = false;
        cooldown = delay;
        return speed;
      }
      float seek = 4.2f;
      if (distance <= seek + .15 && distance > seek - .55) {
        speed = 8;
        slow = true;
        return speed;
      }
    }
    float result = normal();
    if (cooldown > 0) cooldown--;
    return result;
  }

  private float normal() {
    if (Math.abs(speed - 1) > .01f) {
      speed += (1 - speed) * .5f;
      return speed;
    }
    return 1;
  }
}
