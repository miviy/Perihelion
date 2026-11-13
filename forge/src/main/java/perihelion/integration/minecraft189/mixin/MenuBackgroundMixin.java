package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.gui.menu.MenuTheme;

@Mixin(GuiScreen.class)
public abstract class MenuBackgroundMixin {
  @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
  private void perihelion$menuBackground(int tint, CallbackInfo ci) {
    if (MenuTheme.background((GuiScreen) (Object) this)) ci.cancel();
  }
}
