package perihelion.integration.minecraft189.gui;

import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernControls;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.raven.RavenTheme;
import perihelion.integration.minecraft189.gui.theme.ThemePalette;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Shared native chrome for module panels, presets and HUD editing. */
public final class GuiTheme {
  public static final float HEADER = 13, ROW = 16;
  public static final float MODERN_BODY_SCALE = 1.3f, MODERN_TITLE_SCALE = 1.4f;

  private GuiTheme() {}

  public static ClickGuiModule preferences(ClientRuntime runtime) {
    for (ModuleEntry entry : runtime.modules().entries())
      if (entry.module() instanceof ClickGuiModule) return (ClickGuiModule) entry.module();
    return null;
  }

  public static ClickGuiModule.Font font(ClientRuntime runtime) {
    return font(preferences(runtime));
  }

  public static ThemePalette palette(ClientRuntime runtime) {
    ClickGuiModule preferences = preferences(runtime);
    return preferences == null ? ThemePalette.DEFAULT : preferences.palette();
  }

  public static ClickGuiModule.ClientStyle style(ClientRuntime runtime) {
    ClickGuiModule preferences = preferences(runtime);
    return preferences == null || preferences.modern()
        ? ClickGuiModule.ClientStyle.ADJUST
        : preferences.clientStyle.get();
  }

  public static boolean modern(ClientRuntime runtime) {
    ClickGuiModule preferences = preferences(runtime);
    return preferences != null && preferences.modern();
  }

  public static ClickGuiModule.Font font(ClickGuiModule preferences) {
    return preferences == null
        ? ClickGuiModule.Font.SANS
        : preferences.modern()
            ? preferences.modernFont.get()
            : preferences.clientStyle.get() == ClickGuiModule.ClientStyle.RAVEN
                ? ClickGuiModule.Font.MINECRAFT
                : preferences.font.get();
  }

  public static void configure(
      UiCanvas c, GuiFonts fonts, ClickGuiModule preferences, float pixels) {
    c.modern = preferences != null && preferences.modern();
    c.cornerRadius = c.modern ? preferences.cornerRadius.get().floatValue() : 4;
    if (!c.modern
        && preferences != null
        && preferences.clientStyle.get() == ClickGuiModule.ClientStyle.RAVEN) {
      RavenTheme.configure(c, fonts, preferences, pixels);
      return;
    }
    long now = System.currentTimeMillis();
    ClickGuiModule.Font choice = font(preferences);
    c.classic = false;
    c.textShadow = false;
    c.settingPainter = c.modern ? ModernControls.INSTANCE : null;
    boolean custom = preferences != null && !c.modern && preferences.customTextStyle.get();
    boolean bold = bold(preferences);
    c.font =
        choice == ClickGuiModule.Font.MINECRAFT
            ? fonts.minecraft(bold, pixels)
            : c.modern
                ? fonts.pixelFace(choice, bold, MODERN_BODY_SCALE, pixels)
                : fonts.get(choice, bold);
    c.sectionFont =
        choice == ClickGuiModule.Font.MINECRAFT
            ? fonts.minecraft(bold || custom && preferences.boldSections.get(), pixels)
            : c.modern
                ? fonts.pixelFace(choice, bold, MODERN_TITLE_SCALE, pixels)
                : fonts.get(choice, bold || custom && preferences.boldSections.get());
    c.pixelScale = pixels;
    c.text = !custom ? 0xffbfbfbf : preferences.text.get().resolvedArgb(now);
    c.enabled = !custom ? 0xffe5e5e5 : preferences.enabled.get().resolvedArgb(now);
    c.sectionText = !custom ? c.text : preferences.sectionText.get().resolvedArgb(now);
    c.selectedText = !custom ? c.enabled : preferences.selectionText.get().resolvedArgb(now);
    c.accent = !custom ? 0xff427d9e : preferences.accent.get().resolvedArgb(now);
    c.panel = alpha(preferences == null ? .75 : preferences.panelOpacity.get(), 0x141414);
    c.header = alpha(preferences == null ? .95 : preferences.headerOpacity.get(), c.accent);
    c.headerBottom = shade(c.header, .90f);
    c.muted = !custom ? shade(c.text, .82f) : preferences.mutedText.get().resolvedArgb(now);
    c.selection =
        !custom ? alpha(.20, 0x7b7e82) : preferences.selectionHighlight.get().resolvedArgb(now);
    c.hover = alpha((c.selection >>> 24) / 255.0 * .68, c.selection);
    c.activeButton = custom ? c.selection : alpha(.55, c.accent);
    if (c.modern) {
      ModernTheme.palette(c, preferences.palette());
    }
  }

  public static boolean bold(ClickGuiModule preferences) {
    return preferences != null
        && (preferences.modern()
            ? preferences.bold.get()
            : preferences.customTextStyle.get()
                && preferences.textWeight.get() == ClickGuiModule.TextWeight.BOLD);
  }

  public static int alpha(double opacity, int rgb) {
    return (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255) << 24 | rgb & 0xffffff;
  }

  /** Quiet enabled state that stays visible with every text style and survives hover overlays. */
  public static int enabledRow(UiCanvas canvas) {
    return alpha(.16, canvas.accent);
  }

  public static int overlay(UiCanvas canvas) {
    return Math.max(242, canvas.panel >>> 24) << 24 | canvas.panel & 0xffffff;
  }

  private static int shade(int color, float amount) {
    return color & 0xff000000
        | (int) ((color >> 16 & 255) * amount) << 16
        | (int) ((color >> 8 & 255) * amount) << 8
        | (int) ((color & 255) * amount);
  }

  public static void header(UiCanvas c, String title, float x, float y, float width) {
    if (c.classic) {
      RavenTheme.header(c, title, x, y, width);
      return;
    }
    if (c.modern) {
      ModernTheme.surface(c, x, y, width, HEADER, c.header);
      c.section(title, x + 5, c.sectionFont.centeredY(y, HEADER), width - 10, false);
      return;
    }
    c.gradient(x, y, width, HEADER, c.header, c.header, c.headerBottom, c.headerBottom);
    c.section(
        title,
        x + 3,
        (c.sectionFont == null ? c.font : c.sectionFont).centeredY(y, HEADER),
        width - 6,
        true);
  }

  public static void button(
      UiCanvas c,
      String title,
      float x,
      float y,
      float w,
      float h,
      boolean active,
      boolean enabled,
      boolean hover) {
    if (c.classic) {
      RavenTheme.button(c, title, x, y, w, h, active, enabled, hover);
      return;
    }
    if (c.modern) {
      ModernTheme.button(c, x, y, w, h, active, enabled, hover);
      c.center(
          title,
          x + 4,
          c.font.centeredY(y, h),
          w - 8,
          !enabled ? c.muted : active ? c.selectedText : c.text);
      return;
    }
    c.rect(x, y, w, h, c.panel);
    if (active || hover && enabled) c.rect(x, y, w, h, active ? c.activeButton : c.hover);
    c.center(
        title,
        x + 4,
        c.font.centeredY(y, h),
        w - 8,
        !enabled ? c.muted : active ? c.selectedText : c.text);
  }
}
