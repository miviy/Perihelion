package perihelion.integration.minecraft189.gui.modern;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Original category symbols rasterized at their destination framebuffer size. */
final class CategoryArtwork {
  static final int COUNT = 8;

  private CategoryArtwork() {}

  static BufferedImage atlas(int cell) {
    if (cell < 8 || cell > 512) throw new IllegalArgumentException("Invalid category icon size");
    BufferedImage image = new BufferedImage(cell * COUNT, cell, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(Color.WHITE);
      // Leave a consistent optical inset; at 24px most strokes are two physical pixels.
      g.scale(cell / 24.0, cell / 24.0);
      for (int i = 0; i < COUNT; i++) {
        AffineTransform saved = g.getTransform();
        g.translate(i * 24, 0);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        symbol(g, i);
        g.setTransform(saved);
      }
    } finally {
      g.dispose();
    }
    // Linear sampling must interpolate coverage, not black RGB from transparent pixels.
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    for (int i = 0; i < pixels.length; i++) pixels[i] |= 0x00ffffff;
    return image;
  }

  private static Path2D.Float path(boolean close, float... points) {
    Path2D.Float shape = new Path2D.Float();
    shape.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) shape.lineTo(points[i], points[i + 1]);
    if (close) shape.closePath();
    return shape;
  }

  private static void symbol(Graphics2D g, int id) {
    switch (id) {
      case 0:
        // A broad blade, crossguard and grip remain recognizable at a small size.
        g.fill(path(true, 5, 3, 9, 4, 17.3f, 12.4f, 13.2f, 16.5f, 4.8f, 8.2f));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path(false, 12, 18, 18, 12));
        g.draw(path(false, 16, 16, 20, 20));
        break;
      case 1:
        // A forward-leaning torso separates both arms and both bent legs.
        g.fill(new Ellipse2D.Float(12.4f, 2.3f, 4.5f, 4.5f));
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path(false, 6, 10, 10.2f, 7.7f, 14, 10.5f, 19, 11));
        g.draw(path(false, 12.4f, 9.3f, 10.3f, 14.4f, 5, 20.5f));
        g.draw(path(false, 10.7f, 14.2f, 16, 16.8f, 16, 21));
        break;
      case 2:
        g.draw(new Ellipse2D.Float(8.2f, 3, 7.6f, 7.6f));
        Path2D.Float bust = new Path2D.Float();
        bust.moveTo(4, 21);
        bust.lineTo(4, 18.4);
        bust.curveTo(4, 11.7, 20, 11.7, 20, 18.4);
        bust.lineTo(20, 21);
        bust.closePath();
        g.draw(bust);
        break;
      case 3:
        g.draw(new Ellipse2D.Float(3, 3, 18, 18));
        g.draw(new Ellipse2D.Float(8, 3, 8, 18));
        g.draw(path(false, 3, 12, 21, 12));
        break;
      case 4:
        // The eye is almond-shaped, with one solid pupil rather than nested ovals.
        Path2D.Float eye = new Path2D.Float();
        eye.moveTo(2.5, 12);
        eye.curveTo(7.6, 4, 16.4, 4, 21.5, 12);
        eye.curveTo(16.4, 20, 7.6, 20, 2.5, 12);
        eye.closePath();
        g.draw(eye);
        g.fill(new Ellipse2D.Float(8.7f, 8.7f, 6.6f, 6.6f));
        break;
      case 5:
        // Eight squared teeth and a real central cutout make this read as a gear.
        Area gear = new Area(new Ellipse2D.Float(5, 5, 14, 14));
        for (int i = 0; i < 8; i++) {
          Area tooth = new Area(new RoundRectangle2D.Float(10, 2.2f, 4, 7, .7f, .7f));
          tooth.transform(AffineTransform.getRotateInstance(i * Math.PI / 4, 12, 12));
          gear.add(tooth);
        }
        gear.subtract(new Area(new Ellipse2D.Float(8.6f, 8.6f, 6.8f, 6.8f)));
        g.fill(gear);
        break;
      case 6:
        g.draw(path(true, 5, 3, 14, 3, 19, 8, 19, 21, 5, 21));
        g.draw(path(false, 14, 3.5f, 14, 8, 18.5f, 8));
        g.draw(path(false, 9, 12, 15, 12));
        g.draw(path(false, 9, 16, 15, 16));
        break;
      default:
        g.draw(path(false, 7.5f, 6, 2.5f, 12, 7.5f, 18));
        g.draw(path(false, 16.5f, 6, 21.5f, 12, 16.5f, 18));
        g.draw(path(false, 14, 3, 10, 21));
    }
  }
}
