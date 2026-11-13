package perihelion.core.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** GUI-independent, immutable information for inspecting a setting definition. */
public final class SettingMetadata {
    public enum Kind { BOOLEAN, INTEGER, DECIMAL, ENUM, STRING, RANGE, COLOR, MULTI_SELECT }

    private final Kind kind;
    private final NumericConstraints numeric;
    private final List<String> options;
    private final int maximumLength;

    private SettingMetadata(Kind kind, NumericConstraints numeric, List<String> options, int maximumLength) {
        this.kind = kind;
        this.numeric = numeric;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.maximumLength = maximumLength;
    }
    static SettingMetadata simple(Kind kind) { return new SettingMetadata(kind, null, Collections.emptyList(), -1); }
    static SettingMetadata numeric(Kind kind, NumericConstraints constraints) {
        return new SettingMetadata(kind, constraints, Collections.emptyList(), -1);
    }
    static SettingMetadata choices(Kind kind, List<String> options) { return new SettingMetadata(kind, null, options, -1); }
    static SettingMetadata text(int maximumLength) { return new SettingMetadata(Kind.STRING, null, Collections.emptyList(), maximumLength); }

    public Kind kind() { return kind; }
    public Optional<NumericConstraints> numeric() { return Optional.ofNullable(numeric); }
    /** Stable lowercase enum names, in declaration order, for ENUM and MULTI_SELECT. */
    public List<String> options() { return options; }
    public OptionalInt maximumLength() { return maximumLength < 0 ? OptionalInt.empty() : OptionalInt.of(maximumLength); }
}
