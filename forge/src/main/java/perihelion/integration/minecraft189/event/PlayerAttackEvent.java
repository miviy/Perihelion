package perihelion.integration.minecraft189.event;

import net.minecraft.entity.EntityLivingBase;

/** Client-thread notification before the local controller submits a living-entity attack. */
public final class PlayerAttackEvent extends perihelion.core.event.CancellableEvent {
    private final EntityLivingBase target;

    public PlayerAttackEvent(EntityLivingBase target) {
        this.target = target;
    }

    public EntityLivingBase target() {
        return target;
    }
}
