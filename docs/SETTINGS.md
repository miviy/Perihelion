# Setting definitions

Settings belong to the Java 8 core and have no GUI or game dependency. Every factory
begins with `(id, displayName, description, ...)`, followed by the existing value,
constraints and client-thread guard. `id()` remains the stable lowercase identifier
used by the module's setting map, commands and config. `displayName()` is an explicit,
immutable human-readable label, and `description()` explains the setting.

Display names must be non-null and non-blank; they can contain spaces, capitalization
and Unicode. They are not derived from IDs and need not be unique. Descriptions may be
empty. For example, `"attack_range"`, `"Attack range"`, `"Maximum target distance"`
are three separate pieces of information. Renaming a label does not change an ID,
notify value observers, dirty config or require a config migration.

Definition call sites must supply the new display-name argument. The old unnamed
factory signatures are intentionally removed before production modules are added.
The config schema remains unchanged: only IDs and values are persisted, never
display names, descriptions or definition metadata.

## Metadata and visibility

`metadata()` returns immutable information any editor can inspect without guessing
from the current value: `kind()`, optional `numeric()` constraints, enum `options()` in
declaration order, and an optional string `maximumLength()`. Numeric constraints expose
`minimum()`, `maximum()` and an `OptionalDouble step()`. Enum option IDs are lowercase,
locale-independent names; enums with ambiguous IDs are rejected.

Use `visibleWhen(BooleanSupplier)` while constructing a setting. Module registration
freezes this rule, while the value remains editable. Standalone settings can call
`freeze()` explicitly. Evaluate `isVisible()` on the client thread. Predicates should be
pure, read other settings' current values, and avoid recursive visibility dependencies.
Predicate failures propagate to the caller. No dependency subscriptions or cached
visibility state are created.

Visibility only controls presentation: hidden settings still validate, notify observers,
accept commands, and persist. Changing a dependency does not reset a hidden value or
emit a value-change notification for it. Definitions and visibility rules are not saved
in configuration.

## Values and formats

| Factory | Value type | Command value | JSON value |
| --- | --- | --- | --- |
| `bool` | `Boolean` | `true` | `true` |
| `integer` | `Integer` | `4` | `4` |
| `decimal` | `Double` | `0.5` | `0.5` |
| `enumeration` | Enum constant | `fast` | `"fast"` |
| `string` | `String` | `"two words"` | `"two words"` |
| `range` | `DoubleRange` | `1.5..3.5` | `{"lower":1.5,"upper":3.5}` |
| `color` | `ColorValue` | `#12345678` | `"#12345678"` |
| `multiSelect` | Immutable `Set<E>` of enum constants | `first,third` | `["first","third"]` |

Integer factories default to step 1; an overload accepts an explicit integer step before
the guard. Decimal and range factories without a step accept any finite value within
their bounds. Their stepped overloads accept a positive finite step before the guard.
Step grids start at the minimum: minimum 1 and step 2 allow 1, 3, 5, and so on. Bounds
are inclusive, but a maximum that is off the grid is not a valid stepped value.

Defaults, `set`, `validate`, command parsing and JSON decoding all enforce the same
constraints. Off-grid numbers are rejected rather than rounded to another user value.
Tiny floating-point arithmetic errors, such as `0.1 + 0.2`, normalize to the grid;
the tolerance is limited to four ULPs of the value/origin/step and one millionth of a
step, whichever is smaller. `validate` returns the normalized value. Observers receive
that committed value, and equivalent normalized values do not trigger extra events.

`DoubleRange` holds finite, ordered, inclusive endpoints. Both endpoints obey the
setting's bounds and optional step. Reversed endpoints are rejected, not swapped.
Range JSON requires exactly the numeric `lower` and `upper` members.

`ColorValue` holds immutable sRGB byte channels (0–255). Six-digit `#RRGGBB` input implies
alpha 255. Eight-digit text is **`#RRGGBBAA`**, with alpha last; encoding always uses
eight uppercase digits. `rgba`, `rgb`, `fromArgb` and `argb()` provide explicit channel
and packed-ARGB conversion without an AWT, Minecraft or rendering dependency.

