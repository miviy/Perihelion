package perihelion.core.config;

import com.google.gson.JsonObject;
import java.nio.file.Path;

/** Immutable result; source is null when defaults were used. */
public final class LoadResult {
    private final JsonObject document;
    private final boolean readOnly;
    private final boolean recoveredFromBackup;
    private final Path source;

    public LoadResult(JsonObject document, boolean readOnly, boolean recoveredFromBackup, Path source) {
        this.document = JsonDocuments.copy(document);
        this.readOnly = readOnly;
        this.recoveredFromBackup = recoveredFromBackup;
        this.source = source;
    }
    public JsonObject json() { return JsonDocuments.copy(document); }
    public boolean readOnly() { return readOnly; }
    public boolean recoveredFromBackup() { return recoveredFromBackup; }
    public Path source() { return source; }
}
