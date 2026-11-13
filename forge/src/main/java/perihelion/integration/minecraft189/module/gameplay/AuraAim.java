package perihelion.integration.minecraft189.module.gameplay;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;
import perihelion.core.control.Rotation;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;

/** Aura-local aim variation. Uses native settings and no global rotation/model manager. */
final class AuraAim {
  static Vec3 normalPoint(Minecraft game, Entity target) {
    AxisAlignedBB box = target.getEntityBoundingBox();
    double height =
        Math.max(
            0,
            Math.min(
                game.thePlayer.posY - target.posY + game.thePlayer.getEyeHeight(),
                (box.maxY - box.minY) * .9));
    return target.getPositionVector().addVector(0, height, 0);
  }

  enum Hit {
    RANDOMIZED,
    HEAD,
    BODY,
    FEET
  }

  enum Noise {
    GAUSSIAN,
    PERLIN,
    FRACTAL3D,
    MIXED
  }

  enum Preset {
    NONE,
    LEGIT,
    AI,
    CUSTOM
  }

  private final Setting<Boolean> advanced, override, cycle, overshoot, noise, flick;
  private final Setting<Hit> hit;
  private final Setting<Noise> noiseMode;
  private final Setting<Preset> preset;
  private final Setting<String> samples;
  private final Setting<Double> randomization, frequency, bodyEase, mlEase;
  private final Setting<Integer> cycleTicks,
      overshootAmount,
      recovery,
      chance,
      angle,
      hold,
      cooldown;

  AuraAim(GameplayModule module) {
    advanced = module.bool("advanced-mode", "Advanced Mode", false);
    override =
        module.bool("hit-vec-override", "Hit Vec Override", false).visibleWhen(advanced::get);
    hit =
        module
            .mode("hit-vec-mode", "Hit Vec Mode", Hit.RANDOMIZED, Hit.class)
            .visibleWhen(() -> advanced.get() && override.get());
    randomization =
        module
            .number("hit-vec-randomization", "Hit Vec Randomization", 5, 0, 10, 0.5)
            .visibleWhen(() -> advanced.get() && override.get());
    cycle =
        module
            .bool("cycle-hit-points", "Cycle Hit Points", false)
            .visibleWhen(() -> advanced.get() && override.get());
    cycleTicks = module.integer("cycle-ticks", "Cycle Ticks", 10, 2, 40).visibleWhen(cycle::get);
    overshoot = module.bool("overshoot", "Overshoot", false).visibleWhen(advanced::get);
    overshootAmount =
        module
            .integer("overshoot-amount", "Overshoot Amount", 8, 1, 30)
            .visibleWhen(overshoot::get);
    recovery =
        module
            .integer("overshoot-recovery", "Overshoot Recovery", 4, 1, 15)
            .visibleWhen(overshoot::get);
    noise = module.bool("noise", "Noise", false).visibleWhen(advanced::get);
    noiseMode =
        module
            .mode("noise-mode", "Noise Mode", Noise.GAUSSIAN, Noise.class)
            .visibleWhen(noise::get);
    frequency =
        module.number("noise-frequency", "Noise Frequency", 50, 1, 100, 1).visibleWhen(noise::get);
    flick = module.bool("flick", "Flick", false).visibleWhen(advanced::get);
    chance = module.integer("flick-chance", "Flick Chance", 15, 0, 100).visibleWhen(flick::get);
    angle = module.integer("flick-angle", "Flick Angle", 35, 5, 90).visibleWhen(flick::get);
    hold = module.integer("flick-ticks", "Flick Ticks", 2, 1, 10).visibleWhen(flick::get);
    cooldown =
        module.integer("flick-cooldown", "Flick Cooldown", 20, 0, 100).visibleWhen(flick::get);
    bodyEase = module.number("body-ease", "Body Ease", 0.2, 0.01, 1, 0.01);
    mlEase = module.number("ml-ease", "ML Ease", 0.2, 0.01, 1, 0.01);
    preset = module.mode("ml-preset", "ML Preset", Preset.NONE, Preset.class);
    samples =
        module.text(
            "ml-samples",
            "ML Samples",
            "Custom yaw,pitch samples separated by semicolons.",
            "",
            16384);
  }

