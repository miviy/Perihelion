package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.NameRenderEvent;
import perihelion.integration.minecraft189.event.PlayerAttackEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.render.*;

final class ScreenVisualModules {
  private ScreenVisualModules() {}

  static final class ESP extends GameplayModule {
    enum Mode {
      BOX,
      CORNERS
    }

    private final Setting<Mode> mode;
    private final Setting<Boolean> health, held, chests, self, left, outline, background;
    private final Setting<Double> width, outlineWidth, bgAlpha, itemScale;

    ESP(ClientRuntime r) {
      super(
          r,
          "esp",
          "ESP",
          "Draw projected player boxes, health bars and held-item labels.",
          ModuleCategory.RENDER);
      mode = mode("mode", "Mode", Mode.BOX, Mode.class);
      health = bool("health-bars", "Health Bars", true);
      held = bool("held-item", "Held Item", true);
      chests = bool("chest-esp", "Chest ESP", true);
      self = bool("render-self", "Render Self", true);
      left = bool("health-bar-left-align", "Health Bar Left Align", true);
      outline = bool("esp-outline", "ESP Outline", true);
      background = bool("esp-bg", "ESP BG", true);
      width = number("esp-line-width", "ESP Line Width", 2, 0.1, 4, 0.1);
      outlineWidth = number("esp-outline-width", "ESP Outline Width", 2, 0.1, 4, 0.1);
      bgAlpha = number("esp-bg-alpha", "ESP BG Alpha", 0.2, 0.1, 1, 0.1);
      itemScale = number("held-item-scale", "Held Item Scale", 1, 0.5, 2, 0.1);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      Map<EntityPlayer, double[]> players = new LinkedHashMap<>();
      List<double[]> storage = new ArrayList<>();
      VisualDraw.Projection projection = new VisualDraw.Projection();
      RenderState state = new RenderState();
      c.listen(
          WorldRenderEvent.class,
          e -> {
            players.clear();
            storage.clear();
            projection.capture(mc());
            for (EntityPlayer player : mc().theWorld.playerEntities)
              if (player != mc().thePlayer
                  || self.get() && mc().gameSettings.thirdPersonView != 0) {
                double[] b = projection.box(VisualDraw.bounds(player, e.partialTicks()));
                if (b != null) players.put(player, b);
              }
            if (chests.get())
              for (TileEntity tile : mc().theWorld.loadedTileEntityList)
                if (tile instanceof TileEntityChest
                    && mc().thePlayer.getDistanceSqToCenter(tile.getPos()) <= 300) {
                  AxisAlignedBB box =
                      mc().theWorld
                          .getBlockState(tile.getPos())
                          .getBlock()
                          .getSelectedBoundingBox(mc().theWorld, tile.getPos());
                  double[] b = box == null ? null : projection.box(box);
                  if (b != null) storage.add(b);
                }
          });
      c.listen(
          HudRenderEvent.class,
          e -> {
            try (RenderState ignored = state.open()) {
              for (Map.Entry<EntityPlayer, double[]> entry : players.entrySet())
                draw(entry.getValue(), entry.getKey());
              for (double[] b : storage) draw(b, null);
            }
          });
    }

