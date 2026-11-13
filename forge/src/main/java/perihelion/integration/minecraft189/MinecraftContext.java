package perihelion.integration.minecraft189;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

/** Small client-thread helpers. Never retains world, player, or connection references. */
public final class MinecraftContext {
    private MinecraftContext() { }

    public static Minecraft current() { return Minecraft.getMinecraft(); }

    public static boolean hasWorld() {
        requireClientThread();
        Minecraft game = current();
        return game.theWorld != null && game.thePlayer != null;
    }

    public static void requireClientThread() {
        if (!current().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Minecraft state requires the client thread");
        }
    }

    public static void chat(String message) {
        requireClientThread();
        Minecraft game = current();
        if (game.ingameGUI != null) game.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
    }

    public static long nowNanos() { return System.nanoTime(); }
}
