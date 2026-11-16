package perihelion.integration.minecraft189.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import perihelion.core.diagnostics.Failures;
import perihelion.integration.minecraft189.HookBridge;

/** Packet callback ownership ends before completion listeners or payload cleanup run. */
public final class RawHookBridge {
  private static final ThreadLocal<Boolean> DISPATCHING = new ThreadLocal<Boolean>();

  private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<Boolean>();
  private static final ThreadLocal<NetworkManager> SOURCE = new ThreadLocal<NetworkManager>();

  private RawHookBridge() {}

  /** Silent snapshot replay must not count as a second observation of the same action. */
  public static boolean isReplaying() {
    return Boolean.TRUE.equals(REPLAYING.get());
  }

  /** Only release snapshots outside raw callbacks, on the Minecraft client thread. */
  public static void replay(Runnable send) {
    perihelion.integration.minecraft189.MinecraftContext.requireClientThread();
    if (Boolean.TRUE.equals(DISPATCHING.get()) || Boolean.TRUE.equals(REPLAYING.get())) {
      throw new IllegalStateException("Recursive packet replay");
    }
    REPLAYING.set(Boolean.TRUE);
    try {
      send.run();
    } finally {
      REPLAYING.set(Boolean.FALSE);
    }
  }

  /** Identity is available only during one synchronous raw callback, never across replay. */
  public static NetworkManager source() {
    return Boolean.TRUE.equals(DISPATCHING.get()) ? SOURCE.get() : null;
  }

  public static boolean dispatch(HookBridge.Target target, Packet<?> packet, boolean outbound) {
    return dispatch(target, null, packet, outbound);
  }

  public static boolean dispatch(
      HookBridge.Target target, NetworkManager source, Packet<?> packet, boolean outbound) {
    // Deliberately outside the isolation catch: the outer listener owns recursive work.
    if (Boolean.TRUE.equals(DISPATCHING.get())) {
      throw new IllegalStateException("Raw packet callbacks cannot recursively dispatch packets");
    }
    if (target == null || Boolean.TRUE.equals(REPLAYING.get())) return false;
    try {
      if (!target.isRunning()) return false;
      DISPATCHING.set(Boolean.TRUE);
      if (source != null) SOURCE.set(source);
      return outbound ? target.beforeSend(packet) : target.beforeReceive(packet);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      HookBridge.reportFailure(outbound ? "before-send" : "before-receive", failure);
      return false;
    } finally {
      // Clear the reference but keep both map entries: remove() would discard the entry and
      // make the next packet on this thread allocate a new one.
      if (source != null) SOURCE.set(null);
      DISPATCHING.set(Boolean.FALSE);
    }
  }

  public static void completeCancellation(
      Channel channel, GenericFutureListener<? extends Future<? super Void>>[] listeners) {
    if (listeners == null || listeners.length == 0) return;
    try {
      ChannelPromise completion = channel.newPromise();
      completion.addListeners(listeners);
      completion.cancel(false);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      HookBridge.reportFailure("cancelled-send-completion", failure);
    }
  }

  /** A cancelled inbound custom payload never reaches the vanilla owner that releases it. */
  public static void releaseCancelledInbound(Packet<?> packet) {
    if (!(packet instanceof S3FPacketCustomPayload)) return;
    try {
      PacketBuffer payload = ((S3FPacketCustomPayload) packet).getBufferData();
      if (payload != null) payload.release();
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      HookBridge.reportFailure("cancelled-inbound-payload", failure);
    }
  }
}
