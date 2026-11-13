package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.HeldItemSync;

@Mixin(PlayerControllerMP.class)
public abstract class PlayerControllerMixin
        implements HeldItemSync, perihelion.integration.minecraft189.control.BlockBreakAccess {
    @Shadow private int blockHitDelay;
    @Shadow private float curBlockDamageMP;
    @Shadow private boolean isHittingBlock;

    @Override
    public int perihelion$hitDelay() {
        return blockHitDelay;
    }

    @Override
    public void perihelion$hitDelay(int value) {
        blockHitDelay = value;
    }

    @Override
    public float perihelion$damage() {
        return curBlockDamageMP;
    }

    @Override
    public void perihelion$damage(float value) {
        curBlockDamageMP = value;
    }

    @Override
    public boolean perihelion$hitting() {
        return isHittingBlock;
    }

    @Inject(
            method =
                    "attackEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void perihelion$attack(EntityPlayer player, Entity target, CallbackInfo callback) {
        if (player == Minecraft.getMinecraft().thePlayer && target instanceof EntityLivingBase) {
            if (HookBridge.attack((EntityLivingBase) target)) callback.cancel();
        }
    }

    @Redirect(
            method = {"clickBlock", "onPlayerDamageBlock"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/Block;getPlayerRelativeBlockHardness(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)F"))
    private float perihelion$miningStrength(
            net.minecraft.block.Block block,
            EntityPlayer player,
            net.minecraft.world.World world,
            net.minecraft.util.BlockPos position) {
        return player == Minecraft.getMinecraft().thePlayer && HookBridge.gameControls() != null
                ? HookBridge.gameControls().miningStrength(block, world, position)
                : block.getPlayerRelativeBlockHardness(player, world, position);
    }

    @Redirect(
            method = {"clickBlock", "onPlayerDestroyBlock", "isHittingPosition"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/EntityPlayerSP;getHeldItem()Lnet/minecraft/item/ItemStack;"))
    private net.minecraft.item.ItemStack perihelion$miningHeld(
            net.minecraft.client.entity.EntityPlayerSP player) {
        return player.inventory.getStackInSlot(HookBridge.serverSlot(player.inventory.currentItem));
    }

    @Redirect(
            method = {"clickBlock", "onPlayerDestroyBlock"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/EntityPlayerSP;getCurrentEquippedItem()Lnet/minecraft/item/ItemStack;"))
    private net.minecraft.item.ItemStack perihelion$miningEquipped(
            net.minecraft.client.entity.EntityPlayerSP player) {
        return player.inventory.getStackInSlot(HookBridge.serverSlot(player.inventory.currentItem));
    }

    @Redirect(
            method = "onPlayerDestroyBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/EntityPlayerSP;destroyCurrentEquippedItem()V"))
    private void perihelion$destroyMiningItem(net.minecraft.client.entity.EntityPlayerSP player) {
        player.inventory.setInventorySlotContents(
                HookBridge.serverSlot(player.inventory.currentItem), null);
    }

    @Shadow
    private void syncCurrentPlayItem() {
        throw new AssertionError("Mixin shadow");
    }

    @Override
    public void perihelion$syncHeldItem() {
        syncCurrentPlayItem();
    }

    @Redirect(
            method = "syncCurrentPlayItem()V",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I",
                            opcode = 180))
    private int perihelion$serverSlot(InventoryPlayer inventory) {
        return HookBridge.serverSlot(inventory.currentItem);
    }
}
