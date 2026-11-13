package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.*;
import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.event.*;

/** Optional manual-combat helpers; they never enable or disable another module. */
final class AssistModules {
  private AssistModules() {}

  static boolean weapon(ItemStack stack, boolean tools) {
    return stack != null
        && (stack.getItem() instanceof ItemSword || tools && stack.getItem() instanceof ItemTool);
  }

  static final class AimAssist extends GameplayModule {
    enum Mode {
      VIEW,
      SILENT
    }

    private final AuraModule aura;
    private final Setting<Mode> mode;
    private final Setting<Double> range, fov, yawSpeed, pitchSpeed;
    private final Setting<Boolean> requirePress, weapons, tools, mining, walls;

    AimAssist(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "aimassist",
          "Aim Assist",
          "Assist manual aiming without attacking automatically.",
          ModuleCategory.COMBAT);
      this.aura = aura;
      mode = mode("mode", "Mode", Mode.VIEW, Mode.class);
      range = number("range", "Range", 3, 1, 6, .1);
      fov = number("fov", "FOV", 90, 5, 360, 5);
      yawSpeed = number("yaw-speed", "Yaw Speed", 5, .1, 90, .1);
      pitchSpeed = number("pitch-speed", "Pitch Speed", 3, 0, 90, .1);
      requirePress = bool("require-press", "Require Attack Press", true);
      weapons = bool("weapons-only", "Weapons Only", true);
      tools = bool("allow-tools", "Allow Tools", false).visibleWhen(weapons::get);
      mining = bool("pause-mining", "Pause When Mining", true);
      walls = bool("through-walls", "Through Walls", false);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Rotation> rotation = c.controls().rotation(35);
      Rotation[] previous = {null};
      int[] targetId = {Integer.MIN_VALUE};
      c.observe(
          mode,
          (setting, before, after) -> {
            previous[0] = null;
            targetId[0] = Integer.MIN_VALUE;
            rotation.clear();
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            rotation.clear();
            if (mc().currentScreen != null
                || !mc().inGameHasFocus
                || mc().thePlayer.isDead
                || mc().thePlayer.isUsingItem()
                || active("freecam")
                || active("freelook")
                || active("aura")
                || active("scaffold")
                || active("blockin")
                || active("beddefender")) {
              previous[0] = null;
              return;
            }
            if (requirePress.get() && !HookBridge.gameControls().physicalKeyDown(InputKey.ATTACK)) {
              previous[0] = null;
              return;
            }
            if (weapons.get() && !weapon(mc().thePlayer.getHeldItem(), tools.get())) {
              previous[0] = null;
              return;
            }
            if (mining.get()
                && mc().objectMouseOver != null
                && mc().objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
              previous[0] = null;
              return;
            }
            if (mode.get() == Mode.SILENT && !HookBridge.gameControls().supportsServerRotation())
              return;
            EntityLivingBase best = null;
            double error = Double.MAX_VALUE;
            for (EntityLivingBase entity :
                aura.candidates(range.get(), EnumSet.of(AuraModule.Target.PLAYERS), walls.get())) {
              Rotation desired =
                  GameActions.lookAt(mc().thePlayer, GameActions.nearest(mc().thePlayer, entity));
              double angle =
                  Math.abs(
                      MathHelper.wrapAngleTo180_float(desired.yaw() - mc().thePlayer.rotationYaw));
              if (angle <= fov.get() / 2 && angle < error) {
                best = entity;
                error = angle;
              }
            }
            if (best == null) {
              previous[0] = null;
              return;
            }
            Rotation desired =
                GameActions.lookAt(mc().thePlayer, GameActions.nearest(mc().thePlayer, best));
            if (targetId[0] != best.getEntityId()) previous[0] = null;
            targetId[0] = best.getEntityId();
            float baseYaw =
                mode.get() == Mode.SILENT && previous[0] != null
                    ? previous[0].yaw()
                    : mc().thePlayer.rotationYaw;
            float basePitch =
                mode.get() == Mode.SILENT && previous[0] != null
                    ? previous[0].pitch()
                    : mc().thePlayer.rotationPitch;
            float yaw =
                baseYaw
                    + MathHelper.clamp_float(
                        MathHelper.wrapAngleTo180_float(desired.yaw() - baseYaw),
                        -yawSpeed.get().floatValue(),
                        yawSpeed.get().floatValue());
            float pitch =
                basePitch
                    + MathHelper.clamp_float(
                        desired.pitch() - basePitch,
                        -pitchSpeed.get().floatValue(),
                        pitchSpeed.get().floatValue());
            previous[0] = Rotation.server(yaw, pitch);
            rotation.setForTicks(
                mode.get() == Mode.VIEW ? Rotation.view(yaw, pitch) : Rotation.server(yaw, pitch),
                1);
          });
      c.listen(
          StrafeEvent.class,
          e -> {
            if (mode.get() == Mode.SILENT && rotation.isWinning())
              GameActions.fixMovement(e, mc().thePlayer.rotationYaw, rotation.value().get().yaw());
          });
    }
  }

  static final class AutoClicker extends GameplayModule {
    private final Setting<Integer> min, max, blockTicks;
    private final Setting<Boolean> weapons, tools, mining, blockHit;

    AutoClicker(ClientRuntime r) {
      super(
          r,
          "autoclicker",
          "Auto Clicker",
          "Repeat held attacks with bounded timing and optional block hits.",
          ModuleCategory.COMBAT);
      min = integer("min-cps", "Min CPS", 8, 1, 20);
      max = integer("max-cps", "Max CPS", 12, 1, 20);
      weapons = bool("weapons-only", "Weapons Only", true);
      tools = bool("allow-tools", "Allow Tools", false).visibleWhen(weapons::get);
      mining = bool("break-blocks", "Preserve Block Breaking", true);
      blockHit = bool("block-hit", "Block Hit", false);
      blockTicks = integer("block-ticks", "Block Ticks", 2, 1, 10).visibleWhen(blockHit::get);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Boolean> attack = c.controls().key(InputKey.ATTACK, 40);
      ControlRequest<Boolean> use = c.controls().key(InputKey.USE_ITEM, 40);
      double[] next = {0};
      long[] unblock = {0};
      boolean[] started = {false}, ownedBlock = {false};
      Runnable reset =
          () -> {
            attack.clear();
            use.clear();
            started[0] = false;
            next[0] = 0;
            ownedBlock[0] = false;
          };
      c.onCleanup(
          () -> {
            if (ownedBlock[0]
                && mc().thePlayer != null
                && mc().thePlayer.isBlocking()
                && !HookBridge.gameControls().physicalKeyDown(InputKey.USE_ITEM))
              mc().playerController.onStoppedUsingItem(mc().thePlayer);
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT && e != PlayerControlEvent.BEFORE_ACTIONS)
              return;
            if (mc().currentScreen != null
                || !mc().inGameHasFocus
                || mc().thePlayer.isDead
                || active("aura")
                || active("freecam")
                || active("scaffold")
                || !HookBridge.gameControls().physicalKeyDown(InputKey.ATTACK)
                || weapons.get() && !weapon(mc().thePlayer.getHeldItem(), tools.get())
                || mining.get()
                    && mc().objectMouseOver != null
                    && mc().objectMouseOver.typeOfHit
                        == MovingObjectPosition.MovingObjectType.BLOCK) {
              reset.run();
              return;
            }
            if (mc().thePlayer.isUsingItem() && !mc().thePlayer.isBlocking()) {
              reset.run();
              return;
            }
            attack.setForTicks(false, 1);
            if (e != PlayerControlEvent.BEFORE_ACTIONS || !attack.isWinning()) return;
            long time = now();
            if (!started[0]) {
              started[0] = true;
              next[0] = time + delay();
              return;
            }
            if (ownedBlock[0] && time < unblock[0]) return;
            if (time < next[0]) return;
            boolean block =
                blockHit.get()
                    && mc().thePlayer.getHeldItem() != null
                    && mc().thePlayer.getHeldItem().getItem() instanceof ItemSword
                    && HookBridge.gameControls().physicalKeyDown(InputKey.USE_ITEM);
            if (mc().thePlayer.isBlocking()) {
              if (!block) return;
              use.setForTicks(false, 1);
              if (!use.isWinning()) return;
              mc().playerController.onStoppedUsingItem(mc().thePlayer);
            }
            ((GameplayAccess) mc()).perihelion$clickMouse();
            next[0] = ClickTiming.next(next[0], time, delay());
            ownedBlock[0] = false;
            if (block) {
              use.setForTicks(true, 1);
              if (use.isWinning()) {
                mc().playerController
                    .sendUseItem(mc().thePlayer, mc().theWorld, mc().thePlayer.getHeldItem());
                ownedBlock[0] = mc().thePlayer.isBlocking();
                unblock[0] = time + blockTicks.get() * 50L;
              }
            } else use.clear();
          });
    }

    private double delay() {
      int low = Math.min(min.get(), max.get()), high = Math.max(min.get(), max.get());
      return 1000.0 / (low + Math.random() * (high - low));
    }
  }
}
