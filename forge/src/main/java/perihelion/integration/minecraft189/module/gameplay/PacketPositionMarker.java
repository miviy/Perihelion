// SPDX-License-Identifier: AGPL-3.0-only
// Marker dimensions and opacity follow Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def.
package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.ColorValue;
import perihelion.integration.minecraft189.network.SentPosition;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Reuses native theme and rendering ownership; positions contain no entity/world references. */
final class PacketPositionMarker {
  private final ClientRuntime runtime;
  private final RenderState rendering = new RenderState();

  PacketPositionMarker(ClientRuntime runtime) {
    this.runtime = runtime;
  }

  void draw(SentPosition.Point point) {
    Minecraft game = Minecraft.getMinecraft();
    if (point == null || game.thePlayer == null || game.gameSettings.thirdPersonView == 0) return;
    double x = point.x - game.getRenderManager().viewerPosX;
    double y = point.y - game.getRenderManager().viewerPosY;
    double z = point.z - game.getRenderManager().viewerPosZ;
    AxisAlignedBB bounds = new AxisAlignedBB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
    int accent =
        ((ColorValue) runtime.modules().require("hud").module().settings().get("accent").get())
                .resolvedArgb(System.currentTimeMillis())
            & 0xffffff;
    try (RenderState ignored = rendering.open()) {
      RenderStyle.lines(true, .5f, 60 << 24 | accent);
      VisualDraw.fill(bounds);
      RenderStyle.color(40 << 24 | accent);
      VisualDraw.box(bounds);
    }
  }
}
