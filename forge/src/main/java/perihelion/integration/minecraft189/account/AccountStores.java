package perihelion.integration.minecraft189.account;

import static perihelion.integration.minecraft189.account.AccountHttp.*;

import com.google.gson.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Provider protocols share a private delivery journal, durable import ledger and UI model. */
public final class AccountStores {
  public enum Provider {
    LOCALTS("Localts", "https://localts.store", "https://localts.store"),
    NICEALTS("Nicealts", "https://app.nicealts.com", "https://app.nicealts.com"),
    FERNAN("Fernan Club", "https://api.fernan.club/api/v1", "https://fernan.club/api");
    public final String label, api, website;

    Provider(String label, String api, String website) {
      this.label = label;
      this.api = api;
      this.website = website;
    }
  }

  public static final class Product {
    public String id, name, category, kind;
    public BigDecimal price;
    public int stock, limit = 100;
    public final SortedMap<Integer, BigDecimal> discounts = new TreeMap<>();

    public BigDecimal total(int amount) {
      if (amount < 1 || amount > Math.min(stock, limit))
        throw new IllegalArgumentException("Quantity exceeds available stock or the order limit.");
      BigDecimal discount = BigDecimal.ZERO;
      for (Map.Entry<Integer, BigDecimal> tier : discounts.entrySet())
        if (amount >= tier.getKey()) discount = discount.max(tier.getValue());
      return price
          .multiply(BigDecimal.valueOf(amount))
          .multiply(
              BigDecimal.ONE.subtract(
                  discount.min(new BigDecimal("100")).max(BigDecimal.ZERO).movePointLeft(2)));
    }
  }

  public static final class Catalog {
    public final Provider provider;
    public final String key, user, balance, subscription;
    public final List<Product> products;

    Catalog(Provider p, String key, JsonObject user, List<Product> products) {
      provider = p;
      this.key = key;
      this.user = text(user, "username");
      balance = text(user, "balance");
      subscription = text(user, "sub_status");
      this.products = products;
    }
  }

  private final AccountService service;
  private long pace = 200;

  AccountStores(AccountService service) {
    this.service = service;
  }

  public String savedKey(Provider p) {
    return text(service.data("storeKeys"), p.name());
  }

  public Catalog connect(Provider p, String key) throws Exception {
    if (key.trim().isEmpty()) throw new IOException("Paste the store API key.");
    key = key.trim();
    JsonObject user =
        request(
            p,
            key,
            p == Provider.NICEALTS ? "POST" : "GET",
            p == Provider.LOCALTS ? "/v1/me" : p == Provider.FERNAN ? "/user/me" : "/api/balance",
            new JsonObject());
    List<Product> products = new ArrayList<>();
    JsonObject stock =
        request(
            p,
            key,
            "GET",
            p == Provider.LOCALTS
                ? "/v1/products"
                : p == Provider.FERNAN ? "/store/stock" : "/public/stock",
            null);
    JsonObject cooldowns =
        p == Provider.FERNAN ? request(p, key, "GET", "/store/cooldown", null) : new JsonObject();
    if (p == Provider.NICEALTS) {
      String[] names = {
        "Hypixel Unbanned 1-7",
        "Hypixel Bedwars 8+",
        "Hypixel Ranked",
        "LQ Unbanned",
        "DonutSMP Unbanned",
        "Banned"
      };
      for (int i = 1; i <= 6; i++) {
        if (!stock.has("prices") || !stock.getAsJsonObject("prices").has("" + i)) continue;
        Product product = new Product();
        product.id = "" + i;
        product.name = names[i - 1];
        product.category = i < 5 ? "Hypixel" : "Other";
        product.kind = "refresh";
        product.limit = 10;
        product.price = decimal(stock.getAsJsonObject("prices"), product.id);
        product.stock = integer(stock.getAsJsonObject("stock"), product.id);
        products.add(product);
      }
    } else
      for (JsonElement element : array(stock, p == Provider.LOCALTS ? "products" : "stock")) {
        JsonObject row = element.getAsJsonObject();
        Product product = new Product();
        product.id = required(row, p == Provider.LOCALTS ? "id" : "product_id");
        product.name = required(row, p == Provider.LOCALTS ? "name" : "product_name");
        product.category = text(row, "category");
        product.price = decimal(row, p == Provider.LOCALTS ? "priceInCredits" : "price");
        product.stock = integer(row, p == Provider.LOCALTS ? "stock" : "count");
        product.kind =
            p == Provider.FERNAN
                ? "cookies"
                : row.toString().toLowerCase(Locale.ROOT).contains("cookie")
                    ? "cookie-token"
                    : "refresh";
        if (p == Provider.LOCALTS) {
          String searchable = row.toString().toLowerCase(Locale.ROOT);
          if (!searchable.contains("cookie")
              && !searchable.contains("refresh token")
              && !searchable.contains("refresh-token")
              && !searchable.contains("oauth token")) continue;
          if (row.has("quantityDiscounts"))
            for (Map.Entry<String, JsonElement> e :
                row.getAsJsonObject("quantityDiscounts").entrySet())
              product.discounts.put(Integer.parseInt(e.getKey()), e.getValue().getAsBigDecimal());
        } else {
          int cap = integer(row, "purchase_limit");
          if (cap > 0) product.limit = Math.min(100, cap);
          for (JsonElement e : array(cooldowns, "cooldowns")) {
            JsonObject cooldown = e.getAsJsonObject();
            if (product.id.equals(text(cooldown, "product_id")))
              product.limit = Math.min(product.limit, integer(cooldown, "remaining_allowed"));
          }
        }
        if (product.price.signum() < 0) throw new IOException("Store returned an invalid price.");
        products.add(product);
      }
    products.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
    service.data("storeKeys").addProperty(p.name(), key);
    service.save();
    return new Catalog(p, key, user, Collections.unmodifiableList(products));
  }

