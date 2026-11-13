package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.event.*;

final class UtilityModules {
  private UtilityModules() {}

  static final class AutoSwap extends GameplayModule {
    enum Mode {
      CLIENT,
      SERVER
    }

    private final Setting<Boolean> sneak, swapBack;
    private final Setting<Boolean> skipInteractable;
    private final Setting<Integer> hoverDelay;
    private final Setting<Mode> mode;
    private final AuraModule aura;
    private final java.util.function.BooleanSupplier projectileBusy;

    AutoSwap(ClientRuntime r, AuraModule aura, java.util.function.BooleanSupplier projectileBusy) {
      super(
          r,
          "autoswap",
          "Auto Swap",
          "Select the strongest mining tool or combat weapon.",
          ModuleCategory.PLAYER);
      this.aura = aura;
      this.projectileBusy = projectileBusy;
      sneak = bool("sneak-only", "Sneak Only", false);
      mode = mode("swap-mode", "Swap Mode", Mode.CLIENT, Mode.class);
      swapBack = bool("swap-back", "Swap Back", false).visibleWhen(() -> mode.get() == Mode.CLIENT);
      hoverDelay = integer("tool-hover-delay", "Tool Hover Delay", 0, 0, 20);
      skipInteractable = bool("skip-interactable", "Skip Interactable", false);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(25);
      int[] hover = {0};
      c.listen(
          PlayerControlEvent.class,
          e -> {
            // Yuri selects after PreUpdate attacks, at Motion PRE. A newly selected
            // weapon belongs to the next attack, not the attack earlier this tick.
            if (e != PlayerControlEvent.BEFORE_MOTION) return;
            // Auto Projectile suspends tool swaps as well as weapon swaps while aiming.
            if (projectileBusy.getAsBoolean()) {
              slot.clear();
              if (hoverDelay.get() == 0) hover[0] = 0;
              return;
            }
            int best = -1;
            double score = 0;
            if (mc().currentScreen == null
                && !active("freecam")
                && (!sneak.get() || mc().thePlayer.isSneaking())) {
              boolean attacking = mc().gameSettings.keyBindAttack.isKeyDown();
              boolean blockHit =
                  mc().objectMouseOver != null
                      && mc().objectMouseOver.typeOfHit
                          == MovingObjectPosition.MovingObjectType.BLOCK;
              // B4 pauses its hover count at its early eligibility gates, including
              // released attack. Only an eligible non-block ray resets that count.
              if (hoverDelay.get() == 0 || attacking && !blockHit) hover[0] = 0;
              boolean mining = attacking && blockHit;
              if (mining) {
                if (hoverDelay.get() > 0) {
                  // The setting is bounded at 20; saturation avoids an unbounded counter.
                  if (hover[0] < 20) hover[0]++;
                  // Retain only this activation's existing claim while still in the
                  // eligible mining context. Native arbitration keeps competing owners safe.
                  if (hover[0] < hoverDelay.get()) return;
                }
                if (skipInteractable.get()
                    && perihelion.integration.minecraft189.module.B4BuildingItems.interactable(
                        mc().theWorld
                            .getBlockState(mc().objectMouseOver.getBlockPos())
                            .getBlock())) {
                  slot.clear();
                  return;
                }
              }
              if (mc().objectMouseOver == null) {
                slot.clear();
                return;
              }
              score = mining ? 1 : 0;
              for (int i = 0; i < 9; i++) {
                ItemStack stack = mc().thePlayer.inventory.getStackInSlot(i);
                if (stack == null) continue;
                double value =
                    mining
                        ? toolScore(
                            stack,
                            mc().theWorld
                                .getBlockState(mc().objectMouseOver.getBlockPos())
                                .getBlock())
                        : active("aura") && aura.attackEligible() ? weaponDamage(stack) : 0;
                if (value > score) {
                  score = value;
                  best = i;
                }
              }
            } else if (hoverDelay.get() == 0) hover[0] = 0;
            if (best < 0) slot.clear();
            else
              // Keep the slot through the following PRE attack; clear or replace it
              // at the next Motion PRE. Activation/world cleanup still owns the claim.
              slot.set(
                  mode.get() == Mode.CLIENT
                      ? (swapBack.get()
                          ? HotbarSelection.visible(best)
                          : HotbarSelection.persistent(best))
                      : HotbarSelection.spoof(best));
          });
    }

