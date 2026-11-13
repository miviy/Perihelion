package perihelion.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;

/**
 * One serialized disk writer. Public methods copy caller-owned JSON before queueing work. Reload
 * discards unsaved debounced changes; completed/running writes precede its disk read.
 */
public final class ConfigStore {
  public static final long SAVE_DEBOUNCE_MILLIS = 750;
  private final Path file;
  private final Path backup;
  private final ClientLog log;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private final ScheduledThreadPoolExecutor writer;
  private final Object lock = new Object();
  private ScheduledFuture<?> debounce;
  private JsonObject latest;
  private long revision;
  private long generation;
  private boolean readOnly;
  private boolean dirty;
  private boolean closed;
  private volatile Throwable lastFailure;

  public ConfigStore(Path file, ClientLog log) {
    this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
    this.log = Objects.requireNonNull(log, "log");
    writer =
        new ScheduledThreadPoolExecutor(
            1,
            task -> {
              Thread thread = new Thread(task, "Perihelion configuration writer");
              thread.setDaemon(true);
              return thread;
            });
    writer.setRemoveOnCancelPolicy(true);
    // Debounce is cancelled explicitly; keep the immediate final flush during shutdown.
    writer.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
  }

  public Path file() {
    return file;
  }

  public Throwable lastFailure() {
    return lastFailure;
  }

  public boolean isReadOnly() {
    synchronized (lock) {
      return readOnly;
    }
  }

  public boolean isDirty() {
    synchronized (lock) {
      return dirty;
    }
  }

  /** Named presets share the existing serialized disk worker; listing never runs in rendering. */
  public Path profilesDirectory() {
    return file.getParent().resolve("configs");
  }

