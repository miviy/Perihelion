// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.ItemUseDelayAccess;
import perihelion.integration.minecraft189.event.*;

/** Yuri 1.3.1 shift-held clutch; native requests own the selected slot, aim and placement. */
final class ClutchModule extends GameplayModule {
  private final Setting<Double> minRotation, maxRotation;
  private final Setting<Integer> minDelay, maxDelay;

  ClutchModule(ClientRuntime r) {
    super(
        r,
        "clutch",
        "Clutch",
        "Place reachable blocks while holding Sneak over a drop.",
        ModuleCategory.WORLD);
    minRotation = number("rotation-speed-min", "Rotation Speed Min", 5, 0, 10, .5);
    maxRotation = number("rotation-speed-max", "Rotation Speed Max", 10, 0, 10, .5);
    minDelay = integer("place-delay-min", "Place Delay Min", 0, 0, 1);
    maxDelay = integer("place-delay-max", "Place Delay Max", 0, 0, 1);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    ClutchState state = new ClutchState();
    c.listen(ClientSendFactEvent.class, 2000, false, e -> state.packet(traffic(e.action())));
    ControlRequest<HotbarSelection> slot = c.controls().hotbar(105);
    ControlRequest<Rotation> rotation = c.controls().rotation(105);
    Placement[] plan = {null};
    Rotation[] desired = {Rotation.server(mc().thePlayer.rotationYaw - 180, 90)},
        aim = {Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch)};
    GameActions.movementFix(c, rotation, () -> true);
    c.listen(
        PlayerControlEvent.class,
        90,
        false,
        e -> {
          if (e == PlayerControlEvent.BEFORE_INPUT) {
            state.flight(
                ((perihelion.integration.minecraft189.control.PlayerGroundAccess) mc().thePlayer)
                    .perihelion$groundTicks()
                    .beforeAir());
            plan[0] = null;
            slot.clear();
            rotation.clear();
            if (mc().currentScreen != null
                || mc().thePlayer.isDead
                || mc().thePlayer.ticksExisted <= 50
                || active("scaffold")
                || !mc().gameSettings.keyBindSneak.isKeyDown()
                || state.bad()) return;
            boolean blockAtTenBelow =
                !mc().theWorld
                    .getCollidingBoundingBoxes(
                        mc().thePlayer, mc().thePlayer.getEntityBoundingBox().offset(0, -10, 0))
                    .isEmpty();
            if (!state.attemptWindow(blockAtTenBelow)) return;
            int selected = GameActions.hotbar(mc(), InventoryPolicy::buildingBlock);
            if (selected < 0) return;
            slot.setForTicks(HotbarSelection.persistent(selected), 1);
            state.underfoot(Placement.replaceable(mc(), new BlockPos(mc().thePlayer).down()));
            plan[0] = Placement.nearby(mc(), new BlockPos(mc().thePlayer).down(), null);
            if (plan[0] == null) return;
            if (state.onAir() > 0 && !plan[0].ray(mc(), aim[0], 4.5, true))
              desired[0] = search(plan[0]);
            double speed =
                (float)
                    (Math.min(minRotation.get(), maxRotation.get())
                        + Math.random() * Math.abs(maxRotation.get() - minRotation.get()));
            if (speed != 0) aim[0] = RotationDynamics.smooth(mc(), aim[0], desired[0], speed);
            rotation.setForTicks(aim[0], 1);
          } else if (e == PlayerControlEvent.BEFORE_ACTIONS && plan[0] != null) {
            GameActions.previousAction(
                rotation,
                previous -> {
                  if (state.delayPassed(minDelay.get(), maxDelay.get(), Math.random())
                      && !state.placementBad()
                      && plan[0].ray(
                          mc(), previous, mc().playerController.getBlockReachDistance(), true)) {
                    plan[0].place(mc(), slot, rotation, false, true, true);
                    state.attempted();
                    ((ItemUseDelayAccess) mc()).perihelion$itemUseDelay(0);
                  } else if (Math.random() > .92
                      && ((ItemUseDelayAccess) mc()).perihelion$itemUseDelay() <= 0) {
                    HookBridge.gameControls()
                        .withHotbar(
                            slot,
                            stack -> {
                              if (stack != null)
                                GameActions.send(new C08PacketPlayerBlockPlacement(stack));
                            });
                    ((ItemUseDelayAccess) mc()).perihelion$itemUseDelay(0);
                  }
                });
          }
        });
  }

  private static ClutchState.Traffic traffic(ClientSendFactEvent.Action action) {
    switch (action) {
      case WALK:
        return ClutchState.Traffic.WALK;
      case SLOT:
        return ClutchState.Traffic.SLOT;
      case ATTACK:
      case USE_ENTITY:
        return ClutchState.Traffic.ATTACK;
      case SWING:
        return ClutchState.Traffic.SWING;
      case USE_ITEM:
      case PLACE:
      case DIG:
      case RELEASE_USE_ITEM:
        return ClutchState.Traffic.BLOCK;
      case INVENTORY:
        return ClutchState.Traffic.INVENTORY;
      default:
        return ClutchState.Traffic.OTHER;
    }
  }

  private Rotation search(Placement plan) {
    double difference =
        mc().thePlayer.posY
            + mc().thePlayer.getEyeHeight()
            - plan.target.getY()
            - .1
            - Math.random() * .8;
    Vec3 origin = mc().thePlayer.getPositionEyes(1).addVector(0, -difference, 0);
    for (int offset = -180; offset <= 180; offset += 45) {
      Vec3 direction = Placement.direction(Rotation.server(mc().thePlayer.rotationYaw + offset, 0));
      MovingObjectPosition hit =
          mc().theWorld
              .rayTraceBlocks(
                  origin,
                  origin.addVector(
                      direction.xCoord * 4.5, direction.yCoord * 4.5, direction.zCoord * 4.5),
                  false,
                  false,
                  true);
      if (hit != null && plan.support.equals(hit.getBlockPos()) && plan.face == hit.sideHit)
        return GameActions.yuriLookAt(mc().thePlayer, hit.hitVec);
    }
    return GameActions.yuriLookAt(mc().thePlayer, plan.hit);
  }
}