    private static float toolScore(ItemStack stack, net.minecraft.block.Block block) {
      float score = stack.getStrVsBlock(block);
      if (score > 1) {
        int efficiency =
            net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
                net.minecraft.enchantment.Enchantment.efficiency.effectId, stack);
        if (efficiency > 0) score += efficiency * efficiency + 1;
      }
      return score;
    }

    private static double weaponDamage(ItemStack stack) {
      double value = 0;
      for (net.minecraft.entity.ai.attributes.AttributeModifier modifier :
          stack
              .getAttributeModifiers()
              .get(
                  net.minecraft.entity.SharedMonsterAttributes.attackDamage
                      .getAttributeUnlocalizedName())) {
        value += modifier.getAmount();
      }
      return value;
    }
  }

  static final class Derp extends GameplayModule {
    enum Mode {
      CLIENT,
      SERVER
    }

    private final Setting<Mode> mode;
    private final Setting<Double> pitch, speed;
    private final Setting<Boolean> moveFix;
    private int spinningYaw;

    Derp(ClientRuntime r) {
      super(r, "derp", "Derp", "Spin the player model or server rotation.", ModuleCategory.MISC);
      mode = mode("mode", "Mode", Mode.CLIENT, Mode.class);
      pitch = number("pitch", "Pitch", 90, 0, 90, 1);
      speed = number("rotation-speed", "Rotation Speed", 1, 1, 5, 0.1);
      moveFix = bool("move-fix", "Move Fix", true).visibleWhen(() -> mode.get() == Mode.SERVER);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Rotation> rotation = c.controls().rotation(-10);
      float[] yaw = {spinningYaw}, previousYaw = {spinningYaw};
      Rotation[] aim = {Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch)};
      c.observe(
          mode,
          (setting, before, after) -> {
            rotation.clear();
            aim[0] = Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch);
          });
      c.listen(
          ModelRotationEvent.class,
          e -> {
            if (mode.get() == Mode.CLIENT && mc().gameSettings.thirdPersonView != 0)
              e.angles(e.previous() ? previousYaw[0] : yaw[0], pitch.get().floatValue());
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            previousYaw[0] = yaw[0];
            spinningYaw += speed.get().intValue() * 10 % 360;
            yaw[0] = spinningYaw;
            if (mode.get() == Mode.SERVER) {
              aim[0] =
                  RotationDynamics.smooth(
                      mc(), aim[0], Rotation.server(yaw[0], pitch.get().intValue()), speed.get());
              rotation.setForTicks(aim[0], 1);
            } else rotation.clear();
          });
      GameActions.movementFix(c, rotation, moveFix::get);
    }
  }

  static final class InputFixes extends GameplayModule {
    InputFixes(ClientRuntime r) {
      super(
          r,
          "inputfixes",
          "Input Fixes",
          "Resume held movement after screens close and remove the miss-click delay.",
          ModuleCategory.MISC);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      InputKey[] keys = {
        InputKey.FORWARD,
        InputKey.BACK,
        InputKey.LEFT,
        InputKey.RIGHT,
        InputKey.JUMP,
        InputKey.SNEAK,
        InputKey.SPRINT
      };
      List<ControlRequest<Boolean>> claims = new ArrayList<>();
      for (InputKey key : keys) claims.add(c.controls().key(key, 5));
      boolean[] screen = {mc().currentScreen != null}, restoring = {false};
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if ((e == PlayerControlEvent.BEFORE_MOTION || e == PlayerControlEvent.AFTER_MOTION)
                && mc().inGameHasFocus) ((GameplayAccess) mc()).perihelion$clearClickDelay();
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (screen[0] && mc().currentScreen == null) restoring[0] = true;
            if (mc().currentScreen != null) restoring[0] = false;
            claims.forEach(ControlRequest::clear);
            if (restoring[0]) {
              KeyBinding[] bindings = {
                mc().gameSettings.keyBindForward,
                mc().gameSettings.keyBindBack,
                mc().gameSettings.keyBindLeft,
                mc().gameSettings.keyBindRight,
                mc().gameSettings.keyBindJump,
                mc().gameSettings.keyBindSneak,
                mc().gameSettings.keyBindSprint
              };
              boolean anyHeld = false;
              for (int i = 0; i < keys.length; i++) {
                boolean held = GameSettings.isKeyDown(bindings[i]);
                anyHeld |= held;
                claims.get(i).setForTicks(held, 1);
              }
              restoring[0] = anyHeld;
            }
            screen[0] = mc().currentScreen != null;
          });
    }
  }

  static final class Disabler extends GameplayModule {
    // The donor currently exposes only its inventory mode. Values read on Netty are snapshots.
    private volatile boolean suppressOpen, suppressClose;
    private final InventoryManagementModules.Manager manager;

    Disabler(ClientRuntime r, InventoryManagementModules.Manager manager) {
      super(
          r,
          "disabler",
          "Disabler",
          "Suppress redundant inventory notifications and movement while opening" + " containers.",
          ModuleCategory.MISC);
      this.manager = manager;
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Boolean> sprint = c.controls().sprinting(110);
      InputKey[] keys = {
        InputKey.FORWARD, InputKey.BACK, InputKey.LEFT, InputKey.RIGHT, InputKey.JUMP
      };
      List<ControlRequest<Boolean>> claims = new ArrayList<>();
      for (InputKey key : keys) claims.add(c.controls().key(key, 110));
      suppressOpen = suppressClose = false;
      c.onCleanup(
          () -> {
            suppressOpen = suppressClose = false;
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            boolean container = mc().currentScreen instanceof GuiContainer;
            boolean stop =
                container
                    && (mc().thePlayer.isSprinting()
                        || !(mc().currentScreen instanceof GuiInventory));
            if (container) sprint.setForTicks(false, 1);
            else sprint.clear();
            for (ControlRequest<Boolean> claim : claims) {
              if (stop) claim.setForTicks(false, 1);
              else claim.clear();
            }
            suppressOpen = !container || mc().currentScreen instanceof GuiInventory;
          });
      c.listenNetwork(
          PacketSendEvent.class,
          50,
          false,
          e -> {
            if (manager.isHandshake(e.packet())) return;
            if (suppressOpen
                && e.packet() instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) e.packet()).getStatus()
                    == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
              suppressClose = true;
              e.cancel();
            }
            if (suppressOpen
                && suppressClose
                && e.packet() instanceof C0DPacketCloseWindow
                && GameActions.closeWindowId((C0DPacketCloseWindow) e.packet()) == 0) {
              suppressClose = false;
              e.cancel();
            }
          });
    }
  }

  static final class AutoChat extends GameplayModule {
    private final Setting<Boolean> register, play, twice;
    private final Setting<String> password;

    AutoChat(ClientRuntime r) {
      super(
          r,
          "autochat",
          "Auto Chat",
          "Respond to registration and play-again prompts.",
          ModuleCategory.MISC);
      register = bool("register", "Register", true);
      play = bool("auto-play", "Auto Play", true);
      twice = bool("double-password", "Double Password", true);
      password =
          setting(
              Setting.string(
                  "password",
                  "Password",
                  "Registration password; empty disables registration responses.",
                  "",
                  128,
                  guard));
    }

    @Override
    protected void onEnable(ModuleContext c) {
      long[] last = {0};
      c.listen(
          ClientPacketEvent.class,
          e -> {
            if (!(e.packet() instanceof S02PacketChat) || now() - last[0] < 1000) return;
            S02PacketChat packet = (S02PacketChat) e.packet();
            IChatComponent component = packet.getChatComponent();
            String text = component.getUnformattedText().toLowerCase(Locale.ROOT), command = null;
            if (register.get() && !password.get().isEmpty() && !password.get().matches(".*\\s.*")) {
              for (String keyword :
                  new String[] {
                    "/register",
                    "/reg",
                    "/registrar",
                    "/зарег",
                    "/rejestracja",
                    "/cadastrar",
                    "/kayit",
                    "/enregistrer"
                  }) {
                if (text.contains(keyword)) {
                  command =
                      keyword + " " + password.get() + (twice.get() ? " " + password.get() : "");
                  break;
                }
              }
            }
            if (play.get() && !packet.isChat()) {
              if (text.contains("you were spawned in limbo.")) command = "/lobby";
              else if (text.contains("play again?"))
                for (IChatComponent part : component) {
                  ClickEvent click = part.getChatStyle().getChatClickEvent();
                  if (click != null
                      && click.getAction() == ClickEvent.Action.RUN_COMMAND
                      && click.getValue().matches("/play [a-zA-Z0-9_ -]+")) {
                    command = click.getValue();
                    break;
                  }
                }
            }
            if (command != null) {
              mc().thePlayer.sendChatMessage(command);
              last[0] = now();
            }
          });
    }
  }

  static final class BedWarsUtility extends GameplayModule {
    private final Setting<Boolean> swords, stone, armor, invisible, ownInvis;

    BedWarsUtility(ClientRuntime r) {
      super(
          r,
          "bedwarsutility",
          "BedWars Utility",
          "Notify about sword, armor and invisibility changes.",
          ModuleCategory.MISC);
      swords = bool("swords", "Swords", true);
      stone = bool("include-stone", "Include Stone", false);
      armor = bool("armor", "Armor", true);
      invisible = bool("invisible-check", "Invisible Check", true);
      ownInvis = bool("potion-invis", "Potion Invis", false);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      Map<UUID, String> weapons = new HashMap<>(), equipment = new HashMap<>();
      Set<UUID> hidden = new HashSet<>();
      boolean[] playing = {false};
      c.listen(
          ClientPacketEvent.class,
          e -> {
            if (e.packet() instanceof S02PacketChat) {
              String text = ((S02PacketChat) e.packet()).getChatComponent().getUnformattedText();
              if (text.contains("Protect your bed and destroy the enemy beds.")
                  || text.contains("Goodluck with your BedWars Game")) {
                playing[0] = true;
                weapons.clear();
                equipment.clear();
                hidden.clear();
              }
            }
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT || !playing[0]) return;
            for (EntityPlayer player : mc().theWorld.playerEntities) {
              if (player == mc().thePlayer) continue;
              ItemStack held = player.getHeldItem(), leggings = player.getCurrentArmor(1);
              UUID id = player.getUniqueID();
              if (swords.get() && held != null) {
                Item item = held.getItem();
                String name =
                    item == Items.diamond_sword
                        ? "a diamond sword"
                        : item == Items.iron_sword
                            ? "an iron sword"
                            : stone.get() && item == Items.stone_sword ? "a stone sword" : null;
                if (item == Items.wooden_sword) weapons.remove(id);
                if (name != null && !name.equals(weapons.put(id, name)))
                  notify(player.getName() + " has " + name);
              }
              if (armor.get() && leggings != null) {
                Item item = leggings.getItem();
                String name =
                    item == Items.diamond_leggings
                        ? "diamond armor"
                        : item == Items.iron_leggings
                            ? "iron armor"
                            : item == Items.chainmail_leggings ? "chain armor" : null;
                if (item == Items.leather_leggings) equipment.remove(id);
                if (name != null && !name.equals(equipment.put(id, name)))
                  notify(player.getName() + " has " + name);
              }
              if (invisible.get()) {
                if (player.isInvisible()) {
                  if (hidden.add(id)) notify(player.getName() + " is invisible");
                } else hidden.remove(id);
              }
            }
            if (ownInvis.get()
                && mc().thePlayer.ticksExisted % 200 == 0
                && mc().thePlayer.isPotionActive(Potion.invisibility))
              notify(
                  "Invisibility: "
                      + mc().thePlayer.getActivePotionEffect(Potion.invisibility).getDuration() / 20
                      + "s");
          });
    }

    private void notify(String message) {
      mc().thePlayer.addChatMessage(new ChatComponentText("[Perihelion] " + message));
    }
  }
}
