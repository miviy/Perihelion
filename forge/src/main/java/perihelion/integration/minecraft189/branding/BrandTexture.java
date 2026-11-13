package perihelion.integration.minecraft189.branding;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Lazy, renderer-owned brand textures. Call draw inside the owner's RenderState scope. */
public final class BrandTexture implements AutoCloseable {
  private final Artwork emblem = new Artwork();
  private final Artwork name = new Artwork(BrandArtwork.name(), "name");

  public float emblemWidth(float height) {
    return emblem.aspect * height;
  }

  public float nameWidth(float height) {
    return name.aspect * height;
  }

  public void emblem(float x, float y, float height, int color) {
    emblem.draw(x, y, height, color);
  }

  public void name(float x, float y, float height, int color) {
    name.draw(x, y, height, color);
  }

  @Override
  public void close() {
    emblem.close();
    name.close();
  }

  private static final class Artwork implements AutoCloseable {
    private static final int RASTER_HEIGHT = 128, PAD = 2;
    private final Shape shape;
    private final BrandArtwork.Raster raster;
    private final String id;
    private final float aspect;
    private ResourceLocation texture;
    private int width;

    Artwork(Shape shape, String id) {
      this.shape = BrandArtwork.fitHeight(shape, RASTER_HEIGHT);
      raster = null;
      this.id = id;
      aspect = (float) (this.shape.getBounds2D().getWidth() / RASTER_HEIGHT);
    }

    Artwork() {
      shape = null;
      raster = BrandArtwork.primary();
      id = "emblem";
      aspect = (float) raster.aspect();
    }

    void draw(float x, float y, float height, int color) {
      if (texture == null) initialize();
      float scale = height / RASTER_HEIGHT;
      float left = x - PAD * scale, top = y - PAD * scale;
      RenderStyle.text();
      Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
      GlStateManager.color(
          raster == null ? ((color >> 16) & 255) / 255f : 1,
          raster == null ? ((color >> 8) & 255) / 255f : 1,
          raster == null ? (color & 255) / 255f : 1,
          ((color >>> 24) & 255) / 255f);
      GL11.glBegin(GL11.GL_QUADS);
      try {
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(left, top);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(left, top + (RASTER_HEIGHT + PAD * 2) * scale);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(left + width * scale, top + (RASTER_HEIGHT + PAD * 2) * scale);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(left + width * scale, top);
      } finally {
        GL11.glEnd();
      }
    }

    private void initialize() {
      width = (int) Math.ceil(aspect * RASTER_HEIGHT) + PAD * 2;
      BufferedImage image =
          new BufferedImage(width, RASTER_HEIGHT + PAD * 2, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.translate(PAD, PAD);
        if (raster != null) raster.draw(g, 0, 0, RASTER_HEIGHT);
        else {
          g.setColor(Color.WHITE);
          g.fill(shape);
        }
      } finally {
        g.dispose();
      }
      Minecraft mc = Minecraft.getMinecraft();
      texture =
          mc.getTextureManager()
              .getDynamicTextureLocation("perihelion_brand_" + id, new DynamicTexture(image));
      mc.getTextureManager().bindTexture(texture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    @Override
    public void close() {
      if (texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
      texture = null;
    }
  }
}
