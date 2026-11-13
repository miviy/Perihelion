package perihelion.integration.minecraft189.branding;

import java.awt.geom.Path2D;

/** Original capture-loop emblem. This geometry is also the source of the external brand assets. */
public final class EmblemGeometry {
  public static final int VIEWBOX = 512;

  private EmblemGeometry() {}

  public static Path2D shape() {
    Path2D p = new Path2D.Double();
    p.moveTo(442, 167);
    p.curveTo(385, 79, 275, 48, 181, 88);
    p.curveTo(66, 135, 37, 263, 103, 366);
    p.curveTo(165, 463, 296, 479, 393, 407);
    p.curveTo(449, 365, 464, 306, 447, 255);
    p.curveTo(428, 226, 381, 220, 329, 252);
    p.curveTo(372, 241, 395, 276, 378, 314);
    p.curveTo(355, 368, 288, 390, 232, 364);
    p.curveTo(163, 332, 143, 257, 176, 202);
    p.curveTo(222, 125, 333, 118, 410, 185);
    p.lineTo(442, 167);
    p.closePath();
    java.awt.geom.AffineTransform tilt = new java.awt.geom.AffineTransform();
    tilt.translate(256, 256); tilt.rotate(-.18); tilt.scale(1, .81); tilt.translate(-256, -256);
    p.transform(tilt);
    return p;
  }
}
