package perihelion.integration.minecraft189.branding;

import static org.junit.Assert.*;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

public class BrandArtworkTest {
  @Test
  public void monochromeMasterHasEqualTipReachAndPairedCurves() {
    Shape mark = new Area(EmblemGeometry.shape());
    Rectangle2D b = mark.getBounds2D();
    assertEquals(512, b.getMinX() + b.getMaxX(), .0001);
    assertEquals(512, b.getMinY() + b.getMaxY(), .0001);
    for (double y = .37; y < 512; y += 3.1)
      for (double x = .61; x < 512; x += 3.1)
        assertEquals(
            "opposing curves at " + x + "," + y,
            mark.contains(x, y),
            mark.contains(512 - x, 512 - y));
  }

  @Test
  public void coreRimFollowsACircleAndLeavesTheShadowOpen() {
    Shape mark = EmblemGeometry.shape();
    for (int degrees = 0; degrees < 360; degrees += 5) {
      double a = Math.toRadians(degrees), dx = Math.cos(a), dy = Math.sin(a);
      // The accretion path intentionally crosses the rim; sample the unobscured circle.
      if (Math.abs(dx * Math.sin(Math.toRadians(17)) + dy * Math.cos(Math.toRadians(17))) < .35)
        continue;
      assertTrue("round rim at " + degrees, mark.contains(256 + dx * 76, 256 + dy * 76));
      assertFalse("no stretched rim at " + degrees, mark.contains(256 + dx * 81, 256 + dy * 81));
      assertFalse("open shadow at " + degrees, mark.contains(256 + dx * 60, 256 + dy * 60));
    }
  }

  @Test
  public void packagedCopperMasterHasTrueTransparencyAndUsableInkBounds() throws Exception {
    try (InputStream in =
        getClass().getResourceAsStream("/assets/perihelion/brand/emblem-copper.png")) {
      assertNotNull("primary asset must ship in the client", in);
      BufferedImage image = ImageIO.read(in);
      assertTrue(image.getColorModel().hasAlpha());
      assertEquals(0, image.getRGB(0, 0) >>> 24);
      assertEquals(0, image.getRGB(image.getWidth() - 1, image.getHeight() - 1) >>> 24);
      assertEquals(
          "upper shadow stays transparent",
          0,
          image.getRGB(image.getWidth() / 2, (int) (image.getHeight() * .40)) >>> 24);
      assertEquals(
          "lower shadow stays transparent",
          0,
          image.getRGB(image.getWidth() / 2, (int) (image.getHeight() * .63)) >>> 24);
    }
    assertTrue(BrandArtwork.primary().aspect() > 1.1);
    assertTrue(BrandArtwork.primary().aspect() < 1.8);
  }
}
