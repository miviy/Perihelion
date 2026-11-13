package perihelion.integration.minecraft189.branding;

import java.awt.geom.Path2D;

/** Original capture-loop emblem. This geometry is also the source of the external brand assets. */
public final class EmblemGeometry {
  public static final int VIEWBOX = 512;

  private EmblemGeometry() {}

  public static Path2D shape() {
    Path2D p = new Path2D.Double();
    p.moveTo(428, 139);
    p.curveTo(360, 53, 234, 47, 144, 118);
    p.curveTo(50, 191, 51, 329, 137, 407);
    p.curveTo(218, 482, 348, 463, 412, 380);
    p.curveTo(460, 317, 452, 239, 402, 196);
    p.curveTo(367, 166, 319, 169, 285, 190);
    p.curveTo(323, 182, 356, 211, 365, 243);
    p.curveTo(381, 298, 326, 352, 274, 352);
    p.curveTo(210, 352, 166, 298, 176, 238);
    p.curveTo(190, 151, 310, 105, 408, 165);
    p.lineTo(428, 139);
    p.closePath();
    p.transform(java.awt.geom.AffineTransform.getRotateInstance(-.48, 256, 256));
    return p;
  }
}
