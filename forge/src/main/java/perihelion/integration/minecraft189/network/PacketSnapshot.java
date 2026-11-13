package perihelion.integration.minecraft189.network;

import io.netty.buffer.Unpooled;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import java.io.IOException;

/** Encoded, privately owned bytes; never retains a Netty payload or mutable packet. */
public final class PacketSnapshot {
    private final EnumPacketDirection direction;
    private final int id;
    private final byte[] bytes;

    private PacketSnapshot(EnumPacketDirection direction, int id, byte[] bytes) {
        this.direction = direction;
        this.id = id;
        this.bytes = bytes;
    }

    public static PacketSnapshot copy(Packet<?> packet, boolean outbound) throws IOException {
        EnumPacketDirection direction =
                outbound ? EnumPacketDirection.SERVERBOUND : EnumPacketDirection.CLIENTBOUND;
        Integer id = EnumConnectionState.PLAY.getPacketId(direction, packet);
        if (id == null) throw new IOException("Not a vanilla play packet");
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer(256, 2 * 1024 * 1024));
        PacketBuffer payload =
                packet instanceof S3FPacketCustomPayload
                        ? ((S3FPacketCustomPayload) packet).getBufferData()
                        : packet instanceof C17PacketCustomPayload
                                ? ((C17PacketCustomPayload) packet).getBufferData()
                                : null;
        int reader = payload == null ? 0 : payload.readerIndex();
        try {
            packet.writePacketData(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new PacketSnapshot(direction, id, data);
        } finally {
            if (payload != null) payload.readerIndex(reader);
            buffer.release();
        }
    }

    public boolean outbound() {
        return direction == EnumPacketDirection.SERVERBOUND;
    }

    public int size() {
        return bytes.length;
    }

    public Packet<?> decode() throws IOException {
        PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
        try {
            Packet<?> packet = EnumConnectionState.PLAY.getPacket(direction, id);
            if (packet == null) throw new IOException("Unknown buffered play packet " + id);
            packet.readPacketData(buffer);
            return packet;
        } catch (InstantiationException | IllegalAccessException error) {
            throw new IOException("Cannot recreate play packet", error);
        } finally {
            buffer.release();
        }
    }
}
