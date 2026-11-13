package perihelion.integration.minecraft189.gui.menu;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.branding.BrandArtwork;
import perihelion.integration.minecraft189.gui.font.MenuLetterforms;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Bespoke wordmark and shared gravitational-lens emblem, owned by the title screen. */
final class MenuWordmark implements AutoCloseable {
  private static final float NAME_Y = 144, EMBLEM_Y = 62;
  private ResourceLocation texture;
  private int width, height;

  /** Align the whole lockup to the controls' center, with a stable lower edge. */
  void drawCentered(float centerX, float bottom, float seconds) {
    if (texture == null) initialize();
    float scale = Math.min(Math.min(164f / width, 58f / height), Math.max(1, bottom - 6) / height);
    float x = centerX - width * scale / 2, y = bottom - height * scale;
    RenderStyle.text();
    Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
    GL11.glBegin(GL11.GL_QUADS);
    try {
      GL11.glTexCoord2f(0, 0);
      GL11.glVertex2f(x, y);
      GL11.glTexCoord2f(0, 1);
      GL11.glVertex2f(x, bottom);
      GL11.glTexCoord2f(1, 1);
      GL11.glVertex2f(x + width * scale, bottom);
      GL11.glTexCoord2f(1, 0);
      GL11.glVertex2f(x + width * scale, y);
    } finally {
      GL11.glEnd();
    }
  }

  private void initialize() {
    Path2D letters = new Path2D.Float();
    float cursor = 0;
    for (char letter : "PERIHELION".toCharArray()) {
      Area glyph = MenuLetterforms.glyph(letter);
      letters.append(
          AffineTransform.getTranslateInstance(cursor, NAME_Y).createTransformedShape(glyph),
          false);
      cursor += (float) glyph.getBounds2D().getWidth() + 13;
    }
    Rectangle2D ink = letters.getBounds2D();
    width = (int) Math.ceil(ink.getWidth()) + 24;
    height = (int) NAME_Y + 88;
    double nameX = (width - ink.getWidth()) / 2;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setPaint(finish(NAME_Y, NAME_Y + 80));
      g.fill(AffineTransform.getTranslateInstance(nameX, 0).createTransformedShape(letters));

      BrandArtwork.Raster emblem = BrandArtwork.primary();
      double emblemHeight = Math.min(104, 140 / emblem.aspect());
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      emblem.draw(
          g,
          (width - emblemHeight * emblem.aspect()) / 2,
          EMBLEM_Y - emblemHeight / 2,
          emblemHeight);
    } finally {
      g.dispose();
    }
    texture =
        Minecraft.getMinecraft()
            .getTextureManager()
            .getDynamicTextureLocation("perihelion_wordmark", new DynamicTexture(image));
    Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
  }

  private static LinearGradientPaint finish(float top, float bottom) {
    return new LinearGradientPaint(
        0,
        top,
        0,
        bottom,
        new float[] {0, .43f, 1},
        new Color[] {new Color(0xf4d3a2), new Color(0xeee9de), new Color(0xa8b0be)});
  }

  @Override
  public void close() {
    if (texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
    texture = null;
  }
}
