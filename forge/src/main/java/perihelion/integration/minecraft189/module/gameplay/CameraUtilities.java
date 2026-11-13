package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

final class CameraUtilities {
  private CameraUtilities() {}

  static final class Freecam extends GameplayModule implements PlayerSpectator {
    private final Setting<Double> horizontal, vertical, distance;
    private EntityOtherPlayerMP activeCamera;
    private UUID target;
    private boolean retiredSpectate;

    Freecam(ClientRuntime r) {
      super(
          r,
          "freecam",
          "Freecam",
          "Move a detached camera while local movement and interaction are suppressed.",
          ModuleCategory.RENDER);
      horizontal = number("horizontal-speed", "Horizontal Speed", .8, .1, 5, .1);
      vertical = number("vertical-speed", "Vertical Speed", .6, .1, 5, .1);
      distance = number("max-distance", "Max Distance", 64, 8, 128, 1);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      if (retiredSpectate) {
        retiredSpectate = false;
        c.requestDisable();
        return;
      }
      long generation = runtime.tasks().generation();
      Entity previous = mc().getRenderViewEntity();
      net.minecraft.client.multiplayer.WorldClient world = mc().theWorld;
      EntityOtherPlayerMP camera = new EntityOtherPlayerMP(world, mc().thePlayer.getGameProfile());
      camera.copyLocationAndAnglesFrom(mc().thePlayer);
      camera.prevPosX = camera.lastTickPosX = camera.posX;
      camera.prevPosY = camera.lastTickPosY = camera.posY;
      camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
      camera.prevRotationYaw = camera.rotationYaw;
      camera.prevRotationPitch = camera.rotationPitch;
      camera.noClip = true;
      activeCamera = camera;
      target = null;
      c.onCleanup(
          () -> {
            if (mc().getRenderViewEntity() == camera) {
              boolean validPrevious =
                  mc().theWorld == world
                      && previous != null
                      && previous.worldObj == world
                      && !previous.isDead
                      && (previous == mc().thePlayer
                          || world.getEntityByID(previous.getEntityId()) == previous);
              mc().setRenderViewEntity(validPrevious ? previous : mc().thePlayer);
            }
            if (activeCamera == camera) {
              retiredSpectate = target != null && generation != runtime.tasks().generation();
              activeCamera = null;
              target = null;
            }
          });
      mc().playerController.resetBlockRemoving();
      mc().setRenderViewEntity(camera);
      List<ControlRequest<Boolean>> keys = new ArrayList<>();
      for (InputKey key : InputKey.values()) keys.add(c.controls().key(key, 1000));
      ControlRequest<Boolean> sprint = c.controls().sprinting(1000);
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(1000);
      ControlRequest<Rotation> rotation = c.controls().rotation(1000);
      c.listen(LocalInteractionEvent.class, 1000, false, e -> e.cancel());
      c.listen(PlayerAttackEvent.class, 1000, false, e -> e.cancel());
      c.listen(
          OverlayEffectEvent.class,
          e -> {
            if (e.kind() == OverlayEffectEvent.Kind.HAND) e.cancel();
          });
      c.listen(
          MouseLookEvent.class,
          1000,
          false,
          e -> {
            if (mc().inGameHasFocus && mc().currentScreen == null)
              camera.setAngles(e.yaw(), e.pitch());
            e.cancel();
          });
      c.listen(
          PlayerControlEvent.class,
          1000,
          false,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (mc().getRenderViewEntity() != camera || mc().thePlayer.isDead) {
              c.requestDisable();
              return;
            }
            Entity center = target == null ? mc().thePlayer : world.getPlayerEntityByUUID(target);
            if (center == null || center.isDead) {
              c.requestDisable();
              return;
            }
            for (ControlRequest<Boolean> key : keys) key.setForTicks(false, 1);
            sprint.setForTicks(false, 1);
            slot.setForTicks(HotbarSelection.visible(mc().thePlayer.inventory.currentItem), 1);
            rotation.setForTicks(
                Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch), 1);
            camera.prevPosX = camera.lastTickPosX = camera.posX;
            camera.prevPosY = camera.lastTickPosY = camera.posY;
            camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
            if (mc().currentScreen != null || !mc().inGameHasFocus) return;
            double forward = axis(InputKey.FORWARD, InputKey.BACK),
                strafe = axis(InputKey.RIGHT, InputKey.LEFT);
            double length = Math.max(1, Math.hypot(forward, strafe));
            double yaw = Math.toRadians(camera.rotationYaw);
            double x =
                camera.posX
                    + (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe)
                        * horizontal.get()
                        / length;
            double z =
                camera.posZ
                    + (Math.cos(yaw) * forward + Math.sin(yaw) * strafe)
                        * horizontal.get()
                        / length;
            double y = camera.posY + axis(InputKey.JUMP, InputKey.SNEAK) * vertical.get();
            double dx = x - center.posX, dy = y - center.posY, dz = z - center.posZ;
            double scale =
                Math.min(
                    1, distance.get() / Math.max(.001, Math.sqrt(dx * dx + dy * dy + dz * dz)));
            camera.setPosition(
                center.posX + dx * scale, center.posY + dy * scale, center.posZ + dz * scale);
          });
    }

    public boolean spectate(UUID id) {
      guard.run();
      if (mc().theWorld == null || mc().thePlayer == null || mc().thePlayer.isDead) return false;
      Entity player = mc().theWorld.getPlayerEntityByUUID(id);
      if (player == null || player == mc().thePlayer || player.isDead) return false;
      runtime.modules().setEnabled("freecam", true);
      if (activeCamera == null || mc().getRenderViewEntity() != activeCamera) return false;
      target = id;
      double yaw = Math.toRadians(player.rotationYaw);
      activeCamera.setPositionAndRotation(
          player.posX + Math.sin(yaw) * 3,
          player.posY + 1,
          player.posZ - Math.cos(yaw) * 3,
          player.rotationYaw,
          15);
      activeCamera.prevPosX = activeCamera.lastTickPosX = activeCamera.posX;
      activeCamera.prevPosY = activeCamera.lastTickPosY = activeCamera.posY;
      activeCamera.prevPosZ = activeCamera.lastTickPosZ = activeCamera.posZ;
      activeCamera.prevRotationYaw = activeCamera.rotationYaw;
      activeCamera.prevRotationPitch = activeCamera.rotationPitch;
      return true;
    }

    public UUID spectating() {
      guard.run();
      return target;
    }

    private int axis(InputKey positive, InputKey negative) {
      return (HookBridge.gameControls().physicalKeyDown(positive) ? 1 : 0)
          - (HookBridge.gameControls().physicalKeyDown(negative) ? 1 : 0);
    }
  }

  static final class Zoom extends GameplayModule {
    private final Setting<Double> fov;
    private final Setting<Integer> step;
    private final Setting<Boolean> scroll, hand, smooth;

    Zoom(ClientRuntime r) {
      super(
          r,
          "zoom",
          "Zoom",
          "Adjust magnification without changing the saved FOV.",
          ModuleCategory.RENDER);
      fov = number("fov", "Zoom FOV", 30, 5, 90, 1);
      scroll = bool("scroll", "Scroll Zoom", true);
      step = integer("scroll-step", "Scroll Step", 5, 1, 20).visibleWhen(scroll::get);
      hand = bool("hide-hand", "Hide Hand", true);
      smooth = bool("smooth-look", "Smooth Look", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      double[] value = {fov.get()};
      boolean[] previous = {mc().gameSettings.smoothCamera}, wrote = {false};
      c.onCleanup(
          () -> {
            if (wrote[0] && mc().gameSettings.smoothCamera)
              mc().gameSettings.smoothCamera = previous[0];
          });
      c.observe(fov, (s, before, after) -> value[0] = after);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (smooth.get()) {
              if (!wrote[0] || !mc().gameSettings.smoothCamera)
                previous[0] = mc().gameSettings.smoothCamera;
              mc().gameSettings.smoothCamera = true;
              wrote[0] = true;
            } else if (wrote[0]) {
              if (mc().gameSettings.smoothCamera) mc().gameSettings.smoothCamera = previous[0];
              wrote[0] = false;
            }
          });
      c.listen(
          CameraFovEvent.class,
          e ->
              e.value(
                  (float)
                      (e.value()
                          * Math.min(1, value[0] / Math.max(1, mc().gameSettings.fovSetting)))));
      c.listen(
          MouseScrollEvent.class,
          e -> {
            if (!scroll.get() || mc().currentScreen != null || !mc().inGameHasFocus) return;
            value[0] =
                MathHelper.clamp_double(
                    value[0] - Math.signum(e.delta()) * step.get(),
                    5,
                    Math.min(90, mc().gameSettings.fovSetting));
            e.cancel();
          });
      c.listen(
          OverlayEffectEvent.class,
          e -> {
            if (hand.get() && e.kind() == OverlayEffectEvent.Kind.HAND) e.cancel();
          });
    }
  }
}
