package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.Minecraft;

import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleInfo;
import perihelion.core.setting.Setting;

/** Definition helpers only. Registry, settings, events and lifecycle belong to Perihelion. */
abstract class GameplayModule extends ClientModule {
    protected final ClientRuntime runtime;
    protected final Runnable guard;

    GameplayModule(
            ClientRuntime runtime,
            String id,
            String name,
            String description,
            ModuleCategory category) {
        super(new ModuleInfo(id, name, description, category, true));
        this.runtime = runtime;
        guard = runtime.tasks()::checkThread;
    }

    protected final Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    protected final boolean active(String id) {
        return runtime.modules().require(id).state() == perihelion.core.module.ModuleState.ENABLED;
    }

    protected final Setting<String> text(
            String id, String name, String description, String value, int limit) {
        return setting(Setting.string(id, name, description, value, limit, guard));
    }

    private boolean retiredByWorld;

    protected final boolean stopAfterWorldChange(
            perihelion.core.module.ModuleContext context, boolean enabled) {
        if (retiredByWorld && enabled) {
            retiredByWorld = false;
            context.requestDisable();
            return true;
        }
        retiredByWorld = false;
        long generation = runtime.tasks().generation();
        context.onCleanup(() -> retiredByWorld = generation != runtime.tasks().generation());
        return false;
    }

    protected final Setting<Boolean> bool(String id, String name, boolean value) {
        return bool(id, name, name, value);
    }

    protected final Setting<Boolean> bool(String id, String name, String description, boolean value) {
        return setting(Setting.bool(id, name, description, value, guard));
    }

    protected final Setting<Integer> integer(String id, String name, int value, int min, int max) {
        return setting(Setting.integer(id, name, name, value, min, max, guard));
    }

    protected final Setting<Double> number(
            String id, String name, double value, double min, double max, double step) {
        return setting(Setting.decimal(id, name, name, value, min, max, step, guard));
    }

    protected final <E extends Enum<E>> Setting<E> mode(
            String id, String name, E value, Class<E> type) {
        return setting(Setting.enumeration(id, name, name, value, type, guard));
    }

    protected static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}
