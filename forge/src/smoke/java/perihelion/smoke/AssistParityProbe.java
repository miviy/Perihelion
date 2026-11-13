package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;

/** Actual render-frame view commits, yielding, and retirement, using an isolated player fixture. */
public final class AssistParityProbe {
  public static void run(ClientRuntime runtime, Object game, Object player, Object world)
      throws Exception {
    final String id = "aimassist";
    ModuleEntry entry = runtime.modules().require(id);
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : entry.module().settings().values()) saved.put(setting, setting.get());
    boolean enabled = entry.requestedEnabled();
    Field yaw = field(player.getClass(), "rotationYaw", "field_70177_z"),
        pitch = field(player.getClass(), "rotationPitch", "field_70125_A");
    Field oldYaw = field(player.getClass(), "prevRotationYaw", "field_70126_B"),
        oldPitch = field(player.getClass(), "prevRotationPitch", "field_70127_C");
    float sy = yaw.getFloat(player),
        sp = pitch.getFloat(player),
        spy = oldYaw.getFloat(player),
        spp = oldPitch.getFloat(player);
    Object fixture =
        Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
            .getConstructor(
                Class.forName("net.minecraft.world.World"), com.mojang.authlib.GameProfile.class)
            .newInstance(
                world, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AimFixture"));
    double x = field(player.getClass(), "posX", "field_70165_t").getDouble(player),
        y = field(player.getClass(), "posY", "field_70163_u").getDouble(player),
        z = field(player.getClass(), "posZ", "field_70161_v").getDouble(player);
    method(
            fixture.getClass(),
            new Class<?>[] {double.class, double.class, double.class},
            "setPosition",
            "func_70107_b")
        .invoke(fixture, x + 3, y, z + 1);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -7654388, fixture);
    try {
      runtime.modules().setEnabled(id, false);
      set(runtime, id, "require-mouse-down", "false");
      set(runtime, id, "max-angle", "360");
      set(runtime, id, "horizontal-speed", "10");
      set(runtime, id, "distance", "8");
      runtime.modules().setEnabled(id, true);
      Object assist = entry.module();
      Method frame = method(assist.getClass(), "renderFrame");
      Field stamp = field(assist.getClass(), "lastSimpleNanos");
      yaw.setFloat(player, 0);
      oldYaw.setFloat(player, 0);
      for (int i = 0; i < 25; i++) {
        stamp.setLong(assist, System.nanoTime() - 30_000_000L);
        frame.invoke(assist);
      }
      check(
          Math.abs(yaw.getFloat(player)) > .001, "Aim Assist commits view during its render frame");
      check(
          !runtime.controls().rotation().isPresent(),
          "Aim Assist retires its frame view claim immediately");
      ModuleContext context = (ModuleContext) field(ModuleEntry.class, "context").get(entry);
      ControlRequest<Rotation> blocker = context.controls().rotation(1000);
      blocker.set(Rotation.server(35, 0));
      float before = yaw.getFloat(player), previous = oldYaw.getFloat(player);
      stamp.setLong(assist, System.nanoTime() - 30_000_000L);
      frame.invoke(assist);
      check(
          yaw.getFloat(player) == before && oldYaw.getFloat(player) == previous,
          "a stronger owner prevents current and previous view mutation");
      blocker.close();
      runtime.modules().setEnabled(id, false);
      check(
          !runtime.controls().rotation().isPresent(), "Aim Assist disable leaves no view request");
      System.out.println("PERIHELION_SMOKE ASSIST_FRAME checks=4");
    } finally {
      runtime.modules().setEnabled(id, false);
      for (Map.Entry<Setting<?>, Object> value : saved.entrySet())
        restore(runtime, id, value.getKey(), value.getValue());
      yaw.setFloat(player, sy);
      pitch.setFloat(player, sp);
      oldYaw.setFloat(player, spy);
      oldPitch.setFloat(player, spp);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -7654388);
      if (enabled) runtime.modules().setEnabled(id, true);
    }
  }

  private static void check(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, String id, Setting setting, Object value) {
    runtime.modules().setSetting(id, setting, value);
  }
}
