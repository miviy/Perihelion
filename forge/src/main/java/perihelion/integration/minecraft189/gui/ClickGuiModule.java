package perihelion.integration.minecraft189.gui;

import perihelion.core.input.Keybind;
import perihelion.core.module.*;
import perihelion.core.setting.*;
import perihelion.integration.minecraft189.gui.theme.ThemeModule;
import perihelion.integration.minecraft189.gui.theme.ThemePalette;

/** Configuration and one-shot opener for this frontend. Gameplay modules never depend on it. */
public final class ClickGuiModule extends ClientModule {
  public enum Visuals {
    NEW,
    OLD
  }

  public enum ClientStyle {
    ADJUST,
    RAVEN
  }

  public enum Font {
    MINECRAFT("Minecraft", null, null),
    SANS("Sans", null, null),
    SERIF("Serif", null, null),
    MONO("Monospace", null, null),
    INTER("Inter", "Inter", "ttf"),
    ROBOTO("Roboto", "Roboto", "ttf"),
    ROBOTO_CONDENSED("Roboto Condensed", "RobotoCondensed", "ttf"),
    OPEN_SANS("Open Sans", "OpenSans", "ttf"),
    LATO("Lato", "Lato", "ttf"),
    MONTSERRAT("Montserrat", "Montserrat", "ttf"),
    POPPINS("Poppins", "Poppins", "ttf"),
    MONASPACE("Monaspace Neon", "MonaspaceNeon", "otf"),
    NERD_MONO("JetBrains Mono Nerd", "JetBrainsMonoNerd", "ttf");
    private final String label, resource, extension;

    Font(String label, String resource, String extension) {
      this.label = label;
      this.resource = resource;
      this.extension = extension;
    }

    public String displayName() {
      return label;
    }

    public String resource(boolean bold) {
      return resource == null
          ? null
          : "/assets/perihelion/fonts/" + resource + (bold ? "-Bold." : "-Regular.") + extension;
    }
  }

  public enum TextWeight {
    REGULAR,
    BOLD
  }

  public final Setting<Double> background, panelOpacity, headerOpacity, scale, cornerRadius;
  public final Setting<Integer> ravenPanelAlpha;
  public final Setting<ColorValue> accent,
      text,
      enabled,
      sectionText,
      selectionText,
      selectionHighlight,
      mutedText;
  public final Setting<Font> font, modernFont;
  public final Setting<Boolean> bold;
  public final Setting<ClientStyle> clientStyle;
  public final Setting<Visuals> visuals;
  public final Setting<String> modernUtilityLayout, modernLayout;
  public final Setting<TextWeight> textWeight;
  public final Setting<Boolean> search, tooltips, customTextStyle, boldSections;
  public final Setting<String> layout, ravenLayout, utilityLayout, ravenUtilityLayout;
  private final Runnable open;
  private final ThemeModule themes;

  public ClickGuiModule(Runnable guard, Runnable open) {
    this(guard, open, null);
  }

  public ThemePalette palette() {
    return themes == null ? ThemePalette.DEFAULT : themes.palette();
  }

