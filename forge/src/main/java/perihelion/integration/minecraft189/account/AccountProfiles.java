package perihelion.integration.minecraft189.account;

import static perihelion.integration.minecraft189.account.AccountHttp.*;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Profile and skin service operations, called by the account worker with captured credentials. */
public final class AccountProfiles {
  public static final class Skin {
    public final byte[] png;
    public final boolean slim;
    public final String label;

    public Skin(byte[] png, boolean slim, String label) throws IOException {
      validate(png);
      this.png = png;
      this.slim = slim;
      this.label = label;
    }

    public BufferedImage image() throws IOException {
      return ImageIO.read(new ByteArrayInputStream(png));
    }
  }

  private final AccountService service;

  AccountProfiles(AccountService service) {
    this.service = service;
  }

  private static void name(String value) throws IOException {
    if (!value.matches("[A-Za-z0-9_]{3,16}"))
      throw new IOException("Use a username with 3-16 letters, numbers or underscores.");
  }

  public String available(String name, String token) throws Exception {
    name(name);
    return "Name status: "
        + required(
            service.http.json(
                "GET",
                AccountAuth.PROFILE + "/name/" + name + "/available",
                AccountAuth.bearer(token),
                null,
                false),
            "status");
  }

  public Account rename(String name, String token) throws Exception {
    name(name);
    service.http.json(
        "PUT", AccountAuth.PROFILE + "/name/" + name, AccountAuth.bearer(token), null, false);
    Account a = service.auth.access(token);
    service.add(a);
    return a;
  }

  public String history(String name) throws Exception {
    name(name);
    JsonObject response =
        service.http.json(
            "GET",
            "https://api.ashcon.app/mojang/v2/user/" + name,
            Collections.emptyMap(),
            null,
            false);
    List<String> names = new ArrayList<>();
    for (JsonElement e : array(response, "username_history"))
      names.add(text(e.getAsJsonObject(), "username"));
    return names.isEmpty()
        ? "No public name history available."
        : "Name history: " + String.join(", ", names);
  }

  public Skin skin(String source, boolean slim) throws Exception {
    source = source.trim();
    if (source.matches("[A-Za-z0-9_]{3,16}")) {
      JsonObject profile =
          service.http.json(
              "GET",
              "https://api.mojang.com/users/profiles/minecraft/" + source,
              Collections.emptyMap(),
              null,
              false);
      JsonObject session =
          service.http.json(
              "GET",
              "https://sessionserver.mojang.com/session/minecraft/profile/"
                  + required(profile, "id"),
              Collections.emptyMap(),
              null,
              false);
      for (JsonElement e : array(session, "properties")) {
        JsonObject property = e.getAsJsonObject();
        if (!"textures".equals(text(property, "name"))) continue;
        JsonObject textures =
            new JsonParser()
                .parse(
                    new String(
                        Base64.getDecoder().decode(required(property, "value")),
                        StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("textures");
        if (!textures.has("SKIN")) throw new IOException("This profile uses a default skin.");
        JsonObject skin = textures.getAsJsonObject("SKIN");
        String url = required(skin, "url");
        URI uri = URI.create(url);
        if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost()))
          throw new IOException("Profile returned an unexpected skin host.");
        if (url.startsWith("http://")) url = "https://" + url.substring(7);
        boolean model =
            skin.has("metadata") && "slim".equals(text(skin.getAsJsonObject("metadata"), "model"));
        return new Skin(service.http.download(url, 1024 * 1024), model, source);
      }
      throw new IOException("No skin found for this player.");
    }
    if (source.startsWith("https://"))
      return new Skin(service.http.download(source, 1024 * 1024), slim, "URL skin");
    Path file = Paths.get(source).toAbsolutePath().normalize();
    if (Files.size(file) > 1024 * 1024) throw new IOException("Skin file must be under 1 MiB.");
    return new Skin(AccountVault.readBounded(file), slim, file.getFileName().toString());
  }

  public String apply(Skin skin, String token) throws Exception {
    String boundary = "Perihelion" + UUID.randomUUID().toString().replace("-", "");
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (skin.slim ? "slim" : "classic")
                + "\r\n--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(skin.png);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    Reply reply =
        service.http.send(
            "POST",
            AccountAuth.PROFILE + "/skins",
            AccountAuth.bearer(token),
            body.toByteArray(),
            "multipart/form-data; boundary=" + boundary);
    if (reply.status < 200 || reply.status >= 300) throw failure(reply.status);
    return "Skin changed. Rejoin to refresh the server's skin cache.";
  }

  static void validate(byte[] bytes) throws IOException {
    if (bytes.length < 24
        || bytes.length > 1024 * 1024
        || !Arrays.equals(
            Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
      throw new IOException("Choose a PNG skin under 1 MiB.");
    ByteBuffer header = ByteBuffer.wrap(bytes);
    int width = header.getInt(16), height = header.getInt(20);
    if (width != 64 || height != 32 && height != 64)
      throw new IOException("Skins must be 64x32 or 64x64 pixels.");
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    if (image == null || image.getWidth() != 64 || image.getHeight() != height)
      throw new IOException("Invalid skin PNG.");
  }

  public List<String> favorites() {
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, JsonElement> entry : service.data("skinFavorites").entrySet())
      result.add(entry.getKey());
    return result;
  }

  public void favorite(String name, Skin skin) throws Exception {
    if (name.trim().isEmpty() || name.length() > 48)
      throw new IOException("Use a favorite name up to 48 characters.");
    JsonObject favorites = service.data("skinFavorites");
    if (favorites.entrySet().size() >= 50 && !favorites.has(name))
      throw new IOException("Keep up to 50 favorite skins.");
    JsonObject row = new JsonObject();
    row.addProperty("png", Base64.getEncoder().encodeToString(skin.png));
    row.addProperty("slim", skin.slim);
    favorites.add(name, row);
    service.save();
  }

  public Skin favorite(String name) throws Exception {
    JsonObject row = service.data("skinFavorites").getAsJsonObject(name);
    if (row == null) throw new IOException("Choose a saved favorite.");
    return new Skin(
        Base64.getDecoder().decode(required(row, "png")), row.get("slim").getAsBoolean(), name);
  }

  public void removeFavorite(String name) throws Exception {
    service.data("skinFavorites").remove(name);
    if (name.equals(text(service.data("skinSettings"), "default")))
      service.data("skinSettings").remove("default");
    service.save();
  }

  public void defaultFavorite(String name) throws Exception {
    favorite(name);
    service.data("skinSettings").addProperty("default", name);
    service.save();
  }

  public void clearDefault() throws Exception {
    service.data("skinSettings").remove("default");
    service.save();
  }

  public void applyDefault(Account account) throws Exception {
    String name = text(service.data("skinSettings"), "default");
    if (!name.isEmpty() && !account.access.isEmpty()) apply(favorite(name), account.access);
  }

  public void saveLocal(Skin skin) throws Exception {
    JsonObject row = service.data("localSkin");
    row.addProperty("png", Base64.getEncoder().encodeToString(skin.png));
    row.addProperty("slim", skin.slim);
    row.addProperty("enabled", true);
    service.save();
  }

  public Skin local() throws Exception {
    JsonObject row = service.data("localSkin");
    if (!row.has("enabled") || !row.get("enabled").getAsBoolean()) return null;
    return new Skin(
        Base64.getDecoder().decode(required(row, "png")),
        row.get("slim").getAsBoolean(),
        "Local skin");
  }

  public void disableLocal() throws Exception {
    service.data("localSkin").addProperty("enabled", false);
    service.save();
  }
}
