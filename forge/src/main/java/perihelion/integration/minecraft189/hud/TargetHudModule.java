package perihelion.integration.minecraft189.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.EntityLivingBase;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.event.HudRenderEvent;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.ChatHudEvent;
import perihelion.integration.minecraft189.event.PlayerAttackEvent;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.SettingSection;

/** Owns combat visibility; the shared HUD renderer preserves saved presentation and placement. */
public final class TargetHudModule extends ClientModule {
  public enum Design {
    PERIHELION(146, 38),
    YURI(130, 36),
    YURI_PULSIVE(100, 55),
    YURI_ADJUST(110, 31),
    YURI_ATMOSPHERE(135, 31),
    YURI_ASTOLFO(125, 36),
    YURI_OLD_ASTOLFO(127, 45),
    YURI_NOVOLINE(130, 30),
    YURI_OLD_NOVOLINE(90, 28),
    YURI_EXHIBITION(138, 44),
    YURI_OLD_EXHIBITION(130, 36),
    YURI_RAVEN_B4(90, 30),
    YURI_OLD_RAVEN_B4(90, 30),
    B4_MODERN(90, 37),
    B4_LEGACY(90, 37),
    ARSENIC_FACE(150, 50),
    ARSENIC_SIMPLE(130, 32),
    COOLWARE_FLAT(130, 44),
    COOLWARE_ISLAND(140, 48),
    MYAU_DEFAULT(95, 27),
    MYAU_RAVEN_MODERN(90, 37),
    MYAU_RAVEN_LEGACY(90, 37),
    MYAU_FACE(150, 50),
    MYAU_3D(150, 50),
    MYAU_SIMPLE(130, 32),
    MYAU_CIRCLE(130, 40),
    MYAU_BLUEDEV(165, 55),
    MYAU_ASTOLFO(130, 56),
    MYAU_EXHIBITION(125, 45),
    MYAU_MOON(110, 41),
    MYAU_RISE(160, 41),
    MYAU_NEVERLOSE(125, 33),
    MYAU_TENACITY(120, 44);
    final int width, height;

    Design(int width, int height) {
      this.width = width;
      this.height = height;
    }

    public String displayName() {
      switch (this) {
        case MYAU_3D:
          return "3D";
        case MYAU_BLUEDEV:
          return "BlueDev";
        case YURI_RAVEN_B4:
          return "Raven B4";
        case YURI_OLD_RAVEN_B4:
          return "Old Raven B4";
        case MYAU_RAVEN_MODERN:
          return "RavenBS modern";
        case MYAU_RAVEN_LEGACY:
          return "RavenBS legacy";
        default:
          String label = name();
          int separator = label.indexOf('_');
          return perihelion.integration.minecraft189.gui.GuiValues.readable(
              separator < 0 ? label : label.substring(separator + 1));
      }
    }
  }

  public enum Theme {
    ORIGINAL,
    CLIENT,
    CUSTOM
  }

  private final HudModule hud;
  private final Setting<Design> design;
  private final Setting<Double> hideDelay;
  private final Setting<Theme> theme;
  private final Setting<Boolean> nativeEffects;
  private final Setting<ColorValue> primary, secondary;
  private EntityLivingBase attackedTarget;
  private long attackedAt;
  private HudRenderer renderer;

  public TargetHudModule(HudModule hud) {
    super(
        new ModuleInfo(
            "targethud", "Target HUD", "Show the entity you attack.", ModuleCategory.RENDER, true));
    this.hud = hud;
    hud.targetHud = this;
    Runnable guard = hud.runtime.tasks()::checkThread;
    design =
        setting(
            Setting.enumeration(
                "design", "Design", "Target card design.", Design.PERIHELION, Design.class, guard));
    hideDelay =
        setting(
            Setting.decimal(
                "hide-delay",
                "Hide delay (s)",
                "Seconds to keep the card after the last attack.",
                3,
                .1,
                10,
                .1,
                guard));
    theme =
        setting(
            Setting.enumeration(
                    "theme",
                    "Theme",
                    "Original keeps the donor's colors and layout; Client follows your client;"
                        + " Custom uses your accent colors.",
                    Theme.ORIGINAL,
                    Theme.class,
                    guard)
                .visibleWhen(this::donorDesign));
    nativeEffects =
        setting(
            Setting.bool(
                    "native-effects",
                    "Original effects",
                    "Use this design's original blur and shadow.",
                    true,
                    guard)
                .visibleWhen(() -> false));
    primary =
        setting(
            Setting.color(
                    "primary",
                    "Accent",
                    "Custom first accent color.",
                    ColorValue.rgb(161, 82, 230),
                    guard)
                .visibleWhen(() -> donorDesign() && theme.get() == Theme.CUSTOM));
    secondary =
        setting(
            Setting.color(
                    "secondary",
                    "End accent",
                    "Custom gradient end color.",
                    ColorValue.rgb(55, 18, 90),
                    guard)
                .visibleWhen(() -> donorDesign() && theme.get() == Theme.CUSTOM));
  }

