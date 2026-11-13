package perihelion.core.module;

import perihelion.core.event.EventOwner;
import perihelion.core.input.Keybind;

/** Read-only registry view. Mutable transitions are package-private and client-thread owned. */
public final class ModuleEntry {
    final ClientModule module;
    boolean requested;
    ModuleState state = ModuleState.DISABLED;
    Keybind binding;
    EventOwner owner;
    ModuleContext context;
    Throwable failure;
    ModuleEntry(ClientModule module) { this.module = module; this.binding = module.defaultBinding(); }
    public ClientModule module() { return module; }
    public ModuleInfo info() { return module.info(); }
    public boolean requestedEnabled() { return requested; }
    public ModuleState state() { return state; }
    public Keybind binding() { return binding; }
    public Throwable lastFailure() { return failure; }
}
