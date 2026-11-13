package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.gui.menu.MenuTheme;

@Mixin(Gui.class)
public abstract class MenuPrimitivesMixin {
  @Inject(method = "drawTexturedModalRect(IIIIII)V", at = @At("HEAD"), cancellable = true)
  private void perihelion$sliderTexture(int x, int y, int u, int v, int w, int h, CallbackInfo ci) {
    if (MenuTheme.sliderTexture(x, y, w, h)) ci.cancel();
  }

  @Inject(method = "drawCenteredString", at = @At("HEAD"), cancellable = true)
  private void perihelion$centeredText(
      FontRenderer font, String text, int x, int y, int color, CallbackInfo ci) {
    if (MenuTheme.text(text, x, y, color, true)) ci.cancel();
  }

  @Inject(method = "drawString", at = @At("HEAD"), cancellable = true)
  private void perihelion$labelText(
      FontRenderer font, String text, int x, int y, int color, CallbackInfo ci) {
    if (MenuTheme.text(text, x, y, color, false)) ci.cancel();
  }
}
