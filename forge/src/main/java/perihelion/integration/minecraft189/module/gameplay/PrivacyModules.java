package perihelion.integration.minecraft189.module.gameplay;

import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

final class PrivacyModules {
  private PrivacyModules() {}

  static final class AutoF5 extends GameplayModule {
    private final Setting<Boolean> onAura, onScaffold;
    private final AuraModule aura;

    AutoF5(ClientRuntime runtime, AuraModule aura) {
      super(runtime, "autof5", "Auto F5", "Use third person while Aura has a target or Scaffold is active.", ModuleCategory.RENDER);
      this.aura = aura;
      onAura = bool("on-aura", "On Aura", true);
      onScaffold = bool("on-scaffold", "On Scaffold", true);
    }

    @Override protected void onEnable(ModuleContext context) {
      ControlRequest<CameraPerspective> view = context.controls().perspective(50);
      boolean[] requested = {false}, manual = {false};
      context.onCleanup(() -> {
        if (HookBridge.gameControls() != null) HookBridge.gameControls().apply();
      });
      context.listen(PlayerControlEvent.class, event -> {
        if (event != PlayerControlEvent.BEFORE_INPUT) return;
        boolean trigger = onAura.get() && active("aura") && aura.target() != null
            || onScaffold.get() && active("scaffold");
        if (requested[0] && view.isWinning() && mc().gameSettings.thirdPersonView != 1)
          manual[0] = true;
        if (!trigger) { manual[0] = false; requested[0] = false; view.clear(); return; }
        if (active("freelook") || active("freecam") || manual[0]) {
          requested[0] = false; view.clear(); return;
        }
        if (view.isWinning() || mc().gameSettings.thirdPersonView == 0) {
          view.set(CameraPerspective.THIRD_PERSON_BACK);
          requested[0] = true;
        }
      });
    }
  }

  static final class NameProtect extends GameplayModule {
    NameProtect(ClientRuntime runtime) {
      super(runtime, "nameprotect", "Name Protect", "Show your local player name as You in rendered text.", ModuleCategory.RENDER);
    }
    @Override protected void onEnable(ModuleContext context) {
      context.listen(TextRenderEvent.class, event -> {
        if (mc().thePlayer == null) return;
        String name = mc().thePlayer.getName();
        if (name != null && !name.isEmpty() && event.text().contains(name))
          event.text(event.text().replace(name, "You"));
      });
    }
  }
}
