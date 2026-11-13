package perihelion.core.input;

import java.util.Locale;
import java.util.Objects;

public final class Keybind {
    public enum Device { NONE, KEYBOARD, MOUSE }
    public static final Keybind UNBOUND = new Keybind(Device.NONE, 0);
    private final Device device;
    private final int code;

    public Keybind(Device device, int code) {
        this.device = Objects.requireNonNull(device, "device");
        if ((device == Device.NONE && code != 0) || (device == Device.KEYBOARD && (code < 1 || code > 255))
                || (device == Device.MOUSE && (code < 0 || code > 31))) {
            throw new IllegalArgumentException("Invalid input code for " + device + ": " + code);
        }
        this.code = code;
    }

    public Device device() { return device; }
    public int code() { return code; }
    public boolean isBound() { return device != Device.NONE; }
    public static Keybind parse(String text) {
        if (text.equalsIgnoreCase("none")) return UNBOUND;
        String[] parts = text.toLowerCase(Locale.ROOT).split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Use keyboard:<code>, mouse:<code>, or none");
        Device device = Device.valueOf(parts[0].toUpperCase(Locale.ROOT));
        if (device == Device.NONE) throw new IllegalArgumentException("Use none for an unbound key");
        return new Keybind(device, Integer.parseInt(parts[1]));
    }

    @Override public boolean equals(Object other) {
        return other instanceof Keybind && ((Keybind) other).device == device && ((Keybind) other).code == code;
    }
    @Override public int hashCode() { return 31 * device.ordinal() + code; }
    @Override public String toString() { return isBound() ? device.name().toLowerCase(Locale.ROOT) + ":" + code : "none"; }
}
