package perihelion.integration.minecraft189.account;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, interruptible HTTPS. Error messages never echo response bodies or credentials. */
public class AccountHttp implements AutoCloseable {
  private final Set<HttpURLConnection> active =
      Collections.newSetFromMap(new ConcurrentHashMap<HttpURLConnection, Boolean>());
  private volatile boolean closed;

  public static final class Reply {
    public final int status;
    public final String body, location;
    public final Map<String, List<String>> headers;

    public Reply(int status, String body, String location, Map<String, List<String>> headers) {
      this.status = status;
      this.body = body;
      this.location = location;
      this.headers = headers;
    }

    public JsonObject json() throws IOException {
      if (body.trim().isEmpty()) return new JsonObject();
      try {
        return new JsonParser().parse(body).getAsJsonObject();
      } catch (RuntimeException e) {
        throw new IOException("Service returned an invalid response.");
      }
    }
  }

  public Reply send(
      String method, String url, Map<String, String> headers, byte[] body, String contentType)
      throws Exception {
    if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException error) {
      throw new IOException("Invalid service address.");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getUserInfo() != null
        || uri.getHost() == null) throw new IOException("A secure HTTPS address is required.");
    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
    active.add(connection);
    try {
      connection.setConnectTimeout(10000);
      connection.setReadTimeout(20000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod(method);
      connection.setRequestProperty("User-Agent", "Perihelion/0.1 AccountManager");
      connection.setRequestProperty("Accept-Encoding", "identity");
      for (Map.Entry<String, String> h : headers.entrySet())
        connection.setRequestProperty(h.getKey(), h.getValue());
      if (body != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        try (OutputStream out = connection.getOutputStream()) {
          out.write(body);
        }
      }
      int status = connection.getResponseCode();
      InputStream stream =
          status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      String response = "";
      if (stream != null)
        try (InputStream in = stream) {
          response = new String(read(in, 2 * 1024 * 1024), StandardCharsets.UTF_8);
        }
      return new Reply(
          status, response, connection.getHeaderField("Location"), connection.getHeaderFields());
    } catch (SocketTimeoutException e) {
      throw new IOException("Service timed out. Try again.");
    } finally {
      active.remove(connection);
      connection.disconnect();
    }
  }

  public byte[] download(String url, int limit) throws Exception {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid skin URL.");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null) throw new IOException("Use an HTTPS skin URL.");
    if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
    active.add(connection);
    try {
      connection.setConnectTimeout(10000);
      connection.setReadTimeout(15000);
      connection.setInstanceFollowRedirects(false);
      if (connection.getResponseCode() != 200) throw failure(connection.getResponseCode());
      try (InputStream in = connection.getInputStream()) {
        return read(in, limit);
      }
    } finally {
      active.remove(connection);
      connection.disconnect();
    }
  }

  public JsonObject json(
      String method, String url, Map<String, String> headers, String body, boolean form)
      throws Exception {
    Reply reply =
        send(
            method,
            url,
            headers,
            body == null ? null : body.getBytes(StandardCharsets.UTF_8),
            form ? "application/x-www-form-urlencoded" : "application/json");
    if (reply.status < 200 || reply.status >= 300) throw failure(reply.status);
    return reply.json();
  }

  public static IOException failure(int status) {
    return new IOException(
        status == 401 || status == 403
            ? "Service refused the credentials (HTTP " + status + ")."
            : status == 429
                ? "Service rate limit reached. Try again later."
                : "Service request failed (HTTP " + status + ").");
  }

  public static String text(JsonObject json, String field) {
    JsonElement e = json.get(field);
    return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? "" : e.getAsString();
  }

  public static String required(JsonObject json, String field) throws IOException {
    String value = text(json, field);
    if (value.isEmpty()) throw new IOException("Service response is missing " + field + ".");
    return value;
  }

  public static JsonArray array(JsonObject json, String key) {
    return json.has(key) && json.get(key).isJsonArray()
        ? json.getAsJsonArray(key)
        : new JsonArray();
  }

  public static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public static String form(String... fields) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < fields.length; i += 2) {
      if (i > 0) out.append('&');
      out.append(encode(fields[i])).append('=').append(encode(fields[i + 1]));
    }
    return out.toString();
  }

  public static byte[] read(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = in.read(buffer)) != -1) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
      if (out.size() + n > limit) throw new IOException("Response is too large.");
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }

  public void cancel() {
    for (HttpURLConnection c : active) c.disconnect();
  }

  @Override
  public void close() {
    closed = true;
    cancel();
  }
}