  @Override
  protected void onEnable(ModuleContext context) {
    Minecraft mc = Minecraft.getMinecraft();
    renderer = context.own(new HudRenderer(hud, true));
    HudChatInteraction chat = context.own(new HudChatInteraction(hud, renderer));
    context.onCleanup(
        () -> {
          attackedTarget = null;
          renderer = null;
        });
    context.listen(
        PlayerAttackEvent.class,
        Integer.MIN_VALUE,
        false,
        event -> {
          EntityLivingBase entity = event.target();
          if (entity == null || entity == mc.thePlayer || entity.worldObj != mc.theWorld) return;
          attackedTarget = entity;
          attackedAt = System.nanoTime();
          renderer.invalidate();
        });
    context.listen(
        ClientTickEvent.class,
        event -> {
          if (event == ClientTickEvent.END) renderer.update(false);
        });
    context.listen(ChatHudEvent.class, chat::handle);
    context.observe(
        design,
        (setting, before, after) -> {
          chat.cancelDrag();
          renderer.targetDesigns.reset();
          renderer.invalidate();
        });
    context.observe(theme, (setting, before, after) -> chat.cancelDrag());
    context.observe(hud.allowChatMove, (setting, before, after) -> chat.cancelDrag());
    for (Setting<?> option : hud.styles.get(HudModule.Element.TARGET).settings)
      context.observe(option, (setting, before, after) -> chat.cancelDrag());
    perihelion.integration.minecraft189.gui.ClickGuiModule preferences =
        perihelion.integration.minecraft189.gui.GuiTheme.preferences(hud.runtime);
    if (preferences != null) {
      context.observe(preferences.font, (setting, before, after) -> chat.cancelDrag());
      context.observe(preferences.visuals, (setting, before, after) -> chat.cancelDrag());
    }
    context.listen(
        HudRenderEvent.class,
        event -> {
          if (mc.currentScreen instanceof HudEditor
              || mc.currentScreen instanceof GuiChat
              || hud.hideScreens.get() && mc.currentScreen != null
              || hud.hideDebug.get() && mc.gameSettings.showDebugInfo) return;
          renderer.render(event.scaledWidth(), event.scaledHeight(), false, null, 0, 0);
        });
  }

  EntityLivingBase target() {
    Minecraft mc = Minecraft.getMinecraft();
    EntityLivingBase entity = attackedTarget;
    if (entity != null
        && (System.nanoTime() - attackedAt >= hideDelay.get() * 1_000_000_000L
            || mc.thePlayer == null
            || mc.theWorld == null
            || entity.isDead
            || entity.getHealth() <= 0
            || entity.worldObj != mc.theWorld
            || mc.theWorld.getEntityByID(entity.getEntityId()) != entity)) attackedTarget = null;
    return attackedTarget;
  }

  Design design() {
    return design.get();
  }

  Theme theme() {
    return theme.get();
  }

  boolean donorDesign() {
    return design.get() != Design.PERIHELION;
  }

  boolean nativeEffects() {
    return nativeEffects.get();
  }

  boolean nativeGlass() {
    if (!donorDesign() || !nativeEffects()) return false;
    if (hud.styles.get(HudModule.Element.TARGET).background.get()
        != HudElementStyle.Background.AUTO) return false;
    Design current = design();
    return current == Design.B4_MODERN
        || current == Design.MYAU_RAVEN_MODERN
        || current == Design.MYAU_BLUEDEV;
  }

  int customColor(boolean end, long now) {
    return (end ? secondary : primary).get().resolvedArgb(now);
  }

  public List<SettingControl> controls(
      SettingControl.Actions actions, Runnable arrange, float rowHeight) {
    List<SettingControl> controls = simpleControls(actions);
    controls.add(
        SettingSection.action(
            hud.runtime.modules(),
            info().id(),
            "Arrange on screen",
            "Drag the target card to place it.",
            rowHeight,
            arrange));
    return controls;
  }

  List<SettingControl> simpleControls(SettingControl.Actions actions) {
    List<SettingControl> controls = new ArrayList<>();
    controls.add(SettingControl.create(hud.runtime.modules(), info().id(), design, actions));
    controls.add(
        SettingControl.create(
            hud.runtime.modules(), "hud", hud.styles.get(HudModule.Element.TARGET).scale, actions));
    controls.add(SettingControl.create(hud.runtime.modules(), info().id(), hideDelay, actions));
    for (Setting<?> option : new Setting<?>[] {theme, primary, secondary})
      controls.add(SettingControl.create(hud.runtime.modules(), info().id(), option, actions));
    return controls;
  }
}
