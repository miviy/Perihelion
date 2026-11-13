// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

/** The donor's distance, rotation and item-retention boundaries. */
final class ProjectileTiming {
  private ProjectileTiming() {}

  static long delayFor(double distance) {
    if (distance <= 3) return Long.MAX_VALUE;
    if (distance <= 5) return 550;
    if (distance <= 6) return 450;
    if (distance <= 7) return 350;
    return 250;
  }

  static int storeTicks(boolean rod) {
    return Math.max(2, (rod ? 200 : 80) / 50);
  }

  static boolean needsRotation(float yaw, float pitch, float playerYaw, float playerPitch) {
    float difference = (yaw - playerYaw) % 360;
    if (difference >= 180) difference -= 360;
    if (difference < -180) difference += 360;
    return Math.abs(difference) > 1.5F || Math.abs(pitch - playerPitch) > 1.5F;
  }
}
