// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.awt.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.render.RenderState;

final class HornsModule extends GameplayModule {

  HornsModule(ClientRuntime r) {
    super(r, "horns", "Horns", "Animated player head cosmetics.", ModuleCategory.RENDER);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    RenderState state = new RenderState();
    c.listen(
        perihelion.core.event.WorldRenderEvent.class,
        e -> {
          try (RenderState ignored = state.open()) {
            render(e.partialTicks());
          }
        });
  }

  public final Setting<Boolean> renderSelf = bool("render-self", "Render Self", true);
  public final Setting<Boolean> renderOthers = bool("render-others", "Render Others", false);
  public final Setting<Boolean> clientColor = bool("client-color", "Client Color", false);
  public final Setting<Boolean> glow = bool("glow", "Glow", true);

  private static final Color DEFAULT_COLOR = new Color(255, 24, 46);

  private static final float SCALE = 0.70F;
  private static final float GLOW_STRENGTH = 1.00F;
  private static final float OPACITY = 0.90F;
  private static final float LINE_WIDTH = 1.40F;
  private static final float SPACING = 0.00F;
  private static final float HEIGHT = 0.00F;
  private static final float FRONT = 0.0F;
  private static final float HEAD_PIVOT_DROP = 0.20F;
  private static final int SEGMENTS = 22;
  private static final double[][] SPINE = {
    {0.020, 0.000},
    {0.240, -0.010},
    {0.330, 0.200},
    {0.290, 0.470}
  };
  private static final double MAX_HALF_WIDTH = 0.115;
  private static final int VERTEX_COUNT = SEGMENTS * 2 + 1;
  private static final float[] VERTEX_X = new float[VERTEX_COUNT];
  private static final float[] VERTEX_Y = new float[VERTEX_COUNT];
  private static final float[] VERTEX_SHADE = new float[VERTEX_COUNT];
  private static final int RIGHT = 1;
  private static final int LEFT = -1;

  private boolean prevLighting;
  private boolean prevCull;
  private boolean prevBlend;
  private boolean prevTexture;
  private boolean prevLineSmooth;
  private boolean prevDepthMask;
  private int prevSrcRgb;
  private int prevDstRgb;
  private int prevSrcAlpha;
  private int prevDstAlpha;

  private void render(float partialTicks) {
    if (mc().theWorld == null || mc().thePlayer == null) {
      return;
    }

    prepareGL();

    try {
      final Color color = clientColor.get() ? new Color(0x6f9bd1) : DEFAULT_COLOR;

      for (EntityPlayer player : mc().theWorld.playerEntities) {
        if (!player.isEntityAlive() || player.isPlayerSleeping()) {
          continue;
        }

        if (player == mc().thePlayer) {
          if (renderSelf.get() && mc().gameSettings.thirdPersonView != 0) {
            drawHorns(player, partialTicks, color);
          }
        } else if (renderOthers.get()) {
          drawHorns(player, partialTicks, color);
        }
      }
    } finally {
      restoreGL();
    }
  }

  private void prepareGL() {
    prevLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
    prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
    prevTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
    prevLineSmooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
    prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    prevSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    prevDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    prevSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    prevDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

    GlStateManager.pushMatrix();
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    GlStateManager.disableLighting();
    GlStateManager.disableCull();
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    GlStateManager.depthMask(false);
  }

  private void restoreGL() {
    GlStateManager.depthMask(prevDepthMask);

    if (prevLineSmooth) {
      GL11.glEnable(GL11.GL_LINE_SMOOTH);
    } else {
      GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_DONT_CARE);
    GL11.glLineWidth(1.0F);

    GlStateManager.tryBlendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcAlpha, prevDstAlpha);

    if (prevBlend) {
      GlStateManager.enableBlend();
    } else {
      GlStateManager.disableBlend();
    }

    if (prevCull) {
      GlStateManager.enableCull();
    } else {
      GlStateManager.disableCull();
    }

