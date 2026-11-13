package perihelion.integration.minecraft189.network;

import java.io.IOException;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleContext;
import perihelion.integration.minecraft189.event.PacketReceiveEvent;
import perihelion.integration.minecraft189.event.PacketSendEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Activation-owned packet buffering. Replay is client-thread-only and connection-bound. */
public final class PacketBufferScope implements AutoCloseable {
  private final ClientRuntime runtime;
  private final long generation;
  private final NetHandlerPlayClient handler;
  private final NetworkManager manager;
  private final Object world;
  private final PacketOrderBarrier<PacketBufferScope> ordering;
  private final OrderedPacketQueue<PacketSnapshot> queue =
      new OrderedPacketQueue<>(8192, 16 * 1024 * 1024);
  private volatile boolean collecting = true;
  private volatile boolean overflow;

  public PacketBufferScope(
      ClientRuntime runtime,
      ModuleContext context,
      BiPredicate<Packet<?>, Boolean> filter,
      LongSupplier delay) {
    this.runtime = runtime;
    generation = runtime.tasks().generation();
    handler = Minecraft.getMinecraft().getNetHandler();
    manager = handler == null ? null : handler.getNetworkManager();
    world = Minecraft.getMinecraft().theWorld;
    ordering = perihelion.integration.minecraft189.HookBridge.gameControls().outboundOrder();
    context.own(this);
    context.listenNetwork(
        PacketSendEvent.class,
        -1000,
        false,
        event -> {
          if (capture(event.source(), event.packet(), true, filter, delay)) {
            if (overflow) event.cancel();
            else event.defer();
          }
        });
    context.listenNetwork(
        PacketReceiveEvent.class,
        -1000,
        false,
        event -> {
          if (capture(event.source(), event.packet(), false, filter, delay)) event.cancel();
        });
    context.listen(
        PlayerControlEvent.class,
        event -> {
          if (event != PlayerControlEvent.AFTER_MOTION) return;
          if (overflow) {
            // Fault drops the entire backlog: partial stale replay is worse than
            // releasing it.
            throw new IllegalStateException("Packet buffer capacity exceeded; backlog discarded");
          }
          flush(false);
        });
  }

  private synchronized boolean capture(
      NetworkManager source,
      Packet<?> packet,
      boolean outbound,
      BiPredicate<Packet<?>, Boolean> filter,
      LongSupplier delay)
      throws IOException {
    synchronized (ordering) {
      if (generation != runtime.tasks().generation() || source != null && source != manager)
        return false;
      boolean dependent = outbound && dependent(packet);
      PacketBufferScope first = ordering.owner();
      if (dependent && first != null && first != this) return false;
      if (!(collecting && filter.test(packet, outbound) || dependent && first == this))
        return false;
      PacketSnapshot copy = PacketSnapshot.copy(packet, outbound);
      long milliseconds = delay.getAsLong();
      long due =
          milliseconds == Long.MAX_VALUE
              ? Long.MAX_VALUE
              : System.nanoTime() / 1_000_000L + milliseconds;
      if (!queue.offer(copy, due, copy.size())) {
        overflow = true;
        collecting = false;
      } else if (dependent && !ordering.queued(this)) {
        throw new IllegalStateException("Outbound sequence changed owner during capture");
      }
    }
    return true;
  }

  private static boolean dependent(Packet<?> packet) {
    return packet instanceof C03PacketPlayer
        || packet instanceof C02PacketUseEntity
        || packet instanceof C07PacketPlayerDigging
        || packet instanceof C08PacketPlayerBlockPlacement
        || packet instanceof C09PacketHeldItemChange
        || packet instanceof C0APacketAnimation
        || packet instanceof C0BPacketEntityAction;
  }

  public synchronized void collecting(boolean value) {
    runtime.tasks().checkThread();
    collecting = value;
  }

  private boolean current() {
    Minecraft game = Minecraft.getMinecraft();
    return !overflow
        && runtime.isRunning()
        && generation == runtime.tasks().generation()
        && game.theWorld == world
        && game.getNetHandler() == handler
        && handler != null
        && manager != null
        && handler.getNetworkManager() == manager
        && manager.isChannelOpen();
  }

  public void flush(boolean all) {
    runtime.tasks().checkThread();
    if (!current()) {
      queue.close();
      ordering.discard(this);
      return;
    }
    do {
      for (PacketSnapshot copy : queue.drain(System.nanoTime() / 1_000_000L, all)) {
        if (!current()) break;
        try {
          Packet<?> packet = copy.decode();
          if (copy.outbound()) {
            RawHookBridge.replay(() -> manager.sendPacket(packet));
            if (dependent(packet)) ordering.replayed(this);
          } else processInbound(packet);
        } catch (IOException failure) {
          throw new IllegalStateException("Buffered packet decode failed", failure);
        }
      }
    } while (all && current() && queue.size() > 0);
  }

  @SuppressWarnings("unchecked")
  private void processInbound(Packet<?> packet) {
    ((Packet<INetHandlerPlayClient>) packet).processPacket(handler);
  }

  @Override
  public synchronized void close() {
    runtime.tasks().checkThread();
    collecting = false;
    try {
      flush(true);
    } finally {
      queue.close();
      ordering.discard(this);
      if (overflow && perihelion.integration.minecraft189.HookBridge.gameControls() != null)
        perihelion.integration.minecraft189.HookBridge.gameControls().requestRotationResend();
    }
  }
}
