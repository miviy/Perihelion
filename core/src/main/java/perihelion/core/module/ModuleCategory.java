package perihelion.core.module;

/** Fixed, GUI-independent groups for module definitions; not persisted in config. */
public enum ModuleCategory {
  COMBAT("Combat"),
  MOVEMENT("Movement"),
  PLAYER("Player"),
  WORLD("World"),
  RENDER("Render"),
  MISC("Misc"),
  CLIENT("Client");

  private final String displayName;

  ModuleCategory(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
