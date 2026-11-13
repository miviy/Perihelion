package perihelion.core.control;

/** One 1.8.9 hotbar slot; visible selection and server spoofing compete in the same channel. */
public final class HotbarSelection {
  public enum Mode {
    VISIBLE,
    SPOOF
  }

  private final int slot;
  private final Mode mode;
  private final boolean restore;

  private HotbarSelection(int slot, Mode mode) {
    this(slot, mode, true);
  }

  private HotbarSelection(int slot, Mode mode, boolean restore) {
    if (slot < 0 || slot > 8) throw new IllegalArgumentException("Hotbar slot must be 0 through 8");
    this.slot = slot;
    this.mode = mode;
    this.restore = restore;
  }

  public static HotbarSelection visible(int slot) {
    return new HotbarSelection(slot, Mode.VISIBLE);
  }

  /** Retain a winning visible selection, preserving any still-active restoring handoff. */
  public static HotbarSelection persistent(int slot) {
    return new HotbarSelection(slot, Mode.VISIBLE, false);
  }

  public static HotbarSelection spoof(int slot) {
    return new HotbarSelection(slot, Mode.SPOOF);
  }

  public int slot() {
    return slot;
  }

  public Mode mode() {
    return mode;
  }

  public boolean restore() {
    return restore;
  }
}
