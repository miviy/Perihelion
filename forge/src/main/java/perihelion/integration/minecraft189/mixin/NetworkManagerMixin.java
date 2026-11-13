package perihelion.integration.minecraft189.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.network.RawHookBridge;

@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin {
  @Shadow @Final private EnumPacketDirection direction;
  @Shadow private Channel channel;
  @Shadow private INetHandler packetListener;

  @Shadow
  public abstract boolean isLocalChannel();

  @Unique
  private boolean perihelion$isClientPlay(Packet<?> packet) {
    return HookBridge.isRunning()
        && direction == EnumPacketDirection.CLIENTBOUND
        && channel != null
        && channel.isOpen()
        && !isLocalChannel()
        && packetListener instanceof INetHandlerPlayClient
        && EnumConnectionState.getFromPacket(packet) == EnumConnectionState.PLAY;
  }

  @Unique
  private void perihelion$observeClientSend(Packet<?> packet) {
    if (direction == EnumPacketDirection.CLIENTBOUND
        && channel != null
        && channel.isOpen()
        && packetListener instanceof INetHandlerPlayClient
        && EnumConnectionState.getFromPacket(packet) == EnumConnectionState.PLAY)
      HookBridge.observeClientSend((NetworkManager) (Object) this, packet);
  }

  // Observe the attempt before NetworkManager can queue/asynchronously dispatch it. The raw bus
  // remains at dispatchPacket, retaining its multiplayer-only interception and replay contract.
  @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"))
  private void perihelion$sendFact(Packet<?> packet, CallbackInfo callback) {
    perihelion$observeClientSend(packet);
  }

  @Inject(
      method =
          "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V",
      at = @At("HEAD"))
  private void perihelion$sendFactWithListeners(
      Packet<?> packet,
      GenericFutureListener<? extends Future<? super Void>> listener,
      GenericFutureListener<? extends Future<? super Void>>[] listeners,
      CallbackInfo callback) {
    perihelion$observeClientSend(packet);
  }

  // Both public overloads and the pending queue converge here exactly once per packet.
  @Inject(
      method =
          "dispatchPacket(Lnet/minecraft/network/Packet;[Lio/netty/util/concurrent/GenericFutureListener;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void perihelion$beforeSend(
      Packet<?> packet,
      GenericFutureListener<? extends Future<? super Void>>[] listeners,
      CallbackInfo callback) {
    // Local PLAY traffic supplies facts too, while retaining the raw bus's multiplayer-only
    // contract. Record once at the shared dispatch boundary, including cancelled attempts.
    boolean play =
        HookBridge.isRunning()
            && direction == EnumPacketDirection.CLIENTBOUND
            && channel != null
            && channel.isOpen()
            && packetListener instanceof INetHandlerPlayClient
            && EnumConnectionState.getFromPacket(packet) == EnumConnectionState.PLAY;
    if (play) HookBridge.observeOutgoing(packet);
    if (!perihelion$isClientPlay(packet)
        || !HookBridge.beforeSend((NetworkManager) (Object) this, packet)) {
      if (play) HookBridge.acceptedOutgoing(packet);
      return;
    }
    callback.cancel();
    RawHookBridge.completeCancellation(channel, listeners);
  }

  @Inject(
      method =
          "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void perihelion$beforeReceive(
      ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
    if (!perihelion$isClientPlay(packet)
        || !HookBridge.beforeReceive((NetworkManager) (Object) this, packet)) return;
    callback.cancel();
    RawHookBridge.releaseCancelledInbound(packet);
  }
}
