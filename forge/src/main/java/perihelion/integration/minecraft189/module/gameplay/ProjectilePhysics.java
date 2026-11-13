package perihelion.integration.minecraft189.module.gameplay;

/** Vanilla tick order: move, apply drag, then gravity. No game objects or mutable caches. */
final class ProjectilePhysics {
  static final double ARROW_DRAG = .99, ARROW_GRAVITY = .05;

  private ProjectilePhysics() {}

  static double bowSpeed(double chargeTicks) {
    double charge = Math.max(0, chargeTicks) / 20;
    return Math.min(1, (charge * charge + 2 * charge) / 3) * 3;
  }

  static double travelFactor(double ticks, double drag) {
    int whole = (int) ticks;
    return (1 - Math.pow(drag, whole)) / (1 - drag) + (ticks - whole) * Math.pow(drag, whole);
  }

  static double drop(double ticks, double drag, double gravity) {
    int whole = (int) ticks;
    double factor = (1 - Math.pow(drag, whole)) / (1 - drag);
    return gravity * ((whole - factor) / (1 - drag) + (ticks - whole) * factor);
  }

  static final class Shot {
    final double x, y, z, ticks;

    Shot(double x, double y, double z, double ticks) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.ticks = ticks;
    }
  }

  /** Earliest reachable arc; bounded to 120 ticks and rejects unreachable/invalid inputs. */
  static Shot solve(double x, double y, double z, double vx, double vy, double vz, double speed) {
    if (!Double.isFinite(x + y + z + vx + vy + vz + speed) || speed < .3) return null;
    double previous = .0001;
    for (double time = .25; time <= 120; time += .25) {
      Shot shot = at(x, y, z, vx, vy, vz, time);
      if (length(shot) <= speed) {
        double low = previous, high = time;
        for (int i = 0; i < 18; i++) {
          double middle = (low + high) * .5;
          if (length(at(x, y, z, vx, vy, vz, middle)) > speed) low = middle;
          else high = middle;
        }
        return at(x, y, z, vx, vy, vz, high);
      }
      previous = time;
    }
    return null;
  }

  private static Shot at(double x, double y, double z, double vx, double vy, double vz, double t) {
    double factor = travelFactor(t, ARROW_DRAG);
    return new Shot(
        (x + vx * t) / factor,
        (y + vy * t + drop(t, ARROW_DRAG, ARROW_GRAVITY)) / factor,
        (z + vz * t) / factor,
        t);
  }

  private static double length(Shot shot) {
    return Math.sqrt(shot.x * shot.x + shot.y * shot.y + shot.z * shot.z);
  }
}
