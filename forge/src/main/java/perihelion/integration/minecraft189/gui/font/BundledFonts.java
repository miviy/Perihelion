package perihelion.integration.minecraft189.gui.font;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import perihelion.integration.minecraft189.gui.ClickGuiModule;

/** Font data only; no GL objects, global font registration, or runtime network requests. */
public final class BundledFonts {
  private BundledFonts() {}

  public static Font load(ClickGuiModule.Font choice, boolean bold, float size) {
    String resource = choice.resource(bold);
    if (resource == null) {
      String family =
          choice == ClickGuiModule.Font.MONO
              ? Font.MONOSPACED
              : choice == ClickGuiModule.Font.SERIF ? Font.SERIF : Font.SANS_SERIF;
      return new Font(family, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
    }
    try (InputStream input = BundledFonts.class.getResourceAsStream(resource)) {
      if (input == null) throw new IOException("Missing bundled font: " + resource);
      // The selected file already contains the real weight; do not synthesize bold over it.
      return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(size);
    } catch (IOException | FontFormatException failure) {
      throw new IllegalStateException("Cannot load " + resource, failure);
    }
  }
}
