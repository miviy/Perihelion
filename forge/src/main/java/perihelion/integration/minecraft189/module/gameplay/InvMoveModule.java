// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.EnumMap;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.network.*;

/** Yuri 1.3.1 inventory transaction modes with native activation and connection ownership. */
final class InvMoveModule extends GameplayModule {
  private final Setting<InventoryMoveState.Inventory> inventory;
  private final Setting<InventoryMoveState.Chest> chest;
  private final Setting<Double> motion;
  private final Setting<Boolean> afterClick,
      slow,
      jumping,
      movement,
      guis,
      midair,
      rotating,
      potion,
      sprint;
  private final ThreadLocal<Boolean> own = new ThreadLocal<>();
  private volatile Facts facts;

  InvMoveModule(ClientRuntime r) {
    super(
        r,
        "invmove",
        "Inv Move",
        "Movement and transaction timing while containers are open.",
        ModuleCategory.MOVEMENT);
    inventory =
        mode(
            "inventory",
            "Inventory",
            InventoryMoveState.Inventory.VANILLA,
            InventoryMoveState.Inventory.class);
    chest =
        mode(
            "chest",
            "Chest & Others",
            InventoryMoveState.Chest.VANILLA,
            InventoryMoveState.Chest.class);
    motion = number("motion", "Motion", 1, 0, 1, .01);
    afterClick = bool("modify-after-click", "Modify Motion After Click", false);
    slow = bool("slow-when-necessary", "Slow Motion When Necessary", false);
    jumping = bool("allow-jumping", "Allow Jumping", true);
    movement = bool("allow-movement", "Allow Movement", true);
    guis =
        bool(
            "allow-in-guis",
            "Allow in GUIs",
            "Move in other screens. Excludes chat and creative inventory.",
            false);
    midair = bool("allow-midair", "Allow Midair", true);
    rotating = bool("allow-rotating", "Allow Rotating", true);
    potion = bool("allow-speed-potion", "Allow Speed Potion", true);
    sprint = bool("allow-sprinting", "Allow Sprinting", false);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    InventoryMoveState<PacketSnapshot> state = new InventoryMoveState<>();
    long generation = runtime.tasks().generation();
    net.minecraft.client.network.NetHandlerPlayClient connection = mc().getNetHandler();
    Object world = mc().theWorld;
    java.util.function.BooleanSupplier current =
        () -> {
          runtime.tasks().checkThread();
          return generation == runtime.tasks().generation()
              && world != null
              && mc().theWorld == world
              && connection != null
              && mc().getNetHandler() == connection
              && connection.getNetworkManager().isChannelOpen();
        };
    facts = snapshot();
    PacketBufferScope buffer =
        new PacketBufferScope(
            runtime, c, (packet, outbound) -> outbound && state.blinking(), () -> Long.MAX_VALUE);
    EnumMap<InputKey, ControlRequest<Boolean>> keys = new EnumMap<>(InputKey.class);
    for (InputKey key :
        new InputKey[] {
          InputKey.FORWARD,
          InputKey.BACK,
          InputKey.LEFT,
          InputKey.RIGHT,
          InputKey.JUMP,
          InputKey.SNEAK,
          InputKey.SPRINT
        }) keys.put(key, c.controls().key(key, 65));
    ControlRequest<Boolean> sprinting = c.controls().sprinting(65);
    ControlRequest<Rotation> view = c.controls().rotation(20);
    Runnable reset =
        () -> {
          state.reset();
          buffer.flush(true);
          keys.values().forEach(ControlRequest::clear);
          sprinting.clear();
          view.clear();
        };
    c.observe(inventory, (s, b, a) -> reset.run());
    c.observe(chest, (s, b, a) -> reset.run());
    c.onCleanup(
        () -> {
          facts = null;
          state.retire();
        });
    c.listenNetwork(
        PacketSendEvent.class,
        900,
        false,
        e -> {
          if (Boolean.TRUE.equals(own.get())
              || state.retired()
              || generation != runtime.tasks().generation()) return;
          if (runtime.tasks().isClientThread() && !current.getAsBoolean()) return;
          Facts f = runtime.tasks().isClientThread() ? snapshot() : facts;
          if (f == null) return;
          Packet<?> packet = e.packet();
          boolean allow = true;
          if (packet instanceof C16PacketClientStatus
              && ((C16PacketClientStatus) packet).getStatus()
                  == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)
            allow = state.open(f.inventory, f.blinkAvailable);
          else if (packet instanceof C0DPacketCloseWindow)
            allow = state.close(f.inventory, f.chest, f.inInventory);
          else if (packet instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow click = (C0EPacketClickWindow) packet;
            boolean reject =
                !f.holding
                    && (click.getMode() == 3
                        || click.getMode() == 4 && click.getSlotId() == -999
                        || click.getMode() == 0
                            && click.getUsedButton() == 1
                            && emptySlot(click.getSlotId(), f));
            try {
              PacketSnapshot copy =
                  f.inventory.legit() && f.inInventory ? PacketSnapshot.copy(packet, true) : null;
              allow =
                  state.click(
                      f.inventory,
                      f.inInventory,
                      f.chestLike,
                      f.holding,
                      reject,
                      f.tick,
                      copy,
                      copy == null ? 0 : copy.size(),
                      f.blinkAvailable);
            } catch (java.io.IOException failure) {
              throw new IllegalStateException(failure);
            }
          }
          if (!allow) e.cancel();
        });
    c.listen(
        PlayerControlEvent.class,
        100,
        false,
        e -> {
          if (e != PlayerControlEvent.BEFORE_INPUT) return;
          if (state.retired() || !current.getAsBoolean()) {
            facts = null;
            state.retire();
            buffer.flush(true);
            keys.values().forEach(ControlRequest::clear);
            sprinting.clear();
            view.clear();
            return;
          }
          Facts f = snapshot();
          facts = f;
          keys.values().forEach(ControlRequest::clear);
          sprinting.clear();
          view.clear();
          for (InventoryMoveState.Action<PacketSnapshot> action :
              state.tick(f.inventory, mc().currentScreen == null)) {
            if (!current.getAsBoolean()) {
              facts = null;
              state.retire();
              return;
            }
            Packet<?> packet;
            switch (action.kind) {
              case OPEN:
                packet =
                    new C16PacketClientStatus(
                        C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT);
                break;
              case CLICK:
                try {
                  packet = action.packet.decode();
                } catch (java.io.IOException failure) {
                  throw new IllegalStateException(failure);
                }
                break;
              default:
                packet = new C0DPacketCloseWindow(0);
            }
            own.set(true);
            try {
              if (action.kind == InventoryMoveState.Kind.BYPASS_CLOSE)
                RawHookBridge.replay(() -> connection.getNetworkManager().sendPacket(packet));
              else GameActions.send(packet);
            } finally {
              own.remove();
            }
          }
          if (state.releaseReady()) {
            state.finishBlink();
            buffer.flush(true);
          }
          boolean stop =
              state.consumeStop(
                  f.inventory, f.chest, f.inInventory, mc().currentScreen instanceof GuiChest);
          // Legit transactions can still be pending after the GUI closes.
          if (stop) {
            for (InputKey key :
                new InputKey[] {InputKey.FORWARD, InputKey.BACK, InputKey.LEFT, InputKey.RIGHT})
              keys.get(key).setForTicks(false, 1);
            if (f.inventory.legit()) {
              keys.get(InputKey.JUMP).setForTicks(false, 1);
              keys.get(InputKey.SNEAK).setForTicks(false, 1);
            }
          }
          boolean stopSprint =
              f.inInventory
                      && (f.inventory == InventoryMoveState.Inventory.PAUSE
                          || f.inventory == InventoryMoveState.Inventory.BLINK
                          || f.inventory == InventoryMoveState.Inventory.CLOSE)
                  || f.chestLike
                      && (f.chest == InventoryMoveState.Chest.PAUSE
                          || f.chest == InventoryMoveState.Chest.BLINK);
          if (stopSprint) {
            keys.get(InputKey.SPRINT).setForTicks(false, 1);
            sprinting.setForTicks(false, 1);
          }
          if (!eligible(f)) return;
          // Chest Blink starts only after the donor's midair/potion eligibility checks.
          if (!f.inInventory && f.chestLike && f.chest == InventoryMoveState.Chest.BLINK)
            state.startBlink(f.blinkAvailable);
          InputKey[] order = {
            InputKey.FORWARD,
            InputKey.BACK,
            InputKey.LEFT,
            InputKey.RIGHT,
            InputKey.JUMP,
            InputKey.SNEAK
          };
          KeyBinding[] bindings = {
            mc().gameSettings.keyBindForward,
            mc().gameSettings.keyBindBack,
            mc().gameSettings.keyBindLeft,
            mc().gameSettings.keyBindRight,
            mc().gameSettings.keyBindJump,
            mc().gameSettings.keyBindSneak
          };
          boolean inContainer = f.inInventory || f.chestLike;
          if (movement.get())
            for (int i = 0; i < order.length; i++) {
              if (inContainer && (i == 5 || i == 4 && !jumping.get())) continue;
              keys.get(order[i])
                  .setForTicks(
                      !(stop && (i < 4 || f.inventory.legit()))
                          && GameSettings.isKeyDown(bindings[i]),
                      1);
            }
          if (inContainer
              && motion.get() != 1
              && (!afterClick.get() || state.clicked())
              && GameActions.moving(mc().thePlayer)) {
            double scale = motion.get();
            if (slow.get()) {
              if (!mc().thePlayer.onGround) scale -= .2 + (sprint.get() ? 0 : .05);
              scale = Math.max(0, scale - speedLevel() * .25);
            }
            mc().thePlayer.motionX *= scale;
            mc().thePlayer.motionZ *= scale;
          }
          if (rotating.get() && Keyboard.isCreated()) {
            float yaw =
                (Keyboard.isKeyDown(Keyboard.KEY_RIGHT) ? 8 : 0)
                    - (Keyboard.isKeyDown(Keyboard.KEY_LEFT) ? 8 : 0);
            float pitch =
                (Keyboard.isKeyDown(Keyboard.KEY_DOWN) ? 5 : 0)
                    - (Keyboard.isKeyDown(Keyboard.KEY_UP) ? 5 : 0);
            if (yaw != 0 || pitch != 0)
              view.setForTicks(
                  Rotation.view(
                      mc().thePlayer.rotationYaw
                          + yaw
                          + (yaw == 0 ? 0 : (float) Math.random() - .5f),
                      MathHelper.clamp_float(
                          mc().thePlayer.rotationPitch
                              + pitch
                              + (pitch == 0 ? 0 : (float) Math.random() - .5f),
                          -90,
                          90)),
                  1);
          }
        });
  }

