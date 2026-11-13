package perihelion.integration.minecraft189.gui.widget;

import org.lwjgl.input.Keyboard;
import perihelion.core.input.Keybind;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleManager;
import perihelion.integration.minecraft189.gui.GuiValues;

/** One exclusive binding capture; visual placement belongs to the hosting frontend. */
public final class KeybindEditor {
    private final ModuleManager modules;
    private ModuleEntry target;

    public KeybindEditor(ModuleManager modules) { this.modules = modules; }
    public boolean active() { return target != null; }
    public String title() { return target == null ? "" : "Bind " + GuiValues.readable(target.info().name()); }
    public void begin(ModuleEntry entry) { target = entry; }
    public void cancel() { target = null; }
    public void mouse(int button) {
        if (target == null || button < 0 || button >= 32) return;
        modules.setBinding(target.info().id(), new Keybind(Keybind.Device.MOUSE, button));
        cancel();
    }
    public void key(int code) {
        if (target == null || code <= 0 || code > 255) return;
        if (code != Keyboard.KEY_ESCAPE) {
            modules.setBinding(target.info().id(), code == Keyboard.KEY_DELETE || code == Keyboard.KEY_BACK
                    ? Keybind.UNBOUND : new Keybind(Keybind.Device.KEYBOARD, code));
        }
        cancel();
    }
    public static String name(Keybind key) {
        if (key.device() == Keybind.Device.KEYBOARD) {
            String name = Keyboard.getKeyName(key.code());
            if (name != null) {
                if ("RSHIFT".equals(name)) return "Right Shift";
                if ("LSHIFT".equals(name)) return "Left Shift";
                if ("RCONTROL".equals(name)) return "Right Ctrl";
                if ("LCONTROL".equals(name)) return "Left Ctrl";
                if ("RMENU".equals(name)) return "Right Alt";
                if ("LMENU".equals(name)) return "Left Alt";
                return GuiValues.readable(name);
            }
        }
        return key.device() == Keybind.Device.MOUSE ? "Mouse " + (key.code() + 1) : "None";
    }
}
