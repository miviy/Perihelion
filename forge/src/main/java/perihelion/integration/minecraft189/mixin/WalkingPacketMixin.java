package perihelion.integration.minecraft189.mixin;

import net.minecraft.network.play.client.C03PacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import perihelion.integration.minecraft189.control.WalkingPacketAccess;

@Mixin(C03PacketPlayer.class)
public abstract class WalkingPacketMixin implements WalkingPacketAccess {
  @Shadow protected double y;

  public void perihelion$packetY(double y) {
    if (!Double.isFinite(y)) throw new IllegalArgumentException("Nonfinite walking packet Y");
    this.y = y;
  }
}
