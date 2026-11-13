package perihelion.core.context;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConnectionContextTest {
    @Test public void hypixelHintRequiresCompleteDomainBoundaryAndMultiplayer() {
        for (String host : new String[] {"hypixel.net", " MC.HYPIXEL.NET:25565 ", "mc.hypixel.net.:25565"})
            assertTrue(new ConnectionContext(ConnectionContext.Kind.MULTIPLAYER, host).isHypixel());
        for (String host : new String[] {"nothypixel.net", "hypixel.net.example.com", "127.0.0.1", "[::1]:25565", ""})
            assertFalse(new ConnectionContext(ConnectionContext.Kind.MULTIPLAYER, host).isHypixel());
        assertFalse(new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, "hypixel.net").isHypixel());
        assertFalse(ConnectionContext.DISCONNECTED.isHypixel());
    }
}
