package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.Minecraft;
import perihelion.core.control.Rotation;
import perihelion.integration.minecraft189.control.PlayerRotationAccess;

/** Movement headings and vector-limited turning, independent of module ownership. */
final class RotationDynamics {
  private RotationDynamics() {}

  static float movementYaw(
      float camera, boolean forward, boolean back, boolean left, boolean right) {
    float offset = 0;
    if (forward) offset = left != right ? (left ? -45 : 45) : 0;
    else if (back) offset = left != right ? (left ? -135 : 135) : 180;
    else if (left != right) offset = left ? -90 : 90;
    return (camera + offset) % 360 < 0 ? (camera + offset) % 360 + 360 : (camera + offset) % 360;
  }

  static float movementYaw(Minecraft game) {
    return movementYaw(
        game.thePlayer.rotationYaw,
        game.gameSettings.keyBindForward.isKeyDown(),
        game.gameSettings.keyBindBack.isKeyDown(),
        game.gameSettings.keyBindLeft.isKeyDown(),
        game.gameSettings.keyBindRight.isKeyDown());
  }

  static Rotation step(Rotation previous, Rotation target, double degrees) {
    float[] move = turn(previous, target, degrees);
    return Rotation.serverUnwrapped(previous.yaw() + move[0], previous.pitch() + move[1]);
  }

  private static float[] turn(Rotation previous, Rotation target, double degrees) {
    // Manager branch selection precedes the source move() distribution and float cast.
    float selected = previous.yaw() + wrap(target.yaw() - previous.yaw());
    double yaw = wrap(selected - previous.yaw()), pitch = target.pitch() - previous.pitch();
    double distance = Math.sqrt(yaw * yaw + pitch * pitch);
    // Keep finite settled/zero-speed behavior instead of the donor's zero-vector division.
    if (distance == 0 || degrees == 0) return new float[] {0, 0};
    double maxYaw = degrees * Math.abs(yaw / distance),
        maxPitch = degrees * Math.abs(pitch / distance);
    return new float[] {
      (float) Math.max(Math.min(yaw, maxYaw), -maxYaw),
      (float) Math.max(Math.min(pitch, maxPitch), -maxPitch)
    };
  }

  static Rotation quantize(Rotation angle, Rotation origin, float sensitivity) {
    return quantize(angle, origin, sensitivity, 0);
  }

  static Rotation quantize(Rotation angle, Rotation origin, float sensitivity, double jitter) {
    return quantize(angle.yaw(), angle.pitch(), origin, sensitivity, jitter);
  }

  private static Rotation quantize(
      float yaw, float pitch, Rotation origin, float sensitivity, double jitter) {
    float scale = (float) (sensitivity * (1 + jitter / 10000000) * .6f + .2f);
    double unit = scale * scale * scale * 8f * .15;
    return Rotation.serverUnwrapped(
        origin.yaw() + (float) (Math.round((yaw - origin.yaw()) / unit) * unit),
        clamp(origin.pitch() + (float) (Math.round((pitch - origin.pitch()) / unit) * unit)));
  }

  static Rotation smooth(Minecraft game, Rotation previous, Rotation target, double speed) {
    PlayerRotationAccess access = (PlayerRotationAccess) game.thePlayer;
    Rotation origin =
        Rotation.serverUnwrapped(
            access.perihelion$reportedYaw(), access.perihelion$reportedPitch());
    return smooth(
        previous,
        target,
        origin,
        speed,
        Minecraft.getDebugFPS(),
        game.gameSettings.mouseSensitivity,
        Math::random);
  }

  static Rotation smooth(
      Rotation previous,
      Rotation target,
      Rotation origin,
      double speed,
      int fps,
      float sensitivity,
      java.util.function.DoubleSupplier random) {
    float[] move = turn(previous, target, speed * 18 + random.getAsDouble());
    float yaw = previous.yaw() + move[0], pitch = previous.pitch() + move[1];
    boolean moving = Math.abs(move[0]) + Math.abs(move[1]) > .0001;
    for (int i = 1; i <= (int) (fps / 20f + random.getAsDouble() * 10); i++) {
      if (moving) {
        yaw += (random.getAsDouble() - .5) / 1000;
        pitch -= random.getAsDouble() / 200;
      }
      Rotation patched = quantize(yaw, pitch, origin, sensitivity, random.getAsDouble());
      yaw = patched.yaw();
      pitch = patched.pitch();
    }
    return Rotation.serverUnwrapped(yaw, pitch);
  }

  private static float wrap(float angle) {
    return Rotation.server(angle, 0).yaw();
  }

  private static float clamp(float angle) {
    return Math.max(-90, Math.min(90, angle));
  }
}