    if (prevLighting) {
      GlStateManager.enableLighting();
    } else {
      GlStateManager.disableLighting();
    }

    if (prevTexture) {
      GlStateManager.enableTexture2D();
    } else {
      GlStateManager.disableTexture2D();
    }

    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    GlStateManager.popMatrix();
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
  }

  private void drawHorns(EntityPlayer player, float partialTicks, Color color) {
    double x =
        player.lastTickPosX
            + (player.posX - player.lastTickPosX) * partialTicks
            - mc().getRenderManager().viewerPosX;
    double y =
        player.lastTickPosY
            + (player.posY - player.lastTickPosY) * partialTicks
            - mc().getRenderManager().viewerPosY;
    double z =
        player.lastTickPosZ
            + (player.posZ - player.lastTickPosZ) * partialTicks
            - mc().getRenderManager().viewerPosZ;

    float headYaw =
        player.prevRotationYawHead
            + MathHelper.wrapAngleTo180_float(player.rotationYawHead - player.prevRotationYawHead)
                * partialTicks;

    float headPitch = getHeadPitch(player, partialTicks);

    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, z);
    GlStateManager.rotate(-headYaw, 0.0F, 1.0F, 0.0F);

    GlStateManager.translate(
        0.0F,
        mc().thePlayer.isSneaking()
            ? player.height - 0.2f + HEIGHT - HEAD_PIVOT_DROP
            : player.height + HEIGHT - HEAD_PIVOT_DROP,
        0.0F);
    GlStateManager.rotate(headPitch, 1.0F, 0.0F, 0.0F);

    GlStateManager.translate(0.0F, HEAD_PIVOT_DROP, FRONT);

    if (glow.get()) {
      renderGlow(color);
    }
    renderOutline(color);

    GlStateManager.popMatrix();
  }

  private static float getHeadPitch(EntityPlayer player, float partialTicks) {
    if (player instanceof EntityPlayerSP) {
      EntityPlayerSP local = (EntityPlayerSP) player;
      perihelion.integration.minecraft189.event.ModelRotationEvent previous =
          new perihelion.integration.minecraft189.event.ModelRotationEvent(
              perihelion.core.control.Rotation.server(
                  local.prevRotationYaw, local.prevRotationPitch),
              true);
      perihelion.integration.minecraft189.event.ModelRotationEvent current =
          new perihelion.integration.minecraft189.event.ModelRotationEvent(
              perihelion.core.control.Rotation.server(local.rotationYaw, local.rotationPitch),
              false);
      perihelion.integration.minecraft189.HookBridge.clientEvent(previous);
      perihelion.integration.minecraft189.HookBridge.clientEvent(current);
      return previous.rotation().pitch()
          + (current.rotation().pitch() - previous.rotation().pitch()) * partialTicks;
    }

    return player.prevRotationPitch
        + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
  }

  private void renderGlow(Color color) {
    if (GLOW_STRENGTH <= 0.0F) {
      return;
    }

    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

    GL11.glLineWidth(LINE_WIDTH * 3.0F);
    for (int pass = 0; pass < 2; pass++) {
      colorize(color, 0.10F * GLOW_STRENGTH / (pass + 1));
      drawHornPair(SCALE * (1.25F + pass * 0.30F));
    }

    for (int layer = 3; layer >= 1; layer--) {
      colorize(color, 0.22F * GLOW_STRENGTH / layer);
      drawHornPair(SCALE * (1.0F + layer * 0.08F));
    }

    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
  }

  private void renderOutline(Color color) {
    for (int side = RIGHT; side >= LEFT; side -= 2) {
      GL11.glLineWidth(LINE_WIDTH);
      GL11.glBegin(GL11.GL_LINE_LOOP);
      for (int i = 0; i < VERTEX_COUNT; i++) {
        shade(color, 0.55F + 0.45F * VERTEX_SHADE[i], OPACITY);
        vertex(i, SCALE, side);
      }
      GL11.glEnd();
    }
  }

  private void drawHornPair(float grow) {
    for (int side = RIGHT; side >= LEFT; side -= 2) {
      GL11.glBegin(GL11.GL_LINE_LOOP);
      for (int i = 0; i < VERTEX_COUNT; i++) {
        vertex(i, grow, side);
      }
      GL11.glEnd();
    }
  }

  private void vertex(int index, float scale, float side) {
    GL11.glVertex3f(
        side * (VERTEX_X[index] * scale + SPACING * scale), VERTEX_Y[index] * scale, 0.0F);
  }

  private void colorize(Color color, float alpha) {
    GlStateManager.color(
        color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, alpha);
  }

  private void shade(Color color, float shade, float alpha) {
    GlStateManager.color(
        (color.getRed() / 255.0F) * shade,
        (color.getGreen() / 255.0F) * shade,
        (color.getBlue() / 255.0F) * shade,
        alpha);
  }

  static {
    double[] outerX = new double[SEGMENTS + 1];
    double[] outerY = new double[SEGMENTS + 1];
    double[] innerX = new double[SEGMENTS + 1];
    double[] innerY = new double[SEGMENTS + 1];
    double[] normalX = new double[SEGMENTS + 1];
    double[] normalY = new double[SEGMENTS + 1];

    for (int i = 0; i <= SEGMENTS; i++) {
      double t = (double) i / SEGMENTS;
      double cx = bezier(SPINE, t, 0);
      double cy = bezier(SPINE, t, 1);
      double dx = bezierDerivative(SPINE, t, 0);
      double dy = bezierDerivative(SPINE, t, 1);

      double length = Math.sqrt(dx * dx + dy * dy);
      if (length < 1e-9) {
        length = 1e-9;
      }

      normalX[i] = dy / length;
      normalY[i] = -dx / length;

      double halfWidth = widthAt(t) * MAX_HALF_WIDTH;
      outerX[i] = cx + normalX[i] * halfWidth;
      outerY[i] = cy + normalY[i] * halfWidth;
      innerX[i] = cx - normalX[i] * halfWidth;
      innerY[i] = cy - normalY[i] * halfWidth;
    }

    int index = 0;
    for (int i = 0; i <= SEGMENTS; i++) {
      VERTEX_X[index] = (float) outerX[i];
      VERTEX_Y[index] = (float) outerY[i];
      VERTEX_SHADE[index] = (float) shadeOf(normalX[i], normalY[i]);
      index++;
    }
    for (int i = SEGMENTS - 1; i >= 0; i--) {
      VERTEX_X[index] = (float) innerX[i];
      VERTEX_Y[index] = (float) innerY[i];
      VERTEX_SHADE[index] = (float) shadeOf(-normalX[i], -normalY[i]);
      index++;
    }
  }

  private static double bezier(double[][] points, double t, int component) {
    double u = 1.0 - t;
    return u * u * u * points[0][component]
        + 3.0 * u * u * t * points[1][component]
        + 3.0 * u * t * t * points[2][component]
        + t * t * t * points[3][component];
  }

  private static double bezierDerivative(double[][] points, double t, int component) {
    double u = 1.0 - t;
    return 3.0 * u * u * (points[1][component] - points[0][component])
        + 6.0 * u * t * (points[2][component] - points[1][component])
        + 3.0 * t * t * (points[3][component] - points[2][component]);
  }

  private static double widthAt(double t) {
    return (0.45 + 0.55 * Math.sin(Math.PI * Math.pow(t, 0.8))) * Math.pow(1.0 - t, 0.9);
  }

  private static double shadeOf(double nx, double ny) {
    final double lightX = -0.45;
    final double lightY = 0.89;
    return 0.45 + 0.55 * Math.max(0.0, nx * lightX + ny * lightY);
  }
}
