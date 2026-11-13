package perihelion.core.event;

@FunctionalInterface
public interface EventListener<E> {
    void onEvent(E event) throws Exception;
}
