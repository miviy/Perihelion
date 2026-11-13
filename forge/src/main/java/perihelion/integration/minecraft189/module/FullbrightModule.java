package perihelion.integration.minecraft189.module;

import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

public final class FullbrightModule extends ClientModule {
    public FullbrightModule() {
        super(new ModuleInfo("fullbright", "Fullbright", "Illuminate dark areas using render gamma 100.", ModuleCategory.RENDER, true));
    }
    @Override protected void onEnable(ModuleContext context) {
        context.listen(VisualEffectEvent.class, event -> {
            if (event.kind() == VisualEffectEvent.Kind.GAMMA) event.cancel();
        });
    }
}
