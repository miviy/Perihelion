package perihelion.integration.minecraft189.control;

/** Player-owned counters, including the preceding update's values used by PRE routines. */
public final class GroundTickHistory {
  private int ground, air, beforeGround, beforeAir;

  public void advance(boolean grounded) {
    beforeGround = ground;
    beforeAir = air;
    ground = grounded ? ground + 1 : 0;
    air = grounded ? 0 : air + 1;
  }

  public int ground() {
    return ground;
  }

  public int air() {
    return air;
  }

  public int beforeGround() {
    return beforeGround;
  }

  public int beforeAir() {
    return beforeAir;
  }
}
