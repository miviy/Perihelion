package perihelion.smoke;

import static perihelion.smoke.B4ManualCombatProbe.*;
import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.PacketSendEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Survival mining: real tool strengths, layered covers and serialized PLAY digging packets. */
public final class BreakerLayerProbe {
  private static int checks, cases;

  public static void run(ClientRuntime r) throws Exception {
    checks = cases = 0;
    try (Fixture f = new Fixture(r)) {
      for (String mode : new String[] {"VANILLA", "LEGIT", "HYPIXEL"}) {
        f.layout(0, new String[] {"wool"});
        set(r, "breaker", "mode", mode);
        f.finish(100, mode + " exposed");
      }
      for (String mode : new String[] {"LEGIT", "HYPIXEL"})
        for (int layers = 1; layers <= 3; layers++)
          for (String[] materials :
              new String[][] {
                {"wool"},
                {"planks"},
                {"end_stone"},
                {"stained_hardened_clay"},
                {"glass"},
                {"obsidian"},
                {"end_stone", "planks", "wool"}
              }) {
            f.layout(layers, materials);
            set(r, "breaker", "mode", mode);
            f.finish(1800, mode + " layers=" + layers + " " + Arrays.toString(materials));
          }
      f.layout(1, new String[] {"tnt"});
      f.finish(100, "zero-hardness TNT cover");
      f.layout(2, new String[] {"wool"});
      f.put.invoke(f.world, pos(f.x, f.y + 1, f.z), state("air"), 2);
      f.finish(500, "hidden inner air pocket behind outer cover");
      f.layout(1, new String[] {"end_stone"});
      Class<?> enchant = Class.forName("net.minecraft.enchantment.Enchantment");
      Object efficiency =
          method(enchant, new Class<?>[] {int.class}, "getEnchantmentById", "func_180306_c")
              .invoke(null, 32);
      method(
              f.items[4].getClass(),
              new Class<?>[] {enchant, int.class},
              "addEnchantment",
              "func_77966_a")
          .invoke(f.items[4], efficiency, 5);
      f.finish(150, "enchanted wooden pick versus unenchanted diamond pick");
      f.layout(1, new String[] {"end_stone"});
      set(r, "breaker", "auto-tool", "false");
      f.finish(1300, "manual held-tool mining");
      f.layout(1, new String[] {"end_stone"});
      Object fatigue =
          Class.forName("net.minecraft.potion.PotionEffect")
              .getConstructor(int.class, int.class, int.class)
              .newInstance(4, 1200, 0);
      method(
              f.player.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.potion.PotionEffect")},
              "addPotionEffect",
              "func_70690_d")
          .invoke(f.player, fatigue);
      try {
        f.finish(500, "mining fatigue");
      } finally {
        method(
                f.player.getClass(),
                new Class<?>[] {int.class},
                "removePotionEffect",
                "func_82170_o")
            .invoke(f.player, 4);
      }
      f.layout(1, new String[] {"planks"});
      field(f.player.getClass(), "onGround", "field_70122_E").setBoolean(f.player, false);
      f.finish(500, "airborne mining slowdown");
      f.layout(1, new String[] {"obsidian"});
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 2; dz++)
          f.put.invoke(f.world, pos(f.x + dx, f.y + 1, f.z + dz), state("wool"), 2);
      f.tick();
      wireBoundary(f.peer);
      check(
          !f.starts.isEmpty() && f.starts.get(0).block.equals(f.block("wool")),
          "fast wool route wins over obsidian");
      f.stop();

      f.layout(1, new String[] {"obsidian"});
      f.tick();
      Object unnecessary = f.starts.get(0).pos;
      for (int dx = -1; dx <= 1; dx++)
        for (int dy = 0; dy <= 1; dy++)
          for (int dz = -1; dz <= 2; dz++) {
            if (dx == 0 && dy == 0 && (dz == 0 || dz == 1)) continue;
            Object p = pos(f.x + dx, f.y + dy, f.z + dz);
            if (!p.equals(unnecessary)) f.put.invoke(f.world, p, state("air"), 2);
          }
      // Approach the opened side so the remaining front block no longer occludes both halves.
      position(f.player, f.x + 2.35, f.y, f.z + 2.5);
      f.finish(100, "switches to newly exposed bed instead of finishing redundant obsidian");
      check(
          f.get.invoke(f.world, unnecessary).equals(state("obsidian")),
          "B4-style exposure handoff leaves unnecessary cover intact");

      f.layout(1, new String[] {"end_stone"});
      f.tick();
      check(!f.starts.isEmpty(), "replacement test establishes mining");
      Object replaced = f.starts.get(0).pos;
      f.put.invoke(f.world, replaced, state("obsidian"), 2);
      f.tick();
      wireBoundary(f.peer);
      check(
          countAction(f.peer.received, "ABORT_DESTROY_BLOCK") >= 1,
          "same-position material change aborts old progress");
      for (int i = 0; i < 10; i++) f.tick();
      check(
          f.get.invoke(f.world, replaced).equals(state("obsidian")),
          "obsidian cannot inherit end-stone progress");
      f.stop();

      f.layout(1, new String[] {"end_stone"});
      f.tick();
      Object target = f.starts.get(0).pos;
      Object update =
          Class.forName("net.minecraft.network.play.server.S23PacketBlockChange")
              .getConstructor(
                  Class.forName("net.minecraft.world.World"),
                  Class.forName("net.minecraft.util.BlockPos"))
              .newInstance(f.world, target);
      r.clientEvents()
          .post(
              Class.forName("perihelion.integration.minecraft189.event.ClientPacketEvent")
                  .getConstructor(Class.forName("net.minecraft.network.Packet"))
                  .newInstance(update));
      wireBoundary(f.peer);
      check(
          countAction(f.peer.received, "ABORT_DESTROY_BLOCK") == 1,
          "server correction retires mining even for identical block state");
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "correction releases aim/tool");
      f.stop();