    private void draw(double[] b, EntityLivingBase player) {
      RenderStyle.lines(true, width.get().floatValue(), VisualDraw.ACCENT);
      if (background.get()) {
        RenderStyle.color((int) (bgAlpha.get() * 255) << 24);
        VisualDraw.rect(b[0], b[1], b[2], b[3]);
      }
      if (outline.get()) {
        RenderStyle.lines(
            true, width.get().floatValue() + outlineWidth.get().floatValue(), 0xff000000);
        VisualDraw.outline(b, mode.get() == Mode.CORNERS);
      }
      RenderStyle.lines(true, width.get().floatValue(), VisualDraw.ACCENT);
      VisualDraw.outline(b, mode.get() == Mode.CORNERS);
      if (player != null && health.get()) {
        double
            ratio =
                MathHelper.clamp_double(
                    player.getHealth() / Math.max(1, player.getMaxHealth()), 0, 1),
            x = left.get() ? b[0] - 5 : b[2] + 3;
        RenderStyle.color(0xff000000);
        VisualDraw.rect(x - 1, b[1] - 1, x + 3, b[3] + 1);
        RenderStyle.color(0x80333333);
        VisualDraw.rect(x, b[1], x + 2, b[3]);
        RenderStyle.color(
            ratio < 0.3
                ? 0xffff3333
                : ratio < 0.5 ? 0xffff9933 : ratio < 0.7 ? 0xffffff33 : 0xff33ff33);
        VisualDraw.rect(x, b[3] - (b[3] - b[1]) * ratio, x + 2, b[3]);
      }
      if (player != null && held.get() && player.getHeldItem() != null) {
        String text = player.getHeldItem().getDisplayName();
        RenderStyle.text();
        GL11.glPushMatrix();
        try {
          GL11.glTranslated((b[0] + b[2]) * 0.5, b[3] + 4, 0);
          GL11.glScaled(itemScale.get(), itemScale.get(), 1);
          mc().fontRendererObj
              .drawStringWithShadow(text, -mc().fontRendererObj.getStringWidth(text) / 2f, 0, -1);
        } finally {
          GL11.glPopMatrix();
        }
      }
    }
  }

  static final class NameTags extends GameplayModule {
    private final Setting<Boolean> self, background;
    private final Setting<Double> opacity, scale;

