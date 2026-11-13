package perihelion.integration.minecraft189.branding;

import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** Circular shadow and one accretion band. Shared vector master for client and exports. */
public final class EmblemGeometry {
  public static final int VIEWBOX = 512;
  private EmblemGeometry() {}

  public static Path2D shape() {
    // Offset circles give the upper-right rim slightly more weight without ornament.
    Area ring = new Area(new Ellipse2D.Double(128, 100, 264, 264));
    Area shadow = new Area(new Ellipse2D.Double(145, 118, 226, 226));
    ring.subtract(shadow);
    // Only the sides of the far disk remain visible behind the circular shadow.
    Area disk = new Area(new Ellipse2D.Double(44, 262, 424, 62));
    disk.subtract(new Area(new Ellipse2D.Double(78, 275, 356, 34)));
    disk.subtract(shadow);
    ring.add(disk);
    // One broad foreground band; no duplicate contours, hooks, or internal sphere.
    Path2D front = new Path2D.Double();
    front.moveTo(44, 293);
    front.curveTo(94, 332, 411, 337, 468, 293);
    front.lineTo(434, 293);
    front.curveTo(354, 314, 153, 309, 78, 293);
    front.closePath();
    ring.add(new Area(front));
    Path2D result = new Path2D.Double();
    result.append(ring, false);
    return result;
  }
}