  public String purchase(Catalog catalog, Product product, int amount, AccountService.Job job)
      throws Exception {
    product.total(amount);
    job.check();
    Provider p = catalog.provider;
    String orderId;
    if (p == Provider.LOCALTS) {
      JsonObject paid =
          request(
              p,
              catalog.key,
              "POST",
              "/v1/products/" + encode(product.id) + "/purchase?amount=" + amount,
              null);
      orderId = required(paid, "orderId");
      JsonObject pending = delivery(orderId, product.name, product.kind, new JsonArray());
      pending.addProperty("pending", true);
      journal(p, pending); // Recovery id survives cancellation and packaging failures.
      job.notice =
          "Paid order " + orderId + ". Recover it from History if packaging is interrupted.";
      for (int i = 0; i < 60; i++) {
        job.check();
        job.status = "Waiting for order " + orderId + "...";
        JsonObject order = order(catalog, orderId);
        if (array(order, "items").size() > 0) {
          journal(p, order);
          return importDelivery(p, order, false, job);
        }
        Thread.sleep(2500);
      }
      throw new IOException("Packaging timed out. Recover this purchase from order history.");
    }
    if (p == Provider.NICEALTS) {
      orderId = "NA-" + UUID.randomUUID().toString();
      JsonObject order = delivery(orderId, product.name, "refresh", new JsonArray());
      journal(p, order);
      for (int i = 0; i < amount; i++) {
        job.check();
        JsonObject body = new JsonObject();
        body.addProperty("product_id", product.id);
        JsonObject reply;
        try {
          reply = request(p, catalog.key, "POST", "/api/purchase", body);
        } catch (Exception error) {
          if (array(order, "items").size() > 0)
            job.notice =
                "Some purchases completed. Recover the saved delivery from History before buying"
                    + " again.";
          throw error;
        }
        for (JsonElement item : array(reply, "items")) order.getAsJsonArray("items").add(item);
        journal(p, order); // Each paid item is saved before another charge is made.
      }
      return importDelivery(p, order, false, job);
    }
    JsonObject body = new JsonObject();
    body.addProperty("product_id", Integer.parseInt(product.id));
    body.addProperty("amount", amount);
    JsonObject reply = request(p, catalog.key, "POST", "/store/purchase", body);
    JsonObject order = fernanDelivery(reply);
    journal(p, order);
    return importDelivery(p, order, false, job);
  }

