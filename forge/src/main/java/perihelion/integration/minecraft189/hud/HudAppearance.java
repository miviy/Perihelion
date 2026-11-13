package perihelion.integration.minecraft189.hud;

/** Shared HUD layout choices and alpha-preserving color blending. */
final class HudAppearance {
  enum Alignment {
    RIGHT,
    LEFT
  }

  enum Sort {
    WIDTH,
    ALPHABETICAL
  }

  enum Border {
    NONE,
    LEFT,
    RIGHT,
    TOP,
    OUTLINE
  }

  enum Suffix {
    SPACE,
    BRACKETS,
    PARENTHESES,
    DASH;

    String join(String name, String mode) {
      switch (this) {
        case BRACKETS:
          return name + " \u00a77[" + mode + "]";
        case PARENTHESES:
          return name + " \u00a77(" + mode + ")";
        case DASH:
          return name + " \u00a77- " + mode;
        default:
          return name + " \u00a77" + mode;
      }
    }
  }

  static int blend(int first, int second, float amount) {
    float f = Math.max(0, Math.min(1, amount));
    int result = 0;
    for (int shift = 0; shift <= 24; shift += 8)
      result |=
          (int) (((first >>> shift) & 255) * (1 - f) + ((second >>> shift) & 255) * f) << shift;
    return result;
  }
}
