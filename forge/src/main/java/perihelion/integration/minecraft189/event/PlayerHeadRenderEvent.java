package perihelion.integration.minecraft189.event;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelRenderer;

/** Render-thread model-space hook after a player model, inside the owning renderer transform. */
public final class PlayerHeadRenderEvent {
  private final AbstractClientPlayer player;
  private final ModelRenderer head;
  private final float partial;

  public PlayerHeadRenderEvent(AbstractClientPlayer player, ModelRenderer head, float partial) {
    this.player = player;
    this.head = head;
    this.partial = partial;
  }

  public AbstractClientPlayer player() {
    return player;
  }

  public ModelRenderer head() {
    return head;
  }

  public float partialTicks() {
    return partial;
  }
}
