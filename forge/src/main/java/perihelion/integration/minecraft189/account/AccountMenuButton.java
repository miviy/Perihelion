package perihelion.integration.minecraft189.account;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.*;

/** Menu entry uses the client's current palette and font, including custom presets. */
public final class AccountMenuButton extends GuiButton {
  private final AccountService service;
  private final UiCanvas canvas = new UiCanvas();
  private final RenderState state = new RenderState();

  public AccountMenuButton(AccountService service, int id, int x, int y) {
    super(id, x, y, 84, 18, "Accounts");
    this.service = service;
  }

  @Override
  public void drawButton(Minecraft game, int x, int y) {
    if (!visible) return;
    hovered = x >= xPosition && y >= yPosition && x < xPosition + width && y < yPosition + height;
    if (perihelion.integration.minecraft189.gui.menu.MenuTheme.button(this, x, y, () -> {})) return;
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      GuiTheme.configure(
          canvas,
          service.menuFonts,
          GuiTheme.preferences(service.runtime),
          new ScaledResolution(game).getScaleFactor());
      hovered = x >= xPosition && y >= yPosition && x < xPosition + width && y < yPosition + height;
      GuiTheme.button(
          canvas, displayString, xPosition, yPosition, width, height, false, enabled, hovered);
    }
  }
}