  State activate(ModuleContext c) {
    State state = new State();
    state.reload();
    c.observe(preset, (s, a, b) -> state.reload());
    c.observe(samples, (s, a, b) -> state.reload());
    return state;
  }

  final class State {
    private final Random random = new Random();
    private EntityLivingBase previous;
    private Vec3 body;
    private Hit cycled = Hit.HEAD;
    private int cycleLeft, overshootLeft, flickLeft, cooldownLeft;
    private float overshootOffset, flickOffset, easedYaw, easedPitch;
    private Model model;

    private void reload() {
      model = null;
      easedYaw = easedPitch = 0;
      if (preset.get() == Preset.NONE) return;
      try (InputStream input =
          preset.get() == Preset.CUSTOM
              ? new ByteArrayInputStream(
                  samples.get().replace(';', '\n').getBytes(StandardCharsets.UTF_8))
              : AuraAim.class.getResourceAsStream(
                  "/assets/perihelion/rotationpresets/"
                      + preset.get().name().toLowerCase(Locale.ROOT)
                      + ".csv")) {
        if (input != null) model = Model.read(input, random);
      } catch (IOException failure) {
        throw new IllegalStateException("Unable to load rotation samples", failure);
      }
    }

    Rotation aim(Minecraft mc, EntityLivingBase target, boolean ml, double attackRange) {
      if (target != previous) {
        body = null;
        easedYaw = easedPitch = 0;
        if (previous != null && advanced.get() && overshoot.get()) {
          overshootLeft = recovery.get();
          overshootOffset = (random.nextBoolean() ? 1 : -1) * overshootAmount.get();
        }
        previous = target;
      }
      Vec3 point = normalPoint(mc, target);
      if (ml && model != null) {
        Vec3 next = point(target, Hit.RANDOMIZED, 0);
        body =
            body == null
                ? next
                : body.addVector(
                    (next.xCoord - body.xCoord) * bodyEase.get(),
                    (next.yCoord - body.yCoord) * bodyEase.get(),
                    (next.zCoord - body.zCoord) * bodyEase.get());
        point = body;
      } else if (advanced.get() && override.get()) {
        Hit selected = hit.get();
        if (cycle.get()) {
          if (cycleLeft-- <= 0) {
            cycled = Hit.values()[1 + random.nextInt(3)];
            cycleLeft = cycleTicks.get();
          }
          selected = cycled;
        }
        point = point(target, selected, randomization.get());
      }
      Rotation base =
          ml && model != null
              ? GameActions.lookAt(mc.thePlayer, point)
              : GameActions.yuriLookAt(mc.thePlayer, point);
      float yaw = base.yaw(), pitch = base.pitch();
      if (ml && model != null) {
        double[] offset = model.sample(random);
        easedYaw += (offset[0] - easedYaw) * mlEase.get();
        easedPitch += (offset[1] - easedPitch) * mlEase.get();
        yaw += easedYaw;
        pitch += easedPitch;
      }
      if (overshootLeft > 0) {
        yaw += overshootOffset;
        overshootOffset -= overshootOffset / overshootLeft--;
      }
      if (advanced.get() && noise.get()) {
        double frequencyValue = Math.max(0.1, frequency.get() / 5),
            t = GameplayModule.now() / 1000.0 * frequencyValue,
            amplitude = 0.4 + frequencyValue * 0.15,
            dy,
            dp;
        switch (noiseMode.get()) {
          case PERLIN:
            dy = Math.sin(t) * 0.6 + Math.sin(t * 2.13) * 0.3;
            dp = Math.cos(t * 1.7) * 0.6 + Math.sin(t * 3.1) * 0.3;
            break;
          case FRACTAL3D:
            dy = Math.sin(t) + Math.sin(t * 2) * 0.5 + Math.sin(t * 4) * 0.25;
            dp = Math.cos(t) + Math.cos(t * 2) * 0.5 + Math.cos(t * 4) * 0.25;
            break;
          case MIXED:
            dy = (Math.sin(t) + random.nextDouble() * 2 - 1) * 0.5;
            dp = (Math.cos(t) + random.nextDouble() * 2 - 1) * 0.5;
            break;
          default:
            dy = random.nextDouble() * 2 - 1;
            dp = random.nextDouble() * 2 - 1;
            break;
        }
        yaw += dy * amplitude;
        pitch += dp * amplitude * 0.5;
      }
      if (!advanced.get() || !flick.get()) {
        flickLeft = 0;
        flickOffset = 0;
      } else if (flickLeft > 0) {
        if (--flickLeft == 0) {
          flickOffset = 0;
          cooldownLeft = cooldown.get();
        }
      } else if (cooldownLeft > 0) cooldownLeft--;
      else if (mc.thePlayer.getDistanceToEntity(target) <= attackRange + 1
          && random.nextDouble() * 100 < chance.get()) {
        flickLeft = hold.get();
        flickOffset = (random.nextBoolean() ? 1 : -1) * angle.get();
      }
      return Rotation.server(yaw + flickOffset, MathHelper.clamp_float(pitch, -90, 90));
    }

