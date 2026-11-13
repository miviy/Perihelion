package perihelion.core.event;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override void close();
}
