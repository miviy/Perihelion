package perihelion.bootstrap.forge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import perihelion.core.context.ConnectionContext;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;
import perihelion.core.event.HudRenderEvent;
import perihelion.core.event.RenderTickEvent;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.MinecraftControls;
import perihelion.integration.minecraft189.event.ChatHudEvent;
import perihelion.integration.minecraft189.event.PacketReceiveEvent;
import perihelion.integration.minecraft189.event.PacketSendEvent;

final class ForgeClientAdapter implements HookBridge.Target {
  private final ClientRuntime runtime;
  private final ClientLog log;
  private final Minecraft minecraft;
  private final ForgeRootCommand command;
  private final MinecraftControls controls;
  private volatile boolean attached;
  private volatile boolean contextDirty = true;
  private Object lastWorld, lastPlayer;
  private NetworkManager lastManager;
  private boolean lastOpen;

  ForgeClientAdapter(ClientRuntime runtime, ClientLog log, MinecraftControls controls) {
    this.runtime = runtime;
    this.log = log;
    this.minecraft = Minecraft.getMinecraft();
    this.controls = controls;
    command = new ForgeRootCommand(runtime);
  }

  void attach() {
    runtime.tasks().checkThread();
    if (attached || !runtime.isRunning()) return;
    attached = true;
    try {
      // FMLCommonHandler.bus() is the same bus in Forge 2318. Register exactly once.
      MinecraftForge.EVENT_BUS.register(this);
      ClientCommandHandler.instance.registerCommand(command);
      HookBridge.install(this);
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      stop();
      throw failure;
    }
  }

  @Override
  public boolean isRunning() {
    return attached && runtime.isRunning();
  }

  @Override
  public MinecraftControls controls() {
    return runtime.tasks().isClientThread() ? controls : null;
  }

  @Override
  public void observeOutgoing(Packet<?> packet) {
    controls.outgoingFacts().record(packet);
  }

  @Override
  public void acceptedOutgoing(Packet<?> packet) {
    controls.outgoingFacts().accepted(packet);
  }

  @Override
  public void clientEvent(Object event) {
    runtime.tasks().checkThread();
    if (event instanceof perihelion.integration.minecraft189.event.ClientPacketEvent
        && ((perihelion.integration.minecraft189.event.ClientPacketEvent) event).packet()
            instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook)
      controls.requestRotationResend();
    if (isRunning() && runtime.worldReady()) runtime.clientEvents().post(event);
  }

  @Override
  public String displayText(String text) {
    if (!runtime.tasks().isClientThread()
        || !runtime.worldReady()
        || !runtime
            .clientEvents()
            .hasListeners(perihelion.integration.minecraft189.event.TextRenderEvent.class))
      return text;
    perihelion.integration.minecraft189.event.TextRenderEvent event =
        new perihelion.integration.minecraft189.event.TextRenderEvent(text);
    runtime.clientEvents().post(event);
    return event.text();
  }

  @Override
  public void command(String text) {
    runtime.tasks().checkThread();
    runtime
        .commands()
        .executeChat(
            text,
            message ->
                perihelion.integration.minecraft189.MinecraftContext.chat(
                    "[Perihelion] " + message));
  }

  @Override
  public java.util.List<String> completeCommand(String text) {
    java.util.List<String> result = runtime.commands().complete(text);
    if (!text.matches(".*\\s.*")) {
      java.util.List<String> roots = new java.util.ArrayList<>();
      for (String name : result) roots.add("." + name);
      return roots;
    }
    return result;
  }

  @Override
  public boolean beforeSend(Packet<?> packet) {
    if (!runtime.networkEvents().hasListeners(PacketSendEvent.class)) return false;
    PacketSendEvent event =
        runtime
            .networkEvents()
            .post(
                new PacketSendEvent(
                    perihelion.integration.minecraft189.network.RawHookBridge.source(), packet));
    if (event.isCancelled()
        && !event.isDeferred()
        && packet instanceof net.minecraft.network.play.client.C03PacketPlayer
        && runtime.tasks().isClientThread())
      controls.walkingPacketCancelled(
          ((net.minecraft.network.play.client.C03PacketPlayer) packet).isMoving());
    return event.isCancelled();
  }