      f.layout(1, new String[] {"end_stone"});
      f.tick();
      f.items[3] = stack("wooden_pickaxe");
      f.tick();
      wireBoundary(f.peer);
      check(
          countAction(f.peer.received, "ABORT_DESTROY_BLOCK") >= 1,
          "tool replacement resets mining");
      f.stop();
      f.layout(1, new String[] {"end_stone"});
      f.tick();
      wireBoundary(f.peer);
      int aborts = countAction(f.peer.received, "ABORT_DESTROY_BLOCK");
      sendDig(f, "ABORT_DESTROY_BLOCK", f.starts.get(0).pos);
      sendDig(f, "RELEASE_USE_ITEM", pos(f.x, f.y, f.z));
      wireBoundary(f.peer);
      check(
          countAction(f.peer.received, "ABORT_DESTROY_BLOCK") == aborts,
          "foreign manual abort cannot cancel owned mining");
      check(
          countAction(f.peer.received, "RELEASE_USE_ITEM") == 1,
          "mining guard preserves item release");
      f.stop();
      f.layout(1, new String[] {"bedrock"});
      for (int i = 0; i < 8; i++) f.tick();
      wireBoundary(f.peer);
      check(f.starts.isEmpty(), "unbreakable cover is not mined");
      f.stop();
      f.layout(1, new String[] {"wool"});
      set(r, "breaker", "mode", "VANILLA");
      for (int i = 0; i < 4; i++) f.tick();
      check(f.starts.isEmpty(), "Vanilla retains exposed-bed-only behavior");
      f.stop();
      f.layout(1, new String[] {"end_stone"});
      f.tick();
      f.screen.set(f.game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
      f.tick();
      wireBoundary(f.peer);
      check(countAction(f.peer.received, "ABORT_DESTROY_BLOCK") == 1, "opening GUI aborts digging");
      check(
          !r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
          "GUI releases mining claims");
      f.screen.set(f.game, null);
      f.stop();
      f.layout(1, new String[] {"wool"});
      set(r, "breaker", "break-near-block", "false");
      AutoCloseable cancel =
          r.networkEvents()
              .subscribe(
                  f.owner,
                  PacketSendEvent.class,
                  Integer.MAX_VALUE,
                  true,
                  e -> {
                    Object p = e.getClass().getMethod("packet").invoke(e);
                    if (Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
                        .isInstance(p)) e.cancel();
                  });
      try {
        f.tick();
        wireBoundary(f.peer);
        check(f.starts.isEmpty(), "cancelled walking cannot authorize mining");
      } finally {
        cancel.close();
        f.stop();
      }
      f.layout(0, new String[] {"wool"});
      AutoCloseable retire =
          r.networkEvents()
              .subscribe(
                  f.owner,
                  PacketSendEvent.class,
                  2000,
                  false,
                  e -> {
                    Object packet = e.getClass().getMethod("packet").invoke(e);
                    if (packet.getClass().getSimpleName().equals("C07PacketPlayerDigging")
                        && method(packet.getClass(), "getStatus", "func_180762_c")
                            .invoke(packet)
                            .toString()
                            .equals("START_DESTROY_BLOCK"))
                      r.modules().setEnabled("breaker", false);
                  });
      try {
        f.tick();
        wireBoundary(f.peer);
        check(
            countAction(f.peer.received, "ABORT_DESTROY_BLOCK") >= 1,
            "disable during START leaves no orphan mining session");
      } finally {
        retire.close();
        f.stop();
      }
      f.layout(0, new String[] {"wool"});
      f.mode("ADVENTURE");
      f.tick();
      wireBoundary(f.peer);
      check(f.starts.isEmpty(), "B4-style Adventure restriction prevents unauthorized digging");
      f.mode("SURVIVAL");
      f.stop();
      // Visual is guidance only, including while tools and layered cover are present.
      f.layout(3, new String[] {"end_stone", "planks", "wool"});
      set(r, "breaker", "mode", "VISUAL");
      float yaw = (float) number(f.player, "rotationYaw", "field_70177_z");
      for (int i = 0; i < 5; i++) f.tick();
      Object preview = f.preview();
      check(preview != null, "Visual finds a layered route");
      List<?> route = (List<?>) field(preview.getClass(), "route").get(preview);
      check(route.size() >= 2, "Visual retains ordered cover and bed steps");
      check(f.starts.isEmpty(), "Visual sends no automatic dig packets");
      check(
          !r.controls().rotation().isPresent()
              && !r.controls().hotbar().isPresent()
              && !r.controls().key(InputKey.ATTACK).isPresent()
              && !r.controls().key(InputKey.USE_ITEM).isPresent(),
          "Visual owns no gameplay controls");
      check(
          f.selected.getInt(f.inventory) == 0
              && (float) number(f.player, "rotationYaw", "field_70177_z") == yaw,
          "Visual preserves view and selected item");
      Object firstPos = field(route.get(0).getClass(), "pos").get(route.get(0));
      check(
          firstPos.equals(field(preview.getClass(), "target").get(preview)),
          "route starts at first visible obstruction");
      f.visualFrame(preview);
      f.put.invoke(f.world, firstPos, state("air"), 2);
      f.tick();
      check(f.preview() != preview, "Visual updates after manual removal");
      f.stop();
      check(f.preview() == null, "Visual clears on disable");
      cases++;

      f.layout(0, new String[] {"wool"});
      Object foot = pos(f.x, f.y, f.z), head = pos(f.x, f.y, f.z + 1);
      Object footState = f.get.invoke(f.world, foot), headState = f.get.invoke(f.world, head);
      f.put.invoke(f.world, foot, state("air"), 2);
      f.put.invoke(f.world, head, state("air"), 2);
      set(r, "breaker", "mode", "VISUAL");
      set(r, "breaker", "rate", "0.2");
      f.tick();
      check(f.preview() == null, "empty Visual scan has no route");
      f.put.invoke(f.world, foot, footState, 2);
      f.put.invoke(f.world, head, headState, 2);
      for (int i = 0; i < 3; i++) {
        f.tick();
        check(f.preview() == null, "empty Visual scans respect the configured rate");
      }
      f.tick();
      check(f.preview() != null, "Visual refreshes after the rate interval");
      f.stop();
      cases++;

      for (String mode : new String[] {"INSTANT", "SWAP"}) {
        f.layout(2, new String[] {"end_stone", "planks"});
        set(r, "breaker", "mode", mode);
        f.finish(500, mode + " layered cover");
      }
      f.layout(0, new String[] {"wool"});
      set(r, "breaker", "mode", "INSTANT");
      set(r, "breaker", "maximize-speed", "true");
      f.finish(50, "maximized two-tick quick break");
      f.layout(1, new String[] {"wool"});
      set(r, "breaker", "break-speed", "1.8");
      f.finish(100, "B4 1.8 initial progress credit");
      f.layout(1, new String[] {"planks"});
      field(f.player.getClass(), "onGround", "field_70122_E").setBoolean(f.player, false);
      set(r, "breaker", "ignore-slow", "true");
      f.finish(100, "Ignore Slow airborne behavior");
      f.layout(1, new String[] {"wool"});
      set(r, "breaker", "skip-break-verification", "false");
      f.finish(200, "server-confirmed cover and bed handoff");
      f.layout(0, new String[] {"wool"});
      set(r, "breaker", "skip-break-verification", "false");
      f.acknowledge = false;
      for (int i = 0; i < 30 && f.lastStop < 0; i++) f.tick();
      check(
          f.lastStop >= 0 && f.bedPresent(pos(f.x, f.y, f.z)),
          "STOP does not erase an unconfirmed bed");
      int stoppedAt = f.lastStop;
      for (int i = 0; i < 12; i++) f.tick();
      check(
          f.bedPresent(pos(f.x, f.y, f.z)) && f.starts.size() >= 2,
          "unconfirmed break retries without ghost removal");
      check(f.starts.get(1).tick - stoppedAt >= 9, "verification bounds retries");
      f.stop();
      cases++;

      f.layout(1, new String[] {"end_stone"});
      set(r, "breaker", "only-while-visible", "true");
      for (int i = 0; i < 4; i++) f.tick();
      check(f.starts.isEmpty(), "Only While Visible refuses covered beds");
      set(r, "breaker", "only-while-visible", "false");
      set(r, "breaker", "break-near-block", "false");
      for (int i = 0; i < 4; i++) f.tick();
      check(
          !f.starts.isEmpty() && f.starts.get(0).block.equals(f.block("bed")),
          "Break Near Block off targets the bed directly as in B4");
      check(
          f.starts.stream().allMatch(d -> d.block.equals(f.starts.get(0).block)),
          "direct-bed option leaves cover untouched");
      f.stop();
      f.layout(0, new String[] {"wool"});
      set(r, "breaker", "fov", "20");
      f.tick();
      check(f.starts.isEmpty(), "narrow FOV excludes off-screen beds");
      camera(f.player, 90, 0);
      f.tick();
      check(!f.starts.isEmpty(), "FOV admits a bed after turning toward it");
      f.stop();
      f.layout(0, new String[] {"wool"});
      set(r, "breaker", "rate", "0.2");
      for (int i = 0; i < 3; i++) f.tick();
      check(f.starts.isEmpty(), "B4 rate delays initial search");
      f.tick();
      check(f.starts.size() == 1, "0.2-second rate scans on fourth update");
      f.stop();
      f.layout(1, new String[] {"obsidian"});
      f.tick();
      int beforeRetire = countAction(f.peer.received, "ABORT_DESTROY_BLOCK");
      set(r, "breaker", "mode", "VISUAL");
      wireBoundary(f.peer);
      int retired = countAction(f.peer.received, "ABORT_DESTROY_BLOCK");
      check(retired == beforeRetire + 1, "mode change aborts the active mining task once");
      f.tick();
      check(
          countAction(f.peer.received, "ABORT_DESTROY_BLOCK") == retired,
          "Visual transition retires old mining exactly once");
      check(
          !r.controls().rotation().isPresent() && f.preview() != null,
          "Visual transition releases aim and provides guidance");
      f.stop();

      f.layout(1, new String[] {"obsidian"});
      set(r, "breaker", "cancel-knockback", "true");
      set(r, "breaker", "disable-bhop", "true");
      set(r, "breaker", "silent-swing", "true");
      field(f.player.getClass(), "isSwingInProgress", "field_82175_bq").setBoolean(f.player, false);
      f.tick();
      check(
          !field(f.player.getClass(), "isSwingInProgress", "field_82175_bq").getBoolean(f.player),
          "Silent Swing preserves local arm state");
      check(
          r.controls().key(InputKey.USE_ITEM).isPresent()
              && !r.controls().key(InputKey.USE_ITEM).get(),
          "Disable Right Click owns a released use key");
      Class<?> velocity =
          Class.forName("net.minecraft.network.play.server.S12PacketEntityVelocity");
      int id =
          ((Number) method(f.player.getClass(), "getEntityId", "func_145782_y").invoke(f.player))
              .intValue();
      Object ownVelocity =
          velocity
              .getConstructor(int.class, double.class, double.class, double.class)
              .newInstance(id, 1., 1., 1.);
      Object otherVelocity =
          velocity
              .getConstructor(int.class, double.class, double.class, double.class)
              .newInstance(id + 1, 1., 1., 1.);
      check(f.network("PacketReceiveEvent", ownVelocity), "Cancel Knockback cancels own velocity");
      check(
          !f.network("PacketReceiveEvent", otherVelocity),
          "Cancel Knockback preserves another entity velocity");
      check(
          f.network(
              "PacketReceiveEvent",
              Class.forName("net.minecraft.network.play.server.S27PacketExplosion").newInstance()),
          "Cancel Knockback matches B4 explosion policy");
      check(
          f.network(
              "PacketSendEvent",
              Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
                  .newInstance()),
          "Disable Right Click cancels direct use/placement packets");
      Object module = r.modules().require("breaker").module();
      Method pause = module.getClass().getDeclaredMethod("pauseSpeed");
      pause.setAccessible(true);
      check((Boolean) pause.invoke(module), "Disable Bhop activates native Speed pause binding");
      set(r, "breaker", "mode", "VISUAL");
      f.tick();
      check(!f.network("PacketReceiveEvent", ownVelocity), "Visual never cancels knockback");
      check(
          !f.network(
              "PacketSendEvent",
              Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
                  .newInstance()),
          "Visual never blocks manual interaction");
      check(!(Boolean) pause.invoke(module), "Visual never pauses Speed");
      f.stop();
      cases++;

      // Home acquisition needs an isolated fixture; no earlier mining-test bed may remain.
      f.put.invoke(f.world, pos(f.x, f.y, f.z), state("air"), 2);
      f.put.invoke(f.world, pos(f.x, f.y, f.z + 1), state("air"), 2);
      HomeBedProbe.run(
          r,
          f.game,
          f.player,
          f.world,
          f.items,
          f.get,
          f.put,
          f.peer.client,
          f.outgoing,
          f.x,
          f.y,
          f.z);
      check(f.peer.failure.get() == null, "PLAY peer serializes mining without failures");
      check(
          f.observerFailure.get() == null,
          "all mining packet assertions passed: " + f.observerFailure.get());
      System.out.println(
          "[PERIHELION_SMOKE] BREAKER_LAYERS cases=" + cases + " checks=" + checks + " PASS");
    }
  }

  private static final class Dig {
    final Object pos, block;
    final int tick;
    final float strength;

    Dig(Object p, Object b, int t, float s) {
      pos = p;
      block = b;
      tick = t;
      strength = s;
    }
  }

  private static final class Fixture implements AutoCloseable {
    final ClientRuntime r;
    final Object game, player, world, inventory, controller;
    final Object[] items, oldItems;
    final Field selected, screen;
    final Object oldScreen, oldMode;
    final Method get, put, setMode;
    final int x, y = 200, z, oldSelected;
    final double px, py, pz;
    final Map<String, Boolean> enabled = new LinkedHashMap<>();
    final Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    final Map<Object, Object> terrain = new LinkedHashMap<>();
    final GameplayLoopback peer;
    final NativeKeys keys;
    final EventOwner owner;
    final List<Object> outgoing = new CopyOnWriteArrayList<>();
    final List<Dig> starts = new ArrayList<>();
    final Map<Object, Dig> active = new HashMap<>();
    final java.util.concurrent.atomic.AtomicReference<Throwable> observerFailure =
        new java.util.concurrent.atomic.AtomicReference<>();
    int tick, lastStop = -1;
    boolean finishing, acknowledge = true;
    final List<Object> pendingStops = new ArrayList<>();

    Fixture(ClientRuntime runtime) throws Exception {
      r = runtime;
      game =
          method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
              .invoke(null);
      player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
      world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
      inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
      items =
          (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
      oldItems = items.clone();
      selected = field(inventory.getClass(), "currentItem", "field_70461_c");
      oldSelected = selected.getInt(inventory);
      screen = field(game.getClass(), "currentScreen", "field_71462_r");
      oldScreen = screen.get(game);
      screen.set(game, null);
      px = number(player, "posX", "field_70165_t");
      py = number(player, "posY", "field_70163_u");
      pz = number(player, "posZ", "field_70161_v");
      x = (int) Math.floor(px);
      z = (int) Math.floor(pz);
      for (ModuleEntry entry : r.modules().entries())
        if (entry.info().requiresWorld()) {
          enabled.put(entry.info().id(), entry.requestedEnabled());
          r.modules().setEnabled(entry.info().id(), false);
        }
      for (Setting<?> s : r.modules().require("breaker").module().settings().values())
        settings.put(s, s.get());
      Class<?> bp = Class.forName("net.minecraft.util.BlockPos"),
          state = Class.forName("net.minecraft.block.state.IBlockState");
      get = method(world.getClass(), new Class<?>[] {bp}, "getBlockState", "func_180495_p");
      put =
          method(
              world.getClass(),
              new Class<?>[] {bp, state, int.class},
              "setBlockState",
              "func_180501_a");
      for (int dx = -5; dx <= 5; dx++)
        for (int dy = -1; dy <= 4; dy++)
          for (int dz = -5; dz <= 6; dz++) {
            Object p = pos(x + dx, y + dy, z + dz);
            terrain.put(p, get.invoke(world, p));
          }
      controller = field(game.getClass(), "playerController", "field_71442_b").get(game);
      oldMode =
          method(controller.getClass(), "getCurrentGameType", "func_178889_l").invoke(controller);
      Class<?> type = Class.forName("net.minecraft.world.WorldSettings$GameType");
      setMode = method(controller.getClass(), new Class<?>[] {type}, "setGameType", "func_78746_a");
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object survival = Enum.valueOf((Class) type, "SURVIVAL");
      setMode.invoke(controller, survival);
      peer = new GameplayLoopback(game);
      keys = new NativeKeys();
      owner =
          new EventOwner("breaker-layers", failure -> observerFailure.compareAndSet(null, failure));
      owner.activate();
      r.clientEvents()
          .subscribe(
              owner,
              PlayerControlEvent.class,
              2000,
              false,
              e -> {
                if (e == PlayerControlEvent.BEFORE_INPUT) tick++;
              });
      peer.client
          .pipeline()
          .addLast(
              "breaker-layer-probe",
              new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                    throws Exception {
                  outgoing.add(msg);
                  ctx.write(msg, promise);
                }
              });
      r.networkEvents()
          .subscribe(
              owner,
              PacketSendEvent.class,
              Integer.MIN_VALUE,
              true,
              e -> {
                Object packet = e.getClass().getMethod("packet").invoke(e);
                if (e.isCancelled()
                    || !packet.getClass().getSimpleName().equals("C07PacketPlayerDigging")) return;
                String action =
                    method(packet.getClass(), "getStatus", "func_180762_c")
                        .invoke(packet)
                        .toString();
                Object p = method(packet.getClass(), "getPosition", "func_179715_a").invoke(packet);
                if (action.equals("START_DESTROY_BLOCK")) {
                  if (finishing && lastStop >= 0 && (Boolean) setting("skip-break-verification"))
                    check(tick - lastStop == 2, "next target starts on B4 second-update handoff");
                  int slot = HookBridge.serverSlot(selected.getInt(inventory));
                  float value = nativeStrength(p, slot), best = 0;
                  for (int i = 0; i < 9; i++) best = Math.max(best, nativeStrength(p, i));
                  if ((Boolean) setting("auto-tool") && !setting("mode").toString().equals("SWAP"))
                    check(
                        value == best || Math.abs(value - best) < 1e-6,
                        "START uses maximal actual survival mining strength");
                  else if (!(Boolean) setting("auto-tool"))
                    check(
                        slot == selected.getInt(inventory),
                        "disabled Auto Tool respects the held slot");
                  Dig dig =
                      new Dig(
                          p,
                          method(get.invoke(world, p).getClass(), "getBlock", "func_177230_c")
                              .invoke(get.invoke(world, p)),
                          tick,
                          value);
                  starts.add(dig);
                  active.put(p, dig);
                } else if (action.equals("STOP_DESTROY_BLOCK")) {
                  lastStop = tick;
                  Dig dig = active.remove(p);
                  check(dig != null, "STOP has matching START");
                  String style = setting("mode").toString();
                  if (finishing
                      && !style.equals("INSTANT")
                      && !style.equals("SWAP")
                      && !(Boolean) setting("ignore-slow")) {
                    float progress = 0, credit = (float) ((Double) setting("break-speed") - 1);
                    int updates = 0;
                    while (progress < 1 && updates < 5000) {
                      progress = Math.max(progress + dig.strength, credit);
                      updates++;
                    }
                    check(
                        tick - dig.tick + 1 == updates,
                        "Legit matches B4 per-update progress floor exactly");
                  }
                  if (!style.equals("INSTANT")
                      && !style.equals("SWAP")
                      && !(Boolean) setting("ignore-slow"))
                    check(
                        (tick - dig.tick + 1) * dig.strength + ((Double) setting("break-speed") - 1)
                            >= 1 - 1e-5,
                        "STOP respects mining time and B4 initial progress credit");
                  boolean isBed = dig.block.equals(block("bed"));
                  if (style.equals("INSTANT") || style.equals("SWAP") && isBed) {
                    int quick = (Boolean) setting("maximize-speed") ? 2 : 4;
                    check(tick - dig.tick == quick + 1, "B4 quick-break countdown matches mode");
                  }
                  if (acknowledge && !(Boolean) setting("skip-break-verification"))
                    pendingStops.add(p);
                } else if (action.equals("ABORT_DESTROY_BLOCK")) active.remove(p);
              });
    }

    boolean network(String type, Object packet) throws Exception {
      Object event =
          Class.forName("perihelion.integration.minecraft189.event." + type)
              .getConstructor(
                  Class.forName("net.minecraft.network.NetworkManager"),
                  Class.forName("net.minecraft.network.Packet"))
              .newInstance(peer.manager.get(), packet);
      r.networkEvents().post(event);
      return ((perihelion.core.event.CancellableEvent) event).isCancelled();
    }

    Object preview() throws Exception {
      Object module = r.modules().require("breaker").module();
      return field(module.getClass(), "preview").get(module);
    }

    void visualFrame(Object preview) throws Exception {
      BreakerVisualProbe.render(r, game, player, preview);
    }

    Object setting(String id) {
      return r.modules().require("breaker").module().settings().get(id).get();
    }

    float nativeStrength(Object pos, int tool) throws Exception {
      int old = selected.getInt(inventory);
      selected.setInt(inventory, tool);
      try {
        Object state = get.invoke(world, pos),
            block = method(state.getClass(), "getBlock", "func_177230_c").invoke(state);
        return ((Number)
                method(
                        block.getClass(),
                        new Class<?>[] {
                          Class.forName("net.minecraft.entity.player.EntityPlayer"),
                          Class.forName("net.minecraft.world.World"),
                          Class.forName("net.minecraft.util.BlockPos")
                        },
                        "getPlayerRelativeBlockHardness",
                        "func_180647_a")
                    .invoke(block, player, world, pos))
            .floatValue();
      } finally {
        selected.setInt(inventory, old);
      }
    }

    Object block(String name) throws Exception {
      return method(state(name).getClass(), "getBlock", "func_177230_c").invoke(state(name));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void mode(String name) throws Exception {
      setMode.invoke(controller, Enum.valueOf((Class) oldMode.getClass(), name));
    }

    boolean bedPresent(Object pos) throws Exception {
      return method(get.invoke(world, pos).getClass(), "getBlock", "func_177230_c")
              .invoke(get.invoke(world, pos))
          == block("bed");
    }

    void layout(int layers, String[] materials) throws Exception {
      stop();
      wireBoundary(peer);
      tick = 0;
      lastStop = -1;
      pendingStops.clear();
      acknowledge = true;
      starts.clear();
      active.clear();
      outgoing.clear();
      peer.received.clear();
      for (Object p : terrain.keySet()) put.invoke(world, p, state("air"), 2);
      for (int dx = -5; dx <= 5; dx++)
        for (int dz = -5; dz <= 6; dz++)
          put.invoke(world, pos(x + dx, y - 1, z + dz), state("stone"), 2);
      for (int dx = -layers; dx <= layers; dx++)
        for (int dy = 0; dy <= layers; dy++)
          for (int dz = -layers; dz <= 1 + layers; dz++) {
            if (dx == 0 && dy == 0 && (dz == 0 || dz == 1)) continue;
            int shell = Math.max(Math.abs(dx), Math.max(dy, dz < 0 ? -dz : Math.max(0, dz - 1)));
            put.invoke(
                world,
                pos(x + dx, y + dy, z + dz),
                state(materials[(Math.max(1, shell) - 1) % materials.length]),
                2);
          }
      Object bed = block("bed");
      Method meta =
          method(bed.getClass(), new Class<?>[] {int.class}, "getStateFromMeta", "func_176203_a");
      put.invoke(world, pos(x, y, z), meta.invoke(bed, 0), 2);
      put.invoke(world, pos(x, y, z + 1), meta.invoke(bed, 8), 2);
      Arrays.fill(items, null);
      items[0] = stack("stick");
      items[1] = stack("shears");
      items[2] = stack("diamond_axe");
      items[3] = stack("diamond_pickaxe");
      items[4] = stack("wooden_pickaxe");
      items[5] = stack("diamond_shovel");
      selected.setInt(inventory, 0);
      position(player, x + layers + 1.35, y, z + .5);
      camera(player, 179, 0);
      field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
      set(r, "breaker", "whitelist", "false");
      set(r, "breaker", "mode", "HYPIXEL");
      set(r, "breaker", "range", "6");
      set(r, "breaker", "auto-tool", "true");
      set(r, "breaker", "rate", "0.05");
      set(r, "breaker", "break-speed", "1");
      set(r, "breaker", "fov", "360");
      set(r, "breaker", "break-near-block", "true");
      set(r, "breaker", "only-while-visible", "false");
      set(r, "breaker", "ignore-slow", "false");
      set(r, "breaker", "maximize-speed", "false");
      set(r, "breaker", "skip-break-verification", "true");
      set(r, "breaker", "prioritize-kill-aura", "false");
      set(r, "breaker", "allow-kill-aura", "false");
      set(r, "breaker", "cancel-knockback", "false");
      set(r, "breaker", "disable-bhop", "false");
      set(r, "breaker", "silent-swing", "false");
      set(r, "breaker", "send-animations", "true");
      set(r, "breaker", "disable-right-click", "true");
      r.modules().setEnabled("breaker", true);
    }

    void tick() throws Exception {
      r.controls().beginTick();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(peer.client);
      wireBoundary(peer);
      for (Object p : new ArrayList<>(pendingStops)) {
        Class<?> bp = Class.forName("net.minecraft.util.BlockPos"),
            facing = Class.forName("net.minecraft.util.EnumFacing");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object up = Enum.valueOf((Class) facing, "UP");
        method(
                controller.getClass(),
                new Class<?>[] {bp, facing},
                "onPlayerDestroyBlock",
                "func_178888_a")
            .invoke(controller, p, up);
      }
      pendingStops.clear();
    }

    void finish(int budget, String label) throws Exception {
      finishing = true;
      for (int i = 0; i < budget && (bedPresent(pos(x, y, z)) || bedPresent(pos(x, y, z + 1))); i++)
        tick();
      finishing = false;
      wireBoundary(peer);
      check(
          observerFailure.get() == null,
          "mining packet observer stayed healthy: " + observerFailure.get());
      check(
          !bedPresent(pos(x, y, z)) && !bedPresent(pos(x, y, z + 1)),
          "completes " + label + " ticks=" + tick + " starts=" + starts.size());
      check(!starts.isEmpty(), "real digging occurs for " + label);
      check(selected.getInt(inventory) == 0, "visible slot survives " + label);
      Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
      boolean look = false;
      for (Object p : peer.received) {
        if (walking.isInstance(p)
            && (Boolean) method(walking, "getRotating", "func_149463_k").invoke(p)) look = true;
        if (p.getClass().getSimpleName().equals("C07PacketPlayerDigging")
            && method(p.getClass(), "getStatus", "func_180762_c")
                .invoke(p)
                .toString()
                .equals("START_DESTROY_BLOCK")) check(look, "look precedes mining " + label);
      }
      stop();
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "disable releases mining claims");
      cases++;
    }

    void stop() {
      r.modules().setEnabled("breaker", false);
      HookBridge.gameControls().apply();
    }

    @Override
    public void close() throws Exception {
      stop();
      owner.close();
      keys.close();
      peer.client.pipeline().remove("breaker-layer-probe");
      peer.close();
      for (Map.Entry<Object, Object> e : terrain.entrySet())
        put.invoke(world, e.getKey(), e.getValue(), 2);
      setMode.invoke(controller, oldMode);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      selected.setInt(inventory, oldSelected);
      position(player, px, py, pz);
      screen.set(game, oldScreen);
      for (Map.Entry<Setting<?>, Object> e : settings.entrySet())
        restore(r, e.getKey(), e.getValue());
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        if (e.getValue()) r.modules().setEnabled(e.getKey(), true);
    }
  }

  private static int countAction(List<Object> packets, String action) throws Exception {
    int n = 0;
    for (Object p : packets)
      if (p.getClass().getSimpleName().equals("C07PacketPlayerDigging")
          && method(p.getClass(), "getStatus", "func_180762_c").invoke(p).toString().equals(action))
        n++;
    return n;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void sendDig(Fixture f, String action, Object pos) throws Exception {
    Class<?>
        type = Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging$Action"),
        facing = Class.forName("net.minecraft.util.EnumFacing");
    Object packet =
        Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging")
            .getConstructor(type, Class.forName("net.minecraft.util.BlockPos"), facing)
            .newInstance(
                Enum.valueOf((Class) type, action), pos, Enum.valueOf((Class) facing, "UP"));
    method(
            f.peer.manager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(f.peer.manager.get(), packet);
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, Setting<?> s, Object v) {
    r.modules().setSetting("breaker", (Setting<T>) s, (T) v);
  }

  private static void check(boolean v, String label) {
    if (!v) throw new AssertionError("Breaker: " + label);
    checks++;
  }
}