  public ClickGuiModule(Runnable guard, Runnable open, ThemeModule themes) {
    super(
        new ModuleInfo("clickgui", "ClickGUI", "Open the panel menu.", ModuleCategory.MISC, false));
    this.open = open;
    this.themes = themes;
    visuals =
        setting(
            Setting.enumeration(
                "visuals",
                "Visuals",
                "New uses the Perihelion theme. Old restores your previous GUI and HUD.",
                Visuals.NEW,
                Visuals.class,
                guard));
    clientStyle =
        setting(
            Setting.enumeration(
                "client-style",
                "Client style",
                "Adjust keeps the current visuals. Raven uses classic Minecraft panels, controls"
                    + " and HUD.",
                ClientStyle.ADJUST,
                ClientStyle.class,
                guard));
    clientStyle.visibleWhen(() -> visuals.get() == Visuals.OLD);
    customTextStyle =
        Setting.bool(
            "custom-text-style",
            "Custom text style",
            "Enable optional colors and weights. Off uses the selected client style's palette.",
            false,
            guard);
    defaultBinding(new Keybind(Keybind.Device.KEYBOARD, 54)); // LWJGL Right Shift
    background =
        setting(
            Setting.decimal(
                "background-opacity", "Background", "World dimming.", 0.25, 0, 0.9, 0.05, guard));
    background.visibleWhen(() -> !modern());
    panelOpacity =
        setting(
            Setting.decimal(
                "panel-opacity", "Panels", "Panel opacity.", 0.75, 0.15, 1, 0.05, guard));
    headerOpacity =
        setting(
            Setting.decimal(
                "header-opacity", "Headers", "Header opacity.", 0.95, 0.2, 1, 0.05, guard));
    panelOpacity.visibleWhen(() -> !modern() && clientStyle.get() == ClientStyle.ADJUST);
    headerOpacity.visibleWhen(() -> !modern() && clientStyle.get() == ClientStyle.ADJUST);
    ravenPanelAlpha =
        setting(
            Setting.integer(
                    "raven-panel-alpha",
                    "Raven panel alpha",
                    "Opacity of Raven category and tool windows.",
                    110,
                    0,
                    255,
                    guard)
                .visibleWhen(() -> !modern() && clientStyle.get() == ClientStyle.RAVEN));
    accent =
        setting(
            Setting.color(
                "accent",
                "Accent",
                "Header and control color.",
                ColorValue.rgb(66, 125, 158),
                guard));
    text =
        setting(
            Setting.color(
                "text-color", "Text", "Main text color.", ColorValue.rgb(191, 191, 191), guard));
    enabled =
        setting(
            Setting.color(
                "enabled-color",
                "Enabled",
                "Enabled module text color.",
                ColorValue.rgb(229, 229, 229),
                guard));
    scale =
        setting(Setting.decimal("scale", "Menu scale", "Panel size.", 1, 0.75, 1.5, 0.05, guard));
    cornerRadius =
        setting(
            Setting.decimal(
                    "corner-radius",
                    "Corner radius",
                    "Roundness of the new GUI corners. Zero uses square corners.",
                    5,
                    0,
                    10,
                    1,
                    guard)
                .visibleWhen(this::modern));
    font =
        setting(
            Setting.enumeration(
                "font",
                "Font",
                "Shared text font for Modules, Configs, HUD and HUD editor. Sans is the current"
                    + " font; Minecraft uses the game font; Mono is monospace.",
                Font.SANS,
                Font.class,
                guard));
    font.visibleWhen(() -> !modern() && clientStyle.get() == ClientStyle.ADJUST);
    modernFont =
        setting(
            Setting.enumeration(
                    "modern-font",
                    "Font",
                    "Font for the new GUI and HUD. Roboto is the reference-style default.",
                    Font.ROBOTO,
                    Font.class,
                    guard)
                .visibleWhen(this::modern));
    bold =
        setting(
            Setting.bool("modern-bold", "Bold", "Make all new GUI and HUD text bold.", false, guard)
                .visibleWhen(this::modern));
    setting(customTextStyle);
    customTextStyle.visibleWhen(() -> !modern());
    sectionText =
        setting(
            Setting.color(
                "section-text-color",
                "Section text",
                "Category and window heading text color.",
                ColorValue.rgb(229, 229, 229),
                guard));
    selectionText =
        setting(
            Setting.color(
                "selection-text-color",
                "Selected text",
                "Text color for selected presets, options and active buttons.",
                ColorValue.rgb(229, 229, 229),
                guard));
    selectionHighlight =
        setting(
            Setting.color(
                "selection-highlight",
                "Selection highlight",
                "Selection and hover background; its alpha controls the highlight strength.",
                ColorValue.rgba(66, 125, 158, 64),
                guard));
    mutedText =
        setting(
            Setting.color(
                "muted-text-color",
                "Helper text",
                "Descriptions, bindings and inactive helper text.",
                ColorValue.rgb(178, 181, 185),
                guard));
    textWeight =
        setting(
            Setting.enumeration(
                "text-weight",
                "Text weight",
                "Regular or bold GUI text. Keeps the existing row and font size.",
                TextWeight.REGULAR,
                TextWeight.class,
                guard));
    boldSections =
        setting(
            Setting.bool(
                "bold-sections",
                "Bold sections",
                "Use bold category and window headings independently of regular body text.",
                false,
                guard));
    for (Setting<?> appearance :
        new Setting<?>[] {
          accent,
          text,
          enabled,
          sectionText,
          selectionText,
          selectionHighlight,
          mutedText,
          textWeight,
          boldSections
        }) appearance.visibleWhen(() -> !modern() && customTextStyle.get());
    search =
        setting(Setting.bool("search", "Search bar", "Filter modules while typing.", false, guard));
    search.visibleWhen(() -> !modern());
    tooltips =
        setting(
            Setting.bool(
                "tooltips",
                "Tooltips",
                "Show descriptions after hovering for two seconds.",
                true,
                guard));
    layout =
        setting(
            Setting.string(
                    "panel-layout-v3",
                    "Panel layout",
                    "Internal layout for the panel frontend only.",
                    "[]",
                    32768,
                    guard)
                .visibleWhen(() -> false));
    utilityLayout =
        setting(
            Setting.string(
                    "utility-layout",
                    "Utility layout",
                    "Movable HUD, Configs and editor controls.",
                    "{}",
                    4096,
                    guard)
                .visibleWhen(() -> false));
    ravenLayout =
        setting(
            Setting.string(
                    "raven-panel-layout-v2",
                    "Raven panel layout",
                    "Internal layout for the Raven frontend.",
                    "[]",
                    32768,
                    guard)
                .visibleWhen(() -> false));
    ravenUtilityLayout =
        setting(
            Setting.string(
                    "raven-utility-layout",
                    "Raven tools layout",
                    "Internal Raven utility-window positions.",
                    "{}",
                    4096,
                    guard)
                .visibleWhen(() -> false));
    modernUtilityLayout =
        setting(
            Setting.string(
                    "modern-utility-layout",
                    "New tools layout",
                    "HUD and config window positions for the new visuals.",
                    "{}",
                    4096,
                    guard)
                .visibleWhen(() -> false));
    modernLayout =
        setting(
            Setting.string(
                    "dropdown-layout-v2",
                    "Dropdown layout",
                    "Saved positions for the new category panels.",
                    "[]",
                    32768,
                    guard)
                .visibleWhen(() -> false));
  }

  public boolean modern() {
    return visuals.get() == Visuals.NEW;
  }

  public Setting<String> layoutFor(ClientStyle style) {
    return style == ClientStyle.RAVEN ? ravenLayout : layout;
  }

  public Setting<String> panelLayout() {
    return modern() ? modernLayout : layoutFor(clientStyle.get());
  }

  public Setting<String> utilityLayoutFor(ClientStyle style) {
    return style == ClientStyle.RAVEN ? ravenUtilityLayout : utilityLayout;
  }

  public Setting<String> toolsLayout() {
    return modern() ? modernUtilityLayout : utilityLayoutFor(clientStyle.get());
  }

  @Override
  protected void onEnable(ModuleContext context) {
    open.run();
    context.requestDisable();
  }
}
