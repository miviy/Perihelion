package perihelion.core.module;

import java.util.Objects;

public final class ModuleInfo {
    private final String id, name, description;
    private final ModuleCategory category;
    private final boolean requiresWorld;
    public ModuleInfo(String id, String name, String description, ModuleCategory category, boolean requiresWorld) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]*")) throw new IllegalArgumentException("Invalid module ID: " + id);
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Module name must not be blank");
        this.description = Objects.requireNonNull(description, "description");
        this.category = Objects.requireNonNull(category, "category");
        this.requiresWorld = requiresWorld;
    }
    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public ModuleCategory category() { return category; }
    public boolean requiresWorld() { return requiresWorld; }
}
