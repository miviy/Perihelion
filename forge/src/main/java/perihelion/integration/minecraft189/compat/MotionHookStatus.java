package perihelion.integration.minecraft189.compat;

/** Set once while the player class is transformed; never loads Minecraft during bootstrap. */
public final class MotionHookStatus {
    private static volatile boolean serverRotation;
    private MotionHookStatus() { }
    public static boolean supportsServerRotation() { return serverRotation; }
    static void rotationHooks(boolean yaw, boolean pitch) { serverRotation = yaw && pitch; }
}
