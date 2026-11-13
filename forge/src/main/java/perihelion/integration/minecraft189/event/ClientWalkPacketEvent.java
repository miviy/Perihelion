package perihelion.integration.minecraft189.event;

import net.minecraft.network.play.client.C03PacketPlayer;
import perihelion.integration.minecraft189.control.WalkingPacketAccess;

/** Fresh current-connection client-thread C03 preflight; never posted for buffered replay. */
public final class ClientWalkPacketEvent {
  private final C03PacketPlayer packet;

  public ClientWalkPacketEvent(C03PacketPlayer packet) {
    this.packet = packet;
  }

  public double x() {
    return packet.getPositionX();
  }

  public double y() {
    return packet.getPositionY();
  }

  public double z() {
    return packet.getPositionZ();
  }

  public boolean grounded() {
    return packet.isOnGround();
  }

  public void y(double value) {
    ((WalkingPacketAccess) packet).perihelion$packetY(value);
  }
}
