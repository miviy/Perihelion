package perihelion.integration.minecraft189.module;

import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

public final class NoHurtCamModule extends ClientModule {
    public NoHurtCamModule() {
        super(new ModuleInfo("nohurtcam", "NoHurtCam", "Suppress local hurt and death camera tilt.", ModuleCategory.RENDER, true));
    }
    @Override protected void onEnable(ModuleContext context) {
        context.listen(VisualEffectEvent.class, event -> {
            if (event.kind() == VisualEffectEvent.Kind.HURT_CAMERA) event.cancel();
        });
    }
}
