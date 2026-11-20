package perihelion.integration.minecraft189;

import java.util.Objects;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import org.apache.logging.log4j.LogManager;
import perihelion.core.diagnostics.Failures;
import perihelion.integration.minecraft189.control.MinecraftControls;
import perihelion.integration.minecraft189.event.PlayerAttackEvent;
import perihelion.integration.minecraft189.event.VisualEffectEvent;
import perihelion.integration.minecraft189.network.RawHookBridge;

/** The only static runtime reference used by transformed Minecraft classes. */
public final class HookBridge {
  private static volatile Target target;

  private HookBridge() {}

  public interface Target {
    boolean isRunning();

    /** True cancels delivery. Called on the thread dispatching the packet. */
    boolean beforeSend(Packet<?> packet);

    /** True cancels delivery. Called on the channel's inbound thread. */
    boolean beforeReceive(Packet<?> packet);

    void stop();

    void reportHookFailure(String hook, Throwable failure);

    /** Null outside the client thread or when this target has no game controls. */
    default MinecraftControls controls() {
      return null;
    }

    /** Scalar packet classification only; may run on any dispatching thread. */
    default void observeOutgoing(Packet<?> packet) {}

    /** A PLAY packet passed the final cancellation/defer decision. Scalar observations only. */
    default void acceptedOutgoing(Packet<?> packet) {}

    /** Client-thread events; implementations must dispatch through owned listeners. */
    default void clientEvent(Object event) {}

    /**
     * False only when {@link #clientEvent} would deliver this event type to nobody. Must be safe
     * on any thread; the conservative default keeps every event flowing.
     */
    default boolean hasClientListeners(Class<?> type) {
      return true;
    }

    /** Render-only text filtering; the default path needs no event allocation. */
    default String displayText(String text) {
      return text;
    }

    default void command(String text) {}

    default java.util.List<String> completeCommand(String text) {
      return java.util.Collections.emptyList();
    }
  }

  public static synchronized void install(Target next) {
    Objects.requireNonNull(next, "target");
    if (target != null && target != next)
      throw new IllegalStateException("A client runtime is already installed");
    target = next;
  }

  public static synchronized void clear(Target expected) {
    if (target == expected) target = null;
  }

  public static boolean isRunning() {
    Target current = target;
    if (current == null) return false;
    try {
      return current.isRunning();
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("runtime-state", failure);
      return false;
    }
  }

  /** Fail closed for local command input, even if dispatch fails. */
  public static boolean chatCommand(String text) {
    Target current = target;
    if (current == null || text == null || !text.startsWith(".")) return false;
    try {
      if (current.isRunning()) current.command(text);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("client command", failure);
    }
    return true;
  }

  public static java.util.List<String> completeChat(String text) {
    Target current = target;
    if (current == null || text == null || !text.startsWith(".")) return null;
    try {
      return current.isRunning()
          ? current.completeCommand(text.substring(1))
          : java.util.Collections.emptyList();
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("command completion", failure);
      return java.util.Collections.emptyList();
    }
  }

  public static boolean beforeSend(Packet<?> packet) {
    return RawHookBridge.dispatch(target, packet, true);
  }

  public static boolean beforeSend(net.minecraft.network.NetworkManager source, Packet<?> packet) {
    return RawHookBridge.dispatch(target, source, packet, true);
  }

  /**
   * Observe scalar outgoing facts on local and remote PLAY channels without publishing raw events.
   */
  public static void observeOutgoing(Packet<?> packet) {
    if (RawHookBridge.isReplaying()) return;
    Target current = target;
    try {
      if (current != null && current.isRunning()) current.observeOutgoing(packet);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("outgoing packet facts", failure);
    }
  }

  /** Fresh send facts only: the controls provider returns null outside the client thread. */
  public static void observeClientSend(
      net.minecraft.network.NetworkManager source, Packet<?> packet) {
    if (RawHookBridge.isReplaying()) return;
    try {
      MinecraftControls current = controls();
      if (current != null) current.observeClientSend(source, packet);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("client send facts", failure);
    }
  }

  public static void observeServerSlot(net.minecraft.network.NetworkManager source, int slot) {
    try {
      MinecraftControls current = controls();
      if (current != null) current.observeServerSlot(source, slot);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("server slot fact", failure);
    }
  }

  public static boolean beforeReceive(Packet<?> packet) {
    return RawHookBridge.dispatch(target, packet, false);
  }

  public static boolean beforeReceive(
      net.minecraft.network.NetworkManager source, Packet<?> packet) {
    return RawHookBridge.dispatch(target, source, packet, false);
  }

  public static void acceptedOutgoing(Packet<?> packet) {
    Target current = target;
    try {
      if (current != null && current.isRunning()) current.acceptedOutgoing(packet);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("accepted outgoing packet facts", failure);
    }
  }

  public static MinecraftControls gameControls() {
    return controls();
  }

