package perihelion.core.diagnostics;

/** Logging port; the loader supplies its existing logging backend. */
public interface ClientLog {
    void debug(String message);
    void info(String message);
    void warn(String message);
    void error(String message, Throwable failure);

    ClientLog NONE = new ClientLog() {
        public void debug(String message) { }
        public void info(String message) { }
        public void warn(String message) { }
        public void error(String message, Throwable failure) { }
    };
}