    private Vec3 point(EntityLivingBase target, Hit selected, double jitter) {
      AxisAlignedBB b = target.getEntityBoundingBox();
      double x = 0.5, y = selected == Hit.HEAD ? 0.9 : selected == Hit.FEET ? 0.1 : 0.5, z = 0.5;
      if (selected == Hit.RANDOMIZED) {
        x = random.nextDouble();
        y = random.nextDouble();
        z = random.nextDouble();
      } else {
        x += (random.nextDouble() - 0.5) * jitter / 10;
        y += (random.nextDouble() - 0.5) * jitter / 10;
        z += (random.nextDouble() - 0.5) * jitter / 10;
      }
      return new Vec3(
          b.minX + (b.maxX - b.minX) * x,
          b.minY + (b.maxY - b.minY) * y,
          b.minZ + (b.maxZ - b.minZ) * z);
    }
  }

  static final class Model {
    private final List<double[]> reservoir;
    private final double[] mean, deviation;

    private Model(List<double[]> reservoir, double[] mean, double[] deviation) {
      this.reservoir = reservoir;
      this.mean = mean;
      this.deviation = deviation;
    }

    static Model read(InputStream input, Random random) throws IOException {
      List<double[]> rows = new ArrayList<>();
      double[] sum = new double[2], squares = new double[2];
      int count = 0, lines = 0;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (++lines > 100000) throw new IOException("Rotation sample limit exceeded");
          String[] values = line.split(",", -1);
          if (values.length != 2) continue;
          double[] pair = new double[2];
          try {
            pair[0] = Double.parseDouble(values[0]);
            pair[1] = Double.parseDouble(values[1]);
          } catch (NumberFormatException bad) {
            continue;
          }
          if (!Double.isFinite(pair[0])
              || !Double.isFinite(pair[1])
              || Math.abs(pair[0]) > 360
              || Math.abs(pair[1]) > 360) continue;
          count++;
          for (int i = 0; i < 2; i++) {
            sum[i] += pair[i];
            squares[i] += pair[i] * pair[i];
          }
          if (rows.size() < 300) rows.add(pair);
          else {
            int index = random.nextInt(count);
            if (index < 300) rows.set(index, pair);
          }
        }
      }
      if (count == 0) return null;
      double[] mean = {sum[0] / count, sum[1] / count},
          deviation =
              {
                Math.sqrt(Math.max(0, squares[0] / count - mean[0] * mean[0])),
                Math.sqrt(Math.max(0, squares[1] / count - mean[1] * mean[1]))
              };
      return new Model(rows, mean, deviation);
    }

    double[] sample(Random random) {
      if (random.nextBoolean()) return reservoir.get(random.nextInt(reservoir.size())).clone();
      return new double[] {
        mean[0] + random.nextGaussian() * deviation[0],
        mean[1] + random.nextGaussian() * deviation[1]
      };
    }
  }
}
