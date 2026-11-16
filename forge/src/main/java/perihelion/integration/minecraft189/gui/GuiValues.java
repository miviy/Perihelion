package perihelion.integration.minecraft189.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.NumericConstraints;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.hud.TargetHudModule.Design;

/** View-independent editor math and presentation. No Minecraft/GL state. */
public final class GuiValues {
  private GuiValues() {}

  public static double snap(NumericConstraints n, double fraction) {
    double value = n.minimum() + Math.max(0, Math.min(1, fraction)) * (n.maximum() - n.minimum());
    if (!n.step().isPresent()) return value;
    BigDecimal min = BigDecimal.valueOf(n.minimum()),
        step = BigDecimal.valueOf(n.step().getAsDouble());
    BigDecimal maxIndex =
        BigDecimal.valueOf(n.maximum()).subtract(min).divide(step, 0, RoundingMode.FLOOR);
    BigDecimal index =
        BigDecimal.valueOf(value)
            .subtract(min)
            .divide(step, 0, RoundingMode.HALF_UP)
            .max(BigDecimal.ZERO)
            .min(maxIndex);
    return min.add(index.multiply(step)).doubleValue();
  }

  public static String number(double number) {
    return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
  }

  /** Enum display names may change without changing stable config/command tokens. */
  public static String optionLabel(Setting<?> setting, String option) {
    Object value =
        setting.metadata().kind() == perihelion.core.setting.SettingMetadata.Kind.ENUM
            ? setting.parse(option)
            : option;
    if (value instanceof Design) {
      Design design = (Design) value;
      String label = design.displayName();
      String donor = designDonor(design);
      for (Design other : Design.values())
        if (!donor.equals(designDonor(other)) && label.equalsIgnoreCase(other.displayName()))
          return label + " (" + readable(donor) + ")";
      return label;
    }
    return readable(String.valueOf(value));
  }

  private static String designDonor(Design design) {
    String name = design.name();
    int separator = name.indexOf('_');
    return separator < 0 ? name : name.substring(0, separator);
  }

  /** Sentence-like labels for enum names and client module titles; stored values stay unchanged. */
  public static String readable(String raw) {
    StringBuilder result = new StringBuilder(raw.length() + 4);
    boolean first = true;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '_' || c == '-' || Character.isWhitespace(c)) {
        if (result.length() > 0 && result.charAt(result.length() - 1) != ' ') {
          result.append(' ');
          first = false;
        }
        continue;
      }
      if (i > 0 && Character.isUpperCase(c)) {
        char previous = raw.charAt(i - 1);
        boolean boundary =
            Character.isLowerCase(previous)
                || Character.isDigit(previous)
                || Character.isUpperCase(previous)
                    && i + 1 < raw.length()
                    && Character.isLowerCase(raw.charAt(i + 1));
        if (boundary && result.length() > 0 && result.charAt(result.length() - 1) != ' ') {
          result.append(' ');
          first = false;
        }
      }
      result.append(first ? Character.toUpperCase(c) : Character.toLowerCase(c));
      first = false;
    }
    if (result.length() > 0 && result.charAt(result.length() - 1) == ' ')
      result.setLength(result.length() - 1);
    return result.toString();
  }

  public static String moduleLabel(String raw) {
    String value = readable(raw);
    StringBuilder out = new StringBuilder(value.length());
    boolean start = true;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      out.append(start ? Character.toUpperCase(c) : c);
      start = c == ' ';
    }
    return out.toString()
        .replaceAll("\\bEsp\\b", "ESP")
        .replaceAll("\\bHud\\b", "HUD")
        .replaceAll("\\bFov\\b", "FOV")
        .replaceAll("\\bGui\\b", "GUI");
  }

  public static String searchable(ModuleEntry entry) {
    StringBuilder text =
        new StringBuilder(entry.info().id())
            .append(' ')
            .append(entry.info().name())
            .append(' ')
            .append(readable(entry.info().name()))
            .append(' ')
            .append(entry.info().description())
            .append(' ')
            .append(entry.info().category().displayName());
    for (Setting<?> setting : entry.module().settings().values()) {
      text.append(' ')
          .append(setting.id())
          .append(' ')
          .append(setting.displayName())
          .append(' ')
          .append(setting.description());
      for (String option : setting.metadata().options())
        text.append(' ').append(option).append(' ').append(readable(option));
    }
    return text.toString().toLowerCase(Locale.ROOT);
  }

  /** Each search term must match; separators in names and stable IDs are interchangeable. */
  public static boolean matches(String searchable, String query) {
    String compact = searchable.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
      if (!compact.contains(word.replaceAll("[_-]+", ""))) return false;
    return true;
  }

  public static String fault(ModuleEntry entry) {
    Throwable failure = entry.lastFailure();
    if (failure == null) return "Faulted. See log for details.";
    String message;
    try {
      message = failure.getMessage();
    } catch (RuntimeException ignored) {
      message = null;
    }
    if (message == null || message.trim().isEmpty()) message = failure.getClass().getSimpleName();
    message = message.replace('\n', ' ').replace('\r', ' ');
    if (message.length() > 110) message = failure.getClass().getSimpleName();
    return "Faulted: " + message + ". See log.";
  }
}
