package perihelion.core.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.event.EventBus;
import perihelion.core.event.InputEvent;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleManager;

public final class InputManager {
    private final ClientTaskQueue tasks;
    private final ModuleManager modules;
    private final EventBus events;
    private final Map<Keybind, List<String>> bindings = new HashMap<>();
    private final Set<Keybind> pressed = new HashSet<>();
    private final Set<Keybind> blockedUntilRelease = new HashSet<>();
    private boolean allowed;

    public InputManager(ClientTaskQueue tasks, ModuleManager modules, EventBus events) {
        this.tasks = tasks; this.modules = modules; this.events = events;
    }
    public void rebuildBindings() {
        tasks.checkThread();
        bindings.clear();
        for (ModuleEntry entry : modules.entries()) {
            if (entry.binding().isBound()) bindings.computeIfAbsent(entry.binding(), key -> new ArrayList<>()).add(entry.info().id());
        }
    }
    /** Call after focus/screen transitions with all keys currently down, including keys pressed while blocked. */
    public void setAllowed(boolean next, Iterable<Keybind> physicallyDown) {
        tasks.checkThread();
        if (allowed == next) return;
        allowed = next;
        blockedUntilRelease.clear();
        for (Keybind key : physicallyDown) blockedUntilRelease.add(key);
        pressed.clear();
    }
    public boolean isAllowed() { return allowed; }
    public void accept(Keybind key, boolean down) {
        tasks.checkThread();
        if (!key.isBound()) return;
        if (!down) {
            blockedUntilRelease.remove(key);
            boolean wasPressed = pressed.remove(key);
            if (wasPressed && allowed && events.hasListeners(InputEvent.class)) events.post(new InputEvent(key, false));
            return;
        }
        if (!allowed) { blockedUntilRelease.add(key); return; }
        if (blockedUntilRelease.contains(key) || !pressed.add(key)) return;
        if (events.hasListeners(InputEvent.class)) events.post(new InputEvent(key, true));
        List<String> targets = bindings.get(key);
        if (targets != null) for (String id : targets) {
            // An input listener or earlier action may have opened a screen and revoked focus.
            if (!allowed) break;
            modules.toggle(id);
        }
    }
    public void reset() {
        tasks.checkThread();
        allowed = false;
        pressed.clear();
        blockedUntilRelease.clear();
    }
}
