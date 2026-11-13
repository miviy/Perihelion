// SPDX-License-Identifier: AGPL-3.0-only
// Mathematical reference adapted from Yuri 1cb86ad RotationManager and RotationUtils.
package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.Random;
import java.util.function.DoubleSupplier;
import net.minecraft.util.MathHelper;
import org.junit.Test;
import perihelion.core.control.Rotation;

public class YuriRotationParityTest {
  @Test
  public void sameRandomSamplesMatchThePinnedSourceTurnAndSensitivityPath() {
    Random inputs = new Random(0x59495249L);
    for (int sample = 0; sample < 20000; sample++) {
      Rotation previous =
          Rotation.serverUnwrapped(inputs.nextFloat() * 2160 - 1080, inputs.nextFloat() * 178 - 89);
      Rotation target =
          Rotation.server(
              inputs.nextFloat() * 720 - 360,
              sample % 9 == 0 ? -90 : inputs.nextFloat() * 180 - 90);
      Rotation origin =
          Rotation.serverUnwrapped(
              previous.yaw() + inputs.nextFloat() * 360 - 180, inputs.nextFloat() * 180 - 90);
      double speed = new double[] {.01, 1, 3.6f, 5, 10, 100}[sample % 6];
      int fps = new int[] {0, 10, 20, 60, 144, 1000}[sample % 6];
      float sensitivity = new float[] {0, .07f, .1f, .5f, .9f, 1}[sample % 6];
      long seed = inputs.nextLong();
      Tape nativeRandom = new Tape(seed), sourceRandom = new Tape(seed);
      float[] expected = source(previous, target, origin, speed, fps, sensitivity, sourceRandom);
      Rotation actual =
          RotationDynamics.smooth(previous, target, origin, speed, fps, sensitivity, nativeRandom);
      String context =
          "sample="
              + sample
              + " previous="
              + previous.yaw()
              + "/"
              + previous.pitch()
              + " target="
              + target.yaw()
              + "/"
              + target.pitch()
              + " origin="
              + origin.yaw()
              + "/"
              + origin.pitch()
              + " seed="
              + seed;
      assertEquals(context + " yaw", expected[0], actual.yaw(), 0);
      assertEquals(context + " pitch", expected[1], actual.pitch(), 0);
      assertEquals(context + " random consumption", sourceRandom.calls, nativeRandom.calls);
    }
  }

  // Literal source order: manager branch selection, move distribution, compound float jitter,
  // then sensitivity patch. No donor event/module/global architecture is imported.
  private static float[] source(
      Rotation previous,
      Rotation target,
      Rotation origin,
      double speed,
      int fps,
      float sensitivity,
      DoubleSupplier random) {
    float targetYaw =
        previous.yaw() + MathHelper.wrapAngleTo180_float(target.yaw() - previous.yaw());
    double budget = speed * 18 + random.getAsDouble();
    double dy = MathHelper.wrapAngleTo180_float(targetYaw - previous.yaw()),
        dp = target.pitch() - previous.pitch();
    double distance = Math.sqrt(dy * dy + dp * dp);
    float moveYaw =
        (float)
            Math.max(
                Math.min(dy, budget * Math.abs(dy / distance)), -budget * Math.abs(dy / distance));
    float movePitch =
        (float)
            Math.max(
                Math.min(dp, budget * Math.abs(dp / distance)), -budget * Math.abs(dp / distance));
    float yaw = previous.yaw() + moveYaw, pitch = previous.pitch() + movePitch;
    for (int i = 1; i <= (int) (fps / 20f + random.getAsDouble() * 10); i++) {
      if (Math.abs(moveYaw) + Math.abs(movePitch) > .0001) {
        yaw += (random.getAsDouble() - .5) / 1000;
        pitch -= random.getAsDouble() / 200;
      }
      float scale = (float) (sensitivity * (1 + random.getAsDouble() / 10000000) * .6f + .2f);
      double unit = scale * scale * scale * 8f * .15;
      yaw = origin.yaw() + (float) (Math.round((yaw - origin.yaw()) / unit) * unit);
      pitch = origin.pitch() + (float) (Math.round((pitch - origin.pitch()) / unit) * unit);
      pitch = MathHelper.clamp_float(pitch, -90, 90);
    }
    return new float[] {yaw, pitch};
  }

  private static final class Tape implements DoubleSupplier {
    final Random random;
    int calls;

    Tape(long seed) {
      random = new Random(seed);
    }

    public double getAsDouble() {
      calls++;
      return random.nextDouble();
    }
  }
}
