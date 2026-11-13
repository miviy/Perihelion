package perihelion.integration.minecraft189.module;

import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

public final class AntiBlindModule extends ClientModule {
    public AntiBlindModule() {
        super(new ModuleInfo("antiblind", "AntiBlind", "Suppress blindness fog and darkening without removing the potion.", ModuleCategory.RENDER, true));
    }
    @Override protected void onEnable(ModuleContext context) {
        context.listen(VisualEffectEvent.class, event -> {
            if (event.kind() == VisualEffectEvent.Kind.BLINDNESS) event.cancel();
        });
    }
}
