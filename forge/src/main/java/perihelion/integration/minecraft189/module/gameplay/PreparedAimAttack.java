package perihelion.integration.minecraft189.module.gameplay;

import perihelion.core.control.Rotation;

/** One attack waits for the identical aim to appear in a completed walking pass. */
final class PreparedAimAttack<T> {
  static final class Action<T> {
    final T target;
    final Rotation aim;

    Action(T target, Rotation aim) {
      this.target = target;
      this.aim = aim;
    }
  }

  private Action<T> prepared;

  void prepare(T target, Rotation aim) {
    prepared = new Action<>(target, aim);
  }

  Action<T> submitted(Rotation previous) {
    if (prepared == null) return null;
    Action<T> action = prepared;
    prepared = null;
    return action.aim == previous ? action : null;
  }

  boolean pending() {
    return prepared != null;
  }

  Rotation aim() {
    return prepared == null ? null : prepared.aim;
  }

  void clear() {
    prepared = null;
  }
}
