package perihelion.integration.minecraft189.gui.menu;

import net.minecraft.client.renderer.GlStateManager;
import perihelion.integration.minecraft189.gui.font.GuiFonts;

/** Scales selected-font drawing and measurement together for readable menu forms. */
public final class MenuFontScale implements GuiFonts.Face {
  public final GuiFonts.Face source;
  public final float factor;

  public MenuFontScale(GuiFonts.Face source, float factor) {
    this.source = source;
    this.factor = factor;
  }

  @Override
  public float width(String text) {
    return source.width(text) * factor;
  }

  @Override
  public float height() {
    return source.height() * factor;
  }

  @Override
  public float centeredY(float top, float height) {
    return top + source.centeredY(0, height / factor) * factor;
  }

  @Override
  public void draw(String text, float x, float y, int color) {
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(x, y, 0);
      GlStateManager.scale(factor, factor, 1);
      source.draw(text, 0, 0, color);
    } finally {
      GlStateManager.popMatrix();
    }
  }
}
