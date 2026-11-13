package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.ClientPacketEvent;

@Mixin(NetHandlerPlayClient.class)
public abstract class ClientPacketMixin {
  @Shadow private WorldClient clientWorldController;

  @Unique
  private boolean perihelion$currentWorld() {
    Minecraft game = Minecraft.getMinecraft();
    return clientWorldController != null
        && clientWorldController == game.theWorld
        && (Object) this == game.getNetHandler();
  }

  // Vanilla can already have queued chunk work when loadWorld retires this handler.
  // Check after thread handoff so neither stale delivery nor this guard reads game state on
  // Netty.
  @Inject(
      method = "handleChunkData",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$chunk(S21PacketChunkData packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) callback.cancel();
  }

  @Inject(
      method = "handleMapChunkBulk",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$chunks(S26PacketMapChunkBulk packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) callback.cancel();
  }

  @Inject(
      method = "handleMultiBlockChange",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$blocks(S22PacketMultiBlockChange packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) callback.cancel();
  }

  @Inject(
      method = "handleSetSlot",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$slot(S2FPacketSetSlot packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) callback.cancel();
  }

  @Inject(
      method = "handleEntityMovement",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$entityMove(S14PacketEntity packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) callback.cancel();
  }

  @Inject(
      method = "handleEntityTeleport",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$entityTeleport(S18PacketEntityTeleport packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) callback.cancel();
  }

  @Inject(
      method = "handleEntityHeadLook",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$entityHead(S19PacketEntityHeadLook packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) callback.cancel();
  }

  private void perihelion$packet(Packet<?> packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) {
      callback.cancel();
      return;
    }
    ClientPacketEvent event = new ClientPacketEvent(packet);
    HookBridge.clientEvent(event);
    if (event.isCancelled()) callback.cancel();
  }

  @Inject(
      method = "handlePlayerPosLook",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$position(S08PacketPlayerPosLook packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }

  @Inject(
      method = "handleChat",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$chat(S02PacketChat packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }

  @Inject(
      method = "handleEntityVelocity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$velocity(S12PacketEntityVelocity packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }

  @Inject(method = "handleHeldItemChange", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
      shift = At.Shift.AFTER), cancellable = true)
  private void perihelion$heldSlot(S09PacketHeldItemChange packet, CallbackInfo callback) {
    if (!perihelion$currentWorld()) { callback.cancel(); return; }
    HookBridge.observeServerSlot(Minecraft.getMinecraft().getNetHandler().getNetworkManager(),
        packet.getHeldItemHotbarIndex());
  }

  @Inject(
      method = "handleExplosion",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$explosion(S27PacketExplosion packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }

  @Inject(
      method = "handleBlockChange",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$block(S23PacketBlockChange packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }

  @Inject(
      method = "handleWindowItems",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$items(S30PacketWindowItems packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }

  @Inject(
      method = "handleTimeUpdate",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$time(S03PacketTimeUpdate packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }

  @Inject(
      method = "handleChangeGameState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void perihelion$weather(S2BPacketChangeGameState packet, CallbackInfo callback) {
    perihelion$packet(packet, callback);
  }
}
