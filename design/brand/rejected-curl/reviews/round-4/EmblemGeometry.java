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
    p.curveTo(426, 214, 379, 218, 368, 256);
    p.curveTo(365, 269, 365, 284, 370, 294);
    p.curveTo(375, 267, 396, 271, 389, 299);
    p.curveTo(376, 354, 288, 390, 232, 364);
    p.curveTo(163, 332, 143, 257, 176, 202);
    p.curveTo(222, 125, 333, 118, 410, 185);
    p.lineTo(442, 167);
    p.closePath();
    java.awt.geom.AffineTransform tilt = new java.awt.geom.AffineTransform();
    tilt.translate(256, 256); tilt.rotate(-.48); tilt.scale(1, .90); tilt.translate(-256, -256);
    p.transform(tilt);
    return p;
  }
}