  public List<String> history(Catalog catalog, AccountService.Job job) throws Exception {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    Provider p = catalog.provider;
    if (p == Provider.NICEALTS) {
      JsonObject response = request(p, catalog.key, "POST", "/api/history", new JsonObject());
      for (JsonElement element : array(response, "history")) {
        JsonObject row = element.getAsJsonObject();
        String id = required(row, "purchase_id");
        journal(p, delivery(id, "Nicealts order", "refresh", array(row, "items")));
        ids.add(id);
      }
    } else {
      for (int page = 0; page < 1000; page++) {
        job.check();
        JsonObject response =
            request(
                p,
                catalog.key,
                "GET",
                p == Provider.LOCALTS
                    ? "/v1/orders?page=" + page + "&size=100"
                    : "/store/purchases?limit=100&offset=" + page * 100,
                null);
        JsonArray rows = array(response, p == Provider.LOCALTS ? "orders" : "purchases");
        for (JsonElement row : rows)
          ids.add(required(row.getAsJsonObject(), p == Provider.LOCALTS ? "id" : "purchase_id"));
        if (rows.size() == 0
            || (p == Provider.LOCALTS
                ? page + 1 >= integer(response, "totalPages")
                : (page + 1) * 100 >= integer(response, "total"))) break;
      }
    }
    for (Map.Entry<String, JsonElement> row : service.data("deliveries").entrySet())
      if (row.getKey().startsWith(p.name() + ":"))
        ids.add(row.getKey().substring(p.name().length() + 1));
    return new ArrayList<>(ids);
  }

  public String scan(Catalog catalog, boolean full, AccountService.Job job) throws Exception {
    int imported = 0, failed = 0, barren = 0;
    List<String> ids = history(catalog, job);
    for (int i = 0; i < ids.size(); i++) {
      job.check();
      String id = ids.get(i);
      job.status = "Scanning orders " + (i + 1) + "/" + ids.size() + "...";
      boolean cached = service.data("deliveries").has(catalog.provider.name() + ":" + id);
      try {
        if (!cached) Thread.sleep(pace);
        JsonObject delivery = order(catalog, id);
        int before = service.book.snapshot().size();
        importDelivery(catalog.provider, delivery, full, job);
        int added = service.book.snapshot().size() - before;
        imported += added;
        if (!cached) barren = added == 0 ? barren + 1 : 0;
        if (!full && barren >= 12)
          return "Imported "
              + imported
              + "; stopped after 12 orders with no new accounts. Shift+Scan reads all.";
      } catch (InterruptedException error) {
        throw error;
      } catch (Exception error) {
        failed++;
      }
    }
    return "Imported " + imported + " accounts; " + failed + " order reads failed.";
  }

  public JsonObject order(Catalog catalog, String id) throws Exception {
    if (!id.matches("[A-Za-z0-9_.-]{1,128}"))
      throw new IOException("Use a valid order or delivery ID.");
    String key = catalog.provider.name() + ":" + id;
    JsonObject journal = service.data("deliveries");
    if (journal.has(key) && !journal.getAsJsonObject(key).has("pending"))
      return journal.getAsJsonObject(key);
    Provider p = catalog.provider;
    if (p == Provider.NICEALTS)
      throw new IOException("This delivery is not in the local journal. Refresh history first.");
    JsonObject response =
        request(
            p,
            catalog.key,
            "GET",
            p == Provider.LOCALTS
                ? "/v1/orders/get-order?id=" + encode(id)
                : "/store/purchases/" + encode(id),
            null);
    JsonObject result;
    if (p == Provider.LOCALTS) {
      JsonArray items = new JsonArray();
      for (JsonElement element : array(response, "items"))
        items.add(new JsonPrimitive(required(element.getAsJsonObject(), "content")));
      String kind = journal.has(key) ? text(journal.getAsJsonObject(key), "kind") : "auto";
      result = delivery(id, text(response, "product-name"), kind, items);
      if (items.size() == 0) result.addProperty("pending", true);
    } else {
      if (response.has("purchase")) response = response.getAsJsonObject("purchase");
      result = fernanDelivery(response);
    }
    journal(p, result);
    return result;
  }

  public String importDelivery(
      Provider p, JsonObject delivery, boolean force, AccountService.Job job) throws Exception {
    journal(p, delivery);
    return importSelection(p, delivery, delivery, force, job);
  }

  public String importSelection(
      Provider p, JsonObject complete, JsonObject delivery, boolean force, AccountService.Job job)
      throws Exception {
    journal(p, complete);
    int added = 0, failed = 0, skipped = 0;
    JsonObject ledger = service.data("imports");
    JsonArray items = array(delivery, "items");
    for (JsonElement element : items) {
      job.check();
      String fingerprint = p.name() + ":" + fingerprint(element.toString());
      if (!force && ledger.has(fingerprint)) {
        skipped++;
        continue;
      }
      try {
        Account a = importItem(element, text(delivery, "kind"));
        service.add(a);
        ledger.addProperty(fingerprint, "imported");
        added++;
        service.save();
      } catch (InterruptedException error) {
        throw error;
      } catch (Exception error) {
        failed++;
        if (error instanceof IOException && error.getMessage().contains("credentials"))
          ledger.addProperty(fingerprint, "dead");
        service.save();
      }
    }
    return "Imported "
        + added
        + "; skipped "
        + skipped
        + "; failed "
        + failed
        + ". Delivery saved for retry.";
  }

