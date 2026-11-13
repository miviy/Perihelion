package perihelion.integration.minecraft189.account;

import java.util.*;
import net.minecraft.client.gui.GuiScreen;

/** Purchase recovery and provider extras appear only when selected. */
public final class AccountStoreToolsScreen extends AccountUi {
  private final AccountStores.Catalog catalog;
  private int tab, scroll, protocol, category;
  private boolean chat;
  private Input id, server, peso, reason;
  private List<String> orders = Collections.emptyList();

  public AccountStoreToolsScreen(
      AccountService service, GuiScreen parent, AccountStores.Catalog catalog) {
    super(service, parent);
    this.catalog = catalog;
  }

  @Override
  protected String title() {
    return catalog.provider.label + " / History and tools";
  }

  @Override
  protected void setup() {
    id = input(false, 128);
    server = input(false, 255);
    peso = input(true, 512);
    reason = input(false, 50);
    reason.text("Items not working");
  }

  @Override
  protected void content(float mx, float my) {
    button("History", 12, 43, 130, 20, tab == 0, idle(), () -> tab = 0);
    if (catalog.provider == AccountStores.Provider.NICEALTS) {
      button("Custom purchase", 12, 68, 130, 20, tab == 1, idle(), () -> tab = 1);
      button("Generator", 12, 93, 130, 20, tab == 2, idle(), () -> tab = 2);
    }
    if (catalog.provider == AccountStores.Provider.FERNAN) {
      button("Redeem Peso key", 12, 68, 130, 20, tab == 3, idle(), () -> tab = 3);
      button("Request refund", 12, 93, 130, 20, tab == 4, idle(), () -> tab = 4);
    }
    if (tab == 0) {
      field(id, "Order or delivery ID", 156, 43, 280);
      button(
          "View delivery",
          156,
          69,
          136,
          idle() && !id.text().isEmpty(),
          () -> {
            String order = id.text().trim();
            boolean retry = isShiftKeyDown();
            start(
                service.submit(
                    "Reading delivery...",
                    j -> service.stores.order(catalog, order),
                    delivery ->
                        mc.displayGuiScreen(
                            new AccountDeliveryScreen(
                                service, this, catalog.provider, delivery, retry))));
          });
      button(
          "Refresh history",
          296,
          69,
          140,
          idle(),
          () ->
              start(
                  service.submit(
                      "Reading history...",
                      j -> service.stores.history(catalog, j),
                      list -> {
                        orders = list;
                        scroll = 0;
                        result("Select an order to inspect its delivery.");
                      })));
      for (int i = scroll; i < Math.min(orders.size(), scroll + 5); i++) {
        String order = orders.get(i);
        button(
            order,
            156,
            98 + (i - scroll) * 22,
            280,
            20,
            order.equals(id.text()),
            idle(),
            () -> id.text(order));
      }
      button(
          "Scan / import history",
          156,
          217,
          280,
          idle(),
          () -> {
            boolean full = isShiftKeyDown();
            start(
                service.submit(
                    "Scanning purchases...",
                    j -> service.stores.scan(catalog, full, j),
                    this::result));
          });
      label("Shift+Scan reads all and retries dead items.", 156, 244, 280, canvas.muted);
      return;
    }
    if (tab == 1) {
      label("Buy one account checked against a server.", 156, 46, 280, canvas.text);
      field(server, "Server (host or host:port)", 156, 74, 280);
      String[] protocols = {"1.8", "1.21", "26.1"};
      boolean minemen = server.text().toLowerCase(Locale.ROOT).contains("minemen");
      button(
          "Protocol: " + protocols[protocol],
          156,
          105,
          136,
          idle(),
          () -> protocol = (protocol + 1) % 3);
      button(
          chat || minemen ? "Check: chat" : "Check: normal",
          296,
          105,
          140,
          idle() && !minemen,
          () -> chat = !chat);
      label(
          "Price: " + (chat || minemen ? 6 : 5) + " credits / one account",
          156,
          141,
          280,
          canvas.enabled);
      button(
          "Buy custom account",
          156,
          174,
          280,
          idle() && !server.text().isEmpty(),
          () -> {
            String host = server.text().trim(), version = protocols[protocol];
            boolean mode = chat || minemen;
            confirm(
                "Buy one custom account for "
                    + host
                    + " on "
                    + version
                    + " for "
                    + (mode ? 6 : 5)
                    + " credits?",
                () ->
                    start(
                        service.submit(
                            "Buying custom account...",
                            j -> service.stores.custom(catalog, host, version, mode, j),
                            this::result)));
          });
    } else if (tab == 2) {
      String[] categories = {"Unbanned", "DonutSMP", "Banned"};
      label("Requires a Premium / Premium+ subscription.", 156, 46, 280, canvas.text);
      button(
          "Category: " + categories[category],
          156,
          87,
          280,
          idle(),
          () -> category = (category + 1) % 3);
      label("No credits charged. Generated access tokens expire.", 156, 123, 280, canvas.muted);
      button(
          "Generate account",
          156,
          159,
          280,
          idle(),
          () -> {
            String type = categories[category];
            start(
                service.submit(
                    "Generating...", j -> service.stores.generate(catalog, type, j), this::result));
          });
    } else if (tab == 3) {
      label("Add a Peso key's value to your Fernan balance.", 156, 46, 280, canvas.text);
      field(peso, "Peso key", 156, 87, 280);
      button(
          "Redeem key",
          156,
          123,
          280,
          idle() && !peso.text().isEmpty(),
          () -> {
            String key = peso.text();
            confirm(
                "Redeem this Peso key into the connected Fernan Club account?",
                () ->
                    start(
                        service.submit(
                            "Redeeming key...",
                            j -> service.stores.redeem(catalog, key),
                            this::result)));
          });
    } else {
      label("Request a refund for confirmed dead delivery items.", 156, 46, 280, canvas.text);
      field(id, "Purchase ID", 156, 87, 280);
      field(reason, "Reason (50 characters)", 156, 117, 280);
      button(
          "Request refund",
          156,
          153,
          280,
          idle() && !id.text().isEmpty(),
          () -> {
            String order = id.text().trim(), message = reason.text();
            confirm(
                "Request a refund for dead items in order " + order + "?",
                () ->
                    start(
                        service.submit(
                            "Requesting refund...",
                            j -> service.stores.refund(catalog, order, message),
                            this::result)));
          });
    }
  }

  @Override
  protected void wheel(int delta) {
    scroll = Math.max(0, Math.min(Math.max(0, orders.size() - 5), scroll + delta));
  }
}
