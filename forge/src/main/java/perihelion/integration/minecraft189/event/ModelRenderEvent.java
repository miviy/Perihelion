package perihelion.integration.minecraft189.event;

import net.minecraft.entity.EntityLivingBase;

import perihelion.core.event.CancellableEvent;

/** Cancellation requests a scoped through-wall model draw. */
public final class ModelRenderEvent extends CancellableEvent {
    private final EntityLivingBase entity;

    public ModelRenderEvent(EntityLivingBase entity) {
        this.entity = entity;
    }

    public EntityLivingBase entity() {
        return entity;
    }
}
