package perihelion.integration.minecraft189.event;

import net.minecraft.network.Packet;

import perihelion.core.event.CancellableEvent;

/** Selected play packets after vanilla's thread handoff, before world mutation. */
public final class ClientPacketEvent extends CancellableEvent {
    private final Packet<?> packet;

    public ClientPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> packet() {
        return packet;
    }
}
