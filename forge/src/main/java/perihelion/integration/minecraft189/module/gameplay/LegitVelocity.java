// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.Vec3;
import perihelion.core.control.*;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

/** Yuri's close-range knockback attacks and sprint-jump reset, owned by the activation. */
final class LegitVelocity {
  private int reduce = -1, useTicks;
  private volatile int tick;
  private final AtomicInteger lastAttack = new AtomicInteger(-1);
  private EntityLivingBase pending;
  private final PreparedAimAttack<EntityLivingBase> prepared = new PreparedAimAttack<>();
  private PreparedAimAttack.Action<EntityLivingBase> ready;
  private final ThreadLocal<Boolean> own = new ThreadLocal<>();
  private final Runnable reset;

  void reset() {
    reset.run();
  }

  LegitVelocity(
      CombatModules.Velocity module,
      AuraModule aura,
      ModuleContext c,
      Setting<Integer> reduceTicks,
      java.util.function.BooleanSupplier enabled) {
    ControlRequest<Rotation> rotation = c.controls().rotation(72);
    ControlRequest<Boolean> jump = c.controls().key(InputKey.JUMP, 50);
    ActionWindow window = new ActionWindow(c);
    AtomicBoolean jumpNext = new AtomicBoolean();
    reset =
        () -> {
          pending = null;
          ready = null;
          prepared.clear();
          reduce = -1;
          useTicks = 0;
          lastAttack.set(-1);
          rotation.clear();
          jump.clear();
          jumpNext.set(false);
        };
    c.observe(reduceTicks, (s, before, after) -> reset.run());
    GameActions.movementFix(c, rotation, () -> prepared.pending());
    c.listen(
        ClientPacketEvent.class,
        e -> {
          if (!enabled.getAsBoolean() || !(e.packet() instanceof S12PacketEntityVelocity)) return;
          S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.packet();
          if (packet.getEntityID() != module.mc().thePlayer.getEntityId()
              || packet.getMotionY() <= 0) return;
          reduce = reduceTicks.get() == 0 ? -1 : 0;
          jumpNext.set(module.mc().thePlayer.onGround && module.mc().thePlayer.isSprinting());
        });
    c.listen(
        ClientSendFactEvent.class,
        e -> {
          if (e.action() == ClientSendFactEvent.Action.ATTACK)
            lastAttack.set(module.mc().thePlayer.ticksExisted);
        });
    c.listenNetwork(
        PacketSendEvent.class,
        100,
        false,
        e -> {
          if (Boolean.TRUE.equals(own.get()) && e.packet() instanceof C07PacketPlayerDigging)
            e.cancel();
        });
    c.listen(
        PlayerControlEvent.class,
        90,
        false,
        e -> {
          if (!enabled.getAsBoolean()) {
            reset.run();
            return;
          }
          if (e == PlayerControlEvent.BEFORE_INPUT) {
            tick = module.mc().thePlayer.ticksExisted;
            jump.clear();
            Rotation submitted =
                perihelion.integration.minecraft189.HookBridge.gameControls()
                    .previousRotation(rotation)
                    .orElse(null);
            ready = prepared.submitted(submitted);
            if (jumpNext.getAndSet(false)
                && module.mc().thePlayer.onGround
                && module.mc().thePlayer.isSprinting()
                && !module.mc().gameSettings.keyBindJump.isKeyDown()
                && !module.active("jumpreset")) jump.setForTicks(true, 1);
            useTicks = module.mc().thePlayer.isUsingItem() ? 0 : useTicks + 1;
            EntityLivingBase next = null;
            if (pending != null && useTicks >= 1) {
              next = pending;
              pending = null;
            } else if (module.mc().currentScreen == null
                && reduce >= 0
                && reduce < reduceTicks.get()) {
              reduce++;
              if (lastAttack.get() != tick) {
                double distance = 0;
                for (Entity entity : module.mc().theWorld.loadedEntityList) {
                  if (!aura.velocityTarget(entity)) continue;
                  double d = module.mc().thePlayer.getDistanceToEntity(entity);
                  if (d <= 3 && (next == null || d > distance)) {
                    next = (EntityLivingBase) entity;
                    distance = d;
                  }
                }
              }
            }
            if (next != null
                && !prepared.pending()
                && aura.velocityTarget(next)
                && module.mc().thePlayer.getDistanceToEntity(next) <= 3
                && module.mc().currentScreen == null) {
              Rotation desired =
                  GameActions.yuriLookAt(
                      module.mc().thePlayer, AuraAim.normalPoint(module.mc(), next));
              Rotation origin =
                  rotation
                      .value()
                      .orElse(
                          Rotation.server(
                              module.mc().thePlayer.rotationYaw,
                              module.mc().thePlayer.rotationPitch));
              prepared.prepare(next, RotationDynamics.smooth(module.mc(), origin, desired, 18));
            }
            if (prepared.pending()) rotation.setForTicks(prepared.aim(), 1);
            else if (ready != null) rotation.setForTicks(ready.aim, 1);
            else rotation.clear();
          } else if (e == PlayerControlEvent.BEFORE_ACTIONS && ready != null) {
            PreparedAimAttack.Action<EntityLivingBase> action = ready;
            ready = null;
            EntityLivingBase target = action.target;
            if (module.mc().thePlayer.isUsingItem()) {
              pending = target;
              return;
            }
            if (aura.combatBlockedByBuilding()
                || module.mc().currentScreen != null
                || lastAttack.get() == tick
                || !aura.velocityTarget(target)
                || module.mc().thePlayer.getDistanceToEntity(target) > 3
                || module.active("aura") && aura.target() != null && window.combat()) return;
            GameActions.previousAction(
                rotation,
                previous -> {
                  if (previous != action.aim) return;
                  // A target may move after preparation. Never attack it using an unrelated stale
                  // ray.
                  Vec3 eye = module.mc().thePlayer.getPositionEyes(1),
                      look = Placement.direction(previous);
                  Vec3 end = eye.addVector(look.xCoord * 3, look.yCoord * 3, look.zCoord * 3);
                  if (!target.getEntityBoundingBox().isVecInside(eye)
                      && target.getEntityBoundingBox().calculateIntercept(eye, end) == null) return;
                  own.set(true);
                  try {
                    GameActions.send(new C0APacketAnimation());
                    module.mc().playerController.attackEntity(module.mc().thePlayer, target);
                  } finally {
                    own.remove();
                  }
                });
          }
        });
  }
}
