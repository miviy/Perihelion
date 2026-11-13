package perihelion.integration.minecraft189.control;

/** Access to vanilla's transient right-click cooldown, not a held-input override. */
public interface ItemUseDelayAccess {
    int perihelion$itemUseDelay();
    void perihelion$itemUseDelay(int ticks);
}
