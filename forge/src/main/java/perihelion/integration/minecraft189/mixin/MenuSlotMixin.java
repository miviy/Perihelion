package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.gui.menu.MenuTheme;

@Mixin(GuiSlot.class)
public abstract class MenuSlotMixin {
  @Shadow public int left, right, top, bottom;

  @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
  private void perihelion$listSurface(Tessellator tessellator, CallbackInfo ci) {
    if (MenuTheme.listSurface(left, top, right, bottom, false)) ci.cancel();
  }

  @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
  private void perihelion$listMask(
      int start, int end, int alphaStart, int alphaEnd, CallbackInfo ci) {
    if (MenuTheme.listSurface(left, start, right, end, true)) ci.cancel();
  }
}
