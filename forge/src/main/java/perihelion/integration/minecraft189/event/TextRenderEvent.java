package perihelion.integration.minecraft189.event;

/** Client-side text shared by drawing and measurement; never changes game or network data. */
public final class TextRenderEvent {
  private String text;
  public TextRenderEvent(String text) { this.text = text; }
  public String text() { return text; }
  public void text(String next) { text = java.util.Objects.requireNonNull(next, "text"); }
}