  private int speedLevel() {
    PotionEffect effect = mc().thePlayer.getActivePotionEffect(Potion.moveSpeed);
    return effect == null ? 0 : effect.getAmplifier() + 1;
  }

  private boolean eligible(Facts f) {
    return mc().thePlayer.isEntityAlive()
        && (f.inInventory && f.inventory != InventoryMoveState.Inventory.DISABLED
            || f.chestLike && f.chest != InventoryMoveState.Chest.DISABLED
            || !f.inInventory
                && !f.chestLike
                && guis.get()
                && mc().currentScreen != null
                && !(mc().currentScreen instanceof GuiChat)
                && !(mc().currentScreen instanceof GuiContainerCreative))
        && (midair.get() || f.air <= 2)
        && (potion.get() || speedLevel() == 0);
  }

  private boolean emptySlot(int slot, Facts f) {
    return slot >= 0 && slot < f.empty.length && f.empty[slot];
  }

  private Facts snapshot() {
    boolean[] empty = new boolean[mc().thePlayer.inventoryContainer.inventorySlots.size()];
    for (int i = 0; i < empty.length; i++)
      empty[i] = mc().thePlayer.inventoryContainer.getSlot(i).getStack() == null;
    boolean chestLike =
        mc().currentScreen instanceof GuiChest
            || mc().currentScreen instanceof GuiCrafting
            || mc().currentScreen instanceof GuiFurnace
            || mc().currentScreen instanceof GuiBeacon
            || mc().currentScreen instanceof GuiBrewingStand
            || mc().currentScreen instanceof GuiDispenser;
    int air =
        ((perihelion.integration.minecraft189.control.PlayerGroundAccess) mc().thePlayer)
            .perihelion$groundTicks()
            .beforeAir();
    return new Facts(
        inventory.get(),
        chest.get(),
        mc().currentScreen instanceof GuiInventory,
        chestLike,
        mc().thePlayer.inventory.getItemStack() != null,
        !active("blink"),
        mc().thePlayer.ticksExisted,
        air,
        empty);
  }

  private static final class Facts {
    final InventoryMoveState.Inventory inventory;
    final InventoryMoveState.Chest chest;
    final boolean inInventory, chestLike, holding, blinkAvailable;
    final int tick, air;
    final boolean[] empty;

    Facts(
        InventoryMoveState.Inventory inventory,
        InventoryMoveState.Chest chest,
        boolean inInventory,
        boolean chestLike,
        boolean holding,
        boolean blinkAvailable,
        int tick,
        int air,
        boolean[] empty) {
      this.inventory = inventory;
      this.chest = chest;
      this.inInventory = inInventory;
      this.chestLike = chestLike;
      this.holding = holding;
      this.blinkAvailable = blinkAvailable;
      this.tick = tick;
      this.air = air;
      this.empty = empty;
    }
  }
}