  @Override
  public boolean beforeReceive(Packet<?> packet) {
    return runtime.networkEvents().hasListeners(PacketReceiveEvent.class)
        && runtime
            .networkEvents()
            .post(
                new PacketReceiveEvent(
                    perihelion.integration.minecraft189.network.RawHookBridge.source(), packet))
            .isCancelled();
  }

  @Override
  public void reportHookFailure(String context, Throwable failure) {
    log.error(context, failure);
  }

  @Override
  public void stop() {
    runtime.tasks().checkThread();
    if (!attached) {
      try {
        runtime.stop();
      } finally {
        controls.close();
      }
      return;
    }
    attached = false;
    HookBridge.clear(this);
    MinecraftForge.EVENT_BUS.unregister(this);
    if (ClientCommandHandler.instance.getCommands().get("perihelion") == command)
      ClientCommandHandler.instance.getCommands().remove("perihelion");
    command.detach();
    lastWorld = null;
    lastPlayer = null;
    lastManager = null;
    try {
      runtime.stop();
    } finally {
      controls.close();
    }
  }

  @SubscribeEvent
  public void tick(TickEvent.ClientTickEvent event) {
    guard(
        "client tick",
        () -> {
          if (event.phase == TickEvent.Phase.START) {
            refreshContext();
            refreshInput();
            runtime.tickStart();
          } else runtime.tickEnd();
          controls.apply();
        });
  }

  @SubscribeEvent
  public void render(TickEvent.RenderTickEvent event) {
    guard(
        "render tick",
        () -> {
          if (runtime.clientEvents().hasListeners(RenderTickEvent.class))
            runtime
                .clientEvents()
                .post(
                    new RenderTickEvent(
                        event.phase == TickEvent.Phase.START
                            ? RenderTickEvent.Phase.START
                            : RenderTickEvent.Phase.END,
                        event.renderTickTime));
        });
  }

  @SubscribeEvent
  public void worldRender(RenderWorldLastEvent event) {
    guard(
        "world render",
        () -> {
          if (runtime.clientEvents().hasListeners(WorldRenderEvent.class))
            runtime.clientEvents().post(new WorldRenderEvent(event.partialTicks));
        });
  }

  @SubscribeEvent
  public void hideChatInMenus(RenderGameOverlayEvent.Pre event) {
    if (attached
        && runtime.isRunning()
        && event.type == RenderGameOverlayEvent.ElementType.CHAT
        && (minecraft.currentScreen
                instanceof perihelion.integration.minecraft189.gui.panel.ModulePanelScreen
            || Minecraft.getMinecraft().currentScreen
                instanceof perihelion.integration.minecraft189.gui.panel.ConfigScreen
            || minecraft.currentScreen instanceof perihelion.integration.minecraft189.hud.HudEditor
            || minecraft.currentScreen
                instanceof perihelion.integration.minecraft189.account.AccountUi))
      event.setCanceled(true);
  }

  @SubscribeEvent
  public void hudRender(RenderGameOverlayEvent.Post event) {
    if (event.type != RenderGameOverlayEvent.ElementType.ALL
        || !runtime.clientEvents().hasListeners(HudRenderEvent.class)) return;
    guard(
        "HUD render",
        () -> {
          runtime.tasks().checkThread();
          if (minecraft.theWorld == null
              || minecraft.thePlayer == null
              || minecraft.gameSettings.hideGUI) return;
          // Forge has established the GUI projection and finished the vanilla overlay.
          // Reuse its resolution; frame tick END does not provide this drawing contract.
          runtime
              .clientEvents()
              .post(
                  new HudRenderEvent(
                      event.partialTicks,
                      event.resolution.getScaledWidth(),
                      event.resolution.getScaledHeight(),
                      event.resolution.getScaleFactor()));
        });
  }

