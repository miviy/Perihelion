package perihelion.integration.minecraft189.branding;

import java.awt.geom.Path2D;

/** Eclipsed core and asymmetric gravitational lens; shared by client and brand exports. */
public final class EmblemGeometry {
  public static final int VIEWBOX = 512;
  private EmblemGeometry() {}

  public static Path2D shape() {
    Path2D p = new Path2D.Double();
    // Upper lens: a broad cap folds into the incoming light path.
    p.moveTo(75, 290);
    p.curveTo(95, 273, 96, 251, 99, 226);
    p.curveTo(111, 134, 180, 70, 263, 70);
    p.curveTo(326, 70, 382, 104, 422, 149);
    p.curveTo(357, 114, 282, 120, 225, 141);
    p.curveTo(171, 161, 151, 198, 141, 237);
    p.curveTo(135, 256, 112, 275, 75, 290);
    p.closePath();
    // The visible core is eclipsed by one diagonal light passage.
    p.moveTo(188, 264);
    p.curveTo(181, 231, 197, 195, 226, 181);
    p.curveTo(264, 163, 307, 183, 320, 220);
    p.curveTo(323, 228, 325, 237, 325, 245);
    p.curveTo(280, 261, 232, 269, 188, 264);
    p.closePath();
    p.moveTo(210, 304);
    p.curveTo(250, 300, 292, 282, 329, 266);
    p.curveTo(319, 306, 286, 332, 255, 328);
    p.curveTo(238, 327, 220, 317, 210, 304);
    p.closePath();
    // Quieter lower lens, displaced from the cap rather than mirrored.
    p.moveTo(105, 337);
    p.curveTo(175, 365, 247, 359, 304, 332);
    p.curveTo(366, 303, 390, 255, 401, 231);
    p.lineTo(438, 212);
    p.curveTo(426, 232, 426, 251, 424, 276);
    p.curveTo(416, 363, 348, 413, 263, 416);
    p.curveTo(199, 418, 141, 395, 105, 337);
    p.closePath();
    return p;
  }
}
