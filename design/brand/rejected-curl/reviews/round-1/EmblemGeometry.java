package perihelion.integration.minecraft189.branding;

import java.awt.geom.Path2D;

/** Original capture-loop emblem. This geometry is also the source of the external brand assets. */
public final class EmblemGeometry {
  public static final int VIEWBOX = 512;

  private EmblemGeometry() {}

  public static Path2D shape() {
    Path2D p = new Path2D.Double();
    p.moveTo(436, 154);
    p.curveTo(398, 64, 300, 36, 214, 68);
    p.curveTo(97, 111, 51, 227, 90, 339);
    p.curveTo(125, 441, 242, 487, 345, 443);
    p.curveTo(417, 412, 456, 340, 439, 272);
    p.curveTo(426, 211, 379, 166, 318, 165);
    p.curveTo(275, 164, 239, 181, 218, 209);
    p.curveTo(263, 178, 324, 185, 354, 222);
    p.curveTo(385, 260, 383, 317, 345, 351);
    p.curveTo(305, 387, 242, 387, 202, 350);
    p.curveTo(150, 303, 154, 224, 197, 173);
    p.curveTo(253, 106, 349, 87, 436, 154);
    p.closePath();
    return p;
  }
}
