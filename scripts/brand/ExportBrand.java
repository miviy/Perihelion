package perihelion.tools;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import perihelion.integration.minecraft189.branding.BrandArtwork;
import perihelion.integration.minecraft189.branding.EmblemGeometry;

/** Reproducible brand exports. Uses the exact primary image and wordmark used by the client. */
public final class ExportBrand {
  static final Color DARK = new Color(0x0b1017),
      IVORY = new Color(0xece6da),
      COPPER = new Color(0xd1ac76);

  public static void main(String[] args) throws Exception {
    Path out = Paths.get(args[0]);
    Files.createDirectories(out);
    Shape mark = EmblemGeometry.shape();
    write(out, "mark.svg", svg(mark, "0 0 512 512", "currentColor"));
    for (String variant : new String[] {"white", "black", "ivory", "copper"}) {
      Color color = color(variant);
      write(out, "mark-" + variant + ".svg", svg(mark, "0 0 512 512", hex(color)));
      for (int size : new int[] {16, 24, 32, 48, 64, 128, 256, 512, 1024}) {
        BufferedImage img = image(size, size);
        Graphics2D g = graphics(img);
        vector(g, mark, 0, 0, size, color);
        g.dispose();
        save(out, "mark-" + variant + "-" + size + ".png", img);
      }
    }
    for (int size : new int[] {32, 64, 128, 256, 512, 1024}) {
      BufferedImage img = image(size, size);
      Graphics2D g = graphics(img);
      primaryCentered(g, size / 2.0, size / 2.0, size * .60);
      g.dispose();
      save(out, "emblem-primary-" + size + ".png", img);
      for (String tone : new String[] {"copper", "ivory"}) {
        BufferedImage avatar = image(size, size);
        g = graphics(avatar);
        g.setColor(DARK);
        g.fillRect(0, 0, size, size);
        if (tone.equals("copper")) primaryCentered(g, size / 2.0, size / 2.0, size * .60);
        else vector(g, mark, 0, 0, size, IVORY);
        g.dispose();
        save(out, "avatar-" + tone + "-" + size + ".png", avatar);
      }
    }
    Shape name = BrandArtwork.fitHeight(BrandArtwork.name(), 80);
    Shape icon = BrandArtwork.fitHeight(mark, 130);
    double nameX = icon.getBounds2D().getWidth() + 32;
    Path2D lockup = new Path2D.Double();
    lockup.append(icon, false);
    lockup.append(
        AffineTransform.getTranslateInstance(nameX, 25).createTransformedShape(name), false);
    Rectangle2D b = lockup.getBounds2D();
    write(
        out,
        "lockup-horizontal-ivory.svg",
        svg(lockup, "-24 -24 " + (b.getWidth() + 48) + " 178", hex(IVORY)));
    write(
        out,
        "lockup-horizontal-black.svg",
        svg(lockup, "-24 -24 " + (b.getWidth() + 48) + " 178", hex(color("black"))));
    BrandArtwork.Raster primary = BrandArtwork.primary();
    double h = 160, w = primary.aspect() * h;
    Shape text = BrandArtwork.fitHeight(BrandArtwork.name(), 96);
    BufferedImage horizontal = image((int) Math.ceil(96 + w + text.getBounds2D().getWidth()), 240);
    Graphics2D g = graphics(horizontal);
    primary.draw(g, 32, 40, h);
    g.setColor(IVORY);
    g.fill(AffineTransform.getTranslateInstance(32 + w + 32, 72).createTransformedShape(text));
    g.dispose();
    save(out, "lockup-horizontal-primary.png", horizontal);
    BufferedImage stacked = image(1200, 480);
    g = graphics(stacked);
    primaryCentered(g, 600, 140, 190);
    wordmark(g, 600, 295, 130);
    g.dispose();
    save(out, "lockup-stacked-primary.png", stacked);
    board(out);
  }

  static Color color(String v) {
    return v.equals("white")
        ? Color.WHITE
        : v.equals("black") ? new Color(0x111820) : v.equals("ivory") ? IVORY : COPPER;
  }

  static String hex(Color c) {
    return String.format(Locale.ROOT, "#%06x", c.getRGB() & 0xffffff);
  }

  static BufferedImage image(int w, int h) {
    return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
  }

  static Graphics2D graphics(BufferedImage image) {
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    return g;
  }

  static void save(Path out, String name, BufferedImage image) throws Exception {
    ImageIO.write(image, "png", out.resolve(name).toFile());
  }

  static void write(Path out, String name, String text) throws Exception {
    Files.write(out.resolve(name), text.getBytes(StandardCharsets.UTF_8));
  }

