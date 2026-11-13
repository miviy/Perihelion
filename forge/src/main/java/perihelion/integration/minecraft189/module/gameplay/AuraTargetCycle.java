package perihelion.integration.minecraft189.module.gameplay;

import java.util.List;

/** Yuri's stable entity-ID switch order, measured in 50 ms tick units. */
final class AuraTargetCycle {
  private int index;
  private long switchedAt;

  int select(List<Integer> ids, int current, long now, int delayTicks) {
    if (ids.isEmpty()) {
      reset();
      return -1;
    }
    if (index >= ids.size()) index = 0;
    int previous = ids.indexOf(current);
    if (previous >= 0) index = previous;
    if (previous < 0) switchedAt = now;
    else if (ids.size() > 1 && now - switchedAt >= delayTicks * 50L) {
      index = (index + 1) % ids.size();
      switchedAt = now;
    }
    return index;
  }

  void reset() {
    index = 0;
    switchedAt = 0;
  }
}
