package perihelion.integration.minecraft189.event;

import java.util.Objects;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import perihelion.core.event.CancellableEvent;

/** Synchronous before-send callback. Inspect/cancel only; copy data before deferring work. */
public final class PacketSendEvent extends CancellableEvent {
  private final Packet<?> packet;
  private final NetworkManager source;
  private boolean deferred;

  /** Source-null constructor retained for manually posted/synthetic hook fixtures. */
  public PacketSendEvent(Packet<?> packet) {
    this(null, packet);
  }

  public PacketSendEvent(NetworkManager source, Packet<?> packet) {
    this.source = source;
    this.packet = Objects.requireNonNull(packet, "packet");
  }

  /** Actual manager for production hooks, null only for legacy synthetic dispatch. */
  public NetworkManager source() {
    return source;
  }

  public Packet<?> packet() {
    return packet;
  }

  public void defer() {
    deferred = true;
    cancel();
  }

  public boolean isDeferred() {
    return deferred;
  }
}
