package perihelion.integration.minecraft189.network;

/** Keeps a dependent outbound sequence in its first buffer until the sequence drains. */
public final class PacketOrderBarrier<T> {
  private T owner;
  private int pending;

  public synchronized T owner() { return owner; }

  public synchronized boolean queued(T next) {
    if (owner != null && owner != next) return false;
    owner = next;
    pending++;
    return true;
  }

  public synchronized void replayed(T expected) {
    if (owner == expected && --pending == 0) owner = null;
  }

  public synchronized void discard(T expected) {
    if (owner == expected) clear();
  }

  public synchronized void clear() { owner = null; pending = 0; }
}