  private Account importItem(JsonElement item, String kind) throws Exception {
    String access = "", refresh = "", cookie = "";
    if (item.isJsonObject()) {
      JsonObject j = item.getAsJsonObject();
      access = text(j, "access_token");
      cookie = text(j, "data");
      try {
        cookie = new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException ignored) {
      }
    } else {
      String content = item.getAsString().trim();
      java.util.regex.Matcher label =
          java.util.regex.Pattern.compile("(?i)mctoken\\s*:\\s*([^|\\s]+)").matcher(content);
      if (label.find()) access = label.group(1);
      if (kind.equals("cookie-token")) cookie = cookieToken(content);
      else if (kind.equals("cookies") || content.contains("\t")) cookie = content;
      else if (content.startsWith("eyJ")) access = content;
      else {
        List<String> parsed = AccountAuth.refreshTokens(content);
        if (!parsed.isEmpty()) refresh = parsed.get(0);
        // Older Localts history can omit the product type. A bare cookie also looks
        // like a long refresh token, so retain a cookie attempt after token rejection.
        if (kind.equals("auto")) cookie = cookieToken(refresh.isEmpty() ? content : refresh);
      }
    }
    Exception problem = null;
    if (!refresh.isEmpty())
      try {
        return service.importRefresh(refresh);
      } catch (InterruptedException e) {
        throw e;
      } catch (Exception e) {
        problem = e;
      }
    if (!cookie.isEmpty())
      try {
        return service.importCookies(cookie);
      } catch (InterruptedException e) {
        throw e;
      } catch (Exception e) {
        problem = e;
      }
    if (!access.isEmpty()) {
      Account account = service.auth.access(access);
      String recovery =
          !refresh.isEmpty()
              ? service.recoveredToken(refresh, false)
              : !cookie.isEmpty() ? service.recoveredToken(cookie, true) : "";
      if (!recovery.isEmpty() || !refresh.isEmpty()) {
        account.refresh = recovery.isEmpty() ? refresh : recovery;
        account.kind = "msa";
      }
      return account;
    }
    if (problem != null) throw problem;
    throw new IOException("Unsupported store delivery format.");
  }

  private static String cookieToken(String content) {
    return ".login.live.com\tTRUE\t/\tTRUE\t0\tMSAAUTH\t"
        + content
        + "\n.login.live.com\tTRUE\t/\tTRUE\t0\t__Host-MSAAUTH\t"
        + content;
  }

  private void journal(Provider p, JsonObject delivery) throws Exception {
    service.data("deliveries").add(p.name() + ":" + required(delivery, "id"), delivery);
    service.save();
  }

  private static JsonObject delivery(String id, String name, String kind, JsonArray items) {
    JsonObject j = new JsonObject();
    j.addProperty("id", id);
    j.addProperty("name", name);
    j.addProperty("kind", kind);
    j.add("items", items);
    return j;
  }

  private static JsonObject fernanDelivery(JsonObject j) throws IOException {
    return delivery(
        required(j, "purchase_id"), text(j, "product_name"), "cookies", array(j, "products"));
  }

  public String generate(Catalog catalog, String category, AccountService.Job job)
      throws Exception {
    if (catalog.provider != Provider.NICEALTS
        || !Arrays.asList("Unbanned", "DonutSMP", "Banned").contains(category))
      throw new IOException("Choose a Nicealts generator category.");
    JsonObject body = new JsonObject();
    body.addProperty("category", category);
    String token =
        required(request(catalog.provider, catalog.key, "POST", "/api/generate", body), "token");
    JsonArray items = new JsonArray();
    items.add(new JsonPrimitive(token));
    JsonObject d = delivery("GEN-" + UUID.randomUUID(), category, "access", items);
    journal(catalog.provider, d);
    return importDelivery(catalog.provider, d, false, job);
  }

