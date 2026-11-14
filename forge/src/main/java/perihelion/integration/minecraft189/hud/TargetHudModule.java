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
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.ChatHudEvent;
import perihelion.integration.minecraft189.event.PlayerAttackEvent;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.SettingSection;

/** Owns combat visibility; the shared HUD renderer preserves saved presentation and placement. */
public final class TargetHudModule extends ClientModule {
  public enum Design {
    PERIHELION
  }

  private final HudModule hud;
  private final Setting<Design> design;
  private final Setting<Double> hideDelay;
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

  public List<SettingControl> controls(
      SettingControl.Actions actions, Runnable arrange, float rowHeight) {
    List<SettingControl> controls = new ArrayList<>();
    controls.add(SettingControl.create(hud.runtime.modules(), info().id(), design, actions));
    controls.add(SettingControl.create(hud.runtime.modules(), info().id(), hideDelay, actions));
    controls.addAll(HudSettingsControls.target(hud, actions, rowHeight));
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
}
