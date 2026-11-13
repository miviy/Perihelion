package perihelion.integration.minecraft189.account;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.GuiScreen;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Delivery selection never renders credential material. */
public final class AccountDeliveryScreen extends AccountUi {
  private final AccountStores.Provider provider;
  private final JsonObject delivery;
  private final BitSet selected = new BitSet();
  private final boolean force;
  private int scroll;

  public AccountDeliveryScreen(
      AccountService service,
      GuiScreen parent,
      AccountStores.Provider provider,
      JsonObject delivery,
      boolean force) {
    super(service, parent);
    this.provider = provider;
    this.delivery = delivery;
    this.force = force;
    selected.set(0, AccountHttp.array(delivery, "items").size());
  }

  @Override
  protected String title() {
    return "Delivery / " + AccountHttp.text(delivery, "id");
  }

  @Override
  protected void content(float mx, float my) {
    JsonArray items = AccountHttp.array(delivery, "items");
    label(AccountHttp.text(delivery, "name"), 12, 42, 428, canvas.enabled);
    label(items.size() + " delivered items. Credentials stay hidden.", 12, 59, 428, canvas.muted);
    for (int i = scroll; i < Math.min(items.size(), scroll + 6); i++) {
      float y = 82 + (i - scroll) * 23;
      if (selected.get(i) || UiCanvas.inside(mx, my, 12, y, 428, 22))
        surface(12, y, 428, 22, selected.get(i) ? canvas.selection : canvas.hover);
      String name = "Account " + (i + 1);
      if (items.get(i).isJsonObject()) {
        String username = AccountHttp.text(items.get(i).getAsJsonObject(), "username");
        if (!username.isEmpty()) name = username;
      }
      if (canvas.modern) {
        ModernTheme.checkbox(canvas, 20, y + 6, selected.get(i));
        label(name, 36, canvas.font.centeredY(y, 22), 392, canvas.text);
      } else
        label((selected.get(i) ? "Selected / " : "Skipped / ") + name, 18, y + 7, 410, canvas.text);
    }
    button("Select all", 12, 228, 104, true, () -> selected.set(0, items.size()));
    button("Select none", 120, 228, 104, true, selected::clear);
    button(
        force ? "Retry selected" : "Import selected",
        228,
        228,
        212,
        idle() && !selected.isEmpty(),
        () -> {
          JsonObject subset = new JsonParser().parse(delivery.toString()).getAsJsonObject();
          JsonArray values = new JsonArray();
          for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1))
            values.add(items.get(i));
          subset.add("items", values);
          // Keep the complete recovery journal; the subset is only used for this import.
          start(
              service.submit(
                  "Importing selected delivery...",
                  j -> service.stores.importSelection(provider, delivery, subset, force, j),
                  this::result));
        });
    label(
        force
            ? "Retry mode: previously imported and dead items are included."
            : "Hold Shift when viewing an order to retry skipped or dead items.",
        12,
        252,
        428,
        canvas.muted);
  }

  @Override
  protected void contentClick(float x, float y, int button) {
    if (button == 0 && UiCanvas.inside(x, y, 12, 82, 428, 138)) {
      int i = scroll + (int) ((y - 82) / 23);
      if (i < AccountHttp.array(delivery, "items").size()) selected.flip(i);
    }
  }

  @Override
  protected void wheel(int delta) {
    scroll =
        Math.max(
            0,
            Math.min(Math.max(0, AccountHttp.array(delivery, "items").size() - 6), scroll + delta));
  }
}