  static void vector(Graphics2D g, Shape shape, double x, double y, double size, Color color) {
    AffineTransform t = new AffineTransform();
    t.translate(x, y);
    t.scale(size / 512, size / 512);
    g.setColor(color);
    g.fill(t.createTransformedShape(shape));
  }

  static void primaryCentered(Graphics2D g, double x, double y, double height) {
    BrandArtwork.Raster a = BrandArtwork.primary();
    a.draw(g, x - height * a.aspect() / 2, y - height / 2, height);
  }

  static String path(Shape shape) {
    StringBuilder d = new StringBuilder();
    double[] c = new double[6];
    PathIterator it = shape.getPathIterator(null);
    while (!it.isDone()) {
      int k = it.currentSegment(c);
      d.append(k == 0 ? 'M' : k == 1 ? 'L' : k == 2 ? 'Q' : k == 3 ? 'C' : 'Z');
      int n = k == 0 || k == 1 ? 2 : k == 2 ? 4 : k == 3 ? 6 : 0;
      for (int i = 0; i < n; i++)
        d.append(String.format(Locale.ROOT, "%.4f", c[i])).append(i == n - 1 ? ' ' : ',');
      it.next();
    }
    return d.toString();
  }

  static String svg(Shape shape, String box, String fill) {
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\""
        + box
        + "\" fill=\""
        + fill
        + "\" role=\"img\" aria-label=\"Perihelion\"><title>Perihelion</title><path d=\""
        + path(shape)
        + "\"/></svg>\n";
  }

  static void label(Graphics2D g, String text, int x, int y, int size, Color color) {
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
    g.setColor(color);
    g.drawString(text, x, y);
  }

  static void wordmark(Graphics2D g, double center, double top, double height) {
    Shape name = BrandArtwork.fitHeight(BrandArtwork.name(), height);
    g.setColor(IVORY);
    g.fill(
        AffineTransform.getTranslateInstance(center - name.getBounds2D().getWidth() / 2, top)
            .createTransformedShape(name));
  }

  static void board(Path out) throws Exception {
    BufferedImage img = image(1600, 1120);
    Graphics2D g = graphics(img);
    g.setColor(DARK);
    g.fillRect(0, 0, 1600, 1120);
    label(g, "PERIHELION / B", 55, 58, 23, IVORY);
    label(g, "PRIMARY + MONOCHROME", 1110, 58, 18, COPPER);
    g.setColor(new Color(0xf0ede6));
    g.fillRoundRect(40, 90, 740, 450, 16, 16);
    g.setColor(new Color(0x121a24));
    g.fillRoundRect(810, 90, 750, 450, 16, 16);
    vector(g, EmblemGeometry.shape(), 190, 80, 450, color("black"));
    primaryCentered(g, 1185, 305, 300);
    label(g, "SINGLE-COLOR COMPANION", 70, 515, 16, new Color(0x61605d));
    label(g, "PRIMARY / COPPER", 840, 515, 16, new Color(0x9fa8b5));
    label(g, "PRIMARY / ACTUAL VISIBLE HEIGHT", 55, 588, 18, IVORY);
    int x = 70;
    for (int size : new int[] {16, 20, 24, 32, 48, 64}) {
      g.setColor(new Color(0x141c26));
      g.fillRoundRect(x - 14, 614, 122, 122, 10, 10);
      primaryCentered(g, x + 46, 663, size);
      label(g, size + " px", x + 20, 718, 15, new Color(0xaeb6c1));
      x += 155;
    }
    label(g, "AVATAR / CIRCULAR CROP", 1050, 588, 18, IVORY);
    Shape clip = g.getClip();
    g.clip(new Ellipse2D.Double(1115, 612, 160, 160));
    g.setColor(DARK);
    g.fillRect(1115, 612, 160, 160);
    primaryCentered(g, 1195, 692, 96);
    g.setClip(clip);
    label(g, "MENU LOCKUP", 55, 810, 18, IVORY);
    primaryCentered(g, 205, 881, 64);
    wordmark(g, 205, 930, 35);
    label(g, "HUD / WATERMARK", 660, 810, 18, IVORY);
    g.setColor(new Color(0x30463f));
    g.fillRoundRect(650, 840, 880, 200, 10, 10);
    BrandArtwork.primary().draw(g, 678, 866, 32);
    wordmark(g, 842, 870, 24);
    BrandArtwork.primary().draw(g, 680, 925, 20);
    label(g, "Compact watermark proof", 722, 950, 18, new Color(0xc3cec7));
    g.dispose();
    save(out, "proof.png", img);
  }
}
