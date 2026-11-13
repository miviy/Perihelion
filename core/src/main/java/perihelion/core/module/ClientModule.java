package perihelion.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import perihelion.core.input.Keybind;
import perihelion.core.setting.Setting;

/** Definitions are constructed once; activation happens only through ModuleManager. */
public abstract class ClientModule {
    private final ModuleInfo info;
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private final Map<String, Setting<?>> settingsView = Collections.unmodifiableMap(settings);
    private boolean registered;
    private Keybind defaultBinding = Keybind.UNBOUND;

    protected ClientModule(ModuleInfo info) { this.info = Objects.requireNonNull(info, "info"); }
    public final ModuleInfo info() { return info; }
    public final Keybind defaultBinding() { return defaultBinding; }
    /** Definition-time default; user bindings remain owned by ModuleManager and config. */
    protected final void defaultBinding(Keybind binding) {
        if (registered) throw new IllegalStateException("Binding defaults are frozen after registration");
        defaultBinding = Objects.requireNonNull(binding, "binding");
    }
    public final Map<String, Setting<?>> settings() { return settingsView; }
    protected final <T> Setting<T> setting(Setting<T> setting) {
        if (registered) throw new IllegalStateException("Settings are frozen after registration");
        if (settings.putIfAbsent(setting.id(), setting) != null) throw new IllegalArgumentException("Duplicate setting " + setting.id());
        return setting;
    }
    final void freeze() {
        for (Setting<?> setting : settings.values()) setting.freeze();
        registered = true;
    }
    protected void onEnable(ModuleContext context) throws Exception { }
    protected void onDisable() throws Exception { }
}
