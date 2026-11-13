package perihelion.integration.minecraft189.event;

import java.util.Objects;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import perihelion.core.event.CancellableEvent;

/** Synchronous inbound callback. The game may not have processed this packet yet. */
public final class PacketReceiveEvent extends CancellableEvent {
  private final Packet<?> packet;
  private final NetworkManager source;

  /** Source-null constructor retained for manually posted/synthetic hook fixtures. */
  public PacketReceiveEvent(Packet<?> packet) {
    this(null, packet);
  }

  public PacketReceiveEvent(NetworkManager source, Packet<?> packet) {
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
}
