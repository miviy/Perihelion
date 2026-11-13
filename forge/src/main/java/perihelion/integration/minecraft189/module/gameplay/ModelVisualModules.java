package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import org.lwjgl.input.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.render.*;

final class ModelVisualModules {
  private ModelVisualModules() {}

  static final class FreeLook extends GameplayModule {
    private final Setting<Boolean> invert, hold;

    FreeLook(ClientRuntime r) {
      super(
          r,
          "freelook",
          "Free Look",
          "Rotate the third-person camera independently, using a hold or toggle binding.",
          ModuleCategory.RENDER);
      invert = bool("invert-pitch", "Invert Pitch", false);
      hold = bool("hold", "Hold", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      perihelion.core.control.ControlRequest<perihelion.core.control.CameraPerspective>
          perspective = c.controls().perspective(100);
      FreeLookAngles camera =
          new FreeLookAngles(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch);
      if (!active("freecam"))
        perspective.set(perihelion.core.control.CameraPerspective.THIRD_PERSON_BACK);
      c.onCleanup(
          () -> {
            if (perihelion.integration.minecraft189.HookBridge.gameControls() != null)
              perihelion.integration.minecraft189.HookBridge.gameControls().apply();
          });
      c.listen(
          MouseLookEvent.class,
          e -> {
            if (active("freecam")) return;
            camera.mouse(e.yaw(), e.pitch(), invert.get());
            e.cancel();
          });
      c.listen(
          CameraViewEvent.class,
          e -> {
            if (!active("freecam"))
              e.angles(camera.yaw(e.partialTicks()), camera.pitch(e.partialTicks()));
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            Keybind binding = runtime.modules().require("freelook").binding();
            boolean released =
                binding.device() == Keybind.Device.KEYBOARD
                    ? !Keyboard.isCreated() || !Keyboard.isKeyDown(binding.code())
                    : binding.device() == Keybind.Device.MOUSE
                        && (!Mouse.isCreated() || !Mouse.isButtonDown(binding.code()));
            if (active("freecam")) {
              perspective.clear();
              return;
            }
            if (hold.get() && mc().currentScreen == null && released) c.requestDisable();
            else
              perspective.setForTicks(
                  perihelion.core.control.CameraPerspective.THIRD_PERSON_BACK, 1);
          });
    }
  }

  static final class Chams extends GameplayModule {
    private final Setting<Boolean> tileEntities;

    Chams(ClientRuntime r) {
      super(
          r,
          "chams",
          "Chams",
          "Render occluded player models through walls.",
          ModuleCategory.RENDER);
      tileEntities = bool("tile-entities", "Tile Entities", false);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          TileRenderEvent.class,
          e -> {
            if (tileEntities.get()) e.cancel();
          });
      c.listen(
          ModelRenderEvent.class,
          e -> {
            if (e.entity() instanceof EntityPlayer
                && !mc().thePlayer.canEntityBeSeen(e.entity())
                && (e.entity() != mc().thePlayer || mc().gameSettings.thirdPersonView != 0))
              e.cancel();
          });
    }
  }

  static final class Skeletons extends GameplayModule {
    private final Setting<Boolean> self, color;
    private final Setting<Double> width;

