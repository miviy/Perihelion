package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.spongepowered.asm.mixin.*;
import perihelion.integration.minecraft189.account.SessionAccess;

@Mixin(Minecraft.class)
public abstract class AccountSessionMixin implements SessionAccess {
  @Shadow @Final @Mutable private Session session;
  @Shadow @Final private com.mojang.authlib.properties.PropertyMap profileProperties;

  @Override
  public void perihelion$session(Session value) {
    Minecraft game = (Minecraft) (Object) this;
    if (!game.isCallingFromMinecraftThread()
        || game.theWorld != null
        || game.getNetHandler() != null)
      throw new IllegalStateException("Disconnect before switching accounts.");
    session = value;
    profileProperties.clear();
  }
}
