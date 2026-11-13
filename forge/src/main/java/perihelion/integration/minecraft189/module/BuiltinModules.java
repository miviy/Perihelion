package perihelion.integration.minecraft189.module;

import perihelion.core.lifecycle.ClientRuntime;

/** Explicit composition, using only the existing registry and activation lifecycle. */
public final class BuiltinModules {
    private BuiltinModules() {}

    public static void register(ClientRuntime runtime) {
        Runnable guard = runtime.tasks()::checkThread;
        perihelion.integration.minecraft189.module.gameplay.GameplayModules.register(runtime);
        runtime.modules().register(new FullbrightModule());
        runtime.modules().register(new SprintModule(guard));
        runtime.modules().register(new NoFovModule());
        runtime.modules().register(new WTapModule(guard));
        runtime.modules().register(new NoBobModule());
        runtime.modules().register(new NoHurtCamModule());
        runtime.modules().register(new TimerModule(guard));
        runtime.modules().register(new AntiBlindModule());
        runtime.modules().register(new FastPlaceModule(guard));
    }
}
