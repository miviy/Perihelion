package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import perihelion.core.control.Rotation;

/** Donor search strategies expressed as pure eye rays; searching never moves the player. */
final class ScaffoldRotations {
  enum Search {
    NORMAL,
    SECONDARY,
    ULTRA_SAFE
  }

  private ScaffoldRotations() {}

  static Rotation watchdog(Minecraft game, Placement plan, Rotation previous, boolean old) {
    float base = RotationDynamics.movementYaw(game) + 90;
    // Closest heading first, one-degree offsets through 20, then two-degree offsets through 90.
    for (int i = 0; i <= 110; i++) {
      int magnitude = i == 0 ? 0 : (i + 1) / 2;
      float offset = magnitude <= 20 ? magnitude : 20 + (magnitude - 20) * 2;
      if ((i & 1) == 1) offset = -offset;
      for (int j = 0; j <= 40; j++) {
        // Every positive pitch delta clamps to the already-tested 90 degrees.
        // Removing those duplicate rays preserves the first matching candidate.
        if (j > 0 && (j & 1) == 0) continue;
        int delta = j == 0 ? 0 : (j + 1) / 2 * ((j & 1) == 1 ? -1 : 1);
        Rotation candidate =
            Rotation.server(base + offset, MathHelper.clamp_float(90 + delta, -90, 90));
        if (plan.ray(game, candidate, 4.5, true)) return candidate;
      }
    }
    return old
        ? GameActions.lookAt(game.thePlayer, plan.hit)
        : find(game, plan, previous, Search.NORMAL, true);
  }

  static Rotation find(
      Minecraft game, Placement plan, Rotation previous, Search search, boolean strict) {
    Rotation center = GameActions.lookAt(game.thePlayer, plan.hit);
    switch (search) {
      case SECONDARY:
        Rotation block =
            GameActions.lookAt(game.thePlayer, new Vec3(plan.support).addVector(.5, .5, .5));
        Rotation best = null;
        perihelion.integration.minecraft189.control.PlayerRotationAccess access =
            (perihelion.integration.minecraft189.control.PlayerRotationAccess) game.thePlayer;
        Rotation quantizationOrigin =
            Rotation.serverUnwrapped(
                access.perihelion$reportedYaw(), access.perihelion$reportedPitch());
        double distance = Double.MAX_VALUE;
        for (int yaw = -15; yaw <= 15; yaw += 3)
          for (int pitch = -15; pitch <= 15; pitch += 3) {
            Rotation test =
                Rotation.serverUnwrapped(
                    previous.yaw()
                        + MathHelper.wrapAngleTo180_float(block.yaw() + yaw - previous.yaw()),
                    MathHelper.clamp_float(block.pitch() + pitch, -90, 90));
            double difference =
                Math.hypot(
                    MathHelper.wrapAngleTo180_float(test.yaw() - previous.yaw()),
                    test.pitch() - previous.pitch());
            Rotation sent =
                RotationDynamics.quantize(
                    test, quantizationOrigin, game.gameSettings.mouseSensitivity);
            if (difference < distance && plan.ray(game, sent, 4.5, strict)) {
              // A ray on a stair seam can disappear after mouse-step rounding. Validate
              // the angle walking will send while retaining the donor's candidate order.
              best = sent;
              distance = difference;
            }
          }
        return best == null ? center : best;
      case ULTRA_SAFE:
        return nearest(game, plan, previous, center);
      default:
        Vec3 origin =
            new Vec3(
                game.thePlayer.posX,
                plan.support.getY() + .5 + (Math.random() - .5) * .1,
                game.thePlayer.posZ);
        for (int offset = -180; offset <= 180; offset += 45) {
          Vec3 direction =
              Placement.direction(Rotation.server(game.thePlayer.rotationYaw + offset * 3, 0));
          MovingObjectPosition hit =
              game.theWorld.rayTraceBlocks(
                  origin,
                  origin.addVector(direction.xCoord * 4.5, 0, direction.zCoord * 4.5),
                  false,
                  false,
                  true);
          if (hit == null || hit.hitVec == null) continue;
          Rotation test = GameActions.lookAt(game.thePlayer, hit.hitVec);
          if (plan.ray(game, test, 4.5)) return test;
        }
        return plan.ray(game, previous, 4.5) ? previous : center;
    }
  }

  private static Rotation nearest(
      Minecraft game, Placement plan, Rotation previous, Rotation center) {
    if (plan.ray(game, previous, 4.5, true)) return previous;
    Vec3 eye = game.thePlayer.getPositionEyes(1);
    AxisAlignedBB cube = new AxisAlignedBB(plan.support, plan.support.add(1, 1, 1));
    Rotation[] first = new Rotation[EnumFacing.values().length];
    Rotation fallback = null;
    // Each axis has only 541 offsets. Reuse its exact trigonometry through the ordered search.
    float[] yaws = new float[541], pitches = new float[541];
    double[] sinYaw = new double[541],
        cosYaw = new double[541],
        sinPitch = new double[541],
        cosPitch = new double[541];
    for (int offset = -270; offset <= 270; offset++) {
      int index = offset + 270;
      yaws[index] = MathHelper.wrapAngleTo180_float(previous.yaw() + offset);
      pitches[index] = MathHelper.clamp_float(previous.pitch() + offset, -90, 90);
      sinYaw[index] = Math.sin(Math.toRadians(yaws[index]));
      cosYaw[index] = Math.cos(Math.toRadians(yaws[index]));
      sinPitch[index] = Math.sin(Math.toRadians(pitches[index]));
      cosPitch[index] = Math.cos(Math.toRadians(pitches[index]));
    }
    // Preserve Yuri's Manhattan-distance ordering, pruning rays that cannot hit the support
    // cube.
    for (int radius = 0; radius <= 270; radius++)
      for (int yaw = -radius; yaw <= radius; yaw++) {
        int remainder = radius - Math.abs(yaw);
        for (int sign = 0; sign < (remainder == 0 ? 1 : 2); sign++) {
          int yi = yaw + 270, pi = (sign == 0 ? remainder : -remainder) + 270;
          double dx = -sinYaw[yi] * cosPitch[pi] * 4.5;
          double dy = -sinPitch[pi] * 4.5;
          double dz = cosYaw[yi] * cosPitch[pi] * 4.5;
          if (!RayBox.intersects(
              eye.xCoord,
              eye.yCoord,
              eye.zCoord,
              dx,
              dy,
              dz,
              cube.minX,
              cube.minY,
              cube.minZ,
              cube.maxX,
              cube.maxY,
              cube.maxZ)) continue;
          Vec3 end = eye.addVector(dx, dy, dz);
          if (!cube.isVecInside(eye) && cube.calculateIntercept(eye, end) == null) continue;
          MovingObjectPosition hit = game.theWorld.rayTraceBlocks(eye, end, false, false, true);
          if (hit == null
              || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
              || !plan.support.equals(hit.getBlockPos())) continue;
          Rotation candidate = Rotation.server(yaws[yi], pitches[pi]);
          if (hit.sideHit == plan.face) return candidate;
          if (fallback == null) fallback = candidate;
          if (first[hit.sideHit.ordinal()] == null) first[hit.sideHit.ordinal()] = candidate;
        }
      }
    for (EnumFacing face : EnumFacing.values())
      if (first[face.ordinal()] != null) return first[face.ordinal()];
    return fallback == null ? center : fallback;
  }
}