    NameTags(ClientRuntime r) {
      super(
          r,
          "nametags",
          "Name Tags",
          "Show player names and distances through walls.",
          ModuleCategory.RENDER);
      self = bool("render-self", "Render Self", true);
      background = bool("background", "Background", true);
      opacity = number("background-opacity", "Background Opacity", 0.4, 0, 1, 0.05);
      scale = number("scale", "Scale", 1, 0.5, 2, 0.1);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          NameRenderEvent.class,
          e -> {
            if (e.entity() instanceof EntityPlayer) e.cancel();
          });
      Map<EntityPlayer, double[]> labels = new LinkedHashMap<>();
      VisualDraw.Projection projection = new VisualDraw.Projection();
      RenderState state = new RenderState();
      c.listen(
          WorldRenderEvent.class,
          e -> {
            labels.clear();
            projection.capture(mc());
            for (EntityPlayer player : mc().theWorld.playerEntities)
              if (player != mc().thePlayer
                  || self.get() && mc().gameSettings.thirdPersonView != 0) {
                double[] p =
                    projection.point(
                        VisualDraw.position(player, e.partialTicks())
                            .addVector(0, player.height + 0.2, 0));
                if (p != null) labels.put(player, p);
              }
          });
      c.listen(
          HudRenderEvent.class,
          e -> {
            try (RenderState ignored = state.open()) {
              for (Map.Entry<EntityPlayer, double[]> entry : labels.entrySet()) {
                double[] p = entry.getValue();
                if (p[0] < 0 || p[0] > e.scaledWidth() || p[1] < 0 || p[1] > e.scaledHeight())
                  continue;
                String text =
                    (entry.getKey().hasCustomName()
                            ? entry.getKey().getCustomNameTag()
                            : entry.getKey().getDisplayName().getFormattedText())
                        + " "
                        + String.format(Locale.ROOT, "%.1f", entry.getKey().getHealth())
                        + " §c♥";
                double half = mc().fontRendererObj.getStringWidth(text) / 2.0;
                GL11.glPushMatrix();
                try {
                  GL11.glTranslated(p[0], p[1] - 12 * scale.get(), 0);
                  GL11.glScaled(scale.get(), scale.get(), 1);
                  if (background.get()) {
                    RenderStyle.lines(true, 1, (int) (opacity.get() * 255) << 24);
                    VisualDraw.rect(-half - 2, -2, half + 2, 10);
                  }
                  RenderStyle.text();
                  mc().fontRendererObj.drawStringWithShadow(text, (float) -half, 0, -1);
                } finally {
                  GL11.glPopMatrix();
                }
              }
            }
          });
    }
  }

  static final class TargetESP extends GameplayModule {
    enum Mode {
      POINTS,
      GHOST,
      IMAGE,
      SIGMA
    }

    private final Setting<Mode> mode;
    private final Setting<Boolean> onlyPlayers;
    private final AuraModule aura;

    TargetESP(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "targetesp",
          "Target ESP",
          "Mark the current or recently attacked target.",
          ModuleCategory.RENDER);
      this.aura = aura;
      mode = mode("mark-mode", "Mark Mode", Mode.POINTS, Mode.class);
      onlyPlayers = bool("only-players", "Only Players", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      EntityLivingBase[] target = {null};
      long[] touched = {0};
      RenderState state = new RenderState();
      VisualDraw.Projection projection = new VisualDraw.Projection();
      double[][] screen = {null};
      c.listen(
          PlayerAttackEvent.class,
          e -> {
            if (e.target() instanceof EntityLivingBase
                && (!onlyPlayers.get() || e.target() instanceof EntityPlayer)) {
              target[0] = (EntityLivingBase) e.target();
              touched[0] = now();
            }
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT
                && aura.target() != null
                && (!onlyPlayers.get() || aura.target() instanceof EntityPlayer)) {
              target[0] = aura.target();
              touched[0] = now();
            }
          });
      c.listen(
          WorldRenderEvent.class,
          e -> {
            screen[0] = null;
            if (target[0] == null) return;
            float alpha = MathHelper.clamp_float(1 - (now() - touched[0] - 100) / 400f, 0, 1);
            if (alpha <= 0 || target[0].isDead) {
              target[0] = null;
              return;
            }
            Vec3 p = VisualDraw.position(target[0], e.partialTicks());
            if (mode.get() == Mode.IMAGE) {
              projection.capture(mc());
              screen[0] = projection.point(p.addVector(0, target[0].height * 0.5, 0));
              return;
            }
            try (RenderState ignored = state.open()) {
              RenderStyle.lines(
                  true, 2, ((int) (alpha * 255) << 24) | (VisualDraw.ACCENT & 0xffffff));
              VisualDraw.worldOrigin(mc());
              double t = now() / 200.0;
              if (mode.get() == Mode.SIGMA) {
                double y = p.yCoord + Math.sin(t) + 1;
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                for (int i = 0; i <= 50; i++) {
                  double a = i * Math.PI / 25,
                      x = p.xCoord + Math.cos(a) * 0.67,
                      z = p.zCoord + Math.sin(a) * 0.67;
                  GL11.glColor4f(0.68f, 0.55f, 1, alpha * 0.5f);
                  GL11.glVertex3d(x, y, z);
                  GL11.glColor4f(0.68f, 0.55f, 1, 0);
                  GL11.glVertex3d(x, y - Math.cos(t) * 0.5, z);
                }
                GL11.glEnd();
              } else {
                int strands = mode.get() == Mode.POINTS ? 3 : 1;
                for (int strand = 0; strand < strands; strand++) {
                  GL11.glBegin(GL11.GL_LINE_STRIP);
                  for (int i = 0; i < 45; i++) {
                    double a = (strand % 2 == 0 ? 1 : -1) * t + i * Math.PI / 90;
                    GL11.glColor4f(0.68f, 0.55f, 1, alpha * i / 45);
                    GL11.glVertex3d(
                        p.xCoord + Math.cos(a) * 0.67,
                        p.yCoord + 0.5 + strand * 0.45 + Math.sin(a * 1.2) * 0.1,
                        p.zCoord + Math.sin(a) * 0.67);
                  }
                  GL11.glEnd();
                }
              }
            }
          });
      c.listen(
          HudRenderEvent.class,
          e -> {
            if (screen[0] == null || mode.get() != Mode.IMAGE) return;
            try (RenderState ignored = state.open()) {
              RenderStyle.lines(true, 2, VisualDraw.ACCENT);
              GL11.glTranslated(screen[0][0], screen[0][1], 0);
              GL11.glRotated((now() % 7200) / 20.0, 0, 0, 1);
              VisualDraw.outline(new double[] {-24, -24, 24, 24}, true);
            }
          });
    }
  }
}
