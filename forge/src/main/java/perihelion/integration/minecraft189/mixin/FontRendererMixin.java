package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import perihelion.integration.minecraft189.HookBridge;

@Mixin(FontRenderer.class)
public abstract class FontRendererMixin {
  @ModifyVariable(method = {
      "drawString(Ljava/lang/String;FFIZ)I", "getStringWidth(Ljava/lang/String;)I",
      "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;",
      "listFormattedStringToWidth(Ljava/lang/String;I)Ljava/util/List;",
      "wrapFormattedStringToWidth(Ljava/lang/String;I)Ljava/lang/String;"
  }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
  private String perihelion$displayText(String text) { return HookBridge.displayText(text); }
}
