package perihelion.integration.minecraft189.module;

import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

public final class NoBobModule extends ClientModule {
    public NoBobModule() {
        super(new ModuleInfo("nobob", "NoBob", "Suppress local view and hand bobbing.", ModuleCategory.RENDER, true));
    }
    @Override protected void onEnable(ModuleContext context) {
        context.listen(VisualEffectEvent.class, event -> {
            if (event.kind() == VisualEffectEvent.Kind.VIEW_BOBBING) event.cancel();
        });
    }
}
