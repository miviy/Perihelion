package perihelion.integration.minecraft189.event;

/** Client-thread ray selection controls; entity boxes themselves are never mutated. */
public final class MouseOverEvent {
  private final boolean tickSelection;
  private double range = 3;
  private float expand;
  private net.minecraft.util.MovingObjectPosition selection;
  private perihelion.core.control.ControlRequest<perihelion.core.control.Rotation> selectionOwner;
  private perihelion.core.control.Rotation selectionRotation;

  public MouseOverEvent() {
    this(false);
  }

  public MouseOverEvent(boolean tickSelection) {
    this.tickSelection = tickSelection;
  }

  public boolean tickSelection() {
    return tickSelection;
  }

  public double range() {
    return range;
  }

  public float expand() {
    return expand;
  }

  public void range(double value) {
    if (!Double.isFinite(value) || value < 3 || value > 6)
      throw new IllegalArgumentException("Invalid reach");
    range = value;
  }

  public void expand(float value) {
    if (!Float.isFinite(value) || value < 0 || value > 6)
      throw new IllegalArgumentException("Invalid expansion");
    expand = value;
  }

  /** A silent aim may replace selection only while its native rotation claim still wins. */
  public void selection(
      net.minecraft.util.MovingObjectPosition value,
      perihelion.core.control.ControlRequest<perihelion.core.control.Rotation> owner) {
    selection = value;
    selectionOwner = java.util.Objects.requireNonNull(owner, "selection owner");
    selectionRotation = owner.value().orElse(null);
  }

  public boolean hasSelection() {
    return selectionOwner != null
        && selectionRotation != null
        && selectionOwner.isWinning()
        && selectionOwner.value().orElse(null) == selectionRotation;
  }

  public net.minecraft.util.MovingObjectPosition selection() {
    return selection;
  }
}
