package perihelion.core.setting;

import java.util.Locale;

/** Immutable sRGB byte channels. Text/JSON use #RRGGBBAA; six-digit RGB implies alpha 255. */
public final class ColorValue {
    private final int rgba;
    private final boolean rainbow;
    private final double speed;

    private ColorValue(int rgba) { this(rgba, false, 1); }
    private ColorValue(int rgba, boolean rainbow, double speed) {
        if (!Double.isFinite(speed) || speed < 0.1 || speed > 10) throw new IllegalArgumentException("Rainbow speed must be 0.1 through 10");
        this.rgba = rgba; this.rainbow = rainbow; this.speed = speed;
    }
    public boolean rainbow() { return rainbow; }
    /** Hue cycles per ten seconds. */
    public double rainbowSpeed() { return speed; }
    public ColorValue withRainbow(boolean enabled, double speed) { return new ColorValue(rgba, enabled, speed); }
    /** Resolve animation explicitly; argb() remains the stable stored base color. */
    public int resolvedArgb(long millis) {
        if (!rainbow) return argb();
        double hue = (millis / 10000.0 * speed) % 1;
        if (hue < 0) hue += 1;
        double h = hue * 6; int sector = (int) h;
        int up = (int) Math.round((h - sector) * 255), down = 255 - up;
        int r, g, b;
        switch (sector) {
            case 0: r=255; g=up; b=0; break;
            case 1: r=down; g=255; b=0; break;
            case 2: r=0; g=255; b=up; break;
            case 3: r=0; g=down; b=255; break;
            case 4: r=up; g=0; b=255; break;
            default: r=255; g=0; b=down;
        }
        return alpha()<<24 | r<<16 | g<<8 | b;
    }
    public static ColorValue rgb(int red, int green, int blue) { return rgba(red, green, blue, 255); }
    public static ColorValue rgba(int red, int green, int blue, int alpha) {
        for (int channel : new int[] { red, green, blue, alpha }) {
            if (channel < 0 || channel > 255) throw new IllegalArgumentException("Color channels must be 0 through 255");
        }
        return new ColorValue(red << 24 | green << 16 | blue << 8 | alpha);
    }
    public static ColorValue fromArgb(int argb) { return new ColorValue(argb << 8 | argb >>> 24); }
    public static ColorValue parse(String text) {
        if (text == null) throw new IllegalArgumentException("Color text cannot be null");
        String hex = text.trim();
        if (!hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            throw new IllegalArgumentException("Expected #RRGGBB or #RRGGBBAA");
        }
        long bits = Long.parseLong(hex.substring(1), 16);
        return new ColorValue((int) (hex.length() == 7 ? bits << 8 | 255 : bits));
    }
    public int red() { return rgba >>> 24; }
    public int green() { return rgba >>> 16 & 255; }
    public int blue() { return rgba >>> 8 & 255; }
    public int alpha() { return rgba & 255; }
    public int argb() { return rgba >>> 8 | rgba << 24; }
    @Override public boolean equals(Object other) { return other instanceof ColorValue && rgba == ((ColorValue) other).rgba && rainbow == ((ColorValue) other).rainbow && Double.compare(speed, ((ColorValue) other).speed) == 0; }
    @Override public int hashCode() { return 31 * (31 * rgba + Boolean.hashCode(rainbow)) + Double.hashCode(speed); }
    @Override public String toString() { return String.format(Locale.ROOT, "#%08X", rgba); }
}
