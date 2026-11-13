package perihelion.integration.minecraft189.mixin;

import java.util.List;
import net.minecraft.client.gui.GuiChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.ChatHudEvent;

/** Populate vanilla's completion cycle locally, without requesting server suggestions. */
@Mixin(GuiChat.class)
public abstract class ChatCompletionMixin {
  @Shadow private List<String> foundPlayerNames;
  @Shadow private boolean waitingOnAutocomplete;

  @Inject(method = "initGui", at = @At("RETURN"))
  private void perihelion$hudOpen(CallbackInfo callback) {
    HookBridge.clientEvent(ChatHudEvent.lifecycle(ChatHudEvent.Kind.OPEN, (GuiChat) (Object) this));
  }

  @Inject(method = "onGuiClosed", at = @At("HEAD"))
  private void perihelion$hudClose(CallbackInfo callback) {
    HookBridge.clientEvent(
        ChatHudEvent.lifecycle(ChatHudEvent.Kind.CLOSE, (GuiChat) (Object) this));
  }

  @Inject(
      method = "sendAutocompleteRequest(Ljava/lang/String;Ljava/lang/String;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void perihelion$complete(String full, String word, CallbackInfo callback) {
    List<String> choices = HookBridge.completeChat(full);
    if (choices == null) return;
    foundPlayerNames.clear();
    foundPlayerNames.addAll(choices);
    waitingOnAutocomplete = false;
    callback.cancel();
  }
}
