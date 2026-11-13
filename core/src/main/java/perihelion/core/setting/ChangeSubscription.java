package perihelion.core.setting;

/** A client-thread-owned, idempotently removable setting observer. */
public interface ChangeSubscription extends AutoCloseable {
    @Override
    void close();
}
