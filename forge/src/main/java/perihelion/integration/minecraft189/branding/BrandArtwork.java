package perihelion.integration.minecraft189.branding;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import perihelion.integration.minecraft189.gui.font.MenuLetterforms;

/** Shared brand artwork. Layout uses visible ink, independent of export clear space. */
public final class BrandArtwork {
  private BrandArtwork() {}

  public static Raster primary() {
    return Primary.ARTWORK;
  }

  private static final class Primary {
    static final Raster ARTWORK = new Raster();
  }

  /** The original transparent master, sampled once and reused by renderer-owned textures. */
  public static final class Raster {
    private final BufferedImage image;
    private final Rectangle2D bounds;

    private Raster() {
      String resource = "/assets/perihelion/brand/emblem-copper.png";
      try (InputStream input = BrandArtwork.class.getResourceAsStream(resource)) {
        if (input == null) throw new IOException("Missing " + resource);
        image = ImageIO.read(input);
        if (image == null) throw new IOException("Invalid " + resource);
      } catch (IOException error) {
        throw new IllegalStateException("Cannot load Perihelion emblem", error);
      }
      int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < image.getWidth(); x++) {
          if ((image.getRGB(x, y) >>> 24) < 160) continue;
          left = Math.min(left, x);
          top = Math.min(top, y);
          right = Math.max(right, x);
          bottom = Math.max(bottom, y);
        }
      }
      if (right < left) throw new IllegalStateException("Empty Perihelion emblem");
      bounds = new Rectangle2D.Double(left, top, right - left + 1, bottom - top + 1);
    }

    public double aspect() {
      return bounds.getWidth() / bounds.getHeight();
    }

    public void draw(Graphics2D g, double x, double y, double height) {
      double scale = height / bounds.getHeight();
      AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
      transform.scale(scale, scale);
      transform.translate(-bounds.getX(), -bounds.getY());
      g.drawImage(image, transform, null);
    }
  }

  public static Shape emblem() {
    return new Area(EmblemGeometry.shape());
  }

  public static Shape name() {
    Path2D path = new Path2D.Double();
    double cursor = 0;
    for (char letter : "PERIHELION".toCharArray()) {
      Area glyph = MenuLetterforms.glyph(letter);
      path.append(
          AffineTransform.getTranslateInstance(cursor, 0).createTransformedShape(glyph), false);
      cursor += glyph.getBounds2D().getWidth() + 13;
    }
    return path;
  }

  public static Shape fitHeight(Shape shape, double height) {
    Rectangle2D bounds = shape.getBounds2D();
    double scale = height / bounds.getHeight();
    AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
    transform.translate(-bounds.getX(), -bounds.getY());
    return transform.createTransformedShape(shape);
  }
}
