package perihelion.integration.minecraft189.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.BlockCollisionEvent;

import java.util.List;

@Mixin(Block.class)
public abstract class BlockCollisionMixin {
    @Shadow
    public abstract AxisAlignedBB getCollisionBoundingBox(
            World world, BlockPos pos, IBlockState state);

    @Inject(method = "addCollisionBoxesToList", at = @At("HEAD"), cancellable = true)
    private void perihelion$collision(
            World world,
            BlockPos pos,
            IBlockState state,
            AxisAlignedBB mask,
            List<AxisAlignedBB> boxes,
            Entity entity,
            CallbackInfo callback) {
        if (entity == null
                || entity != Minecraft.getMinecraft().thePlayer
                || !world.isRemote
                || !HookBridge.isRunning()) return;
        AxisAlignedBB original = getCollisionBoundingBox(world, pos, state);
        BlockCollisionEvent event = new BlockCollisionEvent((Block) (Object) this, pos, original);
        HookBridge.clientEvent(event);
        if (event.box() == original) return;
        if (event.box() != null && mask.intersectsWith(event.box())) boxes.add(event.box());
        callback.cancel();
    }
}