  public static String displayText(String text) {
    if (text == null) return null;
    Target current = target;
    try {
      return current != null && current.isRunning() ? current.displayText(text) : text;
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("rendered text", failure);
      return text;
    }
  }

  /** Null keeps vanilla trimming: the text is unchanged for display. */
  public static String trimDisplayText(
      net.minecraft.client.gui.FontRenderer font, String text, int width, boolean reverse) {
    if (text == null || text.isEmpty()) return null;
    String shown = displayText(text);
    if (shown == text || shown.equals(text)) return null;
    try {
      return perihelion.integration.minecraft189.render.DisplayText.trim(
          text, width, reverse, font::getStringWidth);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("trimmed text", failure);
      return null;
    }
  }

  private static MinecraftControls controls() {
    Target current = target;
    return current != null && current.isRunning() ? current.controls() : null;
  }

  private static void controlFailure(Throwable failure) {
    Failures.rethrowFatal(failure);
    reportFailure("shared controls", failure);
    stop();
  }

  public static boolean keyState(KeyBinding binding, boolean vanilla) {
    try {
      MinecraftControls controls = controls();
      return controls == null ? vanilla : controls.keyState(binding, vanilla);
    } catch (Throwable failure) {
      controlFailure(failure);
      return vanilla;
    }
  }

  public static int serverSlot(int vanilla) {
    try {
      MinecraftControls controls = controls();
      return controls == null ? vanilla : controls.serverSlot(vanilla);
    } catch (Throwable failure) {
      controlFailure(failure);
      return vanilla;
    }
  }

  public static float motionYaw(float vanilla) {
    try {
      MinecraftControls controls = controls();
      return controls == null ? vanilla : controls.motionYaw(vanilla);
    } catch (Throwable failure) {
      controlFailure(failure);
      return vanilla;
    }
  }

  public static float motionPitch(float vanilla) {
    try {
      MinecraftControls controls = controls();
      return controls == null ? vanilla : controls.motionPitch(vanilla);
    } catch (Throwable failure) {
      controlFailure(failure);
      return vanilla;
    }
  }

  public static double motionY(double vanilla) {
    MinecraftControls controls = controls();
    return controls == null ? vanilla : controls.motionY(vanilla);
  }

  public static boolean motionGround(boolean vanilla) {
    MinecraftControls controls = controls();
    return controls == null ? vanilla : controls.motionGround(vanilla);
  }

  public static void beforeInput() {
    try {
      MinecraftControls controls = controls();
      if (controls != null) controls.beforeInput();
    } catch (Throwable failure) {
      controlFailure(failure);
    }
  }

  public static void beforeMotion() {
    try {
      MinecraftControls controls = controls();
      if (controls != null) controls.beforeMotion();
    } catch (Throwable failure) {
      controlFailure(failure);
    }
  }

  public static void afterMotion() {
    try {
      MinecraftControls controls = controls();
      if (controls != null) controls.afterMotion();
    } catch (Throwable failure) {
      controlFailure(failure);
    }
  }

  /**
   * Allocation-free pre-check for hooks that run per entity, per block or several times a frame.
   * False means an event of this type has no listener, so the caller keeps vanilla behavior
   * without building the event. Only skip events whose untouched defaults equal vanilla.
   */
  public static boolean wantsClientEvent(Class<?> type) {
    Target current = target;
    if (current == null) return false;
    try {
      return current.isRunning() && current.hasClientListeners(type);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("client listener check", failure);
      return false;
    }
  }

  public static boolean suppressEffect(VisualEffectEvent.Kind kind) {
    if (!wantsClientEvent(VisualEffectEvent.class)) return false;
    VisualEffectEvent event = new VisualEffectEvent(kind);
    clientEvent(event);
    return event.isCancelled();
  }

  public static boolean attack(EntityLivingBase target) {
    PlayerAttackEvent event = new PlayerAttackEvent(target);
    clientEvent(event);
    return event.isCancelled();
  }

  public static void clientEvent(Object event) {
    Target current = target;
    try {
      if (current != null && current.isRunning()) current.clientEvent(event);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("client event", failure);
      stop();
    }
  }

  public static void stop() {
    Target current = target;
    if (current == null) return;
    try {
      current.stop();
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      reportFailure("shutdown", failure);
    } finally {
      clear(current);
    }
  }

  public static void reportFailure(String hook, Throwable failure) {
    Failures.rethrowFatal(failure);
    Target current = target;
    if (current != null) {
      try {
        current.reportHookFailure(hook, failure);
        return;
      } catch (Throwable loggingFailure) {
        Failures.rethrowFatal(loggingFailure);
        if (loggingFailure != failure) failure.addSuppressed(loggingFailure);
      }
    }
    LogManager.getLogger("Perihelion/Hooks").error("Hook failed: " + hook, failure);
  }
}
