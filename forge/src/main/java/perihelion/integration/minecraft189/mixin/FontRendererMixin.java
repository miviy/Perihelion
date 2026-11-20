package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perihelion.integration.minecraft189.HookBridge;

@Mixin(FontRenderer.class)
public abstract class FontRendererMixin {
  // Drawing, measurement and whole-line wrapping return rewritten text to their callers.
  @ModifyVariable(method = {
      "drawString(Ljava/lang/String;FFIZ)I", "getStringWidth(Ljava/lang/String;)I",
      "listFormattedStringToWidth(Ljava/lang/String;I)Ljava/util/List;",
      "wrapFormattedStringToWidth(Ljava/lang/String;I)Ljava/lang/String;"
  }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
  private String perihelion$displayText(String text) { return HookBridge.displayText(text); }

  // Chat wrapping and text fields index the original string with the trimmed length, so trimming
  // must return part of the original text, sized by its rewritten width.
  @Inject(
      method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;",
      at = @At("HEAD"),
      cancellable = true)
  private void perihelion$trimDisplayText(
      String text, int width, boolean reverse, CallbackInfoReturnable<String> callback) {
    String trimmed = HookBridge.trimDisplayText((FontRenderer) (Object) this, text, width, reverse);
    if (trimmed != null) callback.setReturnValue(trimmed);
  }
}
