package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.gui.menu.MenuTheme;

@Mixin(GuiTextField.class)
public abstract class MenuTextFieldMixin {
  @Shadow private boolean isFocused;
  @Shadow private boolean isEnabled;
  @Shadow public int xPosition;
  @Shadow public int yPosition;
  @Shadow public int width;
  @Shadow public int height;
  @Unique private int perihelion$previousCursor, perihelion$previousSelection;

  @Inject(method = "mouseClicked", at = @At("HEAD"))
  private void perihelion$rememberCursor(int x, int y, int button, CallbackInfo ci) {
    GuiTextField field = (GuiTextField) (Object) this;
    perihelion$previousCursor = field.getCursorPosition();
    perihelion$previousSelection = field.getSelectionEnd();
  }

  @Inject(method = "drawTextBox", at = @At("HEAD"), cancellable = true)
  private void perihelion$selectedFont(CallbackInfo ci) {
    GuiTextField field = (GuiTextField) (Object) this;
    if (MenuTheme.field(
        field,
        xPosition,
        yPosition,
        width,
        height,
        field.getEnableBackgroundDrawing(),
        isFocused,
        isEnabled)) ci.cancel();
  }

  @Inject(method = "mouseClicked", at = @At("RETURN"))
  private void perihelion$fontCursor(int x, int y, int button, CallbackInfo ci) {
    GuiTextField field = (GuiTextField) (Object) this;
    MenuTheme.fieldClick(
        field,
        xPosition,
        yPosition,
        width,
        height,
        field.getEnableBackgroundDrawing(),
        isFocused,
        x,
        y,
        button,
        perihelion$previousCursor,
        perihelion$previousSelection);
  }

  @Redirect(
      method = "drawTextBox",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V",
              ordinal = 0))
  private void perihelion$fieldBorder(int left, int top, int right, int bottom, int color) {
    if (!MenuTheme.inputSurface(left, top, right, bottom, isFocused))
      Gui.drawRect(left, top, right, bottom, color);
  }

  @Redirect(
      method = "drawTextBox",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V",
              ordinal = 1))
  private void perihelion$fieldFill(int left, int top, int right, int bottom, int color) {
    if (!MenuTheme.active(net.minecraft.client.Minecraft.getMinecraft().currentScreen))
      Gui.drawRect(left, top, right, bottom, color);
  }
}
