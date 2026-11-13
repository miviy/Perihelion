package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.List;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

/** Real module-event regressions for authoritative time restoration and rendered aim changes. */
public final class VisualAuditProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Field worldField = field(game.getClass(), "theWorld", "field_71441_e");
    Object world = worldField.get(game),
        p = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Method getTime = method(world.getClass(), "getWorldTime", "func_72820_D"),
        setTime =
            method(world.getClass(), new Class<?>[] {long.class}, "setWorldTime", "func_72877_b");
    Object rules = method(world.getClass(), "getGameRules", "func_82736_K").invoke(world);
    Method
        getRule =
            method(rules.getClass(), new Class<?>[] {String.class}, "getBoolean", "func_82766_b"),
        setRule =
            method(
                rules.getClass(),
                new Class<?>[] {String.class, String.class},
                "setOrCreateGameRule",
                "func_82764_b");
    long oldTime = ((Number) getTime.invoke(world)).longValue();
    boolean oldDaylight = (Boolean) getRule.invoke(rules, "doDaylightCycle");
    boolean ambience = r.modules().require("ambience").requestedEnabled(),
        trajectories = r.modules().require("trajectories").requestedEnabled();
    Setting<?> time = r.modules().require("ambience").module().settings().get("time"),
        real = r.modules().require("ambience").module().settings().get("real-world-time");
    Object oldSettingTime = time.get(), oldReal = real.get();
    Object inventory = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Field slot = field(inventory.getClass(), "currentItem", "field_70461_c");
    int oldSlot = slot.getInt(inventory);
    Object oldItem = items[0];
    double x = number(p, "posX", "field_70165_t"),
        y = number(p, "posY", "field_70163_u"),
        z = number(p, "posZ", "field_70161_v");
    EventOwner owner =
        new EventOwner(
            "visual-audit-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    java.util.ArrayList<AutoCloseable> resources = new java.util.ArrayList<>();
    ControlRequest<Rotation> aim = r.controls().scope(owner, resources::add).rotation(10000);
    try {
      r.modules().setEnabled("ambience", false);
      r.modules().setEnabled("trajectories", false);
      set(r, "ambience", "time", "6000");
      set(r, "ambience", "real-world-time", "false");
      setTime.invoke(world, 0L);
      setRule.invoke(rules, "doDaylightCycle", "false");
      r.modules().setEnabled("ambience", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          ((Number) getTime.invoke(world)).longValue() == 6000,
          "Ambience applies its local override");
      r.modules().setEnabled("ambience", false);
      check(
          ((Number) getTime.invoke(world)).longValue() == 0
              && !(Boolean) getRule.invoke(rules, "doDaylightCycle"),
          "disable restores an initially frozen zero time without requiring a server packet");
      r.modules().setEnabled("ambience", true);
      Class<?> packet = Class.forName("net.minecraft.network.Packet");
      Constructor<?> timePacket =
          Class.forName("net.minecraft.network.play.server.S03PacketTimeUpdate")
              .getConstructor(long.class, long.class, boolean.class);
      ClientPacketEvent frozen =
          (ClientPacketEvent)
              ClientPacketEvent.class
                  .getConstructor(packet)
                  .newInstance(timePacket.newInstance(100L, 4321L, false));
      r.clientEvents().post(frozen);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      r.modules().setEnabled("ambience", false);
      check(
          frozen.isCancelled()
              && ((Number) getTime.invoke(world)).longValue() == 4321
              && !(Boolean) getRule.invoke(rules, "doDaylightCycle"),
          "a frozen S03 snapshot restores its exact time and disabled daylight rule");
      r.modules().setEnabled("ambience", true);
      ClientPacketEvent advancing =
          (ClientPacketEvent)
              ClientPacketEvent.class
                  .getConstructor(packet)
                  .newInstance(timePacket.newInstance(200L, 12000L, true));
      r.clientEvents().post(advancing);
      r.modules().setEnabled("ambience", false);
      check(
          advancing.isCancelled()
              && ((Number) getTime.invoke(world)).longValue() >= 12000
              && (Boolean) getRule.invoke(rules, "doDaylightCycle"),
          "a newer advancing snapshot restores the enabled daylight rule");
      r.modules().setEnabled("ambience", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      worldField.set(game, null);
      try {
        r.modules().setEnabled("ambience", false);
      } finally {
        worldField.set(game, world);
      }
      check(
          ((Number) getTime.invoke(world)).longValue() == 6000,
          "cleanup cannot write time back into a detached world");

      items[0] = stack("snowball");
      slot.setInt(inventory, 0);
      position(p, x, 200, z);
      r.modules().setEnabled("trajectories", true);
      Object module = r.modules().require("trajectories").module();
      @SuppressWarnings("unchecked")
      List<Object> points = (List<Object>) field(module.getClass(), "points").get(module);
      aim.set(Rotation.server(0, 0));
      r.clientEvents().post(new WorldRenderEvent(1));
      check(
          points.size() >= 2
              && number(points.get(1), "zCoord", "field_72449_c")
                      - number(points.get(0), "zCoord", "field_72449_c")
                  > 1.49,
          "rendered trajectory uses the effective forward aim");
      Object first = points.get(0);
      r.clientEvents().post(new WorldRenderEvent(1));
      check(
          points.get(0) == first,
          "an unchanged same-tick aim retains the bounded cached trajectory");
      aim.set(Rotation.server(90, 0));
      r.clientEvents().post(new WorldRenderEvent(1));
      check(
          points.size() >= 2
              && points.get(0) != first
              && number(points.get(1), "xCoord", "field_72450_a")
                      - number(points.get(0), "xCoord", "field_72450_a")
                  < -1.49,
          "changing effective yaw within the same player tick recomputes the first flight segment");
      first = points.get(0);
      position(p, x + 1, 200, z);
      r.clientEvents().post(new WorldRenderEvent(1));
      check(
          points.get(0) != first, "same-tick position changes invalidate the cached launch origin");
      r.modules().setEnabled("trajectories", false);
      check(points.isEmpty(), "trajectory activation cleanup discards its cached path");
      System.out.println("PERIHELION_SMOKE VISUAL_AUDIT checks=" + checks + " PASS");
    } finally {
      worldField.set(game, world);
      r.modules().setEnabled("ambience", false);
      r.modules().setEnabled("trajectories", false);
      aim.clear();
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      restore(r, time, oldSettingTime);
      restore(r, real, oldReal);
      setTime.invoke(world, oldTime);
      setRule.invoke(rules, "doDaylightCycle", Boolean.toString(oldDaylight));
      items[0] = oldItem;
      slot.setInt(inventory, oldSlot);
      position(p, x, y, z);
      r.modules().setEnabled("ambience", ambience);
      r.modules().setEnabled("trajectories", trajectories);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, Setting<T> setting, Object value) {
    r.modules().setSetting("ambience", setting, (T) value);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
