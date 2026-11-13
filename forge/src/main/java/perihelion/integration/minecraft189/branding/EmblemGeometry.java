package perihelion.integration.minecraft189.branding;

import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** Single-color companion to the copper B emblem: paired light sweeps and a circular shadow. */
public final class EmblemGeometry {
  public static final int VIEWBOX = 512;

  private EmblemGeometry() {}

  public static Path2D shape() {
    Path2D band = new Path2D.Double();
    band.moveTo(48, 321);
    band.curveTo(67, 305, 72, 287, 72, 256);
    band.append(new Arc2D.Double(72, 72, 368, 368, 180, -154, Arc2D.OPEN), true);
    band.curveTo(344, 132, 267, 139, 208, 166);
    band.curveTo(169, 184, 146, 218, 139, 253);
    band.curveTo(132, 285, 104, 306, 48, 321);
    band.closePath();
    Area artwork = new Area(band);
    artwork.add(
        new Area(
            AffineTransform.getRotateInstance(Math.PI, 256, 256).createTransformedShape(band)));

    Area rim = new Area(new Ellipse2D.Double(178, 178, 156, 156));
    rim.subtract(new Area(new Ellipse2D.Double(186, 186, 140, 140)));
    artwork.add(rim);
    Path2D light = new Path2D.Double();
    light.moveTo(48, 321);
    light.curveTo(146, 295, 198, 269, 256, 250);
    light.curveTo(314, 231, 366, 217, 464, 191);
    light.curveTo(366, 217, 314, 243, 256, 262);
    light.curveTo(198, 281, 146, 295, 48, 321);
    light.closePath();
    artwork.add(new Area(light));
    Path2D result = new Path2D.Double();
    result.append(artwork, false);
    return result;
  }
}