  public String custom(
      Catalog catalog, String server, String protocol, boolean chat, AccountService.Job job)
      throws Exception {
    if (catalog.provider != Provider.NICEALTS
        || !server.matches("[A-Za-z0-9.-]+(?::[0-9]{1,5})?")
        || !Arrays.asList("1.8", "1.21", "26.1").contains(protocol))
      throw new IOException("Enter a valid server and protocol.");
    JsonObject body = new JsonObject();
    body.addProperty("server", server);
    body.addProperty("protocol", protocol);
    body.addProperty(
        "ban_message",
        chat || server.toLowerCase(Locale.ROOT).contains("minemen") ? "chat" : "normal");
    JsonObject reply = request(catalog.provider, catalog.key, "POST", "/api/custompurchase", body);
    JsonObject d =
        delivery(
            "CUSTOM-" + UUID.randomUUID(), "Custom " + server, "refresh", array(reply, "tokens"));
    journal(catalog.provider, d);
    return importDelivery(catalog.provider, d, false, job);
  }

  public String redeem(Catalog catalog, String key) throws Exception {
    if (catalog.provider != Provider.FERNAN)
      throw new IOException("Peso redemption requires Fernan Club.");
    JsonObject body = new JsonObject();
    body.addProperty("key", key);
    return "Redeemed. Balance: "
        + text(
            request(catalog.provider, catalog.key, "POST", "/user/redeem", body), "balance_after");
  }

  public String refund(Catalog catalog, String id, String reason) throws Exception {
    if (catalog.provider != Provider.FERNAN)
      throw new IOException("Refund requests require Fernan Club.");
    JsonObject d = order(catalog, id);
    JsonArray uuids = new JsonArray();
    for (JsonElement e : array(d, "items"))
      if (e.isJsonObject()) {
        String uuid = text(e.getAsJsonObject(), "uuid");
        if (uuid.isEmpty()) uuid = text(e.getAsJsonObject(), "mc_uuid");
        if (!uuid.isEmpty()
            && "dead"
                .equals(
                    text(
                        service.data("imports"),
                        catalog.provider.name() + ":" + fingerprint(e.toString()))))
          uuids.add(new JsonPrimitive(uuid));
      }
    if (uuids.size() == 0)
      throw new IOException("No confirmed dead items in this delivery. Retry its import first.");
    JsonObject body = new JsonObject();
    body.addProperty("purchase_id", id);
    body.add("product_uuids", uuids);
    body.addProperty("reason", reason.substring(0, Math.min(50, reason.length())));
    return "Refund requested: "
        + text(request(catalog.provider, catalog.key, "POST", "/store/refunds", body), "refund_id");
  }

  private JsonObject request(Provider p, String key, String method, String path, JsonObject input)
      throws Exception {
    Map<String, String> headers = new HashMap<>();
    JsonObject body =
        input == null ? null : new JsonParser().parse(input.toString()).getAsJsonObject();
    if (p == Provider.NICEALTS && !method.equals("GET")) {
      if (body == null) body = new JsonObject();
      body.addProperty("api_key", key);
    } else if (!key.isEmpty()) headers.put("X-API-Key", key);
    for (int attempt = 0; attempt < 3; attempt++) {
      Reply reply =
          service.http.send(
              method,
              p.api + path,
              headers,
              body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8),
              "application/json");
      if (reply.status == 429 && method.equals("GET") && attempt < 2) {
        pace = Math.min(4000, pace * 2);
        long delay = Math.max(pace, 1500L << attempt);
        for (Map.Entry<String, List<String>> h : reply.headers.entrySet())
          if ("Retry-After".equalsIgnoreCase(h.getKey()))
            try {
              delay = Math.max(delay, Math.min(60000, Long.parseLong(h.getValue().get(0)) * 1000));
            } catch (RuntimeException ignored) {
            }
        Thread.sleep(delay);
        continue;
      }
      if (reply.status < 200 || reply.status >= 300) throw failure(reply.status);
      JsonObject json = reply.json();
      if (json.has("success") && !json.get("success").getAsBoolean())
        throw new IOException(
            "Store declined this request. Check the key, balance and order details.");
      return p == Provider.FERNAN && json.has("data") ? json.getAsJsonObject("data") : json;
    }
    throw new IOException("Store rate limit reached.");
  }

  private static int integer(JsonObject j, String key) {
    try {
      return Math.max(0, j.get(key).getAsInt());
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private static BigDecimal decimal(JsonObject j, String key) throws IOException {
    try {
      BigDecimal value = j.get(key).getAsBigDecimal();
      if (value.signum() < 0) throw new NumberFormatException();
      return value;
    } catch (RuntimeException e) {
      throw new IOException("Store returned an invalid price.");
    }
  }

  private static String fingerprint(String value) throws Exception {
    byte[] hash =
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    StringBuilder out = new StringBuilder();
    for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b));
    return out.toString();
  }
}
