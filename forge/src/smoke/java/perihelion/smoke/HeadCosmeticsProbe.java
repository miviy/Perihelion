package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.event.PlayerHeadRenderEvent;

/** Exercise the transformed model hook and activation-owned GPU resource retirement. */
public final class HeadCosmeticsProbe {
  public static void run(ClientRuntime runtime, Object game, Object player) throws Exception {
    boolean wasEnabled = runtime.modules().require("catears").requestedEnabled();
    Object ears = runtime.modules().require("catears").module();
    EventOwner owner =
        new EventOwner(
            "head-cosmetics-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    int[] events = {0};
    AutoCloseable subscription =
        runtime.clientEvents().subscribe(owner, PlayerHeadRenderEvent.class, event -> events[0]++);
    Class<?> modelType = Class.forName("net.minecraft.client.model.ModelPlayer");
    Object model = modelType.getConstructor(float.class, boolean.class).newInstance(0f, false);
    Method render =
        method(
            modelType,
            new Class<?>[] {
              Class.forName("net.minecraft.entity.Entity"),
              float.class,
              float.class,
              float.class,
              float.class,
              float.class,
              float.class
            },
            "render",
            "func_78088_a");
    try {
      runtime.modules().setEnabled("catears", false);
      runtime.modules().setEnabled("catears", true);
      render.invoke(model, player, 0f, 0f, 0f, 0f, 0f, .0625f);
      if (events[0] != 1)
        throw new AssertionError("ModelPlayer emits its transformed head hook once");
      int list = field(ears.getClass(), "displayList").getInt(ears);
      if (list < 0 || !GL11.glIsList(list))
        throw new AssertionError("Cat Ears compiles a valid GPU list");
      Map<?, ?> rigs = (Map<?, ?>) field(ears.getClass(), "rigs").get(ears);
      if (rigs.size() != 1) throw new AssertionError("Cat Ears owns one local player rig");
      runtime.modules().setEnabled("catears", false);
      if (field(ears.getClass(), "displayList").getInt(ears) != -1
          || GL11.glIsList(list)
          || !rigs.isEmpty())
        throw new AssertionError("Cat Ears disable retires its rig and GPU list");
      System.out.println("PERIHELION_SMOKE HEAD_COSMETICS checks=4 PASS");
    } finally {
      runtime.modules().setEnabled("catears", false);
      subscription.close();
      owner.close();
      if (wasEnabled) runtime.modules().setEnabled("catears", true);
    }
  }
}
