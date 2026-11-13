package perihelion.integration.minecraft189.event;

/** Client-thread collection/action boundaries for the local, non-riding player. */
public enum PlayerControlEvent {
  /** Submit held inputs and PRE actions validated against the preceding walking aim. */
  BEFORE_INPUT,
  /** Collected input/slot claims are applied; perform PRE actions before vanilla movement. */
  BEFORE_ACTIONS,
  /** Submit final rotation/hotbar requests before vanilla builds its walking packets. */
  BEFORE_MOTION,
  /** Walking packets were submitted; cleanup, bookkeeping and ordered buffer flushes. */
  AFTER_MOTION
}
