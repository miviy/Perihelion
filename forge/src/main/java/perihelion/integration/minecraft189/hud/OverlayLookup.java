package perihelion.integration.minecraft189.hud;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/**
 * Activation-owned bounded workers. Only tick() publishes immutable replies to the client cache.
 */
final class OverlayLookup implements AutoCloseable {
  enum Provider {
    ABYSS,
    PRISM,
    BORDIC,
    HYPIXEL,
    URCHIN,
    SERAPH
  }

  static final class Options {
    final Set<Provider> providers;
    final Map<Provider, Path> keys;
    final boolean sessions;

    Options(Set<Provider> providers, Map<Provider, Path> keys, boolean sessions) {
      this.providers = Collections.unmodifiableSet(new LinkedHashSet<>(providers));
      Map<Provider, Path> copy = new EnumMap<>(Provider.class);
      copy.putAll(keys);
      this.keys = Collections.unmodifiableMap(copy);
      this.sessions = sessions;
    }
  }

  interface Transport extends AutoCloseable {
    OverlayProfile fetch(UUID id, String name, Options options);

    Map<UUID, OverlayProfile> sessions(List<UUID> ids, Options options);

    default void reset() {}

    default void close() {}
  }

  private static final class Entry {
    OverlayProfile profile;
    long expires, sessionExpires;

    Entry(OverlayProfile profile, long expires) {
      this.profile = profile;
      this.expires = expires;
    }
  }

  private static final class Pending {
    final long revision, time;
    final FutureTask<?> task;

    Pending(long revision, long time, FutureTask<?> task) {
      this.revision = revision;
      this.time = time;
      this.task = task;
    }
  }

  private final ExecutorService worker;
  private final Transport transport;
  private final LongSupplier clock;
  private final Map<UUID, Entry> cache =
      new LinkedHashMap<UUID, Entry>(256, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<UUID, Entry> e) {
          return size() > 256;
        }
      };
  private final Map<UUID, Pending> pending = new LinkedHashMap<>();
  private final LinkedHashSet<UUID> sessionQueue = new LinkedHashSet<>();
  private Pending sessionJob;
  private long revision;
  private volatile boolean closed;

  OverlayLookup() {
    this(
        new ThreadPoolExecutor(
            3,
            3,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64),
            r -> {
              Thread t = new Thread(r, "Perihelion-Overlay-API");
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy()),
        new OverlayHttp(),
        System::currentTimeMillis);
  }

  OverlayLookup(ExecutorService worker, Transport transport, LongSupplier clock) {
    this.worker = worker;
    this.transport = transport;
    this.clock = clock;
  }

  OverlayProfile get(UUID id) {
    Entry e = cache.get(id);
    return e == null ? null : e.profile;
  }

  void request(UUID id, String name, Options options) {
    if (closed || id == null || !PlayerReport.validUsername(name)) return;
    long now = clock.getAsLong();
    Entry entry = cache.get(id);
    if (entry != null && entry.expires > now || pending.containsKey(id) || pending.size() >= 64)
      return;
    FutureTask<OverlayProfile> task = new FutureTask<>(() -> transport.fetch(id, name, options));
    Pending job = new Pending(revision, now, task);
    try {
      worker.execute(task);
      pending.put(id, job);
    } catch (RejectedExecutionException full) {
      task.cancel(true);
    }
  }

  void tick(Options options) {
    if (closed) return;
    long now = clock.getAsLong();
    Iterator<Map.Entry<UUID, Pending>> iterator = pending.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, Pending> next = iterator.next();
      Pending job = next.getValue();
      if (!job.task.isDone() && now - job.time < 90000) continue;
      OverlayProfile profile;
      try {
        if (!job.task.isDone()) {
          job.task.cancel(true);
          throw new TimeoutException();
        }
        profile = (OverlayProfile) job.task.get();
        if (profile == null) profile = OverlayProfile.empty("Stats unavailable", false);
      } catch (Exception failure) {
        profile = OverlayProfile.empty("Stats request failed or timed out", false);
      }
      iterator.remove();
      if (job.revision != revision) continue;
      cache.put(
          next.getKey(), new Entry(profile, now + (profile.provider.isEmpty() ? 45000 : 600000)));
    }
    if (sessionJob != null && (sessionJob.task.isDone() || now - sessionJob.time >= 90000)) {
      try {
        if (!sessionJob.task.isDone()) {
          sessionJob.task.cancel(true);
          throw new TimeoutException();
        }
        @SuppressWarnings("unchecked")
        Map<UUID, OverlayProfile> answers = (Map<UUID, OverlayProfile>) sessionJob.task.get();
        if (sessionJob.revision == revision)
          for (Map.Entry<UUID, OverlayProfile> answer : answers.entrySet()) {
            Entry entry = cache.get(answer.getKey());
            if (entry != null) entry.profile = entry.profile.merge(answer.getValue());
          }
      } catch (Exception failure) {
        /* Missing session data stays unavailable; bounded retry below. */
      }
      sessionJob = null;
    }
    if (!options.sessions
        || !options.providers.contains(Provider.BORDIC)
        || !options.keys.containsKey(Provider.BORDIC)) return;
    sessionQueue.retainAll(cache.keySet());
    for (Map.Entry<UUID, Entry> entry : cache.entrySet())
      if (entry.getValue().sessionExpires <= now) sessionQueue.add(entry.getKey());
    if (sessionJob != null || sessionQueue.isEmpty()) return;
    List<UUID> batch = new ArrayList<>();
    Iterator<UUID> ids = sessionQueue.iterator();
    while (ids.hasNext() && batch.size() < 10) {
      batch.add(ids.next());
      ids.remove();
    }
    FutureTask<Map<UUID, OverlayProfile>> task =
        new FutureTask<>(() -> transport.sessions(batch, options));
    try {
      worker.execute(task);
      sessionJob = new Pending(revision, now, task);
      for (UUID id : batch) {
        Entry e = cache.get(id);
        if (e != null) e.sessionExpires = now + 300000;
      }
    } catch (RejectedExecutionException full) {
      task.cancel(true);
      sessionQueue.addAll(batch);
    }
  }

  void invalidate() {
    revision++;
    transport.reset();
    for (Pending job : pending.values()) job.task.cancel(true);
    pending.clear();
    cache.clear();
    sessionQueue.clear();
    if (sessionJob != null) sessionJob.task.cancel(true);
    sessionJob = null;
    if (worker instanceof ThreadPoolExecutor) ((ThreadPoolExecutor) worker).purge();
  }

  int pendingCount() {
    return pending.size();
  }

  public void close() {
    closed = true;
    invalidate();
    worker.shutdownNow();
    transport.close();
  }
}
