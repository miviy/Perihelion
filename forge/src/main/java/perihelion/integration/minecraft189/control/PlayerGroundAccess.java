package perihelion.integration.minecraft189.control;

/** Ground/air history follows the player, independently of enabled gameplay modules. */
public interface PlayerGroundAccess {
  GroundTickHistory perihelion$groundTicks();
}
