package perihelion.bootstrap.forge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import perihelion.core.diagnostics.ClientLog;

final class Log4jClientLog implements ClientLog {
    private final Logger logger = LogManager.getLogger("Perihelion");
    public void debug(String message) { logger.debug(message); }
    public void info(String message) { logger.info(message); }
    public void warn(String message) { logger.warn(message); }
    public void error(String message, Throwable failure) { logger.error(message, failure); }
}
