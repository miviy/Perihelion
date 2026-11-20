package perihelion.integration.minecraft189.account;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class AccountVaultTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private JsonObject data(String value) {
    JsonObject j = new JsonObject();
    j.addProperty("version", 1);
    j.addProperty("secret", value);
    return j;
  }

  @Test
  public void encryptedRoundTripOwnerPermissionsAndBackup() throws Exception {
    Path dir = folder.newFolder().toPath();
    AccountVault vault = new AccountVault(dir);
    vault.load();
    vault.save(data("private-refresh-token"));
    byte[] first = Files.readAllBytes(dir.resolve("accounts.vault"));
    assertFalse(new String(first, StandardCharsets.ISO_8859_1).contains("private-refresh-token"));
    assertEquals("private-refresh-token", new AccountVault(dir).load().get("secret").getAsString());
    vault.save(data("rotated"));
    assertArrayEquals(first, Files.readAllBytes(dir.resolve("accounts.vault.bak")));
    if (Files.getFileStore(dir).supportsFileAttributeView("posix"))
      assertEquals(
          PosixFilePermissions.fromString("rw-------"),
          Files.getPosixFilePermissions(dir.resolve("accounts.key")));
  }

  @Test
  public void tamperingNeverOverwritesLastGoodData() throws Exception {
    Path dir = folder.newFolder().toPath();
    AccountVault vault = new AccountVault(dir);
    vault.load();
    vault.save(data("private"));
    Path file = dir.resolve("accounts.vault");
    byte[] bytes = Files.readAllBytes(file);
    bytes[bytes.length - 1] ^= 1;
    Files.write(file, bytes);
    AccountVault damaged = new AccountVault(dir);
    assertThrows(Exception.class, damaged::load);
    assertThrows(Exception.class, () -> damaged.save(data("replace")));
    assertArrayEquals(bytes, Files.readAllBytes(file));
  }

  @Test
  public void missingKeyIsNeverRegeneratedOverAnExistingVault() throws Exception {
    Path dir = folder.newFolder().toPath();
    AccountVault vault = new AccountVault(dir);
    vault.load();
    vault.save(data("private"));
    Files.delete(dir.resolve("accounts.key"));
    assertThrows(Exception.class, () -> new AccountVault(dir).load());
    assertFalse(Files.exists(dir.resolve("accounts.key")));
  }

  @Test
  public void futureSchemaAndMissingPrimaryWithBackupFailClosed() throws Exception {
    Path dir = folder.newFolder().toPath();
    AccountVault vault = new AccountVault(dir);
    vault.load();
    JsonObject future = data("private");
    future.addProperty("version", 9);
    vault.save(future);
    assertThrows(Exception.class, () -> new AccountVault(dir).load());
    vault.save(data("new"));
    Files.delete(dir.resolve("accounts.vault"));
    assertThrows(Exception.class, () -> new AccountVault(dir).load());
  }

  @Test
  public void emptyKeyWithoutDataIsReplacedAndKeyCreationLeavesNoTemporaryFiles()
      throws Exception {
    Path dir = folder.newFolder().toPath();
    Files.createFile(dir.resolve("accounts.key"));
    AccountVault vault = new AccountVault(dir);
    vault.load();
    assertEquals(16, Files.size(dir.resolve("accounts.key")));
    vault.save(data("private"));
    assertEquals("private", new AccountVault(dir).load().get("secret").getAsString());
    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.tmp")) {
      assertFalse(files.iterator().hasNext());
    }
  }

  @Test
  public void emptyKeyBesideExistingDataFailsClosed() throws Exception {
    Path dir = folder.newFolder().toPath();
    AccountVault vault = new AccountVault(dir);
    vault.load();
    vault.save(data("private"));
    byte[] encrypted = Files.readAllBytes(dir.resolve("accounts.vault"));
    Files.write(dir.resolve("accounts.key"), new byte[0]);
    assertThrows(Exception.class, () -> new AccountVault(dir).load());
    assertEquals(0, Files.size(dir.resolve("accounts.key")));
    assertArrayEquals(encrypted, Files.readAllBytes(dir.resolve("accounts.vault")));
  }
}
