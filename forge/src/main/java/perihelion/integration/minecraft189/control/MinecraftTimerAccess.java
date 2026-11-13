package perihelion.integration.minecraft189.control;

import net.minecraft.util.Timer;

/** Implemented by the Minecraft mixin, using the pinned 1.8.9 timer field. */
public interface MinecraftTimerAccess { Timer perihelion$timer(); }
