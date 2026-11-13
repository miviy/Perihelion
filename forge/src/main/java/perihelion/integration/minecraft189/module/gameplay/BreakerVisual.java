package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.HudRenderEvent;
import perihelion.integration.minecraft189.render.*;

/** Presentation only. It never claims input, rotations, tools, or sends packets. */
final class BreakerVisual {
  private final RenderState state = new RenderState();
  private final VisualDraw.Projection projection = new VisualDraw.Projection();
  private final List<double[]> labels = new ArrayList<>();
  private BreakerPlan projected;

  void clear() {
    labels.clear();
    projected = null;
  }

  void world(Minecraft game, BreakerPlan plan, boolean route) {
    clear();
    if (plan == null) return;
    projected = plan;
    projection.capture(game);
    try (RenderState ignored = state.open()) {
      GL11.glPushMatrix();
      try {
        VisualDraw.worldOrigin(game);
        int count = route ? plan.route.size() : 1;
        Vec3 last = null;
        for (int i = 0; i < count; i++) {
          BlockPos pos = route ? plan.route.get(i).pos : plan.target;
          int color = i == 0 ? 0xff6ef0a8 : 0xff80caff;
          AxisAlignedBB box = BreakerPlan.box(game, pos).expand(.003, .003, .003);
          RenderStyle.lines(true, i == 0 ? 2 : 1, color);
          VisualDraw.box(box);
          Vec3 center =
              new Vec3(
                  (box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
          if (route) {
            labels.add(projection.point(center));
            if (last != null) {
              GL11.glBegin(GL11.GL_LINES);
              VisualDraw.vertex(last);
              VisualDraw.vertex(center);
              GL11.glEnd();
            }
            last = center;
          }
        }
      } finally {
        GL11.glPopMatrix();
      }
    }
  }

  void hud(Minecraft game, BreakerPlan plan, HudRenderEvent event) {
    if (plan == null || plan != projected || plan.route.isEmpty()) return;
    BreakerPlan.Step first = plan.route.get(0);
    net.minecraft.item.ItemStack tool = game.thePlayer.inventory.getStackInSlot(first.tool);
    String item = tool == null ? "Hand" : tool.getDisplayName();
    String title =
        String.format(
            Locale.ROOT,
            "Fastest route found: %d blocks / ~%.2fs",
            plan.route.size(),
            plan.cost / 20);
    String next =
        "1. "
            + first.state.getBlock().getLocalizedName()
            + " / "
            + item
            + " (slot "
            + (first.tool + 1)
            + ")";
    int limit = Math.max(20, event.scaledWidth() - 20);
    title = game.fontRendererObj.trimStringToWidth(title, limit);
    next = game.fontRendererObj.trimStringToWidth(next, limit);
    try (RenderState ignored = state.open()) {
      for (int i = 0; i < labels.size(); i++) {
        double[] p = labels.get(i);
        if (p == null
            || p[0] < 0
            || p[0] > event.scaledWidth()
            || p[1] < 0
            || p[1] > event.scaledHeight()) continue;
        String number = Integer.toString(i + 1);
        RenderStyle.text();
        game.fontRendererObj.drawStringWithShadow(
            number,
            (float) p[0] - game.fontRendererObj.getStringWidth(number) / 2f,
            (float) p[1] - 4,
            i == 0 ? 0xff6ef0a8 : 0xff80caff);
      }
      int x = event.scaledWidth() / 2,
          y = Math.min(event.scaledHeight() - 26, event.scaledHeight() / 2 + 24);
      int width =
          Math.max(
              game.fontRendererObj.getStringWidth(title),
              game.fontRendererObj.getStringWidth(next));
      RenderStyle.lines(true, 1, 0xb0000000);
      VisualDraw.rect(x - width / 2. - 5, y - 4, x + width / 2. + 5, y + 22);
      RenderStyle.text();
      game.fontRendererObj.drawStringWithShadow(
          title, x - game.fontRendererObj.getStringWidth(title) / 2f, y, 0xffffffff);
      game.fontRendererObj.drawStringWithShadow(
          next, x - game.fontRendererObj.getStringWidth(next) / 2f, y + 11, 0xff6ef0a8);
    }
  }
}