Optional animation is also immutable value data: `withRainbow(enabled, speed)` returns
another color; speed is finite and within 0.1–10 (default 1). `argb()` remains the stored
base color. A visual consumer can call `resolvedArgb(timeMillis)` to animate hue without
mutating settings, firing observers or writing config every frame. Alpha is preserved.
Static colors retain the old JSON string format. Colors with rainbow enabled or a
non-default speed use `{"rgba":"#12345678","rainbow":true,"speed":2}`; the decoder
accepts both formats. Older Perihelion builds do not understand the optional object
form. Command hex input supplies a new fixed color; the GUI's hex editor preserves
its rainbow settings.

Multi-select uses enum options so definitions and selected values stay typed. Input sets
are copied, and values returned by `get`, `defaultValue`, `validate`, parsing and observers
are unmodifiable. Create a new set to edit the selection. Output follows enum declaration
order regardless of input order. Empty selections use `[]` in JSON or `""` in commands.
Duplicate names, unknown options, null members and incorrect JSON types are rejected.

## Example inside a module definition

```java
enum Target { PLAYERS, ANIMALS, HOSTILE }

// guard is runtime.tasks()::checkThread, supplied when constructing this module.
Setting<Boolean> advanced = setting(Setting.bool(
        "advanced", "Advanced", "Show advanced controls", false, guard));
Setting<Double> scale = setting(Setting.decimal(
        "scale", "Scale", "Display scale", 1.0, 0.5, 2.0, 0.1, guard));
Setting<DoubleRange> interval = setting(Setting.range(
        "interval", "Interval", "Allowed interval", new DoubleRange(1, 3), 0, 10, 0.5, guard)
        .visibleWhen(advanced::get));
Setting<ColorValue> accent = setting(Setting.color(
        "accent", "Accent", "Accent including opacity", ColorValue.rgba(100, 150, 255, 200), guard));
Setting<Set<Target>> targets = setting(Setting.multiSelect(
        "targets", "Targets", "Selected target kinds", EnumSet.of(Target.PLAYERS), Target.class, guard));
```

Values are immutable and published through the existing volatile setting value. Value
and metadata reads and parsing are safe across threads; mutations, observer registration,
definition changes and visibility evaluation use the client-thread guard. JSON trees
returned by `toJson()` are detached from the setting. Compound values use the existing
config store and schema: strict document parsing, default fallback, unknown-setting
preservation, debounced capture and durable saving still apply.

## Observers and mutations

Inside `onEnable`, use `context.observe(setting, listener)` for module work. The setting
may belong to any module. Callbacks run only while this activation is active, after
enable succeeds. Read the initial value explicitly during enable; observation does not
send an initial notification. An ordinary callback failure closes this activation's
existing fault owner and queues its module for client-thread cleanup. Later callbacks
from that activation are skipped immediately; healthy observers still run in registration
order. Fatal JVM errors propagate. Removal belongs to the existing cleanup stack, after
owner retirement and `onDisable`, so network faults never remove setting observers on
the network thread. Closed owners gate callbacks throughout that interval.

```java
@Override protected void onEnable(ModuleContext context) {
    context.observe(scale, (setting, previous, current) -> refreshScale(current));
}

// Shared path for typed editor values, parsed command values and decoded config values:
boolean rawObserversSucceeded = runtime.modules().setSetting(moduleId, scale, candidate);
```

`setSetting` requires the client thread and verifies that the setting belongs to the
specified definition. Validation errors throw before mutation and do not fault modules.
Owned observer failures go to their own modules, and queued faults are processed before
the boundary returns. The value stays committed. A `false` result means a raw observer
failed and the defining module was conservatively quarantined; callers need not repeat
fault handling. A `true` result includes equal-value no-ops and isolated owned faults;
it does not promise that every observing module remains enabled. Commands parse with
`setting.parse`, config decodes with `setting.fromJson`, and typed editors can pass a
value directly. Reset through this boundary by passing `setting.defaultValue()`.

`Setting.set`, `reset` and raw `onChange` retain their low-level contracts: equal values
do not notify, recursive changes to the same setting are rejected, delivery uses a
snapshot with removed observers skipped, and ordinary raw failures are aggregated and
rethrown after delivery. A direct `set` still attributes owned observer faults correctly;
their lifecycle cleanup waits for the manager's normal fault drain. Raw observers are
appropriate for runtime bookkeeping with its own lifetime. Wrapping one in `context.own`
owns removal only; use `context.observe` when its callback belongs to a module.
