// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

/** Yuri 1.3.1 bounded thrown-item and fishing-hook trajectory equations. */
final class ThrownTrajectory {
  private static final double LAUNCH_SPEED = 1.5;
  private static final int MAX_FLIGHT_TICKS = 60;

  private ThrownTrajectory() {}

  static double[] solvePitch(double horiz, double dy, double drag, double gravity) {
    double prevErr = Double.NaN;
    double prevPitch = 0.0;
    double prevTicks = 0.0;
    for (double pitch = 70.0; pitch >= -80.0; pitch -= 0.5) {
      double[] r = simulate(pitch, horiz, drag, gravity);
      if (r == null) {
        prevErr = Double.NaN;
        continue;
      }
      double err = r[0] - dy;
      if (!Double.isNaN(prevErr) && prevErr < 0.0 && err >= 0.0) {
        double f = -prevErr / (err - prevErr);
        return new double[] {
          prevPitch + (pitch - prevPitch) * f, prevTicks + (r[1] - prevTicks) * f
        };
      }
      prevErr = err;
      prevPitch = pitch;
      prevTicks = r[1];
    }
    return null;
  }

  static double[] simulate(double pitch, double horiz, double drag, double gravity) {
    double rad = Math.toRadians(pitch);
    double mh = Math.cos(rad) * LAUNCH_SPEED;
    double my = -Math.sin(rad) * LAUNCH_SPEED;
    if (mh < 0.02) return null;

    double h = 0.0;
    double y = 0.0;
    for (int t = 1; t <= MAX_FLIGHT_TICKS; t++) {
      double nh = h + mh;
      double ny = y + my;
      if (nh >= horiz) {
        double f = (horiz - h) / (nh - h);
        return new double[] {y + (ny - y) * f, (t - 1) + f};
      }
      h = nh;
      y = ny;
      mh *= drag;
      my *= drag;
      my -= gravity;
      if (mh < 0.02) return null;
    }
    return null;
  }
}
