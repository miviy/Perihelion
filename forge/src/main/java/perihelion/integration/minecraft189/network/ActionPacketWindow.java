package perihelion.integration.minecraft189.network;

import java.util.concurrent.atomic.AtomicBoolean;

/** Scalar packet facts for the interval since the last ordinary walking dispatch. */
public final class ActionPacketWindow {
  public enum Kind {
    WALK,
    ACTION,
    OTHER
  }

  private final AtomicBoolean dirty = new AtomicBoolean();

  public void record(Kind kind) {
    if (kind == Kind.WALK) dirty.set(false);
    else if (kind == Kind.ACTION) dirty.set(true);
  }

  public boolean bad() {
    return dirty.get();
  }

  public void reset() {
    dirty.set(false);
  }
}