    Skeletons(ClientRuntime r) {
      super(
          r,
          "skeletons",
          "Skeletons",
          "Draw pose-matched player skeletons through walls.",
          ModuleCategory.RENDER);
      self = bool("render-self", "Render Self", true);
      color = bool("use-client-colors", "Use Client Colors", false);
      width = number("skeletons-width", "Skeletons Width", 1, 0.5, 5, 0.1);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      Map<EntityPlayer, float[][]> poses = new WeakHashMap<>();
      c.onCleanup(poses::clear);
      RenderState state = new RenderState();
      c.listen(
          ModelPoseEvent.class,
          e -> {
            ModelRenderer[] parts = {
              e.model().bipedHead,
              e.model().bipedRightArm,
              e.model().bipedLeftArm,
              e.model().bipedRightLeg,
              e.model().bipedLeftLeg
            };
            float[][] angles = poses.computeIfAbsent(e.player(), ignored -> new float[5][3]);
            for (int i = 0; i < parts.length; i++) {
              angles[i][0] = parts[i].rotateAngleX;
              angles[i][1] = parts[i].rotateAngleY;
              angles[i][2] = parts[i].rotateAngleZ;
            }
          });
      c.listen(
          WorldRenderEvent.class,
          e -> {
            poses
                .keySet()
                .removeIf(p -> p.isDead || mc().theWorld.getEntityByID(p.getEntityId()) != p);
            try (RenderState ignored = state.open()) {
              RenderStyle.lines(
                  true, width.get().floatValue(), color.get() ? VisualDraw.ACCENT : 0xffffffff);
              VisualDraw.worldOrigin(mc());
              for (EntityPlayer p : mc().theWorld.playerEntities) {
                if (p.isInvisible()
                    || !p.isEntityAlive()
                    || p.isPlayerSleeping()
                    || p == mc().thePlayer
                        && (!self.get() || mc().gameSettings.thirdPersonView == 0)) continue;
                float[][] angles = poses.get(p);
                if (angles == null) continue;
                Vec3 point = VisualDraw.position(p, e.partialTicks());
                GL11.glPushMatrix();
                try {
                  GL11.glTranslated(point.xCoord, point.yCoord, point.zCoord);
                  float yaw =
                      p.prevRenderYawOffset
                          + MathHelper.wrapAngleTo180_float(
                                  p.renderYawOffset - p.prevRenderYawOffset)
                              * e.partialTicks();
                  GL11.glRotated(-yaw, 0, 1, 0);
                  double legs = p.isSneaking() ? 0.6 : 0.75;
                  if (p.isSneaking()) GL11.glTranslated(0, 0, -0.235);
                  limb(-0.125, legs, 0, angles[3], -legs);
                  limb(0.125, legs, 0, angles[4], -legs);
                  if (p.isSneaking()) GL11.glTranslated(0, -0.05, 0.23275);
                  limb(-0.375, legs + 0.55, 0, angles[1], -0.5);
                  limb(0.375, legs + 0.55, 0, angles[2], -0.5);
                  GL11.glPushMatrix();
                  GL11.glRotated(
                      yaw
                          - (p.prevRotationYawHead
                              + MathHelper.wrapAngleTo180_float(
                                      p.rotationYawHead - p.prevRotationYawHead)
                                  * e.partialTicks()),
                      0,
                      1,
                      0);
                  limb(0, legs + 0.55, 0, new float[] {angles[0][0], 0, 0}, 0.3);
                  GL11.glPopMatrix();
                  if (p.isSneaking()) {
                    GL11.glRotated(25, 1, 0, 0);
                    GL11.glTranslated(0, -0.16175, -0.48025);
                  }
                  GL11.glBegin(GL11.GL_LINES);
                  GL11.glVertex3d(-0.125, legs, 0);
                  GL11.glVertex3d(0.125, legs, 0);
                  GL11.glVertex3d(0, legs, 0);
                  GL11.glVertex3d(0, legs + 0.55, 0);
                  GL11.glVertex3d(-0.375, legs + 0.55, 0);
                  GL11.glVertex3d(0.375, legs + 0.55, 0);
                  GL11.glEnd();
                } finally {
                  GL11.glPopMatrix();
                }
              }
            }
          });
    }

    private void limb(double x, double y, double z, float[] angles, double length) {
      GL11.glPushMatrix();
      try {
        GL11.glTranslated(x, y, z);
        GL11.glRotated(Math.toDegrees(angles[0]), 1, 0, 0);
        GL11.glRotated(Math.toDegrees(angles[1]), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(angles[2]), 0, 0, 1);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(0, 0, 0);
        GL11.glVertex3d(0, length, 0);
        GL11.glEnd();
      } finally {
        GL11.glPopMatrix();
      }
    }
  }
}
