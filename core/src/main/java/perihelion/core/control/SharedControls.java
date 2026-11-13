package perihelion.core.control;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.event.EventOwner;

/**
 * Client-thread arbitration only; Minecraft 1.8.9 applies the winning values. Higher priority wins,
 * then the earliest claim. Updating a claim never changes tie order.
 */
public final class SharedControls implements AutoCloseable {
  final ClientTaskQueue tasks;
  long tick;
  boolean closed;
  private final List<Channel<?>> channels = new ArrayList<>();
  private final Channel<Rotation> rotation;
  private final Channel<HotbarSelection> hotbar;
  private final Channel<Float> timer;
  private final Channel<Boolean> sprint;
  private final Channel<CameraPerspective> perspective;
  private final EnumMap<InputKey, Channel<Boolean>> keys = new EnumMap<>(InputKey.class);

  public SharedControls(ClientTaskQueue tasks) {
    this.tasks = Objects.requireNonNull(tasks, "tasks");
    rotation = channel(value -> Objects.requireNonNull(value, "rotation"));
    hotbar = channel(value -> Objects.requireNonNull(value, "hotbar"));
    timer =
        channel(
            value -> {
              if (value == null || !Float.isFinite(value) || value <= 0)
                throw new IllegalArgumentException("Timer speed must be finite and positive");
            });
    sprint = channel(value -> Objects.requireNonNull(value, "sprint"));
    perspective = channel(value -> Objects.requireNonNull(value, "perspective"));
    for (InputKey key : InputKey.values())
      keys.put(key, channel(value -> Objects.requireNonNull(value, "key state")));
  }

  private <T> Channel<T> channel(Consumer<T> validator) {
    Channel<T> channel = new Channel<>(this, validator);
    channels.add(channel);
    return channel;
  }

  /** The runtime calls this before tick START callbacks; it performs no game writes. */
  public void beginTick() {
    tasks.checkThread();
    if (!closed) tick++;
  }

  public Optional<Rotation> rotation() {
    return rotation.value();
  }

  /** The integration snapshots this identity before building a walking packet. */
  public Optional<ControlRequest<Rotation>> rotationRequest() {
    tasks.checkThread();
    return Optional.ofNullable(rotation.winner());
  }

  public Optional<HotbarSelection> hotbar() {
    return hotbar.value();
  }

  /** An eligible restoring handoff keeps its baseline while a persistent request wins. */
  public boolean hasRestoringHotbarRequest() {
    tasks.checkThread();
    for (ControlRequest<HotbarSelection> request : hotbar.requests)
      if (request.eligible() && request.value.restore()) return true;
    return false;
  }

  public Optional<Float> timerSpeed() {
    return timer.value();
  }

  public Optional<Boolean> sprinting() {
    return sprint.value();
  }

  public Optional<CameraPerspective> perspective() {
    return perspective.value();
  }

  public Optional<Boolean> key(InputKey key) {
    return keys.get(Objects.requireNonNull(key, "key")).value();
  }

  public boolean ownsHotbar(ControlRequest<HotbarSelection> request) {
    tasks.checkThread();
    return request != null && request.channel == hotbar && request.isWinning();
  }

  public int requestCount() {
    tasks.checkThread();
    int count = 0;
    for (Channel<?> channel : channels) count += channel.requests.size();
    return count;
  }

  /** Used by ModuleContext to bind requests to its existing activation and cleanup stack. */
  public Scope scope(EventOwner owner, Consumer<AutoCloseable> own) {
    tasks.checkThread();
    return new Scope(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(own, "own"));
  }

  @Override
  public void close() {
    tasks.checkThread();
    closed = true;
    for (Channel<?> channel : channels) {
      while (!channel.requests.isEmpty()) channel.requests.get(channel.requests.size() - 1).close();
    }
  }

  public final class Scope {
    private final EventOwner owner;
    private final Consumer<AutoCloseable> own;

    private Scope(EventOwner owner, Consumer<AutoCloseable> own) {
      this.owner = owner;
      this.own = own;
    }

    public ControlRequest<Rotation> rotation(int priority) {
      return claim(rotation, priority);
    }

    public ControlRequest<HotbarSelection> hotbar(int priority) {
      return claim(hotbar, priority);
    }

    public ControlRequest<Float> timerSpeed(int priority) {
      return claim(timer, priority);
    }

    public ControlRequest<Boolean> sprinting(int priority) {
      return claim(sprint, priority);
    }

    public ControlRequest<CameraPerspective> perspective(int priority) {
      return claim(perspective, priority);
    }

    public ControlRequest<Boolean> key(InputKey key, int priority) {
      return claim(keys.get(Objects.requireNonNull(key, "key")), priority);
    }

    private <T> ControlRequest<T> claim(Channel<T> channel, int priority) {
      tasks.checkThread();
      ControlRequest<T> request = new ControlRequest<>(channel, owner, priority);
      if (closed || owner.isClosed()) request.closed = true;
      else {
        channel.requests.add(request);
        own.accept(request);
      }
      return request;
    }
  }

  static final class Channel<T> {
    final SharedControls hub;
    final Consumer<T> validate;
    final List<ControlRequest<T>> requests = new ArrayList<>();

    Channel(SharedControls hub, Consumer<T> validate) {
      this.hub = hub;
      this.validate = validate;
    }

    ControlRequest<T> winner() {
      ControlRequest<T> winner = null;
      for (ControlRequest<T> request : requests) {
        if (request.eligible() && (winner == null || request.priority > winner.priority))
          winner = request;
      }
      return winner;
    }

    Optional<T> value() {
      hub.tasks.checkThread();
      ControlRequest<T> winner = winner();
      return winner == null ? Optional.empty() : Optional.of(winner.value);
    }
  }
}
