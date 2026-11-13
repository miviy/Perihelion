package perihelion.integration.minecraft189.module;

import net.minecraft.client.Minecraft;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

public final class SprintModule extends ClientModule {
    private final Setting<Boolean> cancelInvis;
    public SprintModule(Runnable guard) {
        super(new ModuleInfo("sprint", "Sprint", "Automatically hold sprint, subject to vanilla movement rules.", ModuleCategory.MOVEMENT, true));
        cancelInvis = setting(Setting.bool("cancel-invis", "Cancel Invis", "Yield sprint while invisible.", false, guard));
    }
    @Override protected void onEnable(ModuleContext context) {
        ControlRequest<Boolean> sprint = context.controls().key(InputKey.SPRINT, 10);
        Runnable update = () -> {
            Minecraft game = Minecraft.getMinecraft();
            if (game.thePlayer != null && game.currentScreen == null && game.inGameHasFocus
                    && !(cancelInvis.get() && game.thePlayer.isInvisible())) sprint.setForTicks(true, 1);
            else sprint.clear();
        };
        context.listen(ClientTickEvent.class, event -> { if (event == ClientTickEvent.START) update.run(); });
        context.listen(PlayerControlEvent.class, event -> { if (event == PlayerControlEvent.BEFORE_INPUT) update.run(); });
    }
}
