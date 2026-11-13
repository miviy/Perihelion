package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.control.ManualRaycast;
import perihelion.integration.minecraft189.event.RightClickEvent;

@Mixin(Minecraft.class)
public abstract class GameplayAccessMixin implements GameplayAccess {
  @org.spongepowered.asm.mixin.injection.Redirect(
      method = "runTick",
      at =
          @org.spongepowered.asm.mixin.injection.At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V"))
  private void perihelion$tickSelection(
      net.minecraft.client.renderer.EntityRenderer renderer, float partialTicks) {
    ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
        .perihelion$tickMouseOver(partialTicks);
  }

  @org.spongepowered.asm.mixin.injection.Inject(
      method = "clickMouse",
      at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
      cancellable = true)
  private void perihelion$interaction(
      org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
    perihelion.integration.minecraft189.event.LocalInteractionEvent event =
        new perihelion.integration.minecraft189.event.LocalInteractionEvent();
    perihelion.integration.minecraft189.HookBridge.clientEvent(event);
    if (event.isCancelled()) {
      callback.cancel();
      return;
    }
    perihelion.integration.minecraft189.event.AttackClickEvent click =
        new perihelion.integration.minecraft189.event.AttackClickEvent();
    perihelion.integration.minecraft189.HookBridge.clientEvent(click);
    if (click.isCancelled()) callback.cancel();
  }

  @Unique private MovingObjectPosition perihelion$previousRightSelection, perihelion$rightSelection;
  @Unique private Entity perihelion$rightPlayer;
  @Unique private World perihelion$rightWorld;

  @org.spongepowered.asm.mixin.injection.Inject(
      method = "rightClickMouse",
      at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
      cancellable = true)
  private void perihelion$rightInteraction(
      org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
    perihelion.integration.minecraft189.event.LocalInteractionEvent local =
        new perihelion.integration.minecraft189.event.LocalInteractionEvent();
    perihelion.integration.minecraft189.HookBridge.clientEvent(local);
    if (local.isCancelled()) {
      callback.cancel();
      return;
    }
    RightClickEvent event = new RightClickEvent();
    perihelion.integration.minecraft189.HookBridge.clientEvent(event);
    if (event.isCancelled()) {
      callback.cancel();
      return;
    }
    Minecraft game = (Minecraft) (Object) this;
    perihelion$previousRightSelection = perihelion$rightSelection = null;
    perihelion$rightPlayer = null;
    perihelion$rightWorld = null;
    if (!event.normalRangeRay()) return;
    MovingObjectPosition normal = ManualRaycast.normalRightClick(game);
    if (normal == null) return;
    perihelion$previousRightSelection = game.objectMouseOver;
    perihelion$rightSelection = normal;
    perihelion$rightPlayer = game.thePlayer;
    perihelion$rightWorld = game.theWorld;
    game.objectMouseOver = normal;
  }

  @org.spongepowered.asm.mixin.injection.Inject(
      method = "rightClickMouse",
      at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
  private void perihelion$restoreRightSelection(
      org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
    Minecraft game = (Minecraft) (Object) this;
    if (perihelion$rightSelection != null
        && game.objectMouseOver == perihelion$rightSelection
        && game.thePlayer == perihelion$rightPlayer
        && game.theWorld == perihelion$rightWorld)
      game.objectMouseOver = perihelion$previousRightSelection;
    perihelion$previousRightSelection = perihelion$rightSelection = null;
    perihelion$rightPlayer = null;
    perihelion$rightWorld = null;
  }

  @org.spongepowered.asm.mixin.injection.Inject(
      method = "sendClickBlockToController",
      at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
      cancellable = true)
  private void perihelion$mining(
      boolean held, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
    perihelion.integration.minecraft189.event.LocalInteractionEvent local =
        new perihelion.integration.minecraft189.event.LocalInteractionEvent();
    perihelion.integration.minecraft189.HookBridge.clientEvent(local);
    if (local.isCancelled()) callback.cancel();
  }

  @Shadow private int leftClickCounter;

  @Override
  public void perihelion$clearClickDelay() {
    leftClickCounter = 0;
  }

  @Shadow
  protected abstract void clickMouse();

  @Shadow
  protected abstract void rightClickMouse();

  @Override
  public void perihelion$clickMouse() {
    clickMouse();
  }

  @Override
  public void perihelion$rightClickMouse() {
    rightClickMouse();
  }
}
