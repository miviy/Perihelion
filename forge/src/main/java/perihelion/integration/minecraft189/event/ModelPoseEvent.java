package perihelion.integration.minecraft189.event;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.player.EntityPlayer;

/** Client-thread pose observation. Consumers must copy angles, never mutate or retain the model. */
public final class ModelPoseEvent {
    private final EntityPlayer player;
    private final ModelPlayer model;

    public ModelPoseEvent(EntityPlayer player, ModelPlayer model) {
        this.player = player;
        this.model = model;
    }

    public EntityPlayer player() {
        return player;
    }

    public ModelPlayer model() {
        return model;
    }
}
