package perihelion.integration.minecraft189.module.gameplay;

/** Last authoritative time and daylight rule, independent of the local visual override. */
final class AmbienceTimeState {
  private long time, stamp;
  private boolean daylight;

  AmbienceTimeState(long time, boolean daylight, long now) {
    this.time = time;
    this.daylight = daylight;
    stamp = now;
  }

  void packet(long signedTime, long now) {
    daylight = signedTime >= 0;
    time = daylight ? signedTime : -signedTime;
    stamp = now;
  }

  long restoredTime(long now) {
    return time + (daylight ? Math.max(0, now - stamp) / 50 : 0);
  }

  boolean daylight() {
    return daylight;
  }
}
