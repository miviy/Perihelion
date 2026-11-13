package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.gui.menu.MenuTheme;

@Mixin(GuiButton.class)
public abstract class MenuButtonMixin {
  @Shadow protected boolean hovered;

  @Shadow
  protected abstract void mouseDragged(Minecraft game, int x, int y);

  @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
  private void perihelion$menuButton(Minecraft game, int x, int y, CallbackInfo ci) {
    GuiButton button = (GuiButton) (Object) this;
    if (MenuTheme.active(game.currentScreen)) {
      hovered =
          x >= button.xPosition
              && y >= button.yPosition
              && x < button.xPosition + button.width
              && y < button.yPosition + button.height;
      if (MenuTheme.button(button, x, y, () -> mouseDragged(game, x, y))) ci.cancel();
    }
  }
}