  @SubscribeEvent
  public void chatHudDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
    if (!(event.gui instanceof GuiChat) || !runtime.clientEvents().hasListeners(ChatHudEvent.class))
      return;
    guard(
        "chat HUD render",
        () ->
            runtime
                .clientEvents()
                .post(ChatHudEvent.draw((GuiChat) event.gui, event.mouseX, event.mouseY)));
  }

  @SubscribeEvent
  public void itemUseFinished(
      net.minecraftforge.event.entity.player.PlayerUseItemEvent.Finish event) {
    if (event.entityPlayer != minecraft.thePlayer || !runtime.worldReady()) return;
    guard(
        "item consumption",
        () ->
            clientEvent(
                new perihelion.integration.minecraft189.event.ItemUseFinishedEvent(event.item)));
  }

  @SubscribeEvent
  public void gameplayGuiMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
    if (!runtime.worldReady()
        || !runtime
            .clientEvents()
            .hasListeners(perihelion.integration.minecraft189.event.GuiMouseEvent.class)) return;
    guard(
        "gameplay GUI mouse",
        () -> {
          int x = Mouse.getEventX() * event.gui.width / Math.max(1, minecraft.displayWidth);
          int y =
              event.gui.height
                  - Mouse.getEventY() * event.gui.height / Math.max(1, minecraft.displayHeight)
                  - 1;
          if (runtime
              .clientEvents()
              .post(
                  new perihelion.integration.minecraft189.event.GuiMouseEvent(
                      event.gui,
                      x,
                      y,
                      Mouse.getEventButton(),
                      Mouse.getEventButtonState(),
                      Mouse.getEventDWheel()))
              .isCancelled()) event.setCanceled(true);
        });
  }

  @SubscribeEvent
  public void chatHudMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
    if (!(event.gui instanceof GuiChat) || !runtime.clientEvents().hasListeners(ChatHudEvent.class))
      return;
    guard(
        "chat HUD mouse",
        () -> {
          int x = Mouse.getEventX() * event.gui.width / Math.max(1, minecraft.displayWidth);
          int y =
              event.gui.height
                  - Mouse.getEventY() * event.gui.height / Math.max(1, minecraft.displayHeight)
                  - 1;
          if (runtime
              .clientEvents()
              .post(
                  ChatHudEvent.mouse(
                      (GuiChat) event.gui,
                      x,
                      y,
                      Mouse.getEventButton(),
                      Mouse.getEventButtonState(),
                      Mouse.getEventDWheel()))
              .isCancelled()) event.setCanceled(true);
        });
  }

  @SubscribeEvent
  public void chatHudKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
    if (!(event.gui instanceof GuiChat) || !runtime.clientEvents().hasListeners(ChatHudEvent.class))
      return;
    guard(
        "chat HUD key",
        () -> {
          if (runtime
              .clientEvents()
              .post(
                  ChatHudEvent.key(
                      (GuiChat) event.gui, Keyboard.getEventKey(), Keyboard.getEventKeyState()))
              .isCancelled()) event.setCanceled(true);
        });
  }

  @SubscribeEvent
  public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
    contextDirty = true;
  }

  @SubscribeEvent
  public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
    contextDirty = true;
  }

  @SubscribeEvent
  public void worldLoaded(WorldEvent.Load event) {
    if (event.world.isRemote) contextDirty = true;
  }

  @SubscribeEvent
  public void worldUnloaded(WorldEvent.Unload event) {
    if (!event.world.isRemote) return;
    contextDirty = true;
    guard(
        "world unload",
        () -> {
          runtime.updateWorld(false, runtime.connection(), true);
          controls.apply();
        });
  }

  @SubscribeEvent
  public void keyboard(net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent event) {
    key(false);
  }

  @SubscribeEvent
  public void scroll(net.minecraftforge.client.event.MouseEvent event) {
    if (event.dwheel == 0 || !runtime.isRunning()) return;
    perihelion.integration.minecraft189.event.MouseScrollEvent scroll =
        new perihelion.integration.minecraft189.event.MouseScrollEvent(event.dwheel);
    runtime.clientEvents().post(scroll);
    if (scroll.isCancelled()) event.setCanceled(true);
  }

  @SubscribeEvent
  public void mouse(net.minecraftforge.fml.common.gameevent.InputEvent.MouseInputEvent event) {
    mouseButton(false);
  }

  @SubscribeEvent
  public void guiKeyboard(GuiScreenEvent.KeyboardInputEvent.Post event) {
    key(true);
  }

  @SubscribeEvent
  public void guiMouse(GuiScreenEvent.MouseInputEvent.Post event) {
    mouseButton(true);
  }

  private void key(boolean guiInput) {
    guard(
        "keyboard input",
        () -> {
          // Enter/Escape can close the screen before Forge emits this GUI Post event.
          if (guiInput) runtime.input().setAllowed(false, java.util.Collections.emptyList());
          else refreshInput();
          int code = Keyboard.getEventKey();
          if (code > 0 && code <= 255)
            runtime
                .input()
                .accept(new Keybind(Keybind.Device.KEYBOARD, code), Keyboard.getEventKeyState());
        });
  }

  private void mouseButton(boolean guiInput) {
    guard(
        "mouse input",
        () -> {
          if (guiInput) runtime.input().setAllowed(false, java.util.Collections.emptyList());
          else refreshInput();
          int code = Mouse.getEventButton();
          if (code >= 0 && code < 32)
            runtime
                .input()
                .accept(new Keybind(Keybind.Device.MOUSE, code), Mouse.getEventButtonState());
        });
  }

  private void refreshInput() {
    boolean allowed =
        minecraft.inGameHasFocus && minecraft.currentScreen == null && runtime.worldReady();
    if (allowed == runtime.input().isAllowed()) return;
    List<Keybind> down = new ArrayList<>();
    if (Keyboard.isCreated())
      for (int i = 1; i < 256; i++)
        if (Keyboard.isKeyDown(i)) down.add(new Keybind(Keybind.Device.KEYBOARD, i));
    if (Mouse.isCreated())
      for (int i = 0; i < Math.min(32, Mouse.getButtonCount()); i++)
        if (Mouse.isButtonDown(i)) down.add(new Keybind(Keybind.Device.MOUSE, i));
    runtime.input().setAllowed(allowed, down);
  }

  private void refreshContext() {
    NetHandlerPlayClient handler = minecraft.getNetHandler();
    NetworkManager manager = handler == null ? null : handler.getNetworkManager();
    boolean open = manager != null && manager.isChannelOpen();
    boolean identityChanged =
        minecraft.theWorld != lastWorld
            || minecraft.thePlayer != lastPlayer
            || manager != lastManager;
    if (!identityChanged && open == lastOpen && !contextDirty) return;
    contextDirty = false;
    lastWorld = minecraft.theWorld;
    lastPlayer = minecraft.thePlayer;
    lastManager = manager;
    lastOpen = open;
    boolean ready = open && lastWorld != null && lastPlayer != null;
    ConnectionContext context = ConnectionContext.DISCONNECTED;
    if (open) {
      boolean local = manager.isLocalChannel();
      String address =
          !local && minecraft.getCurrentServerData() != null
              ? minecraft.getCurrentServerData().serverIP
              : "";
      context =
          new ConnectionContext(
              local ? ConnectionContext.Kind.SINGLEPLAYER : ConnectionContext.Kind.MULTIPLAYER,
              address);
    }
    runtime.updateWorld(ready, context, identityChanged);
  }

  private void guard(String context, Runnable action) {
    if (!isRunning()) return;
    try {
      action.run();
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      log.error("Essential bridge failed during " + context + "; stopping Perihelion", failure);
      stop();
    }
  }
}
