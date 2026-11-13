package perihelion.integration.minecraft189.control;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Restores only values this adapter still controls; observes manual/external changes between
 * writes.
 */
final class StateOverride<T> {
  private boolean applied;
  private T baseline, last;

  /** Forget restoration only if the last write still owns the actual value. */
  void commit(Supplier<T> read) {
    if (applied && Objects.equals(read.get(), last)) {
      applied = false;
      baseline = null;
      last = null;
    }
  }

  void apply(Optional<T> requested, Supplier<T> read, Consumer<T> write) {
    T current = read.get();
    if (requested.isPresent()) {
      if (!applied || !Objects.equals(current, last)) baseline = current;
      T next = requested.get();
      write.accept(next);
      last = next;
      applied = true;
    } else {
      if (applied && Objects.equals(current, last)) write.accept(baseline);
      applied = false;
      baseline = null;
      last = null;
    }
  }
}
