package perihelion.integration.minecraft189.gui;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.*;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import perihelion.integration.minecraft189.gui.font.BundledFonts;
import perihelion.integration.minecraft189.gui.font.GuiFonts;

public class BundledFontsTest {
  @Test
  public void everyBundledChoiceLoadsRealRegularAndBoldWithValidMetrics() {
    Set<String> families = new HashSet<>();
    FontRenderContext context = new FontRenderContext(null, true, true);
    for (ClickGuiModule.Font choice : ClickGuiModule.Font.values()) {
      if (choice.resource(false) == null) continue;
      Font regular = BundledFonts.load(choice, false, 28),
          bold = BundledFonts.load(choice, true, 28);
      assertNotEquals(choice.name(), regular.getFontName(), bold.getFontName());
      assertTrue(choice.name(), regular.canDisplay('W') && bold.canDisplay('W'));
      assertTrue(regular.getStringBounds("Menu scale", context).getWidth() > 0);
      assertTrue(bold.getStringBounds("Menu scale", context).getWidth() > 0);
      families.add(regular.getFamily());
    }
    assertEquals(9, families.size());
  }

  @Test
  public void bundledFilesAndRetainedLicensesMatchPinnedProvenance() throws Exception {
    JsonObject manifest;
    try (Reader r =
        new InputStreamReader(
            getClass().getResourceAsStream("/assets/perihelion/fonts/provenance.json"), "UTF-8")) {
      manifest = new JsonParser().parse(r).getAsJsonObject();
    }
    for (JsonElement element : manifest.getAsJsonArray("families")) {
      JsonObject family = element.getAsJsonObject();
      assertTrue(family.get("revision").getAsString().matches("[a-f0-9]{40}"));
      assertEquals(
          family.get("licenseSha256").getAsString(),
          hash("/" + family.get("licenseFile").getAsString()));
      for (JsonElement face : family.getAsJsonArray("faces")) {
        JsonObject value = face.getAsJsonObject();
        assertTrue(value.get("static").getAsBoolean());
        assertEquals(
            value.get("sha256").getAsString(),
            hash("/assets/perihelion/fonts/" + value.get("file").getAsString()));
      }
    }
  }

  @Test
  public void nativeBoldSurvivesColorsAndResetWithoutChangingText() {
    assertEquals("§lPlain", GuiFonts.forceBold("Plain"));
    assertEquals("§lA§c§lB§r§lC", GuiFonts.forceBold("A§cB§rC"));
  }

  private String hash(String path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = getClass().getResourceAsStream(path)) {
      assertNotNull(path, in);
      byte[] bytes = new byte[8192];
      int count;
      while ((count = in.read(bytes)) != -1) digest.update(bytes, 0, count);
    }
    StringBuilder out = new StringBuilder();
    for (byte b : digest.digest()) out.append(String.format("%02x", b & 255));
    return out.toString();
  }
}
