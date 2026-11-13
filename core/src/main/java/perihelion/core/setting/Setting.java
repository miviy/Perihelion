package perihelion.core.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import perihelion.core.setting.SettingMetadata.Kind;

/**
 * A typed, immutable-value setting. Value/metadata reads and parsing are safe across threads;
 * mutations, visibility evaluation and observers use the supplied client-thread assertion.
 * Parsing never mutates the setting.
 */
public final class Setting<T> {
    private final String id;
    private final String displayName;
    private final String description;
    private final SettingMetadata metadata;
    private final T defaultValue;
    private final Codec<T> codec;
    private final Runnable mutationGuard;
    private final List<Observer<T>> observers = new ArrayList<>();
    private volatile T value;
    private BooleanSupplier visibility = () -> true;
    private boolean notifying, frozen;

    private Setting(String id, String displayName, String description, T defaultValue, Codec<T> codec, Runnable mutationGuard,
                    SettingMetadata metadata) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Setting ID must be a stable lowercase identifier: " + id);
        }
        this.id = id;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        if (displayName.trim().isEmpty()) throw new IllegalArgumentException("Setting display name must not be blank");
        this.description = Objects.requireNonNull(description, "description");
        this.metadata = metadata;
        this.codec = codec;
        this.mutationGuard = Objects.requireNonNull(mutationGuard, "mutationGuard");
        this.defaultValue = validate(defaultValue);
        this.value = this.defaultValue;
    }

    public static Setting<Boolean> bool(String id, String displayName, String description, boolean defaultValue, Runnable guard) {
        return new Setting<>(id, displayName, description, defaultValue, new PrimitiveCodec<Boolean>() {
            @Override public Boolean parse(String text) {
                if ("true".equalsIgnoreCase(text.trim())) return true;
                if ("false".equalsIgnoreCase(text.trim())) return false;
                throw new IllegalArgumentException("Expected true or false");
            }
            @Override public Boolean decodePrimitive(JsonPrimitive json) {
                if (!json.isBoolean()) throw new IllegalArgumentException("Expected a JSON boolean");
                return json.getAsBoolean();
            }
            @Override public JsonElement encode(Boolean value) { return new JsonPrimitive(value); }
        }, guard, SettingMetadata.simple(Kind.BOOLEAN));
    }

    public static Setting<Integer> integer(String id, String displayName, String description, int defaultValue,
                                           int minimum, int maximum, Runnable guard) {
        return integer(id, displayName, description, defaultValue, minimum, maximum, 1, guard);
    }
    public static Setting<Integer> integer(String id, String displayName, String description, int defaultValue,
                                           int minimum, int maximum, int step, Runnable guard) {
        NumericConstraints constraints = NumericConstraints.stepped(minimum, maximum, step);
        return new Setting<>(id, displayName, description, defaultValue, new PrimitiveCodec<Integer>() {
            @Override public Integer parse(String text) { return Integer.valueOf(text.trim()); }
            @Override public Integer decodePrimitive(JsonPrimitive json) {
                if (!json.isNumber()) throw new IllegalArgumentException("Expected a JSON integer");
                try { return new BigDecimal(json.getAsString()).intValueExact(); }
                catch (ArithmeticException ex) { throw new IllegalArgumentException("Expected an integer in range", ex); }
            }
            @Override public void validate(Integer value) {
                if (value < minimum || value > maximum) {
                    throw new IllegalArgumentException("Expected " + minimum + " through " + maximum);
                }
                if (((long) value - minimum) % step != 0) throw new IllegalArgumentException("Expected a step of " + step + " from " + minimum);
            }
            @Override public JsonElement encode(Integer value) { return new JsonPrimitive(value); }
        }, guard, SettingMetadata.numeric(Kind.INTEGER, constraints));
    }

    public static Setting<Double> decimal(String id, String displayName, String description, double defaultValue,
                                          double minimum, double maximum, Runnable guard) {
        return decimal(id, displayName, description, defaultValue, NumericConstraints.continuous(minimum, maximum), guard);
    }
    public static Setting<Double> decimal(String id, String displayName, String description, double defaultValue,
                                          double minimum, double maximum, double step, Runnable guard) {
        return decimal(id, displayName, description, defaultValue, NumericConstraints.stepped(minimum, maximum, step), guard);
    }
    private static Setting<Double> decimal(String id, String displayName, String description, double defaultValue,
                                           NumericConstraints constraints, Runnable guard) {
        return new Setting<>(id, displayName, description, defaultValue, new PrimitiveCodec<Double>() {
            @Override public Double parse(String text) { return Double.valueOf(text.trim()); }
            @Override public Double decodePrimitive(JsonPrimitive json) {
                if (!json.isNumber()) throw new IllegalArgumentException("Expected a JSON number");
                return json.getAsDouble();
            }
            @Override public Double normalize(Double value) { return constraints.normalize(value); }
            @Override public JsonElement encode(Double value) { return new JsonPrimitive(value); }
        }, guard, SettingMetadata.numeric(Kind.DECIMAL, constraints));
    }

    public static <E extends Enum<E>> Setting<E> enumeration(String id, String displayName, String description, E defaultValue,
                                                            Class<E> enumType, Runnable guard) {
        Objects.requireNonNull(enumType, "enumType");
        return new Setting<>(id, displayName, description, defaultValue, new PrimitiveCodec<E>() {
            @Override public E parse(String text) { return parseEnum(enumType, text); }
            @Override public E decodePrimitive(JsonPrimitive json) {
                if (!json.isString()) throw new IllegalArgumentException("Expected an enum name string");
                return parse(json.getAsString());
            }
            @Override public void validate(E value) {
                if (!enumType.isInstance(value)) throw new IllegalArgumentException("Incorrect enum type");
            }
            @Override public JsonElement encode(E value) {
                return new JsonPrimitive(value.name().toLowerCase(Locale.ROOT));
            }
        }, guard, SettingMetadata.choices(Kind.ENUM, enumNames(enumType)));
    }

    public static Setting<String> string(String id, String displayName, String description, String defaultValue,
                                          int maximumLength, Runnable guard) {
        if (maximumLength < 0) throw new IllegalArgumentException("Negative maximum length");
        return new Setting<>(id, displayName, description, defaultValue, new PrimitiveCodec<String>() {
            @Override public String parse(String text) { return text; }
            @Override public String decodePrimitive(JsonPrimitive json) {
                if (!json.isString()) throw new IllegalArgumentException("Expected a JSON string");
                return json.getAsString();
            }
            @Override public void validate(String value) {
                if (value.length() > maximumLength) {
                    throw new IllegalArgumentException("String exceeds " + maximumLength + " characters");
                }
            }
            @Override public JsonElement encode(String value) { return new JsonPrimitive(value); }
        }, guard, SettingMetadata.text(maximumLength));
    }

    public static Setting<DoubleRange> range(String id, String displayName, String description, DoubleRange defaultValue,
                                             double minimum, double maximum, Runnable guard) {
        return range(id, displayName, description, defaultValue, NumericConstraints.continuous(minimum, maximum), guard);
    }
    public static Setting<DoubleRange> range(String id, String displayName, String description, DoubleRange defaultValue,
                                             double minimum, double maximum, double step, Runnable guard) {
        return range(id, displayName, description, defaultValue, NumericConstraints.stepped(minimum, maximum, step), guard);
    }
    private static Setting<DoubleRange> range(String id, String displayName, String description, DoubleRange defaultValue,
                                              NumericConstraints constraints, Runnable guard) {
        return new Setting<>(id, displayName, description, defaultValue, new Codec<DoubleRange>() {
            @Override public DoubleRange parse(String text) {
                String[] endpoints = text.trim().split("\\.\\.", -1);
                if (endpoints.length != 2) throw new IllegalArgumentException("Expected lower..upper");
                return new DoubleRange(Double.parseDouble(endpoints[0].trim()), Double.parseDouble(endpoints[1].trim()));
            }
            @Override public DoubleRange decode(JsonElement json) {
                if (!json.isJsonObject()) throw new IllegalArgumentException("Expected a range object");
                JsonObject range = json.getAsJsonObject();
                if (range.entrySet().size() != 2) throw new IllegalArgumentException("Range requires only lower and upper");
                return new DoubleRange(number(range.get("lower")), number(range.get("upper")));
            }
            @Override public DoubleRange normalize(DoubleRange value) {
                return new DoubleRange(constraints.normalize(value.lower()), constraints.normalize(value.upper()));
            }
            @Override public JsonElement encode(DoubleRange value) {
                JsonObject range = new JsonObject();
                range.addProperty("lower", value.lower());
                range.addProperty("upper", value.upper());
                return range;
            }
        }, guard, SettingMetadata.numeric(Kind.RANGE, constraints));
    }

    public static Setting<ColorValue> color(String id, String displayName, String description, ColorValue defaultValue, Runnable guard) {
        return new Setting<>(id, displayName, description, defaultValue, new Codec<ColorValue>() {
            @Override public ColorValue parse(String text) { return ColorValue.parse(text); }
            @Override public ColorValue decode(JsonElement json) {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) return parse(json.getAsString());
                if (!json.isJsonObject()) throw new IllegalArgumentException("Expected a color string or animated color object");
                JsonObject object = json.getAsJsonObject();
                JsonElement rgba = object.get("rgba"), rainbow = object.get("rainbow"), speed = object.get("speed");
                if (object.entrySet().size() != 3 || rgba == null || !rgba.isJsonPrimitive() || !rgba.getAsJsonPrimitive().isString()
                        || rainbow == null || !rainbow.isJsonPrimitive() || !rainbow.getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("Color requires rgba, rainbow and speed");
                }
                return parse(rgba.getAsString()).withRainbow(rainbow.getAsBoolean(), number(speed));
            }
            @Override public JsonElement encode(ColorValue value) {
                if (!value.rainbow() && value.rainbowSpeed() == 1) return new JsonPrimitive(value.toString());
                JsonObject object = new JsonObject();
                object.addProperty("rgba", value.toString()); object.addProperty("rainbow", value.rainbow());
                object.addProperty("speed", value.rainbowSpeed()); return object;
            }
        }, guard, SettingMetadata.simple(Kind.COLOR));
    }

    /** Enum-backed choices; every input set is copied into an immutable declaration-ordered set. */
    public static <E extends Enum<E>> Setting<Set<E>> multiSelect(String id, String displayName, String description, Set<E> defaultValue,
                                                                  Class<E> enumType, Runnable guard) {
        List<String> options = enumNames(enumType);
        return new Setting<>(id, displayName, description, defaultValue, new Codec<Set<E>>() {
            @Override public Set<E> parse(String text) {
                Set<E> result = EnumSet.noneOf(enumType);
                if (!text.trim().isEmpty()) {
                    for (String name : text.split(",", -1)) add(result, name);
                }
                return result;
            }
            @Override public Set<E> decode(JsonElement json) {
                if (!json.isJsonArray()) throw new IllegalArgumentException("Expected an array of enum names");
                JsonArray array = json.getAsJsonArray();
                if (array.size() > options.size()) throw new IllegalArgumentException("Too many selected options");
                Set<E> result = EnumSet.noneOf(enumType);
                for (JsonElement item : array) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("Expected an enum name string");
                    }
                    add(result, item.getAsString());
                }
                return result;
            }
            private void add(Set<E> selected, String name) {
                if (!selected.add(parseEnum(enumType, name))) throw new IllegalArgumentException("Duplicate option: " + name);
            }
            @Override public Set<E> normalize(Set<E> value) {
                Set<E> copy = EnumSet.noneOf(enumType);
                for (Object item : value) {
                    if (!enumType.isInstance(item)) throw new IllegalArgumentException("Incorrect enum type or null option");
                    copy.add(enumType.cast(item));
                }
                return Collections.unmodifiableSet(copy);
            }
            @Override public JsonElement encode(Set<E> value) {
                JsonArray array = new JsonArray();
                for (E item : value) array.add(new JsonPrimitive(item.name().toLowerCase(Locale.ROOT)));
                return array;
            }
        }, guard, SettingMetadata.choices(Kind.MULTI_SELECT, options));
    }

    private static double number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected a JSON number");
        }
        return value.getAsDouble();
    }
    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String text) {
        String name = text.trim().toLowerCase(Locale.ROOT);
        for (E constant : enumType.getEnumConstants()) {
            if (constant.name().toLowerCase(Locale.ROOT).equals(name)) return constant;
        }
        throw new IllegalArgumentException("Unknown " + enumType.getSimpleName() + " value: " + text);
    }
    private static <E extends Enum<E>> List<String> enumNames(Class<E> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        if (!enumType.isEnum()) throw new IllegalArgumentException("Expected an enum class");
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (E constant : enumType.getEnumConstants()) {
            String name = constant.name().toLowerCase(Locale.ROOT);
            if (!unique.add(name)) throw new IllegalArgumentException("Ambiguous enum option: " + name);
            result.add(name);
        }
        return result;
    }

    /** Configure before module registration. Hidden values still validate, notify and persist. */
    public Setting<T> visibleWhen(BooleanSupplier condition) {
        mutationGuard.run();
        if (frozen) throw new IllegalStateException("Setting definition is frozen: " + id);
        visibility = Objects.requireNonNull(condition, "condition");
        return this;
    }
    /** Evaluated on the client thread; predicates should be pure and read current setting values. */
    public boolean isVisible() { mutationGuard.run(); return visibility.getAsBoolean(); }
    /** Freezes definition metadata, not the value. Called by the module registry. */
    public void freeze() { mutationGuard.run(); frozen = true; }

    public String id() { return id; }
    /** Human-readable label; configuration and commands continue to use id(). */
    public String displayName() { return displayName; }
    public String description() { return description; }
    public SettingMetadata metadata() { return metadata; }
    public T defaultValue() { return defaultValue; }
    public T get() { return value; }

    /** Returns a validated immutable value, including defensive copies and step normalization. */
    public T validate(T candidate) {
        if (candidate == null) throw new IllegalArgumentException("Setting value cannot be null");
        return codec.normalize(candidate);
    }

    public T parse(String text) {
        if (text == null) throw new IllegalArgumentException("Setting text cannot be null");
        return validate(codec.parse(text));
    }

    public T fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) throw new IllegalArgumentException("Setting " + id + " cannot be null");
        return validate(codec.decode(json));
    }

    public JsonElement toJson() { return codec.encode(value); }

    /**
     * Commits before notifying observers. Ordinary observer failures do not prevent other observers
     * from running; the first failure is rethrown after delivery with later failures suppressed.
     */
    public void set(T candidate) {
        mutationGuard.run();
        candidate = validate(candidate);
        if (Objects.equals(value, candidate)) return;
        if (notifying) throw new IllegalStateException("Recursive change to setting " + id);
        T previous = value;
        value = candidate;
        notifying = true;
        Throwable failure = null;
        try {
            List<Observer<T>> snapshot = new ArrayList<>(observers);
            for (Observer<T> observer : snapshot) {
                if (!observer.active) continue;
                try { observer.listener.changed(this, previous, candidate); }
                catch (VirtualMachineError | ThreadDeath fatal) { throw fatal; }
                catch (Throwable ex) {
                    if (failure == null) failure = ex;
                    else if (failure != ex) failure.addSuppressed(ex);
                }
            }
        } finally { notifying = false; }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw new IllegalStateException("Observer failed for setting " + id, failure);
    }

    public void reset() { set(defaultValue); }

    public ChangeSubscription onChange(ChangeListener<T> listener) {
        mutationGuard.run();
        Observer<T> observer = new Observer<>(Objects.requireNonNull(listener, "listener"));
        observers.add(observer);
        return () -> {
            mutationGuard.run();
            observer.active = false;
            observers.remove(observer);
        };
    }

    private interface Codec<T> {
        T parse(String text);
        T decode(JsonElement json);
        JsonElement encode(T value);
        default void validate(T value) { }
        default T normalize(T value) { validate(value); return value; }
    }

    private abstract static class PrimitiveCodec<T> implements Codec<T> {
        @Override public T decode(JsonElement json) {
            if (!json.isJsonPrimitive()) throw new IllegalArgumentException("Expected a primitive JSON value");
            return decodePrimitive(json.getAsJsonPrimitive());
        }
        abstract T decodePrimitive(JsonPrimitive json);
    }

    private static final class Observer<T> {
        final ChangeListener<T> listener;
        boolean active = true;
        Observer(ChangeListener<T> listener) { this.listener = listener; }
    }
}
