package perihelion.core.event;

import perihelion.core.context.ConnectionContext;

public final class WorldContextEvent {
    private final long generation;
    private final boolean worldReady;
    private final ConnectionContext connection;
    public WorldContextEvent(long generation, boolean worldReady, ConnectionContext connection) {
        this.generation = generation;
        this.worldReady = worldReady;
        this.connection = connection;
    }
    public long generation() { return generation; }
    public boolean worldReady() { return worldReady; }
    public ConnectionContext connection() { return connection; }
}
