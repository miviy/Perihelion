package perihelion.integration.minecraft189.network;

import static org.junit.Assert.*;

import io.netty.buffer.Unpooled;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.junit.Test;

public class PacketSnapshotTest {
    @Test
    public void movementSnapshotRecreatesTheExactSubtypeAndCoordinates() throws Exception {
        C03PacketPlayer packet =
                new C03PacketPlayer.C06PacketPlayerPosLook(1.25, 64.125, -14, 42, -30, false);
        PacketSnapshot snapshot = PacketSnapshot.copy(packet, true);
        C03PacketPlayer decoded = (C03PacketPlayer) snapshot.decode();
        assertTrue(snapshot.outbound());
        assertNotSame(packet, decoded);
        assertEquals(packet.getClass(), decoded.getClass());
        assertEquals(1.25, decoded.getPositionX(), 0);
        assertEquals(64.125, decoded.getPositionY(), 0);
        assertEquals(-14, decoded.getPositionZ(), 0);
        assertEquals(42, decoded.getYaw(), 0);
        assertEquals(-30, decoded.getPitch(), 0);
        assertFalse(decoded.isOnGround());
    }

    @Test
    public void customPayloadSurvivesOriginalReleaseWithoutChangingReaderIndex() throws Exception {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeByte(11);
        payload.writeByte(22);
        payload.readerIndex(1);
        PacketSnapshot snapshot;
        try {
            snapshot = PacketSnapshot.copy(new S3FPacketCustomPayload("TEST", payload), false);
            assertEquals(1, payload.readerIndex());
        } finally {
            payload.release();
        }
        S3FPacketCustomPayload decoded = (S3FPacketCustomPayload) snapshot.decode();
        try {
            assertEquals("TEST", decoded.getChannelName());
            assertEquals(22, decoded.getBufferData().readUnsignedByte());
        } finally {
            decoded.getBufferData().release();
        }
    }
}
