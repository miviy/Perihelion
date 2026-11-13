package perihelion.integration.minecraft189.event;

import net.minecraft.entity.EntityLivingBase;

import perihelion.core.event.CancellableEvent;

/** Cancellation suppresses the vanilla name label for this draw only. */
public final class NameRenderEvent extends CancellableEvent {
    private final EntityLivingBase entity;

    public NameRenderEvent(EntityLivingBase entity) {
        this.entity = entity;
    }

    public EntityLivingBase entity() {
        return entity;
    }
}
