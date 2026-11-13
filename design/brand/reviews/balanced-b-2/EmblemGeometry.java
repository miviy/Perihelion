package perihelion.integration.minecraft189.branding;

import java.awt.BasicStroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** Direction B, rebuilt with a circular core and balanced, rotationally paired light bands. */
public final class EmblemGeometry {
  public static final int VIEWBOX = 512;
  private static final double CENTER = VIEWBOX / 2.0;

  private EmblemGeometry() {}

  public static Path2D shape() {
    Path2D band = new Path2D.Double();
    band.moveTo(48, 321);
    band.curveTo(67, 305, 72, 287, 72, 256);
    // The outside follows a true circle, rather than an uneven hand-drawn dome.
    band.append(new Arc2D.Double(72, 72, 368, 368, 180, -154, Arc2D.OPEN), true);
    band.curveTo(344, 132, 267, 139, 208, 166);
    band.curveTo(169, 184, 146, 218, 139, 253);
    band.curveTo(132, 285, 104, 306, 48, 321);
    band.closePath();

    Area artwork = new Area(band);
    // Reuse the exact same curve. Neither side can acquire an extra-long tip or bulge.
    artwork.add(new Area(AffineTransform.getRotateInstance(Math.PI, CENTER, CENTER)
        .createTransformedShape(band)));

    // Tapered caustic trails fold back into each shoulder without stretching the core.
    Path2D trail = new Path2D.Double();
    trail.moveTo(339, 230);
    trail.curveTo(382, 219, 423, 205, 464, 191);
    trail.curveTo(426, 216, 389, 237, 344, 252);
    trail.curveTo(348, 244, 345, 236, 339, 230);
    trail.closePath();
    Area light = new Area(trail);
    light.subtract(new Area(new Ellipse2D.Double(166, 166, 180, 180)));
    artwork.add(light);
    artwork.add(new Area(AffineTransform.getRotateInstance(Math.PI, CENTER, CENTER)
        .createTransformedShape(light)));

    // Keep the ball truly round. A flowing light passage clips it without stretching it.
    Area core = new Area(new Ellipse2D.Double(178, 178, 156, 156));
    Path2D flow = new Path2D.Double();
    flow.moveTo(48, 321);
    flow.curveTo(164, 300, 201, 263, 256, 256);
    flow.curveTo(311, 249, 348, 212, 464, 191);
    Area passage = new Area(new BasicStroke(16, BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND).createStrokedShape(flow));
    core.subtract(passage);
    artwork.add(core);

    Path2D result = new Path2D.Double();
    result.append(artwork, false);
    return result;
  }
}
