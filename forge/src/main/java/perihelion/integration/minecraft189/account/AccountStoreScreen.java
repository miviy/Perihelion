package perihelion.integration.minecraft189.account;

import java.util.*;
import net.minecraft.client.gui.GuiScreen;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Three providers, one catalogue and purchase confirmation. */
public final class AccountStoreScreen extends AccountUi {
  private AccountStores.Provider provider = AccountStores.Provider.LOCALTS;
  private AccountStores.Catalog catalog;
  private Input key, search, quantity;
  private List<AccountStores.Product> products = Collections.emptyList();
  private String selected = "";
  private int scroll;

  public AccountStoreScreen(AccountService service, GuiScreen parent) {
    super(service, parent);
  }

  @Override
  protected String title() {
    return "Account stores";
  }

  @Override
  protected float preferredHeight() {
    return catalog == null ? 190 : 294;
  }

  @Override
  protected void setup() {
    key = input(true, 2048);
    search = input(false, 64);
    quantity = input(false, 3);
    quantity.text("1");
  }

  @Override
  protected void content(float mx, float my) {
    for (int i = 0; i < AccountStores.Provider.values().length; i++) {
      AccountStores.Provider p = AccountStores.Provider.values()[i];
      navigation(
          p.label,
          16 + i * (428f / 3),
          42,
          404f / 3,
          20,
          p == provider,
          idle(),
          () -> {
            provider = p;
            catalog = null;
            products = Collections.emptyList();
            selected = "";
            key.text("");
            scroll = 0;
          });
    }
    field(key, "API key (blank uses saved key)", 16, 74, 306);
    button(
        "Connect",
        330,
        74,
        106,
        idle(),
        () -> {
          AccountStores.Provider p = provider;
          String entered = key.text();
          start(
              service.submit(
                  "Connecting to " + p.label + "...",
                  j ->
                      service.stores.connect(
                          p, entered.isEmpty() ? service.stores.savedKey(p) : entered),
                  value -> {
                    catalog = value;
                    selected = "";
                    scroll = 0;
                    key.text("");
                    result("Connected.");
                  }));
        });
    if (catalog == null) {
      label("Connect with your store API key.", 16, 106, 420, canvas.muted);
      button(
          "Open store website",
          16,
          130,
          204,
          idle(),
          () ->
              start(
                  service.submit(
                      "Opening store...",
                      j -> {
                        service.browser(provider.website, j);
                        return true;
                      },
                      ignored -> {})));
      return;
    }
    label(
        catalog.user + "  /  Balance: " + catalog.balance + " credits",
        16,
        102,
        420,
        canvas.enabled);
    field(search, "Products or categories", 16, 119, 218);
    field(quantity, "Qty", 242, 119, 54);
    button(
        "History / extras",
        304,
        119,
        132,
        idle(),
        () -> mc.displayGuiScreen(new AccountStoreToolsScreen(service, this, catalog)));
    products = new ArrayList<>();
    String q = search.text().toLowerCase(Locale.ROOT);
    for (AccountStores.Product p : catalog.products)
      if ((p.name + " " + p.category).toLowerCase(Locale.ROOT).contains(q)) products.add(p);
    scroll = Math.max(0, Math.min(scroll, Math.max(0, products.size() - 3)));
    for (int i = scroll; i < Math.min(products.size(), scroll + 3); i++) {
      AccountStores.Product p = products.get(i);
      float y = 143 + (i - scroll) * 29;
      if (p.id.equals(selected) || UiCanvas.inside(mx, my, 12, y, 428, 29))
        surface(12, y, 428, 29, p.id.equals(selected) ? canvas.selection : canvas.hover);
      label(p.name, 18, y + 4, 320, canvas.text);
      label(
          p.price.stripTrailingZeros().toPlainString() + " credits",
          332,
          y + 4,
          102,
          canvas.enabled);
      label(
          p.category + "  /  " + p.stock + " in stock  /  max " + p.limit,
          18,
          y + 17,
          410,
          canvas.muted);
    }
    if (products.isEmpty()) label("No matching products.", 18, 153, 400, canvas.muted);
    AccountStores.Product chosen = chosen();
    String total = "Choose a product";
    boolean valid = false;
    if (chosen != null)
      try {
        total =
            "Total: " + chosen.total(amount()).stripTrailingZeros().toPlainString() + " credits";
        valid = true;
      } catch (IllegalArgumentException e) {
        total = e.getMessage();
      }
    label(total, 12, 235, 428, canvas.muted);
    button(
        "Buy selected",
        12,
        248,
        208,
        idle() && valid,
        () -> {
          AccountStores.Product product = chosen();
          int amount = amount();
          AccountStores.Catalog snapshot = catalog;
          String price = product.total(amount).stripTrailingZeros().toPlainString();
          confirm(
              "Buy "
                  + amount
                  + " x "
                  + product.name
                  + " from "
                  + snapshot.provider.label
                  + " for "
                  + price
                  + " credits? Purchases are charged once and deliveries are saved for recovery.",
              () ->
                  start(
                      service.submit(
                          "Purchasing...",
                          j -> service.stores.purchase(snapshot, product, amount, j),
                          this::result)));
        });
    button(
        "Refresh balance / stock",
        224,
        248,
        216,
        idle(),
        () -> {
          AccountStores.Catalog old = catalog;
          start(
              service.submit(
                  "Refreshing store...",
                  j -> service.stores.connect(old.provider, old.key),
                  fresh -> {
                    catalog = fresh;
                    result("Store refreshed.");
                  }));
        });
  }

  private int amount() {
    try {
      return Integer.parseInt(quantity.text());
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("Enter a quantity between 1 and the product limit.");
    }
  }

  private AccountStores.Product chosen() {
    for (AccountStores.Product p : products) if (p.id.equals(selected)) return p;
    return null;
  }

  @Override
  protected void contentClick(float x, float y, int button) {
    if (button == 0 && UiCanvas.inside(x, y, 12, 143, 428, 87)) {
      int index = scroll + (int) ((y - 143) / 29);
      if (index < products.size()) selected = products.get(index).id;
    }
  }

  @Override
  protected void wheel(int delta) {
    scroll += delta;
  }
}
