package perihelion.integration.minecraft189.account;

import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/** Small render bridge for the local player's optional override; owned by the client runtime. */
public final class LocalAccountSkin implements AutoCloseable {
  private static LocalAccountSkin current;
  private ResourceLocation texture;
  private boolean slim;

  public void apply(BufferedImage image, boolean slim) {
    close();
    this.slim = slim;
    BufferedImage normalized = new ImageBufferDownload().parseUserSkin(image);
    texture =
        Minecraft.getMinecraft()
            .getTextureManager()
            .getDynamicTextureLocation("perihelion-account-skin", new DynamicTexture(normalized));
    current = this;
  }

  public static ResourceLocation location() {
    return current == null ? null : current.texture;
  }

  public static String model() {
    return current != null && current.slim ? "slim" : "default";
  }

  @Override
  public void close() {
    if (texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
    texture = null;
    if (current == this) current = null;
  }
}
