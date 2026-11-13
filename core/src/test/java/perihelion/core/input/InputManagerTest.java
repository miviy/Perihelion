package perihelion.core.input;

import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.module.*;
import java.util.*;
import static org.junit.Assert.*;

public class InputManagerTest {
    private final ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    private final EventBus bus = new EventBus(tasks::checkThread, ClientLog.NONE);
    private final ModuleManager modules = new ModuleManager(tasks, bus, new EventBus(() -> {}, ClientLog.NONE), ClientLog.NONE);
    private final InputManager input = new InputManager(tasks, modules, bus);
    private final Keybind key = Keybind.parse("keyboard:25");
    private final List<String> activated = new ArrayList<>();
    private void setup() {
        for (String id : Arrays.asList("first", "second")) {
            modules.register(new ClientModule(new ModuleInfo(id, id, "test-only", ModuleCategory.MISC, false)) {
                protected void onEnable(ModuleContext scope) { activated.add(id); }
            });
            modules.setBinding(id, key);
        }
        modules.start(); input.rebuildBindings();
        input.setAllowed(true, Collections.emptyList());
    }
    @Test public void sharedBindingsToggleInRegistrationOrderOncePerPress() {
        setup(); input.accept(key, true); input.accept(key, true);
        assertEquals(Arrays.asList("first", "second"), activated);
        assertTrue(modules.require("first").requestedEnabled());
        input.accept(key, false); input.accept(key, true);
        assertFalse(modules.require("first").requestedEnabled());
        assertFalse(modules.require("second").requestedEnabled()); modules.close();
    }
    @Test public void openingScreenStopsRemainingSharedBindings() {
        modules.register(new ClientModule(new ModuleInfo("opener", "Opener", "test-only", ModuleCategory.CLIENT, false)) {
            protected void onEnable(ModuleContext scope) { input.setAllowed(false, Collections.emptyList()); }
        });
        modules.setBinding("opener", key);
        setup();
        input.accept(key, true);
        assertTrue(modules.require("opener").requestedEnabled());
        assertTrue(activated.isEmpty());
        modules.close();
    }
    @Test public void heldKeyAcrossScreenOrFocusTransitionRequiresRelease() {
        setup();
        input.setAllowed(false, Collections.emptyList()); input.accept(key, true);
        input.setAllowed(true, Arrays.asList(key)); input.accept(key, true);
        assertTrue(activated.isEmpty());
        input.accept(key, false); input.accept(key, true);
        assertEquals(2, activated.size()); modules.close();
    }
    @Test public void releaseWhileUnfocusedDoesNotPermanentlyBlockKey() {
        setup(); input.accept(key, true);
        input.setAllowed(false, Arrays.asList(key));
        // Windowing systems need not deliver a release to an unfocused game.
        input.setAllowed(true, Collections.emptyList()); input.accept(key, true);
        assertFalse(modules.require("first").requestedEnabled()); modules.close();
    }
    @Test public void deviceCodesAreValidatedAndMouseZeroIsBindable() {
        assertEquals(new Keybind(Keybind.Device.MOUSE, 0), Keybind.parse("MOUSE:0"));
        assertEquals(Keybind.UNBOUND, Keybind.parse("none"));
        for (String bad : Arrays.asList("keyboard:0", "mouse:32", "none:0", "mouse:-1", "keyboard:256", "oops")) {
            assertThrows(IllegalArgumentException.class, () -> Keybind.parse(bad));
        }
    }
}
