package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;

/** Intercepts typed dot commands before vanilla/Forge can create a server chat packet. */
@Mixin(GuiScreen.class)
public abstract class ChatCommandMixin {
  @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
  private void perihelion$command(String message, boolean history, CallbackInfo callback) {
    if (HookBridge.chatCommand(message)) {
      if (history) Minecraft.getMinecraft().ingameGUI.getChatGUI().addToSentMessages(message);
      callback.cancel();
    }
  }
}