  public Path profilePath(String name) {
    if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,47}"))
      throw new IllegalArgumentException(
          "Use 1-48 letters, numbers, underscores or hyphens for a config name");
    String normalized = name;
    if (normalized.toLowerCase(Locale.ROOT).matches("con|prn|aux|nul|com[1-9]|lpt[1-9]"))
      throw new IllegalArgumentException("Reserved config name");
    return profilesDirectory().resolve(normalized + ".json");
  }

  private void checkProfilePath(Path path) throws IOException {
    if (Files.isSymbolicLink(profilesDirectory())
        || Files.isSymbolicLink(path)
        || Files.isSymbolicLink(path.resolveSibling(path.getFileName() + ".bak")))
      throw new IOException("Config links are not supported; copy the JSON file instead");
  }

  public CompletableFuture<List<String>> listProfiles() {
    return profileTask(
        () -> {
          Path directory = profilesDirectory();
          checkProfilePath(directory);
          if (!Files.exists(directory)) return Collections.emptyList();
          List<String> result = new ArrayList<>();
          try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
            for (Path entry : entries) {
              if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue;
              String name = entry.getFileName().toString();
              name = name.substring(0, name.length() - 5);
              try {
                if (!profilePath(name).equals(entry)) continue;
              } catch (IllegalArgumentException invalid) {
                continue;
              }
              if (result.size() == 1024)
                throw new IOException("Too many configs; keep at most 1024 in the folder");
              result.add(name);
            }
          }
          Collections.sort(result);
          return Collections.unmodifiableList(result);
        });
  }

  public CompletableFuture<Path> openProfilePath(String name) {
    final Path path = name == null ? profilesDirectory() : profilePath(name);
    return profileTask(
        () -> {
          checkProfilePath(path);
          if (name == null) Files.createDirectories(path);
          else if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Config does not exist");
          return path;
        });
  }

  public CompletableFuture<LoadResult> readProfile(String name) {
    final Path path = profilePath(name);
    return profileTask(
        () -> {
          checkProfilePath(path);
          JsonObject document = readFile(path);
          if (JsonDocuments.schema(document) > 1)
            throw new IOException("Config requires a newer client version");
          if (!document.has("modules") || !document.get("modules").isJsonObject())
            throw new IOException("Config must contain a modules object");
          return new LoadResult(document, false, false, path);
        });
  }

  public CompletableFuture<Void> writeProfile(String name, JsonObject document, boolean update) {
    final Path path = profilePath(name);
    final JsonObject snapshot = JsonDocuments.copy(document);
    return profileTask(
        () -> {
          checkProfilePath(path);
          if (update && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Config does not exist; use save to create it");
          if (!update && Files.exists(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Config already exists; use update to replace it");
          byte[] bytes = encode(snapshot);
          if (update) replaceFile(path, bytes);
          else createProfile(path, bytes);
          return null;
        });
  }

  public CompletableFuture<Void> deleteProfile(String name) {
    final Path path = profilePath(name);
    return profileTask(
        () -> {
          checkProfilePath(path);
          if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Config does not exist");
          // Retain the deleted preset for manual recovery, outside the list of active JSON files.
          Files.move(
              path, path.resolveSibling(path.getFileName() + ".deleted-" + UUID.randomUUID()));
          return null;
        });
  }

  /**
   * Installs ordinary editable presets once. Separate receipts survive profile deletion/loading.
   */
  public CompletableFuture<Void> installProfiles(Map<String, JsonObject> profiles) {
    Map<Path, JsonObject> snapshots = new LinkedHashMap<>();
    profiles.forEach(
        (name, document) -> snapshots.put(profilePath(name), JsonDocuments.copy(document)));
    return profileTask(
        () -> {
          for (Map.Entry<Path, JsonObject> entry : snapshots.entrySet()) {
            Path path = entry.getKey();
            Path receipt = path.resolveSibling("." + path.getFileName() + ".installed");
            checkProfilePath(path);
            if (Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)) {
              if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Invalid bundled config receipt");
              continue;
            }
            Files.createDirectories(profilesDirectory());
            // Existing user presets, backups and deletion recovery files take precedence.
            boolean existing =
                Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(
                        path.resolveSibling(path.getFileName() + ".bak"),
                        LinkOption.NOFOLLOW_LINKS);
            try (DirectoryStream<Path> deleted =
                Files.newDirectoryStream(profilesDirectory(), path.getFileName() + ".deleted-*")) {
              existing |= deleted.iterator().hasNext();
            }
            if (!existing) createProfile(path, encode(entry.getValue()));
            Files.createFile(receipt);
          }
          return null;
        });
  }

  private void createProfile(Path path, byte[] bytes) throws IOException {
    Files.createDirectories(profilesDirectory());
    Path temporary = Files.createTempFile(profilesDirectory(), ".config-", ".tmp");
    try {
      forceWrite(temporary, bytes);
      Files.move(temporary, path);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private <T> CompletableFuture<T> profileTask(Callable<T> operation) {
    CompletableFuture<T> result = new CompletableFuture<>();
    synchronized (lock) {
      requireOpen();
      writer.execute(
          () -> {
            try {
              result.complete(operation.call());
            } catch (Throwable failure) {
              Failures.rethrowFatal(failure);
              result.completeExceptionally(failure);
            }
          });
    }
    return result;
  }

  private byte[] encode(JsonObject snapshot) throws IOException {
    if (JsonDocuments.schema(snapshot) > 1)
      throw new IOException("Cannot write unsupported configuration schema");
    byte[] bytes = (gson.toJson(snapshot) + "\n").getBytes(StandardCharsets.UTF_8);
    if (bytes.length > JsonDocuments.MAX_BYTES)
      throw new IOException("Configuration exceeds 4 MiB size limit");
    return bytes;
  }

  /** Blocking startup read. Ordinary filesystem/format errors are recovered and logged. */
  public LoadResult load() {
    return reload().join();
  }

  public CompletableFuture<LoadResult> reload() {
    synchronized (lock) {
      requireOpen();
      cancelDebounce();
      latest = null;
      dirty = false;
      revision++;
      final long expectedGeneration = ++generation;
      CompletableFuture<LoadResult> result = new CompletableFuture<>();
      writer.execute(
          () -> {
            try {
              LoadResult loaded = readDocument();
              synchronized (lock) {
                if (generation == expectedGeneration) readOnly = loaded.readOnly();
              }
              result.complete(loaded);
            } catch (Throwable failure) {
              Failures.rethrowFatal(failure);
              report("Could not load configuration", failure);
              synchronized (lock) {
                if (generation == expectedGeneration) readOnly = true;
              }
              result.complete(new LoadResult(defaultDocument(), true, false, null));
            }
          });
      return result;
    }
  }

  public void save(JsonObject document) {
    JsonObject snapshot = JsonDocuments.copy(document);
    synchronized (lock) {
      requireWritable();
      latest = snapshot;
      dirty = true;
      final long expectedRevision = ++revision;
      final long expectedGeneration = generation;
      cancelDebounce();
      debounce =
          writer.schedule(
              () -> {
                synchronized (lock) {
                  if (revision != expectedRevision || generation != expectedGeneration) return;
                  debounce = null;
                }
                writeSnapshot(
                    snapshot, expectedRevision, expectedGeneration, new CompletableFuture<Void>());
              },
              SAVE_DEBOUNCE_MILLIS,
              TimeUnit.MILLISECONDS);
    }
  }

  public CompletableFuture<Void> saveNow(JsonObject document) {
    JsonObject snapshot = JsonDocuments.copy(document);
    synchronized (lock) {
      requireWritable();
      latest = snapshot;
      dirty = true;
      revision++;
      cancelDebounce();
      return enqueueWrite(snapshot, revision, generation);
    }
  }

  /** Flushes the latest snapshot; failure leaves it dirty for an explicit retry. */
  public CompletableFuture<Void> flush() {
    synchronized (lock) {
      requireOpen();
      cancelDebounce();
      if (!dirty || readOnly || latest == null) return CompletableFuture.completedFuture(null);
      return enqueueWrite(latest, revision, generation);
    }
  }

  /** Starts graceful shutdown and waits no longer than the supplied timeout. */
  public boolean close(long timeout, TimeUnit unit) {
    if (timeout < 0) throw new IllegalArgumentException("Negative shutdown timeout");
    Objects.requireNonNull(unit, "unit");
    synchronized (lock) {
      if (!closed) {
        cancelDebounce();
        if (dirty && !readOnly && latest != null) enqueueWrite(latest, revision, generation);
        closed = true;
        writer.shutdown();
      }
    }
    try {
      boolean stopped = writer.awaitTermination(timeout, unit);
      if (!stopped) log.warn("Configuration writer did not stop before shutdown deadline");
      return stopped && !isDirty();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while waiting for configuration shutdown");
      return false;
    }
  }

  private CompletableFuture<Void> enqueueWrite(
      JsonObject snapshot, long expectedRevision, long expectedGeneration) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    writer.execute(() -> writeSnapshot(snapshot, expectedRevision, expectedGeneration, result));
    return result;
  }

  private void writeSnapshot(
      JsonObject snapshot,
      long expectedRevision,
      long expectedGeneration,
      CompletableFuture<Void> result) {
    try {
      synchronized (lock) {
        if (expectedGeneration != generation)
          throw new CancellationException("Save superseded by configuration reload");
        if (readOnly) throw new IllegalStateException("Configuration is read-only");
      }
      replaceFile(file, encode(snapshot));
      synchronized (lock) {
        if (revision == expectedRevision && generation == expectedGeneration) dirty = false;
      }
      lastFailure = null;
      result.complete(null);
    } catch (CancellationException cancelled) {
      result.completeExceptionally(cancelled);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      report("Could not save configuration to " + file, failure);
      result.completeExceptionally(failure);
    }
  }

  private LoadResult readDocument() {
    lastFailure = null;
    boolean safeToReplace = true;
    if (!Files.notExists(file)) {
      try {
        JsonObject document = readFile(file);
        return new LoadResult(document, JsonDocuments.schema(document) > 1, false, file);
      } catch (IOException | RuntimeException invalid) {
        report("Invalid or unreadable configuration at " + file, invalid);
        safeToReplace = preserveInvalid(file);
      }
    }
    if (!Files.notExists(backup)) {
      try {
        JsonObject document = readFile(backup);
        boolean newer = JsonDocuments.schema(document) > 1;
        log.warn("Recovered configuration from " + backup);
        return new LoadResult(document, !safeToReplace || newer, true, backup);
      } catch (IOException | RuntimeException invalid) {
        report("Invalid or unreadable configuration backup at " + backup, invalid);
      }
    }
    return new LoadResult(defaultDocument(), !safeToReplace, false, null);
  }

  private JsonObject readFile(Path source) throws IOException {
    if (Files.size(source) > JsonDocuments.MAX_BYTES)
      throw new IOException("Configuration exceeds 4 MiB size limit");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream input = Files.newInputStream(source)) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (bytes.size() + count > JsonDocuments.MAX_BYTES)
          throw new IOException("Configuration exceeds 4 MiB size limit");
        bytes.write(buffer, 0, count);
      }
    }
    try (InputStreamReader reader =
        new InputStreamReader(
            new ByteArrayInputStream(bytes.toByteArray()),
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT))) {
      JsonObject result = JsonDocuments.read(reader);
      JsonDocuments.schema(result);
      return result;
    }
  }

  private void replaceFile(Path destination, byte[] bytes) throws IOException {
    Path file = destination;
    Path backup = file.resolveSibling(file.getFileName() + ".bak");
    Path parent = file.getParent();
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, file.getFileName() + ".", ".tmp");
    try {
      forceWrite(temporary, bytes);
      if (!Files.notExists(file)) {
        boolean valid;
        try {
          JsonObject current = readFile(file);
          if (JsonDocuments.schema(current) > 1) {
            synchronized (lock) {
              if (file.equals(this.file)) readOnly = true;
            }
            throw new UnsupportedSchemaException();
          }
          valid = true;
        } catch (UnsupportedSchemaException newer) {
          throw newer;
        } catch (IOException | RuntimeException invalid) {
          valid = false;
          if (!preserveInvalid(file))
            throw new IOException("Cannot preserve existing configuration", invalid);
        }
        if (valid) {
          Path backupTemporary =
              Files.createTempFile(parent, file.getFileName() + ".backup.", ".tmp");
          try {
            Files.copy(file, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel =
                FileChannel.open(backupTemporary, StandardOpenOption.WRITE)) {
              channel.force(true);
            }
            atomicReplace(backupTemporary, backup);
          } finally {
            Files.deleteIfExists(backupTemporary);
          }
        }
      }
      atomicReplace(temporary, file);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private boolean preserveInvalid(Path source) {
    Path preserved = source.resolveSibling(source.getFileName() + ".corrupt-" + UUID.randomUUID());
    try {
      Files.copy(source, preserved);
      log.warn("Preserved unreadable configuration at " + preserved);
      return true;
    } catch (IOException | RuntimeException failure) {
      report("Cannot preserve configuration; automatic writes disabled", failure);
      return false;
    }
  }

  private static void forceWrite(Path path, byte[] bytes) throws IOException {
    try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) channel.write(buffer);
      channel.force(true);
    }
  }

  private static void atomicReplace(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static JsonObject defaultDocument() {
    JsonObject result = new JsonObject();
    result.addProperty("schemaVersion", 1);
    result.add("modules", new JsonObject());
    return result;
  }

  private void cancelDebounce() {
    if (debounce != null) {
      debounce.cancel(false);
      debounce = null;
    }
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("Configuration store is closed");
  }

  private void requireWritable() {
    requireOpen();
    if (readOnly) throw new IllegalStateException("Configuration schema is read-only");
  }

  private void report(String message, Throwable failure) {
    lastFailure = failure;
    log.error(message, failure);
  }

  private static final class UnsupportedSchemaException extends IOException {
    UnsupportedSchemaException() {
      super("Existing configuration has a newer schema; preserving it");
    }
  }
}
