package perihelion.core.event;

import perihelion.core.input.Keybind;

public final class InputEvent {
    private final Keybind key;
    private final boolean pressed;
    public InputEvent(Keybind key, boolean pressed) { this.key = key; this.pressed = pressed; }
    public Keybind key() { return key; }
    public boolean pressed() { return pressed; }
}
