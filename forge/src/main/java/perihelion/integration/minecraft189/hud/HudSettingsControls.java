package perihelion.integration.minecraft189.hud;

import java.util.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.SettingSection;

/** One native settings surface shared by all module-menu frontends. */
public final class HudSettingsControls {
  private final HudModule hud;
  private final SettingControl.Actions actions;
  private final List<SettingControl> controls = new ArrayList<>();
  private final float rowHeight;

  private HudSettingsControls(
      HudModule hud, SettingControl.Actions actions, Runnable arrange, float rowHeight) {
    this.hud = hud;
    this.actions = actions;
    this.rowHeight = rowHeight;
    for (HudModule.Element element : HudModule.Element.values()) element(element);
    SettingSection general = section("General", "Visibility rules for the whole HUD.", null, null);
    add(general, hud.allowChatMove, hud.hideDebug, hud.hideScreens);
    SettingSection legacy =
        section("Legacy colors", "Shared colors used by Old visuals.", general, null);
    legacy.visibleWhen(() -> general.isOpen() && !GuiTheme.modern(hud.runtime));
    add(legacy, hud.accent, hud.secondary, hud.opacity, hud.shadow);
    controls.add(
        SettingSection.action(
            hud.runtime.modules(),
            "hud",
            "Arrange on screen",
            "Optional drag preview. All HUD settings are available above.",
            rowHeight,
            arrange));
  }

  public static List<SettingControl> create(
      HudModule hud, SettingControl.Actions actions, Runnable arrange) {
    return create(hud, actions, arrange, 17);
  }

  public static List<SettingControl> create(
      HudModule hud, SettingControl.Actions actions, Runnable arrange, float rowHeight) {
    return new HudSettingsControls(hud, actions, arrange, rowHeight).controls;
  }

  private SettingSection section(
      String title, String description, SettingSection parent, Setting<Boolean> enabled) {
    SettingSection section =
        new SettingSection(hud.runtime.modules(), "hud", title, description, rowHeight, enabled);
    if (parent != null) section.visibleWhen(parent::isOpen);
    controls.add(section);
    return section;
  }

  private void add(SettingSection section, Setting<?>... settings) {
    for (Setting<?> setting : settings)
      controls.add(
          SettingControl.create(hud.runtime.modules(), "hud", setting, actions)
              .visibleWhen(section::isOpen));
  }

  private void element(HudModule.Element element) {
    Setting<Boolean> enabled = hud.shown.get(element);
    SettingSection root =
        section(
            element.label,
            "Left-click to toggle; right-click for "
                + element.label.toLowerCase(Locale.ROOT)
                + " settings.",
            null,
            enabled);
    add(root, enabled);
    controls.get(controls.size() - 1).named("Enabled");
    switch (element) {
      case WATERMARK:
        add(root, hud.logoDesign, hud.watermarkVersion, hud.watermarkClock, hud.watermarkInfo);
        break;
      case MODULES:
        add(
            root,
            hud.moduleAlignment,
            hud.moduleSort,
            hud.moduleSpacing,
            hud.modulePadding,
            hud.moduleRows,
            hud.suffixes,
            hud.moduleSuffix,
            hud.moduleLowercase,
            hud.hideVisuals);
        break;
      case PLAYERS:
        players(root);
        break;
      default:
        break;
    }
    HudElementStyle style = hud.styles.get(element);
    SettingSection text = section("Text", "Font, size and text colors.", root, null);
    add(
        text,
        style.font,
        style.scale,
        style.bold,
        style.shadow,
        style.colors,
        style.color,
        style.secondary);
    SettingSection panel = section("Background", "Panel color, shape and effects.", root, null);
    add(
        panel,
        style.background,
        style.backgroundColor,
        style.backgroundSecondary,
        style.direction,
        style.opacity,
        style.padding,
        style.radius);
    if (element == HudModule.Element.MODULES) add(panel, hud.moduleBorder);
    add(
        panel,
        style.blur,
        style.blurStrength,
        style.panelShadow,
        style.shadowOpacity,
        style.shadowSpread);
    controls.add(
        SettingSection.action(
                hud.runtime.modules(),
                "hud",
                "Reset appearance",
                "Restore this element's text and background; keep its content and position.",
                rowHeight,
                () -> hud.resetAppearance(element))
            .visibleWhen(panel::isOpen));
    SettingSection position =
        section("Position", "Position within the available screen space.", root, null);
    add(position, hud.xs.get(element), hud.ys.get(element));
    controls.add(
        SettingSection.action(
                hud.runtime.modules(),
                "hud",
                "Reset position",
                "Restore only this element's original position.",
                rowHeight,
                () -> hud.resetPosition(element))
            .visibleWhen(position::isOpen));
  }

  private void players(SettingSection root) {
    OverlaySettings o = hud.overlay;
    add(
        root,
        o.source,
        hud.playerLimit,
        hud.playerRange,
        hud.playerColumns,
        o.sort,
        o.descending,
        o.includeSelf);
    SettingSection stats =
        section("Statistics", "Lookup providers and game statistics.", root, null);
    add(stats, o.automatic, hud.hypixelGame, o.mode, o.bedwarsOnly, o.providers, o.sessions);
    SettingSection keys =
        section("Provider keys", "Paths to your local API-key files.", stats, null);
    add(keys, hud.hypixelKeyFile);
    for (Setting<String> key : o.keys.values()) if (key != hud.hypixelKeyFile) add(keys, key);
    SettingSection filters = section("Filters", "Choose which players appear.", root, null);
    add(filters, o.minFkdr, o.minStars, o.removeFinals, o.whoOnly, o.hidden);
    SettingSection alerts =
        section(
            "Highlights and alerts", "Optional player highlights and chat notices.", root, null);
    add(
        alerts,
        o.highlights,
        o.partyDetector,
        o.nickAlerts,
        o.tagAlerts,
        o.dodgeWarning,
        o.dodgeThreshold,
        o.autoWho,
        o.autoParty,
        o.gameResults);
  }
}
