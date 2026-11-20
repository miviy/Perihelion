package perihelion.integration.minecraft189.account;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM at rest with an owner-only local key, atomic replacement and a last-good backup. */
public final class AccountVault {
  public static final int MAX_BYTES = 16 * 1024 * 1024;
  private final Path directory, file, key;
  private final SecureRandom random = new SecureRandom();
  private boolean writable;

  public AccountVault(Path directory) {
    this.directory = directory;
    file = directory.resolve("accounts.vault");
    key = directory.resolve("accounts.key");
  }

  public synchronized JsonObject load() throws Exception {
    Files.createDirectories(directory);
    restrict(directory, true);
    if (!Files.exists(file)) {
      if (Files.exists(file.resolveSibling("accounts.vault.bak")))
        throw new IOException(
            "Account file is missing; restore accounts.vault.bak before continuing.");
      keyBytes();
      writable = true;
      JsonObject empty = new JsonObject();
      empty.addProperty("version", 1);
      return empty;
    }
    byte[] bytes = readBounded(file);
    if (bytes.length < 32 || bytes[0] != 'P' || bytes[1] != 'A' || bytes[2] != 1)
      throw new IOException("Unrecognized account file. Existing files were preserved.");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(keyBytes(), "AES"),
        new GCMParameterSpec(128, Arrays.copyOfRange(bytes, 3, 15)));
    cipher.updateAAD(new byte[] {'P', 'A', 1});
    JsonObject json =
        new JsonParser()
            .parse(new String(cipher.doFinal(bytes, 15, bytes.length - 15), StandardCharsets.UTF_8))
            .getAsJsonObject();
    if (!json.has("version") || json.get("version").getAsInt() != 1)
      throw new IOException("Unsupported account file version. Existing files were preserved.");
    writable = true;
    return json;
  }

  public synchronized void save(JsonObject json) throws Exception {
    if (!writable) throw new IOException("Account storage is locked after a load failure.");
    byte[] plain = json.toString().getBytes(StandardCharsets.UTF_8);
    if (plain.length > MAX_BYTES - 64) throw new IOException("Account file is too large.");
    byte[] nonce = new byte[12];
    random.nextBytes(nonce);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(keyBytes(), "AES"),
        new GCMParameterSpec(128, nonce));
    cipher.updateAAD(new byte[] {'P', 'A', 1});
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.write(new byte[] {'P', 'A', 1});
    bytes.write(nonce);
    bytes.write(cipher.doFinal(plain));
    Path temporary = writeTemporary(bytes.toByteArray());
    try {
      if (Files.exists(file)) {
        Path backup = file.resolveSibling("accounts.vault.bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        restrict(backup, false);
      }
      moveIntoPlace(temporary, file);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /** Owner-only from creation (JDK temp files are rw------- on POSIX), flushed to disk. */
  private Path writeTemporary(byte[] bytes) throws IOException {
    Path temporary = Files.createTempFile(directory, "accounts-", ".tmp");
    try {
      restrict(temporary, false);
      try (FileChannel out = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) out.write(buffer);
        out.force(true);
      }
      return temporary;
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(temporary);
      throw failure;
    }
  }

  private static void moveIntoPlace(Path temporary, Path target) throws IOException {
    try {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Atomically creates {@code target}, failing if it exists; a hard link never replaces. */
  static void publishExclusively(Path temporary, Path target) throws IOException {
    try {
      Files.createLink(target, temporary);
    } catch (UnsupportedOperationException | FileSystemException noLinks) {
      if (noLinks instanceof FileAlreadyExistsException) throw (FileAlreadyExistsException) noLinks;
      // File systems without hard links. The JDK checks for the target and then renames, which
      // is not atomic on its own; createKey's directory lock makes it exclusive.
      Files.move(temporary, target);
    }
  }

  private byte[] keyBytes() throws Exception {
    // Unlocked fast path. Another instance may delete an empty key between any two checks, so a
    // missing file is treated like an empty one and revalidated under createKey's locks.
    long size;
    try {
      size = Files.size(key);
    } catch (NoSuchFileException missing) {
      size = 0;
    }
    if (size == 0) createKey();
    restrict(key, false);
    byte[] bytes = readBounded(key);
    if (bytes.length != 16) throw new IOException("Invalid account key.");
    return bytes;
  }

  /**
   * Runs under a lock on the account directory, so client instances starting together agree on
   * one key: a key another instance already used to encrypt accounts is never replaced.
   */
  private void createKey() throws Exception {
    // FileLock is held per process; this also serializes vaults within one JVM.
    synchronized (AccountVault.class) {
      Path lockFile = directory.resolve("accounts.lock");
      try (FileChannel channel =
              FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          FileLock ignored = channel.lock()) {
        restrict(lockFile, false);
        boolean data =
            Files.exists(file) || Files.exists(file.resolveSibling("accounts.vault.bak"));
        // An empty key from an interrupted write by an older build never encrypted anything.
        if (!data && Files.isRegularFile(key) && Files.size(key) == 0) Files.delete(key);
        if (Files.exists(key)) return; // Created by another instance, or kept beside data.
        if (data)
          throw new IOException(
              "Account key is missing. Restore accounts.key; no data was overwritten.");
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        // Written in full before it appears under its final name, so a failure leaves no key.
        Path temporary = writeTemporary(bytes);
        try {
          publishExclusively(temporary, key);
        } catch (FileAlreadyExistsException concurrent) {
          /* A process that does not take the lock published first; use its key. */
        } finally {
          Files.deleteIfExists(temporary);
        }
      }
    }
  }

  private static void restrict(Path path, boolean directory) throws IOException {
    try {
      Files.setPosixFilePermissions(
          path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
    } catch (UnsupportedOperationException ignored) {
      /* Windows relies on the user's profile ACL. */
    }
  }

  public static byte[] readBounded(Path path) throws IOException {
    if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES)
      throw new IOException("Choose a regular file under 16 MiB.");
    try (InputStream in = Files.newInputStream(path)) {
      return AccountHttp.read(in, MAX_BYTES);
    }
  }
}
