package perihelion.integration.minecraft189.hud;

import java.util.*;

/**
 * Client-owned presence history. Continuous tab presence counts once, including repeated snapshots.
 */
final class OverlayEncounters {
  static final class Entry {
    int encounters;
    long joined, lastSeen;
    boolean present;
  }

  private final Map<UUID, Entry> entries =
      new LinkedHashMap<UUID, Entry>(512, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<UUID, Entry> e) {
          return size() > 512;
        }
      };

  void update(Collection<UUID> ids, long now) {
    Set<UUID> present = new HashSet<>(ids);
    for (Map.Entry<UUID, Entry> e : entries.entrySet())
      if (!present.contains(e.getKey())) e.getValue().present = false;
    for (UUID id : present) {
      Entry e = entries.computeIfAbsent(id, ignored -> new Entry());
      if (!e.present) {
        if (now - e.lastSeen > 1800000) e.encounters = 0;
        e.encounters++;
        e.joined = now;
      }
      e.lastSeen = now;
      e.present = true;
    }
  }

  Entry get(UUID id) {
    return entries.get(id);
  }

  void clear() {
    entries.clear();
  }
}
