package perihelion.integration.minecraft189.gui.font;

import java.awt.BasicStroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** Shared original letter outlines for the logo and its matching menu-label atlas. */
public final class MenuLetterforms {
  private MenuLetterforms() {}

  /** Constant-height, open geometric forms with matching cut terminals and rounded bowls. */
  public static Area glyph(int codePoint) {
    switch (Character.toUpperCase(codePoint)) {
      case 'A':
        Area a = polygon(0, 80, 22, 0, 36, 0, 59, 80, 47, 80, 41, 57, 17, 57, 11, 80);
        a.subtract(polygon(20, 46, 38, 46, 29, 13));
        return a;
      case 'B':
        Area b = new Area(new Rectangle2D.Float(0, 0, 11, 80));
        b.add(new Area(new RoundRectangle2D.Float(0, 0, 54, 43, 22, 22)));
        b.add(new Area(new RoundRectangle2D.Float(0, 36, 57, 44, 22, 22)));
        b.subtract(new Area(new RoundRectangle2D.Float(11, 10, 32, 23, 10, 10)));
        b.subtract(new Area(new RoundRectangle2D.Float(11, 46, 35, 24, 11, 11)));
        return b;
      case 'C':
      case 'G':
        Area c = glyph('O');
        c.subtract(new Area(new Rectangle2D.Float(36, 18, 30, 44)));
        if (Character.toUpperCase(codePoint) == 'G') {
          c.add(new Area(new Rectangle2D.Float(31, 38, 30, 10)));
          c.add(new Area(new Rectangle2D.Float(50, 39, 11, 24)));
        }
        return c;
      case 'D':
        Area d = new Area(new RoundRectangle2D.Float(0, 0, 60, 80, 30, 30));
        d.add(new Area(new Rectangle2D.Float(0, 0, 20, 80)));
        d.subtract(new Area(new RoundRectangle2D.Float(11, 10, 38, 60, 20, 20)));
        return d;
      case 'F':
        return polygon(0, 0, 54, 0, 45, 10, 11, 10, 11, 34, 46, 34, 46, 44, 11, 44, 11, 80, 0, 80);
      case 'J':
        Area j = glyph('U');
        j.subtract(new Area(new Rectangle2D.Float(0, -1, 44, 46)));
        return j;
      case 'K':
        Area k = new Area(new Rectangle2D.Float(0, 0, 11, 80));
        k.add(polygon(11, 36, 44, 0, 58, 0, 27, 39, 60, 80, 46, 80, 19, 47, 11, 56));
        return k;
      case 'M':
        return polygon(
            0, 80, 0, 0, 12, 0, 33, 42, 54, 0, 66, 0, 66, 80, 55, 80, 55, 23, 38, 58, 28, 58, 11,
            23, 11, 80);
      case 'Q':
        Area q = glyph('O');
        q.add(polygon(35, 55, 47, 55, 65, 83, 52, 83));
        return q;
      case 'S':
        Path2D sp = new Path2D.Float();
        sp.moveTo(54, 5.5);
        sp.lineTo(19, 5.5);
        sp.quadTo(5.5, 5.5, 5.5, 19);
        sp.lineTo(5.5, 27);
        sp.quadTo(5.5, 39, 19, 39);
        sp.lineTo(37, 39);
        sp.quadTo(50.5, 39, 50.5, 52);
        sp.lineTo(50.5, 61);
        sp.quadTo(50.5, 74.5, 37, 74.5);
        sp.lineTo(0, 74.5);
        return stroke(sp);
      case 'T':
        return polygon(0, 0, 62, 0, 53, 11, 36, 11, 36, 80, 25, 80, 25, 11, 0, 11);
      case 'U':
        Area u = new Area(new RoundRectangle2D.Float(0, 0, 58, 80, 26, 26));
        u.add(new Area(new Rectangle2D.Float(0, 0, 58, 40)));
        u.subtract(new Area(new RoundRectangle2D.Float(11, -20, 36, 89, 16, 16)));
        return u;
      case 'V':
        return polygon(0, 0, 12, 0, 31, 62, 50, 0, 62, 0, 38, 80, 24, 80);
      case 'W':
        return polygon(
            0, 0, 12, 0, 25, 61, 42, 8, 51, 8, 68, 61, 80, 0, 92, 0, 73, 80, 61, 80, 46, 31, 31, 80,
            19, 80);
      case 'X':
        return polygon(
            0, 0, 13, 0, 30, 29, 47, 0, 60, 0, 37, 40, 60, 80, 47, 80, 30, 51, 13, 80, 0, 80, 23,
            40);
      case 'Y':
        return polygon(0, 0, 13, 0, 30, 32, 47, 0, 60, 0, 36, 45, 36, 80, 24, 80, 24, 45);
      case 'Z':
        return polygon(0, 0, 56, 0, 56, 10, 14, 70, 56, 70, 56, 80, 0, 80, 0, 70, 42, 10, 0, 10);
      case '0':
        return glyph('O');
      case '1':
        return polygon(14, 0, 26, 0, 26, 80, 14, 80, 14, 13, 0, 24, 0, 11);
      case '2':
        Path2D two = new Path2D.Float();
        two.moveTo(5.5, 19);
        two.quadTo(5.5, 5.5, 19, 5.5);
        two.lineTo(37, 5.5);
        two.quadTo(51, 5.5, 51, 20);
        two.quadTo(51, 29, 40, 39);
        two.lineTo(5.5, 74.5);
        two.lineTo(56, 74.5);
        return stroke(two);
      case '3':
        Path2D three = new Path2D.Float();
        three.moveTo(0, 5.5);
        three.lineTo(35, 5.5);
        three.quadTo(51, 5.5, 51, 21);
        three.quadTo(51, 39, 35, 39);
        three.lineTo(22, 39);
        three.lineTo(35, 39);
        three.quadTo(51, 39, 51, 57);
        three.quadTo(51, 74.5, 35, 74.5);
        three.lineTo(0, 74.5);
        return stroke(three);
      case '4':
        Area four =
            polygon(
                36, 0, 49, 0, 49, 47, 60, 47, 60, 58, 49, 58, 49, 80, 38, 80, 38, 58, 0, 58, 0, 47);
        four.subtract(polygon(16, 47, 38, 47, 38, 15));
        return four;
      case '5':
        Path2D five = new Path2D.Float();
        five.moveTo(55, 5.5);
        five.lineTo(5.5, 5.5);
        five.lineTo(5.5, 39);
        five.lineTo(35, 39);
        five.quadTo(51, 39, 51, 57);
        five.quadTo(51, 74.5, 35, 74.5);
        five.lineTo(0, 74.5);
        return stroke(five);
      case '6':
        Area six = new Area(new RoundRectangle2D.Float(0, 33, 57, 47, 22, 22));
        six.subtract(new Area(new RoundRectangle2D.Float(11, 44, 35, 25, 11, 11)));
        Path2D hook = new Path2D.Float();
        hook.moveTo(5.5, 56);
        hook.lineTo(5.5, 21);
        hook.quadTo(5.5, 5.5, 22, 5.5);
        hook.lineTo(51, 5.5);
        six.add(stroke(hook));
        return six;
      case '7':
        return polygon(0, 0, 57, 0, 57, 10, 22, 80, 9, 80, 44, 11, 0, 11);
      case '8':
        Area eight = new Area(new RoundRectangle2D.Float(1, 0, 54, 43, 22, 22));
        eight.add(new Area(new RoundRectangle2D.Float(0, 36, 57, 44, 22, 22)));
        eight.subtract(new Area(new RoundRectangle2D.Float(12, 10, 32, 23, 10, 10)));
        eight.subtract(new Area(new RoundRectangle2D.Float(11, 46, 35, 24, 11, 11)));
        return eight;
      case '9':
        Area nine = glyph('6');
        nine.transform(new AffineTransform(-1, 0, 0, -1, 57, 80));
        return nine;
      case '.':
        return new Area(new Rectangle2D.Float(0, 69, 11, 11));
      case ':':
        Area colon = new Area(new Rectangle2D.Float(0, 25, 11, 11));
        colon.add(new Area(new Rectangle2D.Float(0, 69, 11, 11)));
        return colon;
      case '-':
        return new Area(new Rectangle2D.Float(0, 35, 38, 10));
      case '_':
        return new Area(new Rectangle2D.Float(0, 70, 50, 10));
      case '+':
        Area plus = new Area(new Rectangle2D.Float(0, 35, 48, 10));
        plus.add(new Area(new Rectangle2D.Float(19, 16, 10, 48)));
        return plus;
      case '/':
        return polygon(0, 80, 12, 80, 49, 0, 37, 0);
      case 'P':
      case 'R':
        Area bowl = new Area(new RoundRectangle2D.Float(0, 0, 56, 46, 23, 23));
        bowl.add(new Area(new Rectangle2D.Float(0, 0, 12, 80)));
        bowl.subtract(new Area(new RoundRectangle2D.Float(12, 10, 33, 26, 12, 12)));
        if (Character.toUpperCase(codePoint) == 'R')
          bowl.add(polygon(27, 42, 40, 42, 60, 80, 47, 80));
        return bowl;
      case 'E':
        return polygon(
            0, 0, 54, 0, 45, 10, 11, 10, 11, 34, 46, 34, 46, 44, 11, 44, 11, 70, 54, 70, 45, 80, 0,
            80);
      case 'I':
        return new Area(new Rectangle2D.Float(0, 0, 11, 80));
      case 'H':
        return polygon(
            0, 0, 11, 0, 11, 34, 45, 34, 45, 0, 56, 0, 56, 80, 45, 80, 45, 44, 11, 44, 11, 80, 0,
            80);
      case 'L':
        return polygon(0, 0, 11, 0, 11, 70, 54, 70, 45, 80, 0, 80);
      case 'O':
        Area ring = new Area(new RoundRectangle2D.Float(0, 0, 61, 80, 31, 31));
        ring.subtract(new Area(new RoundRectangle2D.Float(11, 10, 39, 60, 20, 20)));
        return ring;
      case 'N':
        return polygon(0, 80, 0, 0, 11, 0, 45, 60, 45, 0, 56, 0, 56, 80, 45, 80, 11, 20, 11, 80);
      default:
        return null;
    }
  }

  private static Area polygon(float... coordinates) {
    Path2D path = new Path2D.Float();
    path.moveTo(coordinates[0], coordinates[1]);
    for (int i = 2; i < coordinates.length; i += 2) path.lineTo(coordinates[i], coordinates[i + 1]);
    path.closePath();
    return new Area(path);
  }

  private static Area stroke(Path2D path) {
    return new Area(
        new BasicStroke(11, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND).createStrokedShape(path));
  }
}
