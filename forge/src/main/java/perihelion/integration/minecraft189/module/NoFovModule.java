package perihelion.integration.minecraft189.module;

import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

public final class NoFovModule extends ClientModule {
    public NoFovModule() {
        super(new ModuleInfo("nofov", "NoFOV", "Suppress sprint, speed, flying and bow FOV changes.", ModuleCategory.RENDER, true));
    }
    @Override protected void onEnable(ModuleContext context) {
        context.listen(VisualEffectEvent.class, event -> {
            if (event.kind() == VisualEffectEvent.Kind.DYNAMIC_FOV) event.cancel();
        });
    }
}
