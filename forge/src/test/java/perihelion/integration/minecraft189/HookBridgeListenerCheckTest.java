package perihelion.integration.minecraft189;

import static org.junit.Assert.*;

import net.minecraft.network.Packet;
import org.junit.Test;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

/** Per-frame hooks skip event construction only when the target reports no listener. */
public class HookBridgeListenerCheckTest {
  private static final class Target implements HookBridge.Target {
    boolean running = true;
    Boolean listeners;
    int events;

    public boolean isRunning() {
      return running;
    }

    public boolean beforeSend(Packet<?> packet) {
      return false;
    }

    public boolean beforeReceive(Packet<?> packet) {
      return false;
    }

    public void stop() {}

    public void reportHookFailure(String hook, Throwable failure) {}

    @Override
    public void clientEvent(Object event) {
      events++;
      if (event instanceof VisualEffectEvent) ((VisualEffectEvent) event).cancel();
    }

    @Override
    public boolean hasClientListeners(Class<?> type) {
      return listeners == null ? HookBridge.Target.super.hasClientListeners(type) : listeners;
    }
  }

  @Test
  public void withoutATargetNothingIsWanted() {
    assertFalse(HookBridge.wantsClientEvent(VisualEffectEvent.class));
    assertFalse(HookBridge.suppressEffect(VisualEffectEvent.Kind.GAMMA));
  }

  @Test
  public void defaultTargetKeepsEveryEventFlowing() {
    Target target = new Target();
    HookBridge.install(target);
    try {
      assertTrue(HookBridge.wantsClientEvent(VisualEffectEvent.class));
      assertTrue(HookBridge.suppressEffect(VisualEffectEvent.Kind.GAMMA));
      assertEquals(1, target.events);

      target.running = false;
      assertFalse(HookBridge.wantsClientEvent(VisualEffectEvent.class));
      assertFalse(HookBridge.suppressEffect(VisualEffectEvent.Kind.GAMMA));
      assertEquals(1, target.events);
    } finally {
      HookBridge.clear(target);
    }
  }

  @Test
  public void eventsWithoutListenersAreNeverBuiltOrDispatched() {
    Target target = new Target();
    target.listeners = false;
    HookBridge.install(target);
    try {
      assertFalse(HookBridge.wantsClientEvent(VisualEffectEvent.class));
      assertFalse(HookBridge.suppressEffect(VisualEffectEvent.Kind.HURT_CAMERA));
      assertEquals(0, target.events);

      target.listeners = true;
      assertTrue(HookBridge.wantsClientEvent(VisualEffectEvent.class));
      assertTrue(HookBridge.suppressEffect(VisualEffectEvent.Kind.HURT_CAMERA));
      assertEquals(1, target.events);
    } finally {
      HookBridge.clear(target);
    }
  }
}
