package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.MouseOverEvent;

@Mixin(EntityRenderer.class)
public abstract class MouseOverMixin
    implements perihelion.integration.minecraft189.control.TickMouseOverAccess {
  @Unique private MouseOverEvent perihelion$selection;
  @Unique private boolean perihelion$tickSelection;
  @Shadow private Entity pointedEntity;

  @Shadow
  public abstract void getMouseOver(float partialTicks);

  @Override
  public void perihelion$tickMouseOver(float partialTicks) {
    boolean previous = perihelion$tickSelection;
    perihelion$tickSelection = true;
    try {
      getMouseOver(partialTicks);
    } finally {
      perihelion$tickSelection = previous;
    }
  }

  @Inject(method = "getMouseOver", at = @At("HEAD"))
  private void perihelion$prepare(float partialTicks, CallbackInfo ci) {
    perihelion$selection = new MouseOverEvent(perihelion$tickSelection);
    HookBridge.clientEvent(perihelion$selection);
  }

  // Preserve vanilla's block-reach flag; only the final entity-distance cutoff changes.
  @ModifyConstant(method = "getMouseOver", constant = @Constant(doubleValue = 3.0, ordinal = 1))
  private double perihelion$reach(double vanilla) {
    return perihelion$selection == null ? vanilla : perihelion$selection.range();
  }

  @Redirect(
      method = "getMouseOver",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getCollisionBorderSize()F"))
  private float perihelion$border(Entity target) {
    Entity view = Minecraft.getMinecraft().getRenderViewEntity();
    return target.getCollisionBorderSize()
        + (perihelion$selection != null && view instanceof EntityPlayer && !view.isInvisible()
            ? perihelion$selection.expand()
            : 0);
  }

  @Inject(method = "getMouseOver", at = @At("RETURN"))
  private void perihelion$finish(float partialTicks, CallbackInfo ci) {
    Minecraft game = Minecraft.getMinecraft();
    if (perihelion$selection != null && perihelion$selection.hasSelection()) {
      game.objectMouseOver = perihelion$selection.selection();
      pointedEntity =
          game.pointedEntity = game.objectMouseOver == null ? null : game.objectMouseOver.entityHit;
      perihelion$selection = null;
      return;
    }
    MovingObjectPosition hit = game.objectMouseOver;
    // Yuri also caps a creative selection when a custom reach above 3.0001 was requested.
    if (perihelion$selection != null
        && perihelion$selection.range() > 3.0001
        && game.getRenderViewEntity() != null
        && hit != null
        && hit.entityHit != null
        && hit.hitVec != null
        && game.getRenderViewEntity().getPositionEyes(partialTicks).distanceTo(hit.hitVec)
            > perihelion$selection.range()) {
      pointedEntity = null;
      game.pointedEntity = null;
      game.objectMouseOver =
          new MovingObjectPosition(
              MovingObjectPosition.MovingObjectType.MISS,
              hit.hitVec,
              null,
              new BlockPos(hit.hitVec));
    }
    perihelion$selection = null;
  }
}
