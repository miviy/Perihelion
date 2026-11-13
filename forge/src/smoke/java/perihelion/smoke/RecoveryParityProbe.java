package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.PlayerGroundAccess;
import perihelion.integration.minecraft189.event.*;

/** Recovery handoffs and exact stage boundaries through the packaged client and a PLAY TCP peer. */
public final class RecoveryParityProbe {
  private static int checks;
  private static final AtomicInteger network = new AtomicInteger();

  public static int networkEvents() {
    return network.get();
  }

  static void run(ClientRuntime r) throws Exception {
    checks = 0;
    network.set(0);
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game),
        world = field(game.getClass(), "theWorld", "field_71441_e").get(game),
        inv = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Object[] items = (Object[]) field(inv.getClass(), "mainInventory", "field_70462_a").get(inv),
        oldItems = items.clone();
    Field selected = field(inv.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    int oldSlot = selected.getInt(inv);
    Object oldScreen = screen.get(game);
    boolean oldFocus = focus.getBoolean(game);
    double px = number(p, "posX", "field_70165_t"),
        py = number(p, "posY", "field_70163_u"),
        pz = number(p, "posZ", "field_70161_v");
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"}, {"rotationPitch", "field_70125_A"},
          {"lastReportedYaw", "field_175164_bL"}, {"lastReportedPitch", "field_175165_bM"},
          {"onGround", "field_70122_E"}, {"inWater", "field_70171_ac"},
          {"fallDistance", "field_70143_R"}
        }) {
      Field f = field(p.getClass(), names);
      saved.put(f, f.get(p));
    }
    Object history = ((PlayerGroundAccess) p).perihelion$groundTicks();
    Map<Field, Object> oldHistory = new LinkedHashMap<>();
    for (Field f : history.getClass().getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers())) continue;
      f.setAccessible(true);
      oldHistory.put(f, f.get(history));
    }
    Object pot = r.modules().require("autopot").module();
    Field attacks = field(pot.getClass(), "attackTicks"),
        deadline = field(pot.getClass(), "nextThrowAt");
    int oldAttacks = attacks.getInt(pot);
    long oldDeadline = deadline.getLong(pot);
    Method
        flag =
            method(
                p.getClass(), new Class<?>[] {int.class, boolean.class}, "setFlag", "func_70052_a"),
        getFlag = method(p.getClass(), new Class<?>[] {int.class}, "getFlag", "func_70083_f");
    boolean oldBurning = (Boolean) getFlag.invoke(p, 0);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (String id :
        new String[] {"autopot", "autoextinguish", "nofall", "aura", "scaffold", "autoswap"}) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
      for (Setting<?> s : r.modules().require(id).module().settings().values())
        settings.put(s, s.get());
    }
    int x = (int) Math.floor(px), z = (int) Math.floor(pz), y = 200;
    Class<?> posType = Class.forName("net.minecraft.util.BlockPos"),
        stateType = Class.forName("net.minecraft.block.state.IBlockState");
    Method
        get = method(world.getClass(), new Class<?>[] {posType}, "getBlockState", "func_180495_p"),
        put =
            method(
                world.getClass(),
                new Class<?>[] {posType, stateType, int.class},
                "setBlockState",
                "func_180501_a");
    Object floor = pos(x, y - 1, z), feet = pos(x, y, z);
    Object oldFloor = get.invoke(world, floor), oldFeet = get.invoke(world, feet);
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    Method add =
        method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a");
    EventOwner owner =
        new EventOwner(
            "recovery-parity",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> blocker = r.controls().scope(owner, resources::add).rotation(1000);
    r.networkEvents()
        .subscribe(
            owner, PacketSendEvent.class, Integer.MAX_VALUE, true, e -> network.incrementAndGet());
    r.networkEvents()
        .subscribe(
            owner,
            PacketReceiveEvent.class,
            Integer.MAX_VALUE,
            true,
            e -> network.incrementAndGet());
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      screen.set(game, null);
      focus.setBoolean(game, true);
      Arrays.fill(items, null);
      items[0] = stack("diamond_sword");
      items[3] = potion();
      selected.setInt(inv, 0);
      put.invoke(world, floor, state("stone"), 2);
      put.invoke(world, feet, state("air"), 2);
      position(p, x + .5, y, z + .5);
      position(pig, x + .5, y, z + 2.5);
      add.invoke(world, -976632, pig);
      field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
      field(p.getClass(), "inWater", "field_70171_ac").setBoolean(p, false);
      field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 0);
      set(r, "autopot", "health", "15");
      potionSelection(pot, p);
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 0);
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 0);
      for (int i = 0; i < 4; i++) ((PlayerGroundAccess) p).perihelion$groundTicks().advance(true);
      attacks.setInt(pot, 9);
      deadline.setLong(pot, 0);
      set(r, "autopot", "delay", "100");
      set(r, "aura", "targets", "ANIMALS");
      set(r, "aura", "through-walls", "true");
      set(r, "aura", "rotations", "NONE");
      set(r, "aura", "auto-disable", "false");
      set(r, "aura", "fov", "360");
      r.modules().setEnabled("aura", true);
      r.modules().setEnabled("autopot", true);
      input(r, p, peer);
      check(busy(pot), "Auto Pot starts an owned routine on its first eligible update");
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "start update does not aim or change slots early");
      check(
          (Boolean)
              method(r.modules().require("aura").module().getClass(), "itemRoutineBusy")
                  .invoke(r.modules().require("aura").module()),
          "Aura observes Auto Pot's active routine");
      check(
          count(peer, "C02PacketUseEntity") == 0,
          "rotation-free Aura cannot attack while the potion is starting");
      walk(p, peer);
      input(r, p, peer);
      Rotation first = r.controls().rotation().get();
      check(
          first.pitch() > 60 && first.pitch() < 85,
          "combined yaw/pitch turn is speed-limited instead of snapping down");
      check(
          selected.getInt(inv) == 0 && count(peer, "C09PacketHeldItemChange") == 0,
          "original sword remains selected throughout aiming");
      StrafeEvent strafe = new StrafeEvent(.4f, .8f, .1f, 12);
      r.clientEvents().post(strafe);
      MovementYawEvent jump = new MovementYawEvent(12, true);
      r.clientEvents().post(jump);
      check(
          strafe.yaw() == first.yaw() && jump.yaw() == first.yaw(),
          "Auto Pot corrects strafe and jump to the smooth aim");
      check(
          strafe.forward() == .8f && strafe.strafe() == .4f,
          "Traditional movement correction retains the original analog input");
      walk(p, peer);
      input(r, p, peer);
      check(
          r.controls().rotation().get().pitch() > 85
              && count(peer, "C08PacketPlayerBlockPlacement") == 0,
          "new down aim cannot authorize a throw before its walking packet");
      walk(p, peer);
      input(r, p, peer);
      check(
          count(peer, "C08PacketPlayerBlockPlacement") == 1 && !busy(pot),
          "one potion is thrown after the down-facing walking boundary");
      int use = index(peer, "C08PacketPlayerBlockPlacement");
      check(
          use > 1
              && simple(peer.received.get(use - 1)).equals("C09PacketHeldItemChange")
              && slot(peer.received.get(use - 1)) == 3,
          "serialized slot selection immediately precedes potion use");
      Object look =
          peer.received.subList(0, use).stream()
              .filter(
                  q ->
                      simple(q).equals("C05PacketPlayerLook")
                          || simple(q).equals("C06PacketPlayerPosLook"))
              .reduce((a, b) -> b)
              .get();
      check(
          ((Number) method(look.getClass(), "getPitch", "func_149470_h").invoke(look)).floatValue()
              > 85,
          "serialized potion use follows a submitted down-facing aim");
      check(
          selected.getInt(inv) == 3 && use + 1 == peer.received.size(),
          "potion completion retains the source selected slot without an extra restore packet");
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "throw completion releases potion claims");
      r.modules().setEnabled("aura", false);
      r.modules().setEnabled("autopot", false);
      r.modules().setEnabled("autopot", true);
      input(r, p, peer);
      check(!busy(pot), "toggle cannot bypass the potion throw deadline");
      r.modules().setEnabled("autopot", false);
      deadline.setLong(pot, 0);
      attacks.setInt(pot, 10);
      r.modules().setEnabled("autopot", true);
      input(r, p, peer);
      screen.set(
          game, Class.forName("net.minecraft.client.gui.GuiChat").getConstructor().newInstance());
      input(r, p, peer);
      check(
          !busy(pot) && !r.controls().rotation().isPresent(),
          "screen interruption retires the Aura handoff and potion aim");
      screen.set(game, null);
      r.modules().setEnabled("autopot", false);
      attacks.setInt(pot, 10);
      r.modules().setEnabled("autopot", true);
      input(r, p, peer);
      items[3] = null;
      input(r, p, peer);
      check(!busy(pot), "removing the selected potion retires the pending routine");
      r.modules().setEnabled("autopot", false);
      items[3] = potion();
      attacks.setInt(pot, 10);
      set(r, "autopot", "max-aim-ticks", "2");
      r.modules().setEnabled("autopot", true);
      input(r, p, peer);
      blocker.set(Rotation.server(20, 0));
      for (int i = 0; i < 2; i++) {
        input(r, p, peer);
        walk(p, peer);
      }
      check(
          !busy(pot) && count(peer, "C08PacketPlayerBlockPlacement") == 1,
          "competing rotation ownership times out without an unauthorized throw");
      blocker.clear();
      r.modules().setEnabled("autopot", false);

      selected.setInt(inv, 0);
      items[3] = stack("water_bucket");
      flag.invoke(p, 0, true);
      r.modules().setEnabled("autoextinguish", true);
      for (String liquid : new String[] {"water", "lava"}) {
        put.invoke(world, feet, state(liquid), 2);
        input(r, p, peer);
        check(
            !r.controls().hotbar().isPresent(),
            "Auto Extinguish excludes liquid at the feet: " + liquid);
      }
      put.invoke(world, feet, state("air"), 2);
      r.modules().setEnabled("scaffold", true);
      input(r, p, peer);
      check(!r.controls().hotbar().isPresent(), "Scaffold suppresses the extinguish routine");
      r.modules().setEnabled("scaffold", false);
      long initialUses = count(peer, "C08PacketPlayerBlockPlacement");
      for (int tick = 1; tick <= 6; tick++) {
        input(r, p, peer);
        walk(p, peer);
        check(
            count(peer, "C08PacketPlayerBlockPlacement") == initialUses,
            "extinguish warmup sends no use packet at tick " + tick);
        check(selected.getInt(inv) == 3, "water remains selected during warmup tick " + tick);
      }
      input(r, p, peer);
      walk(p, peer);
      check(
          count(peer, "C08PacketPlayerBlockPlacement") == initialUses + 2,
          "extinguish acts on tick seven after five complete wait ticks; uses="
              + count(peer, "C08PacketPlayerBlockPlacement")
              + " initial="
              + initialUses
              + " packets="
              + peer.received.stream()
                  .map(RecoveryParityProbe::simple)
                  .collect(java.util.stream.Collectors.toList()));
      List<Object> bucketPackets = new ArrayList<>();
      for (Object q : peer.received)
        if (simple(q).equals("C08PacketPlayerBlockPlacement")) bucketPackets.add(q);
      Method face =
          method(bucketPackets.get(0).getClass(), "getPlacedBlockDirection", "func_149568_f");
      check(
          ((Number) face.invoke(bucketPackets.get(bucketPackets.size() - 2))).intValue() == 1
              && ((Number) face.invoke(bucketPackets.get(bucketPackets.size() - 1))).intValue()
                  == 255,
          "bucket right-click serializes block-top interaction followed by item use");
      check(
          get.invoke(world, feet).equals(state("flowing_water")),
          "extinguish ray uses the submitted aim to place real water");
      for (int tick = 8; tick <= 9; tick++) {
        input(r, p, peer);
        walk(p, peer);
        check(
            selected.getInt(inv) == 3,
            "extinguish retains its slot through post-use delay tick " + tick);
      }
      input(r, p, peer);
      walk(p, peer);
      check(
          selected.getInt(inv) == 0
              && !r.controls().hotbar().isPresent()
              && !r.controls().rotation().isPresent(),
          "extinguish restores the original slot at tick ten");
      check(
          count(peer, "C08PacketPlayerBlockPlacement") == initialUses + 2,
          "one stage cycle emits exactly the block/item bucket-use pair");
      items[3] = stack("water_bucket");
      put.invoke(world, feet, state("air"), 2);
      input(r, p, peer);
      r.modules().setEnabled("scaffold", true);
      input(r, p, peer);
      check(
          selected.getInt(inv) == 0 && !r.controls().hotbar().isPresent(),
          "mid-sequence Scaffold activation restores the original slot");
      r.modules().setEnabled("autoextinguish", false);
      r.modules().setEnabled("scaffold", false);
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "recovery activation cleanup leaves no claims");
      flag.invoke(p, 0, false);
      position(p, x + .5, y + 4, z + .5);
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 0);
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, -90);
      field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 4);
      items[3] = stack("water_bucket");
      blocker.set(Rotation.server(-150, -80));
      set(r, "nofall", "mode", "MLG");
      r.modules().setEnabled("nofall", true);
      input(r, p, peer);
      blocker.clear();
      Rotation catchAim = r.controls().rotation().get();
      check(
          catchAim.pitch() > 0 && catchAim.pitch() < 85,
          "MLG limits the combined inherited yaw/pitch turn instead of snapping down");
      MovementYawEvent catchJump = new MovementYawEvent(0, true);
      r.clientEvents().post(catchJump);
      check(catchJump.yaw() == catchAim.yaw(), "MLG jump correction follows its smooth catch aim");
      walk(p, peer);
      input(r, p, peer);
      walk(p, peer);
      field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 0);
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 170);
      input(r, p, peer);
      Rotation returning = r.controls().rotation().get();
      check(
          returning.pitch() > -80
              && !r.controls().hotbar().isPresent()
              && selected.getInt(inv) == 3,
          "MLG releases its claim, retains the source item slot and returns toward the camera");
      walk(p, peer);
      input(r, p, peer);
      Rotation returned = r.controls().rotation().get();
      check(
          Math.abs(returned.pitch() + 90) < 1 && Math.abs(returned.yaw() - 170) < 1,
          "MLG continues smoothing until it reaches the camera");
      walk(p, peer);
      input(r, p, peer);
      check(
          !r.controls().rotation().isPresent(),
          "MLG retires the return-to-view request after reaching the camera");
      field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 4);
      input(r, p, peer);
      set(r, "nofall", "mode", "VANILLA");
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "live No Fall mode changes retire the MLG sequence immediately");
      r.modules().setEnabled("nofall", false);
      // Regression: Aura's broader item-routine pause must not suppress the donor's
      // independent mining Auto Swap path while Auto Pot is only aiming.
      Object gameSettings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
      Object attackKey =
          field(gameSettings.getClass(), "keyBindAttack", "field_74312_F").get(gameSettings);
      Field pressed = field(attackKey.getClass(), "pressed", "field_74513_e"),
          mouse = field(game.getClass(), "objectMouseOver", "field_71476_x");
      boolean oldPressed = pressed.getBoolean(attackKey);
      Object oldMouse = mouse.get(game);
      ControlRequest<Boolean> mining =
          r.controls().scope(owner, resources::add).key(InputKey.ATTACK, 10000);
      try {
        position(p, x + .5, y, z + .5);
        field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 0);
        Class<?> vec = Class.forName("net.minecraft.util.Vec3"),
            miningFace = Class.forName("net.minecraft.util.EnumFacing");
        mouse.set(
            game,
            Class.forName("net.minecraft.util.MovingObjectPosition")
                .getConstructor(vec, miningFace, posType)
                .newInstance(
                    vec.getConstructor(double.class, double.class, double.class)
                        .newInstance(x + .5, (double) y, z + .5),
                    miningFace.getField("UP").get(null),
                    floor));
        items[0] = stack("diamond_sword");
        items[2] = stack("diamond_pickaxe");
        items[3] = potion();
        selected.setInt(inv, 0);
        attacks.setInt(pot, 10);
        deadline.setLong(pot, 0);
        pressed.setBoolean(attackKey, true);
        mining.set(true);
        set(r, "autoswap", "swap-mode", "CLIENT");
        set(r, "autoswap", "swap-back", "true");
        set(r, "autoswap", "sneak-only", "false");
        set(r, "autopot", "max-aim-ticks", "10");
        r.modules().setEnabled("autopot", true);
        r.modules().setEnabled("autoswap", true);
        input(r, p, peer);
        check(selected.getInt(inv) == 0, "mining selection waits until Motion PRE");
        walk(p, peer);
        check(
            busy(pot) && r.controls().hotbar().get().slot() == 2 && selected.getInt(inv) == 2,
            "Auto Pot start pauses Aura without suppressing mining Auto Swap");
        input(r, p, peer);
        walk(p, peer);
        check(
            busy(pot) && r.controls().rotation().isPresent() && selected.getInt(inv) == 2,
            "Auto Swap keeps the source mining path during actual potion aiming");
        r.modules().setEnabled("autopot", false);
        r.modules().setEnabled("autoswap", false);
        HookBridge.gameControls().apply();
        check(
            !r.controls().hotbar().isPresent() && selected.getInt(inv) == 0,
            "potion and mining cleanup restore the owned original slot");
        // Persistent completion must preserve the lower request's restoration and manual input.
        for (int variant = 0; variant < 3; variant++) {
          boolean server = variant == 1, manual = variant == 2;
          selected.setInt(inv, 0);
          set(r, "autoswap", "swap-mode", server ? "SERVER" : "CLIENT");
          attacks.setInt(pot, 10);
          deadline.setLong(pot, 0);
          field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 0);
          field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 0);
          r.modules().setEnabled("autopot", true);
          r.modules().setEnabled("autoswap", true);
          peer.received.clear();
          input(r, p, peer);
          walk(p, peer);
          check(
              selected.getInt(inv) == (server ? 0 : 2) && busy(pot),
              "competing completion begins with the actual restoring mining selection");
          for (int i = 0; i < 8 && busy(pot); i++) {
            input(r, p, peer);
            walk(p, peer);
          }
          check(
              !busy(pot) && count(peer, "C08PacketPlayerBlockPlacement") == 1,
              "actual potion completes once while mining Auto Swap remains active");
          check(
              selected.getInt(inv) == (server ? 0 : 2) && r.controls().hotbar().get().slot() == 2,
              "mining resumes after the higher-priority persistent potion throw");
          int throwIndex = index(peer, "C08PacketPlayerBlockPlacement");
          check(
              throwIndex > 0
                  && simple(peer.received.get(throwIndex - 1)).equals("C09PacketHeldItemChange")
                  && slot(peer.received.get(throwIndex - 1)) == 3,
              "competing potion still selects its own item immediately before C08");
          if (manual) {
            selected.setInt(inv, 5);
            HookBridge.gameControls().apply();
            check(selected.getInt(inv) == 2, "active mining ownership resumes after manual input");
          }
          r.modules().setEnabled("autopot", false);
          r.modules().setEnabled("autoswap", false);
          HookBridge.gameControls().apply();
          check(
              selected.getInt(inv) == (manual ? 5 : 0) && !r.controls().hotbar().isPresent(),
              "completed potion preserves Auto Swap's original slot for eventual restoration");
        }
        mining.clear();
        pressed.setBoolean(attackKey, false);
        autoSwapPhases(r, p, pig, inv, items, selected, peer);
      } finally {
        r.modules().setEnabled("autopot", false);
        r.modules().setEnabled("autoswap", false);
        mining.clear();
        mouse.set(game, oldMouse);
        pressed.setBoolean(attackKey, oldPressed);
      }
      for (String id : enabled.keySet())
        check(
            r.modules().require(id).state() != perihelion.core.module.ModuleState.FAULTED,
            id + " remains unfaulted");
      System.out.println(
          "PERIHELION_SMOKE RECOVERY_PARITY checks="
              + checks
              + " networkEvents="
              + network.get()
              + " PASS");
    } finally {
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      blocker.clear();
      for (AutoCloseable c : resources) c.close();
      owner.close();
      for (String id : enabled.keySet())
        for (Setting<?> s : r.modules().require(id).module().settings().values())
          restore(r, id, s, settings.get(s));
      put.invoke(world, floor, oldFloor, 2);
      put.invoke(world, feet, oldFeet, 2);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -976632);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      selected.setInt(inv, oldSlot);
      for (Map.Entry<Field, Object> e : saved.entrySet()) e.getKey().set(p, e.getValue());
      for (Map.Entry<Field, Object> e : oldHistory.entrySet())
        e.getKey().set(history, e.getValue());
      attacks.setInt(pot, oldAttacks);
      deadline.setLong(pot, oldDeadline);
      flag.invoke(p, 0, oldBurning);
      position(p, px, py, pz);
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      HookBridge.gameControls().apply();
      for (String id : enabled.keySet()) if (enabled.get(id)) r.modules().setEnabled(id, true);
    }
  }

  private static Object potion() throws Exception {
    return method(QualityGameplayProbe.class, new Class<?>[] {int[].class}, "potion")
        .invoke(null, (Object) new int[] {1});
  }

  private static void potionSelection(Object pot, Object player) throws Exception {
    Method useful =
        method(
            pot.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.item.ItemStack")},
            "useful");
    Method make = method(QualityGameplayProbe.class, new Class<?>[] {int[].class}, "potion");
    Map active = (Map) field(player.getClass(), "activePotionsMap", "field_70713_bf").get(player);
    Map saved = new LinkedHashMap(active);
    Method health =
        method(player.getClass(), new Class<?>[] {float.class}, "setHealth", "func_70606_j");
    float oldHealth =
        ((Number) method(player.getClass(), "getHealth", "func_110143_aJ").invoke(player))
            .floatValue();
    try {
      active.clear();
      check(
          !(Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {16})),
          "Auto Pot excludes night vision outside the source good-effect whitelist");
      Class<?> effect = Class.forName("net.minecraft.potion.PotionEffect");
      active.put(1, effect.getConstructor(int.class, int.class).newInstance(1, 100));
      check(
          !(Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {1, 8})),
          "an active first effect masks a later missing useful effect");
      check(
          (Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {8, 1})),
          "reversing the first effect follows source potion ordering");
      health.invoke(player, 20f);
      check(
          !(Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {6, 8})),
          "healthy first healing effect cannot select a later buff");
      health.invoke(player, 14f);
      check(
          (Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {6, 8})),
          "first healing effect is eligible below the configured health threshold");
      check(
          !(Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {8, 19})),
          "native mixed-harmful-effect guard remains after first-effect matching");
      active.put(8, effect.getConstructor(int.class, int.class).newInstance(8, -1));
      check(
          !(Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {8})),
          "negative active duration is nonzero and blocks the source first effect");
      active.put(8, effect.getConstructor(int.class, int.class).newInstance(8, 0));
      check(
          (Boolean) useful.invoke(pot, make.invoke(null, (Object) new int[] {8})),
          "exactly zero active duration allows the source first effect");
    } finally {
      active.clear();
      active.putAll(saved);
      health.invoke(player, oldHealth);
    }
  }

  private static void autoSwapPhases(
      ClientRuntime r,
      Object p,
      Object pig,
      Object inventory,
      Object[] items,
      Field selected,
      GameplayLoopback peer)
      throws Exception {
    Object aura = r.modules().require("aura").module();
    set(r, "aura", "rotations", "NONE");
    set(r, "aura", "auto-block", "NONE");
    set(r, "aura", "simulate-mouse-clicks", "false");
    set(r, "aura", "ray-cast", "false");
    set(r, "aura", "weapons-only", "false");
    set(r, "aura", "require-press", "false");
    for (int variant = 0; variant < 3; variant++) {
      boolean server = variant == 2, restore = variant != 0;
      String label = server ? "server swap" : restore ? "restoring client swap" : "persistent swap";
      r.modules().setEnabled("aura", false);
      r.modules().setEnabled("autoswap", false);
      HookBridge.gameControls().apply();
      items[0] = stack("wooden_sword");
      items[2] = stack("diamond_sword");
      selected.setInt(inventory, 0);
      method(pig.getClass(), new Class<?>[] {float.class}, "setHealth", "func_70606_j")
          .invoke(pig, 20f);
      position(
          pig,
          number(p, "posX", "field_70165_t"),
          number(p, "posY", "field_70163_u"),
          number(p, "posZ", "field_70161_v") + 2.5);
      set(r, "autoswap", "swap-mode", server ? "SERVER" : "CLIENT");
      set(r, "autoswap", "swap-back", Boolean.toString(restore));
      r.modules().setEnabled("aura", true);
      r.modules().setEnabled("autoswap", true);
      field(aura.getClass(), "nextAttack").setDouble(aura, 0);
      peer.received.clear();
      input(r, p, peer);
      check(count(peer, "C02PacketUseEntity") == 1, label + " starts with a real PRE attack");
      check(
          selected.getInt(inventory) == 0 && !r.controls().hotbar().isPresent(),
          label + " does not select the stronger weapon before the PRE attack");
      boolean earlySwap = false;
      for (Object packet : peer.received)
        if (simple(packet).equals("C09PacketHeldItemChange") && slot(packet) == 2) earlySwap = true;
      check(!earlySwap, label + " has no early C09 for the stronger weapon");
      walk(p, peer);
      int select = -1;
      for (int i = 0; i < peer.received.size(); i++)
        if (simple(peer.received.get(i)).equals("C09PacketHeldItemChange")
            && slot(peer.received.get(i)) == 2) {
          select = i;
          break;
        }
      check(select > index(peer, "C02PacketUseEntity"), label + " orders C09 after the PRE attack");
      check(
          r.controls().hotbar().get().slot() == 2 && selected.getInt(inventory) == (server ? 0 : 2),
          label + " selects at Motion PRE with matching visible/spoof behavior");
      check(
          peer.received.subList(select + 1, peer.received.size()).stream()
              .anyMatch(
                  q ->
                      q.getClass().getSimpleName().startsWith("C03")
                          || q.getClass().getSimpleName().startsWith("C04")
                          || q.getClass().getSimpleName().startsWith("C05")
                          || q.getClass().getSimpleName().startsWith("C06")),
          label + " selects before the walking packet");
      r.controls().beginTick();
      HookBridge.gameControls().apply();
      check(
          r.controls().hotbar().isPresent()
              && r.controls().hotbar().get().slot() == 2
              && selected.getInt(inventory) == (server ? 0 : 2),
          label + " survives the real control-tick boundary before the next PRE attack");
      long attacks = count(peer, "C02PacketUseEntity");
      field(aura.getClass(), "nextAttack").setDouble(aura, 0);
      input(r, p, peer);
      check(
          count(peer, "C02PacketUseEntity") == attacks + 1,
          label + " allows the next PRE attack using the selected weapon");
      r.modules().setEnabled("aura", false);
      input(r, p, peer);
      check(r.controls().hotbar().isPresent(), label + " retains selection until Motion PRE");
      walk(p, peer);
      check(
          !r.controls().hotbar().isPresent() && selected.getInt(inventory) == (restore ? 0 : 2),
          label + " clears the inactive claim with source restoration behavior");
      r.modules().setEnabled("autoswap", false);
      HookBridge.gameControls().apply();
    }
  }

  private static void input(ClientRuntime r, Object p, GameplayLoopback peer) throws Exception {
    HookBridge.gameControls().beforeInput();
    drain(peer);
  }

  private static void walk(Object p, GameplayLoopback peer) throws Exception {
    forceLook(p);
    motion(p);
    drain(peer);
  }

  private static void drain(GameplayLoopback peer) throws Exception {
    peer.client.eventLoop().submit(() -> {}).sync();
    // A keepalive sentinel confirms TCP reception without changing walking aim or slot state.
    Object marker =
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(0x5a138);
    method(
            peer.manager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(peer.manager.get(), marker);
    long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while (System.nanoTime() < end) {
      if (!peer.received.isEmpty()) {
        Object last = peer.received.get(peer.received.size() - 1);
        if (simple(last).equals("C00PacketKeepAlive")
            && ((Number) method(last.getClass(), "getKey", "func_149460_c").invoke(last)).intValue()
                == 0x5a138) {
          peer.received.remove(peer.received.size() - 1);
          return;
        }
      }
      Thread.sleep(2);
    }
    throw new AssertionError("recovery TCP drain timeout");
  }

  private static boolean busy(Object pot) throws Exception {
    return (Boolean) method(pot.getClass(), "busy").invoke(pot);
  }

  private static String simple(Object q) {
    return q.getClass().getSimpleName();
  }

  private static long count(GameplayLoopback peer, String name) {
    return peer.received.stream().filter(q -> simple(q).equals(name)).count();
  }

  private static int index(GameplayLoopback peer, String name) {
    for (int i = 0; i < peer.received.size(); i++)
      if (simple(peer.received.get(i)).equals(name)) return i;
    throw new AssertionError("missing " + name);
  }

  private static int slot(Object packet) throws Exception {
    return ((Number) method(packet.getClass(), "getSlotId", "func_149614_c").invoke(packet))
        .intValue();
  }

  private static void restore(ClientRuntime r, String id, Setting s, Object value) {
    r.modules().setSetting(id, s, value);
  }

  private static void check(boolean b, String message) {
    checks++;
    if (!b) throw new AssertionError(message);
  }
}
