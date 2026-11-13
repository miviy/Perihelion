package perihelion.core.config;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;

public class ConfigStoreTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private final List<ConfigStore> stores = new ArrayList<>();

  @After
  public void closeStores() {
    for (ConfigStore store : stores) store.close(2, TimeUnit.SECONDS);
  }

  private ConfigStore store(Path file) {
    ConfigStore store = new ConfigStore(file, ClientLog.NONE);
    stores.add(store);
    return store;
  }

  private Path file() {
    return folder.getRoot().toPath().resolve("config/perihelion/client.json");
  }

  private static JsonObject document(int value) {
    JsonObject document = new JsonObject();
    document.addProperty("schemaVersion", 1);
    document.addProperty("value", value);
    JsonObject unknown = new JsonObject();
    unknown.addProperty("future", "preserve me");
    document.add("unknown", unknown);
    return document;
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  private static JsonObject read(Path file) throws IOException {
    return JsonDocuments.read(
        new StringReader(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
  }

  private static void await(CompletableFuture<?> future) throws Exception {
    future.get(3, TimeUnit.SECONDS);
  }

  private static JsonObject preset(int value) {
    JsonObject result = document(value);
    result.add("modules", new JsonObject());
    return result;
  }

  @Test
  public void bundledProfilesUseNormalCrudAndNeverOverwriteOrResurrect() throws Exception {
    ConfigStore store = store(file());
    java.util.Map<String, JsonObject> presets = new java.util.LinkedHashMap<>();
    presets.put("default", preset(1));
    presets.put("hypxile", preset(2));
    CompletableFuture<Void> install = store.installProfiles(presets);
    presets.get("hypxile").addProperty("value", 99);
    await(install);
    assertEquals(java.util.Arrays.asList("default", "hypxile"), store.listProfiles().get());
    assertEquals(2, store.readProfile("hypxile").get().json().get("value").getAsInt());
    assertFalse(Files.exists(file()));
    await(store.writeProfile("hypxile", preset(3), true));
    await(store.installProfiles(presets));
    assertEquals(3, store.readProfile("hypxile").get().json().get("value").getAsInt());
    await(store.deleteProfile("hypxile"));
    await(store.deleteProfile("default"));
    assertTrue(store.close(2, TimeUnit.SECONDS));
    ConfigStore restarted = store(file());
    await(restarted.installProfiles(presets));
    assertTrue(restarted.listProfiles().get().isEmpty());
    await(restarted.writeProfile("hypxile", preset(4), false));
    assertEquals(4, restarted.readProfile("hypxile").get().json().get("value").getAsInt());
  }

  @Test
  public void bundledInstallationPreservesExistingAndPreviouslyDeletedUserProfiles()
      throws Exception {
    ConfigStore store = store(file());
    await(store.writeProfile("default", preset(7), false));
    await(store.writeProfile("hypxile", preset(8), false));
    await(store.deleteProfile("hypxile"));
    java.util.Map<String, JsonObject> presets = new java.util.LinkedHashMap<>();
    presets.put("default", preset(1));
    presets.put("hypxile", preset(2));
    await(store.installProfiles(presets));
    assertEquals(java.util.Collections.singletonList("default"), store.listProfiles().get());
    assertEquals(7, store.readProfile("default").get().json().get("value").getAsInt());
    assertFalse(Files.exists(store.profilePath("default").resolveSibling("default.json.bak")));
    await(store.installProfiles(presets));
    assertFalse(Files.exists(store.profilePath("hypxile")));
  }

  @Test
  public void bundledInstallationRejectsLinksAndRetriesAfterOrdinaryIoFailure() throws Exception {
    ConfigStore store = store(file());
    Path directory = store.profilesDirectory();
    Files.createDirectories(directory);
    Path outside = folder.getRoot().toPath().resolve("untouched.json");
    write(outside, "untouched");
    Files.createSymbolicLink(store.profilePath("default"), outside);
    java.util.Map<String, JsonObject> presets =
        java.util.Collections.singletonMap("default", preset(1));
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> await(store.installProfiles(presets)));
    assertEquals("untouched", new String(Files.readAllBytes(outside), StandardCharsets.UTF_8));
    Files.delete(store.profilePath("default"));
    Path receipt = directory.resolve(".default.json.installed");
    Files.createSymbolicLink(receipt, outside);
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> await(store.installProfiles(presets)));
    assertFalse(Files.exists(store.profilePath("default")));
    Files.delete(receipt);
    await(store.installProfiles(presets));
    assertEquals(1, store.readProfile("default").get().json().get("value").getAsInt());
  }

  @Test
  public void bundledInstallationLeavesMalformedUserProfileUntouched() throws Exception {
    ConfigStore store = store(file());
    Path path = store.profilePath("hypxile");
    write(path, "{broken");
    await(store.installProfiles(java.util.Collections.singletonMap("hypxile", preset(1))));
    assertEquals("{broken", new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    await(store.deleteProfile("hypxile"));
    await(store.installProfiles(java.util.Collections.singletonMap("hypxile", preset(1))));
    assertTrue(store.listProfiles().get().isEmpty());
  }

  @Test
  public void namedProfilesCreateUpdateBackupAndRecoverableDelete() throws Exception {
    ConfigStore store = store(file());
    store.load();
    assertTrue(store.listProfiles().get().isEmpty());
    JsonObject source = preset(1);
    CompletableFuture<Void> created = store.writeProfile("Building", source, false);
    source.addProperty("value", 99);
    await(created);
    assertEquals(java.util.Arrays.asList("Building"), store.listProfiles().get());
    assertEquals(1, store.readProfile("Building").get().json().get("value").getAsInt());
    assertThrows(
        java.util.concurrent.ExecutionException.class,
        () -> await(store.writeProfile("Building", preset(2), false)));
    await(store.writeProfile("Building", preset(2), true));
    Path path = store.profilePath("Building");
    assertEquals(1, read(path.resolveSibling("Building.json.bak")).get("value").getAsInt());
    assertEquals(2, store.readProfile("Building").get().json().get("value").getAsInt());
    await(store.deleteProfile("Building"));
    assertTrue(store.listProfiles().get().isEmpty());
    try (Stream<Path> paths = Files.list(store.profilesDirectory())) {
      Path recovery =
          paths
              .filter(p -> p.getFileName().toString().startsWith("Building.json.deleted-"))
              .findFirst()
              .get();
      assertEquals(2, read(recovery).get("value").getAsInt());
    }
    assertFalse(Files.exists(file())); // Profiles do not replace the working file.
  }

  @Test
  public void badNamesLinksAndMissingUpdatesNeverWriteOutsideProfiles() throws Exception {
    ConfigStore store = store(file());
    store.load();
    for (String name : new String[] {"", "../escape", "a/b", "a\\b", ".hidden", "a.json", "CON"})
      assertThrows(
          IllegalArgumentException.class, () -> store.writeProfile(name, preset(1), false));
    assertThrows(
        java.util.concurrent.ExecutionException.class,
        () -> await(store.writeProfile("missing", preset(1), true)));
    Path outside = folder.getRoot().toPath().resolve("outside.json");
    write(outside, preset(8).toString());
    Files.createDirectories(store.profilesDirectory());
    Files.createSymbolicLink(store.profilePath("linked"), outside);
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> store.readProfile("linked").get());
    assertThrows(
        java.util.concurrent.ExecutionException.class,
        () -> await(store.writeProfile("linked", preset(2), true)));
    assertTrue(store.listProfiles().get().isEmpty());
    assertEquals(8, read(outside).get("value").getAsInt());
  }

  @Test
  public void invalidAndFutureProfilesFailWithoutFallingBackOrLockingWorkingConfig()
      throws Exception {
    ConfigStore store = store(file());
    store.load();
    Path path = store.profilePath("broken");
    write(path, "{bad");
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> store.readProfile("broken").get());
    write(path, "{\"schemaVersion\":2,\"modules\":{}}");
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> store.readProfile("broken").get());
    assertThrows(
        java.util.concurrent.ExecutionException.class,
        () -> await(store.writeProfile("broken", preset(2), true)));
    assertFalse(store.isReadOnly());
    await(store.saveNow(preset(4)));
    assertEquals(4, read(file()).get("value").getAsInt());
    write(path, "{}");
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> store.readProfile("broken").get());
  }

  @Test
  public void missingFileUsesDefaultsWithoutWritingAndLoadResultIsDefensive() {
    ConfigStore store = store(file());
    LoadResult loaded = store.load();
    assertNull(loaded.source());
    assertFalse(loaded.readOnly());
    assertFalse(loaded.recoveredFromBackup());
    assertEquals(1, loaded.json().get("schemaVersion").getAsInt());
    loaded.json().addProperty("schemaVersion", 99);
    assertEquals(1, loaded.json().get("schemaVersion").getAsInt());
    assertFalse(Files.exists(file()));
    assertFalse(store.isDirty());
  }

  @Test
  public void savesDefensiveSnapshotAndRetainsLastValidBackup() throws Exception {
    Path file = file();
    ConfigStore store = store(file);
    store.load();
    JsonObject first = document(1);
    CompletableFuture<Void> saved = store.saveNow(first);
    first.addProperty("value", 999);
    first.getAsJsonObject("unknown").addProperty("future", "changed");
    await(saved);
    assertEquals(1, read(file).get("value").getAsInt());
    assertEquals("preserve me", read(file).getAsJsonObject("unknown").get("future").getAsString());
    await(store.saveNow(document(2)));
    assertEquals(2, read(file).get("value").getAsInt());
    assertEquals(1, read(file.resolveSibling("client.json.bak")).get("value").getAsInt());
    assertFalse(store.isDirty());
    assertNull(store.lastFailure());
    try (Stream<Path> paths = Files.list(file.getParent())) {
      assertFalse(paths.anyMatch(path -> path.toString().endsWith(".tmp")));
    }
  }

  @Test
  public void reloadDiscardsPendingDebounceAndUsesExternalDiskChange() throws Exception {
    Path file = file();
    ConfigStore store = store(file);
    await(store.saveNow(document(1)));
    store.save(document(2));
    assertTrue(store.isDirty());
    write(file, document(3).toString());
    LoadResult loaded = store.reload().get(3, TimeUnit.SECONDS);
    assertEquals(3, loaded.json().get("value").getAsInt());
    await(store.flush());
    assertFalse(store.isDirty());
    assertEquals(3, read(file).get("value").getAsInt());
  }

  @Test
  public void debounceFlushAndGracefulCloseSaveOnlyLatestSnapshot() throws Exception {
    Path file = file();
    ConfigStore store = store(file);
    store.save(document(1));
    store.save(document(2));
    assertFalse(Files.exists(file));
    await(store.flush());
    assertEquals(2, read(file).get("value").getAsInt());
    store.save(document(3));
    assertTrue(store.close(2, TimeUnit.SECONDS));
    assertEquals(3, read(file).get("value").getAsInt());
    assertTrue(store.close(2, TimeUnit.SECONDS));
    assertThrows(IllegalStateException.class, () -> store.save(document(4)));
    assertThrows(IllegalStateException.class, store::reload);
  }

  @Test
  public void scheduledDebounceEventuallyPersists() throws Exception {
    Path file = file();
    CountDownLatch saved = new CountDownLatch(1);
    ConfigStore store = store(file);
    store.save(document(7));
    Thread poll =
        new Thread(
            () -> {
              long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
              while (System.nanoTime() < deadline && store.isDirty()) {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                  return;
                }
              }
              if (!store.isDirty()) saved.countDown();
            });
    poll.setDaemon(true);
    poll.start();
    assertTrue(saved.await(3, TimeUnit.SECONDS));
    assertEquals(7, read(file).get("value").getAsInt());
  }

  @Test
  public void malformedPrimaryIsPreservedAndValidBackupRecovered() throws Exception {
    Path file = file();
    String malformed = "{\"schemaVersion\":1, broken";
    write(file, malformed);
    Path backup = file.resolveSibling("client.json.bak");
    write(backup, document(8).toString());
    ConfigStore store = store(file);
    LoadResult loaded = store.load();
    assertTrue(loaded.recoveredFromBackup());
    assertEquals(backup, loaded.source());
    assertEquals(8, loaded.json().get("value").getAsInt());
    assertFalse(loaded.readOnly());
    assertNotNull(store.lastFailure());
    try (Stream<Path> paths = Files.list(file.getParent())) {
      Path preserved =
          paths
              .filter(path -> path.getFileName().toString().startsWith("client.json.corrupt-"))
              .findFirst()
              .get();
      assertEquals(malformed, new String(Files.readAllBytes(preserved), StandardCharsets.UTF_8));
    }
    await(store.saveNow(document(9)));
    assertEquals(8, read(backup).get("value").getAsInt());
    assertEquals(9, read(file).get("value").getAsInt());
  }

  @Test
  public void unsupportedSchemaRemainsReadOnlyIncludingExternalReplacement() throws Exception {
    Path file = file();
    JsonObject newer = document(9);
    newer.addProperty("schemaVersion", 2);
    write(file, newer.toString());
    ConfigStore store = store(file);
    LoadResult loaded = store.load();
    assertTrue(loaded.readOnly());
    assertTrue(store.isReadOnly());
    assertThrows(IllegalStateException.class, () -> store.saveNow(document(1)));
    assertEquals(newer, read(file));
    write(file, document(1).toString());
    await(store.reload());
    assertFalse(store.isReadOnly());
    write(file, newer.toString());
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> await(store.saveNow(document(2))));
    assertTrue(store.isReadOnly());
    assertEquals(newer, read(file));
  }

  @Test
  public void failedWriteLeavesDirtyStateAndCanRetryWithoutLosingExistingFile() throws Exception {
    Path blockedParent = folder.getRoot().toPath().resolve("blocking-file");
    write(blockedParent, "keep me");
    ConfigStore store = store(blockedParent.resolve("client.json"));
    assertThrows(
        java.util.concurrent.ExecutionException.class, () -> await(store.saveNow(document(2))));
    assertTrue(store.isDirty());
    assertNotNull(store.lastFailure());
    assertEquals("keep me", new String(Files.readAllBytes(blockedParent), StandardCharsets.UTF_8));
    Files.delete(blockedParent);
    await(store.flush());
    assertFalse(store.isDirty());
    assertNull(store.lastFailure());
    assertEquals(2, read(blockedParent.resolve("client.json")).get("value").getAsInt());
  }

  @Test
  public void concurrentImmediateSavesCannotOverwriteFinalRevision() throws Exception {
    Path file = file();
    ConfigStore store = store(file);
    List<CompletableFuture<Void>> writes =
        java.util.Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      final int thread = i;
      Thread producer =
          new Thread(
              () -> {
                for (int n = 0; n < 8; n++) writes.add(store.saveNow(document(thread * 8 + n)));
              });
      threads.add(producer);
      producer.start();
    }
    for (Thread thread : threads) thread.join();
    await(store.saveNow(document(100)));
    for (CompletableFuture<Void> saved : writes) await(saved);
    assertEquals(100, read(file).get("value").getAsInt());
    assertFalse(store.isDirty());
  }

  @Test
  public void rejectsDuplicateTrailingCommentAndMalformedJson() {
    for (String invalid :
        new String[] {
          "{\"x\":1,\"x\":2}",
          "{\"x\":{\"n\":1,\"n\":2}}",
          "{} {}",
          "{} trailing",
          "{/*comment*/\"x\":1}",
          "{\"x\":NaN}",
          "{\"x\":01}",
          "{'x':1}",
          "{x:1}",
          "{\"x\":1,}",
          "[]",
          "",
          "{\"x\":TRUE}",
          "{\"x\":\"line\nfeed\"}"
        }) {
      assertThrows(invalid, IOException.class, () -> JsonDocuments.read(new StringReader(invalid)));
    }
  }

  @Test
  public void rejectsOversizeDepthAndInvalidSchemaWithoutCrashing() throws Exception {
    Path file = file();
    StringBuilder deep = new StringBuilder("{\"x\":");
    for (int i = 0; i < 65; i++) deep.append('[');
    deep.append('0');
    for (int i = 0; i < 65; i++) deep.append(']');
    deep.append('}');
    assertThrows(IOException.class, () -> JsonDocuments.read(new StringReader(deep.toString())));
    byte[] oversized = new byte[JsonDocuments.MAX_BYTES + 1];
    Files.createDirectories(file.getParent());
    Files.write(file, oversized);
    ConfigStore store = store(file);
    assertNull(store.load().source());
    assertNotNull(store.lastFailure());
    write(file, "{\"schemaVersion\":1.5}");
    assertNull(store.load().source());
    assertNotNull(store.lastFailure());
    byte[] invalidUtf8 =
        new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xc0, (byte) 0xaf, '"', '}'};
    Files.write(file, invalidUtf8);
    assertNull(store.load().source());
  }

  @Test
  public void rejectsCyclicAndNonFiniteCallerSnapshots() {
    ConfigStore store = store(file());
    JsonObject cyclic = document(1);
    cyclic.add("self", cyclic);
    assertThrows(IllegalArgumentException.class, () -> store.save(cyclic));
    JsonObject infinite = document(1);
    infinite.addProperty("rate", Double.NaN);
    assertThrows(IllegalArgumentException.class, () -> store.save(infinite));
  }
}
