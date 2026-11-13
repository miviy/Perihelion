package perihelion.core.command;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public interface ClientCommand {
    String name();
    String usage();
    void execute(List<String> arguments, Consumer<String> output) throws Exception;
    default List<String> complete(List<String> arguments) { return Collections.emptyList(); }
}
