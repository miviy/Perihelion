package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.*;

final class VisualDraw {
    static final int ACCENT = 0xffad8cff;

    private VisualDraw() {}

    static Vec3 position(Entity e, float partial) {
        return new Vec3(
                e.lastTickPosX + (e.posX - e.lastTickPosX) * partial,
                e.lastTickPosY + (e.posY - e.lastTickPosY) * partial,
                e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial);
    }

    static AxisAlignedBB bounds(Entity e, float partial) {
        Vec3 p = position(e, partial);
        return e.getEntityBoundingBox()
                .offset(p.xCoord - e.posX, p.yCoord - e.posY, p.zCoord - e.posZ);
    }

    static void worldOrigin(Minecraft game) {
        GL11.glTranslated(
                -game.getRenderManager().viewerPosX,
                -game.getRenderManager().viewerPosY,
                -game.getRenderManager().viewerPosZ);
    }

    static void vertex(Vec3 p) {
        GL11.glVertex3d(p.xCoord, p.yCoord, p.zCoord);
    }

    static void box(AxisAlignedBB b) {
        double[][] v = {
            {b.minX, b.minY, b.minZ},
            {b.maxX, b.minY, b.minZ},
            {b.maxX, b.minY, b.maxZ},
            {b.minX, b.minY, b.maxZ},
            {b.minX, b.maxY, b.minZ},
            {b.maxX, b.maxY, b.minZ},
            {b.maxX, b.maxY, b.maxZ},
            {b.minX, b.maxY, b.maxZ}
        };
        GL11.glBegin(GL11.GL_LINES);
        for (int[] edge :
                new int[][] {
                    {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5},
                    {2, 6}, {3, 7}
                }) for (int i : edge) GL11.glVertex3d(v[i][0], v[i][1], v[i][2]);
        GL11.glEnd();
    }

    static void fill(AxisAlignedBB b) {
        double[][] v = {
            {b.minX, b.minY, b.minZ},
            {b.maxX, b.minY, b.minZ},
            {b.maxX, b.minY, b.maxZ},
            {b.minX, b.minY, b.maxZ},
            {b.minX, b.maxY, b.minZ},
            {b.maxX, b.maxY, b.minZ},
            {b.maxX, b.maxY, b.maxZ},
            {b.minX, b.maxY, b.maxZ}
        };
        GL11.glBegin(GL11.GL_QUADS);
        for (int[] face :
                new int[][] {
                    {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6},
                    {3, 0, 4, 7}
                }) for (int i : face) GL11.glVertex3d(v[i][0], v[i][1], v[i][2]);
        GL11.glEnd();
    }

    static void rect(double x, double y, double right, double bottom) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(right, y);
        GL11.glVertex2d(right, bottom);
        GL11.glVertex2d(x, bottom);
        GL11.glEnd();
    }

    static void outline(double[] b, boolean corners) {
        if (!corners) {
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2d(b[0], b[1]);
            GL11.glVertex2d(b[2], b[1]);
            GL11.glVertex2d(b[2], b[3]);
            GL11.glVertex2d(b[0], b[3]);
            GL11.glEnd();
            return;
        }
        double length = Math.min(b[2] - b[0], b[3] - b[1]) * 0.3;
        GL11.glBegin(GL11.GL_LINES);
        for (int x = 0; x < 2; x++)
            for (int y = 0; y < 2; y++) {
                double px = b[x * 2], py = b[y * 2 + 1];
                GL11.glVertex2d(px, py);
                GL11.glVertex2d(px + (x == 0 ? length : -length), py);
                GL11.glVertex2d(px, py);
                GL11.glVertex2d(px, py + (y == 0 ? length : -length));
            }
        GL11.glEnd();
    }

    static final class Projection {
        private final FloatBuffer model = BufferUtils.createFloatBuffer(16),
                projection = BufferUtils.createFloatBuffer(16),
                result = BufferUtils.createFloatBuffer(3);
        private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        private double x, y, z;
        private int scale, height;

        void capture(Minecraft game) {
            model.clear();
            projection.clear();
            viewport.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            x = game.getRenderManager().viewerPosX;
            y = game.getRenderManager().viewerPosY;
            z = game.getRenderManager().viewerPosZ;
            ScaledResolution screen = new ScaledResolution(game);
            scale = screen.getScaleFactor();
            height = game.displayHeight;
        }

        double[] point(Vec3 p) {
            result.clear();
            if (!GLU.gluProject(
                            (float) (p.xCoord - x),
                            (float) (p.yCoord - y),
                            (float) (p.zCoord - z),
                            model,
                            projection,
                            viewport,
                            result)
                    || result.get(2) < 0
                    || result.get(2) >= 1) return null;
            return new double[] {result.get(0) / scale, (height - result.get(1)) / scale};
        }

        double[] box(AxisAlignedBB b) {
            double[] bounds = {
                Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE
            };
            int count = 0;
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 0; dy < 2; dy++)
                    for (int dz = 0; dz < 2; dz++) {
                        double[] p =
                                point(
                                        new Vec3(
                                                dx == 0 ? b.minX : b.maxX,
                                                dy == 0 ? b.minY : b.maxY,
                                                dz == 0 ? b.minZ : b.maxZ));
                        if (p == null) continue;
                        count++;
                        bounds[0] = Math.min(bounds[0], p[0]);
                        bounds[1] = Math.min(bounds[1], p[1]);
                        bounds[2] = Math.max(bounds[2], p[0]);
                        bounds[3] = Math.max(bounds[3], p[1]);
                    }
            return count == 8
                    ? bounds
                    : null; // Reject near-plane crossings instead of drawing giant flipped
                            // rectangles.
        }
    }
}
