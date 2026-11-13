package perihelion.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Gson 2.2.4 compatible strict reading and defensive tree copies. */
final class JsonDocuments {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    static final int MAX_DEPTH = 64;
    private JsonDocuments() { }

    static JsonObject read(Reader source) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = source.read(buffer)) != -1) {
            if (text.length() + count > MAX_BYTES) throw new IOException("Configuration exceeds size limit");
            text.append(buffer, 0, count);
        }
        validateTokens(text);
        JsonReader reader = new JsonReader(new StringReader(text.toString()));
        reader.setLenient(false);
        JsonElement root = readValue(reader, 0);
        if (!root.isJsonObject()) throw new IOException("Configuration root must be an object");
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON content");
        return root.getAsJsonObject();
    }

    // Gson 2.2.4 accepts uppercase literals and unescaped string control characters even
    // with lenient=false. Check lexical JSON rules before its structural reader runs.
    private static void validateTokens(CharSequence text) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || "{}[],:".indexOf(c) >= 0) continue;
            if (c == '"') {
                boolean ended = false;
                while (++i < text.length()) {
                    c = text.charAt(i);
                    if (c == '"') { ended = true; break; }
                    if (c < 0x20) throw new IOException("Unescaped control character in JSON string");
                    if (c == '\\') {
                        if (++i == text.length()) throw new IOException("Unterminated JSON escape");
                        char escape = text.charAt(i);
                        if (escape == 'u') {
                            for (int n = 0; n < 4; n++) {
                                if (++i == text.length() || Character.digit(text.charAt(i), 16) < 0
                                        || text.charAt(i) > 127) throw new IOException("Invalid JSON Unicode escape");
                            }
                        } else if ("\"\\/bfnrt".indexOf(escape) < 0) throw new IOException("Invalid JSON escape");
                    }
                }
                if (!ended) throw new IOException("Unterminated JSON string");
            } else {
                int start = i;
                while (i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    if (next == ' ' || next == '\t' || next == '\n' || next == '\r' || "{}[],:\"".indexOf(next) >= 0) break;
                    i++;
                }
                String token = text.subSequence(start, i + 1).toString();
                if (!"true".equals(token) && !"false".equals(token) && !"null".equals(token)
                        && !token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                    throw new IOException("Invalid JSON token");
                }
            }
        }
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Configuration exceeds nesting limit " + MAX_DEPTH);
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                Set<String> names = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IOException("Duplicate JSON member: " + name);
                    object.add(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader, depth + 1));
                reader.endArray();
                return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER:
                try { return new JsonPrimitive(new BigDecimal(reader.nextString())); }
                catch (NumberFormatException invalid) { throw new IOException("Invalid JSON number", invalid); }
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IOException("Unexpected JSON token " + reader.peek());
        }
    }

    static JsonObject copy(JsonObject source) {
        if (source == null) throw new NullPointerException("document");
        return copyValue(source, 0, new IdentityHashMap<JsonElement, Boolean>()).getAsJsonObject();
    }

    private static JsonElement copyValue(JsonElement value, int depth, IdentityHashMap<JsonElement, Boolean> ancestors) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Configuration exceeds nesting limit " + MAX_DEPTH);
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return new JsonPrimitive(primitive.getAsBoolean());
            if (primitive.isString()) return new JsonPrimitive(primitive.getAsString());
            try { return new JsonPrimitive(new BigDecimal(primitive.getAsString())); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Configuration numbers must be finite JSON numbers", invalid); }
        }
        if (ancestors.put(value, true) != null) throw new IllegalArgumentException("Cyclic JSON document");
        try {
            if (value.isJsonObject()) {
                JsonObject result = new JsonObject();
                for (Map.Entry<String, JsonElement> member : value.getAsJsonObject().entrySet()) {
                    result.add(member.getKey(), copyValue(member.getValue(), depth + 1, ancestors));
                }
                return result;
            }
            JsonArray result = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) result.add(copyValue(item, depth + 1, ancestors));
            return result;
        } finally { ancestors.remove(value); }
    }

    static int schema(JsonObject document) throws IOException {
        JsonElement version = document.get("schemaVersion");
        if (version == null) return 1;
        if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
            throw new IOException("schemaVersion must be a positive integer");
        }
        try {
            int number = version.getAsBigDecimal().intValueExact();
            if (number < 1) throw new ArithmeticException("Nonpositive schema");
            return number;
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IOException("schemaVersion must be a positive integer", invalid);
        }
    }
}
