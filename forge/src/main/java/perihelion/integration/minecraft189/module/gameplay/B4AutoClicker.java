package perihelion.integration.minecraft189.module.gameplay;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.event.RenderTickEvent;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.ClientPacketEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Selected B4 input modes with native claims; donor block-hit/jitter remain at zero defaults. */
final class B4AutoClicker {
  private final GhostCombatModules.AutoClicker module;
  private final Setting<GhostCombatModules.AutoClicker.Mode> mode;
  private final Setting<Double> min, max, inventoryCps;
  private final Setting<Boolean> weapons;
  private final ControlRequest<Boolean> attack, use;
  private final B4ClickTiming timing = new B4ClickTiming(new Random());
  private boolean paused, breaking, inventoryClick;
  private long lastVelocity;
  private GuiScreen inventoryScreen;
  private Method screenClick;

  B4AutoClicker(
      GhostCombatModules.AutoClicker module,
      ModuleContext context,
      Setting<GhostCombatModules.AutoClicker.Mode> mode,
      Setting<Double> min,
      Setting<Double> max,
      Setting<Boolean> weapons,
      Setting<Double> inventoryCps) {
    this.module = module;
    this.mode = mode;
    this.min = min;
    this.max = max;
    this.weapons = weapons;
    this.inventoryCps = inventoryCps;
    attack = context.controls().key(InputKey.ATTACK, 40);
    use = context.controls().key(InputKey.USE_ITEM, 40);
    context.observe(mode, (s, before, after) -> reset());
    context.observe(inventoryCps, (s, before, after) -> reset());
    context.listen(
        RenderTickEvent.class,
        event -> {
          if (event.phase() == RenderTickEvent.Phase.START) frame();
        });
    context.listen(
        PlayerControlEvent.class,
        event -> {
          if (event != PlayerControlEvent.BEFORE_INPUT || !inventoryClick) return;
          inventoryClick = false;
          if (module.mc().currentScreen == inventoryScreen && inventoryScreen != null)
            clickScreen(inventoryScreen);
          inventoryScreen = null;
        });
    context.listen(
        ClientPacketEvent.class,
        event -> {
          if (!(event.packet() instanceof S12PacketEntityVelocity)
              || mode.get() == GhostCombatModules.AutoClicker.Mode.NATIVE) return;
          S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.packet();
          if (packet.getEntityID() != module.mc().thePlayer.getEntityId()
              || packet.getMotionY() <= 0
              || packet.getMotionY() >= 4500) return;
          long now = GameplayModule.now();
          if (left()
              && down(0)
              && timing.nextPress > 0
              && now - lastVelocity > 250
              && module.mc().objectMouseOver != null
              && module.mc().objectMouseOver.typeOfHit
                  == MovingObjectPosition.MovingObjectType.ENTITY) timing.damage(now);
          lastVelocity = now;
        });
  }

  private boolean left() {
    return mode.get() != GhostCombatModules.AutoClicker.Mode.B4_RIGHT;
  }

  private boolean down(int button) {
    return Mouse.isCreated() && Mouse.isButtonDown(button);
  }

  private void frame() {
    if (mode.get() == GhostCombatModules.AutoClicker.Mode.NATIVE) return;
    net.minecraft.client.Minecraft mc = module.mc();
    if (mc.theWorld == null || mc.thePlayer == null) {
      reset();
      return;
    }
    long now = GameplayModule.now();
    if (mc.currentScreen == null && mc.inGameHasFocus) {
      AuraModule aura = (AuraModule) module.runtime.modules().require("aura").module();
      boolean weapon =
          mc.thePlayer.getHeldItem() != null
              && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
      if (module.active("aura") && aura.target() != null
          || ((ScaffoldModule) module.runtime.modules().require("scaffold").module())
              .buildingActive()
          || ((BlockInModule) module.runtime.modules().require("blockin").module()).buildingActive()
          || !weapon && mc.thePlayer.isEating()) {
        paused = true;
        attack.clear();
        use.clear();
        return;
      }
      if (left() && down(0)) {
        use.clear();
        if (weapons.get() && !weapon || mc.thePlayer.isBlocking()) {
          paused = true;
          attack.clear();
          return;
        }
        if (paused) {
          paused = false;
          timing.resume();
        }
        click(now, InputKey.ATTACK, 0);
      } else if (mode.get() != GhostCombatModules.AutoClicker.Mode.B4_LEFT && down(1)) {
        attack.clear();
        if (mc.thePlayer.getHeldItem() == null
            || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock)) {
          use.clear();
          return;
        }
        click(now, InputKey.USE_ITEM, 1);
      } else reset();
    } else if (inventoryCps.get() != 0
        && (mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiChest)) {
      attack.clear();
      use.clear();
      if (down(0) && Keyboard.isCreated() && (Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54))) {
        if (timing.inventory(now, inventoryCps.get())) {
          inventoryClick = true;
          inventoryScreen = mc.currentScreen;
        }
      } else reset();
    } else reset();
  }

  private void click(long now, InputKey key, int button) {
    net.minecraft.client.Minecraft mc = module.mc();
    ControlRequest<Boolean> claim = key == InputKey.ATTACK ? attack : use;
    if (button == 0 && mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null) {
      Block block = mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
      if (block != Blocks.air && !(block instanceof BlockLiquid)) {
        breaking = true;
        claim.set(true);
        return;
      }
      if (breaking) {
        breaking = false;
        claim.set(false);
      }
    }
    B4ClickTiming.Action action = timing.click(now, min.get(), max.get());
    if (action == B4ClickTiming.Action.PRESS) {
      claim.set(true);
      if (claim.isWinning())
        KeyBinding.onTick(
            (button == 0 ? mc.gameSettings.keyBindAttack : mc.gameSettings.keyBindUseItem)
                .getKeyCode());
    } else if (action == B4ClickTiming.Action.RELEASE) claim.set(false);
  }

  private void reset() {
    timing.reset();
    paused = breaking = inventoryClick = false;
    inventoryScreen = null;
    attack.clear();
    use.clear();
  }

  private void clickScreen(GuiScreen gui) throws Exception {
    net.minecraft.client.Minecraft mc = module.mc();
    int x = Mouse.getX() * gui.width / mc.displayWidth;
    int y = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
    if (screenClick == null) {
      try {
        screenClick =
            GuiScreen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
      } catch (NoSuchMethodException mapped) {
        screenClick =
            GuiScreen.class.getDeclaredMethod("func_73864_a", int.class, int.class, int.class);
      }
      screenClick.setAccessible(true);
    }
    try {
      screenClick.invoke(gui, x, y, 0);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof IOException) throw (IOException) cause;
      if (cause instanceof RuntimeException) throw (RuntimeException) cause;
      if (cause instanceof Error) throw (Error) cause;
      throw new IllegalStateException("Inventory click failed", cause);
    }
  }
}
