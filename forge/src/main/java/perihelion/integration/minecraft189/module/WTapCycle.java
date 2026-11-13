package perihelion.integration.minecraft189.module;

/** One activation's pending sprint reset. No entities or keys survive between callbacks. */
final class WTapCycle {
    private boolean pending;
    boolean attack(int hurtTime, boolean sprinting, double chance, double roll) {
        if (hurtTime < 6 || roll * 100 >= chance) { pending = false; return false; }
        if (sprinting) pending = true;
        return pending;
    }
    boolean update(double chance, double roll) {
        if (!pending || roll * 100 >= chance) return false;
        pending = false;
        return true;
    }
    void clear() { pending = false; }
}
