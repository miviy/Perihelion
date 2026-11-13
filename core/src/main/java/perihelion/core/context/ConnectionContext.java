package perihelion.core.context;

import java.util.Locale;

/** Address recognition is a local hint, not server authentication or minigame detection. */
public final class ConnectionContext {
    public enum Kind { DISCONNECTED, SINGLEPLAYER, MULTIPLAYER }
    public static final ConnectionContext DISCONNECTED = new ConnectionContext(Kind.DISCONNECTED, "");
    private final Kind kind;
    private final String address;
    private final boolean hypixel;

    public ConnectionContext(Kind kind, String address) {
        if (kind == null) throw new NullPointerException("kind");
        this.kind = kind;
        this.address = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        String host = this.address;
        int colon = host.lastIndexOf(':');
        if (colon >= 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        this.hypixel = kind == Kind.MULTIPLAYER && (host.equals("hypixel.net") || host.endsWith(".hypixel.net"));
    }

    public Kind kind() { return kind; }
    public String address() { return address; }
    public boolean isHypixel() { return hypixel; }
}
