package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.MovementInputEvent;

@Mixin(MovementInputFromOptions.class)
public abstract class MovementInputMixin extends MovementInput {
  @Inject(method = "updatePlayerMoveState()V", at = @At("RETURN"))
  private void perihelion$postInput(CallbackInfo callback) {
    Minecraft game = Minecraft.getMinecraft();
    if (game.thePlayer == null || game.thePlayer.movementInput != (Object) this) return;
    MovementInputEvent event = new MovementInputEvent(moveStrafe, moveForward);
    HookBridge.clientEvent(event);
    moveStrafe = event.strafe();
    moveForward = event.forward();
  }
}
