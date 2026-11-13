package perihelion.integration.minecraft189.control;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Timer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import perihelion.core.control.CameraPerspective;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.HotbarSelection;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.control.SharedControls;
import perihelion.core.diagnostics.Failures;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.compat.MotionHookStatus;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/**
 * Applies the core's requests at 1.8.9 update boundaries. Construct once and inject into game
 * modules.
 */
public final class MinecraftControls implements AutoCloseable {
  private static final InputKey[] INPUT_KEYS = InputKey.values();
  private final ClientRuntime runtime;
  private final SharedControls controls;
  private final Minecraft game;
  private final StateOverride<Float> timer = new StateOverride<>();
  private final StateOverride<Integer> slot = new StateOverride<>();
  private final StateOverride<Boolean> sprint = new StateOverride<>();
  private final StateOverride<Integer> perspective = new StateOverride<>();
  private final perihelion.integration.minecraft189.network.OutgoingPacketFacts outgoingFacts =
      new perihelion.integration.minecraft189.network.OutgoingPacketFacts();
  private final perihelion.integration.minecraft189.network.PacketOrderBarrier<
          perihelion.integration.minecraft189.network.PacketBufferScope>
      outboundOrder = new perihelion.integration.minecraft189.network.PacketOrderBarrier<>();
  private EntityPlayerSP player;
  private long generation;
  private boolean hadHotbar, inHotbarAction, applying, closed;
  private Rotation motionRotation;
  private ControlRequest<Rotation> motionOwner, appliedOwner;
  private Rotation appliedRotation;
  private ControlRequest<Rotation> previousOwner;
  private Rotation previousRotation;
  private net.minecraft.world.World previousWorld;
  private net.minecraft.network.NetworkManager previousConnection,
      motionConnection,
      appliedConnection;
  private double previousX, previousY, previousZ;
  private boolean beforeWalking, inputActionPhase;
  private long walkingEpoch, inputEpoch = -1;
  private ControlRequest<Rotation> modelOwner;
  private Rotation modelRotation, previousModelRotation;
  private boolean inRotationAction;
  private boolean motionActionPhase;
  private int attemptedServerSlot = -1, walkingAirTicks, clientActions;
  private net.minecraft.network.NetworkManager clientFactConnection;
  private boolean plannedPositionKnown, plannedPositionValid = true;
  private double plannedX, plannedY, plannedZ;
  private boolean walkingCancelled;
  private boolean rotationResend;
  private float reportedYaw, reportedPitch;
  private perihelion.integration.minecraft189.event.MotionPacketEvent motionPacket;

  public MinecraftControls(ClientRuntime runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    runtime.tasks().checkThread();
    this.controls = runtime.controls();
    this.game = Minecraft.getMinecraft();
    this.generation = runtime.tasks().generation();
    runtime.own(this);
  }

  /** Packet facts are atomic and may be recorded without reading live client state. */
  public perihelion.integration.minecraft189.network.OutgoingPacketFacts outgoingFacts() {
    return outgoingFacts;
  }

  private boolean currentFactConnection(net.minecraft.network.NetworkManager source) {
    // This check must precede every live Minecraft read; callers may originate on Netty.
    if (!runtime.tasks().isClientThread()) return false;
    return available()
        && player == game.thePlayer
        && generation == runtime.tasks().generation()
        && player.worldObj == game.theWorld
        && source != null
        && source == connection()
        && source.isChannelOpen();
  }

  public void observeClientSend(
      net.minecraft.network.NetworkManager source, net.minecraft.network.Packet<?> packet) {
    if (perihelion.integration.minecraft189.network.RawHookBridge.isReplaying()
        || !currentFactConnection(source)) return;
    if (clientFactConnection != source) {
      clientFactConnection = source;
      clientActions = 0;
      plannedPositionKnown = false;
      plannedPositionValid = true;
    }
    // Mutate only the already-constructed wire packet. Vanilla's choice of packet type and
    // lastReportedPosY must continue to use its original walking coordinates.
    if (packet instanceof net.minecraft.network.play.client.C03PacketPlayer
        && runtime
            .clientEvents()
            .hasListeners(perihelion.integration.minecraft189.event.ClientWalkPacketEvent.class))
      runtime
          .clientEvents()
          .post(
              new perihelion.integration.minecraft189.event.ClientWalkPacketEvent(
                  (net.minecraft.network.play.client.C03PacketPlayer) packet));
    if (packet instanceof net.minecraft.network.play.client.C03PacketPlayer) {
      clientActions = 0;
      net.minecraft.network.play.client.C03PacketPlayer walk =
          (net.minecraft.network.play.client.C03PacketPlayer) packet;
      if (walk.isMoving()) {
        // Follow fresh encoded order, including native deferred walking/action sequences.
        // An accepted-only position would incorrectly reject an intentionally buffered walk.
        plannedX = walk.getPositionX();
        plannedY = walk.getPositionY();
        plannedZ = walk.getPositionZ();
        plannedPositionKnown = true;
        plannedPositionValid = true;
      }
    } else if (packet instanceof net.minecraft.network.play.client.C02PacketUseEntity) {
      clientActions |= 1;
      if (((net.minecraft.network.play.client.C02PacketUseEntity) packet).getAction()
          == net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK) clientActions |= 2;
    } else if (packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging)
      clientActions |= 4;
    else if (packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement)
      clientActions |= 8;
    if (packet instanceof net.minecraft.network.play.client.C09PacketHeldItemChange)
      attemptedServerSlot =
          ((net.minecraft.network.play.client.C09PacketHeldItemChange) packet).getSlotId();
    if (!runtime
        .clientEvents()
        .hasListeners(perihelion.integration.minecraft189.event.ClientSendFactEvent.class)) return;
    perihelion.integration.minecraft189.event.ClientSendFactEvent fact =
        perihelion.integration.minecraft189.event.ClientSendFactEvent.from(packet);
    if (fact != null) runtime.clientEvents().post(fact);
  }

  /** B4's global fresh action window survives module enable/mode changes, until walking. */
  public int clientActionMask() {
    runtime.tasks().checkThread();
    return currentFactConnection(clientFactConnection) ? clientActions : 0;
  }

  private boolean plannedPositionMatchesPlayer() {
    return clientFactConnection == motionConnection
        && plannedPositionValid
        && (!plannedPositionKnown
            || plannedX == player.posX
                && plannedY == player.getEntityBoundingBox().minY
                && plannedZ == player.posZ);
  }

  public void observeServerSlot(net.minecraft.network.NetworkManager source, int selected) {
    if (currentFactConnection(source) && selected >= 0 && selected < 9)
      attemptedServerSlot = selected;
  }

  public int attemptedServerSlot(int vanilla) {
    runtime.tasks().checkThread();
    return attemptedServerSlot < 0 ? vanilla : attemptedServerSlot;
  }

  /** Retire an applied pre-input sprint write so vanilla owns subsequent sprint eligibility. */
  public void finishInputSprint(ControlRequest<Boolean> request) {
    runtime.tasks().checkThread();
    if (available() && player == game.thePlayer && request.isWinning())
      sprint.commit(player::isSprinting);
    request.clear();
  }

  /** B4's air history is updated after physics, immediately before PRE walking callbacks. */
  public int walkingAirTicks() {
    runtime.tasks().checkThread();
    return walkingAirTicks;
  }

  /** Tick/world-change reconciliation. Slot synchronization may dispatch packet callbacks. */
  public void apply() {
    runtime.tasks().checkThread();
    if (applying) return;
    applying = true;
    try {
      if (!prepare()) return;
      EntityPlayerSP current = player;
      Timer gameTimer = ((MinecraftTimerAccess) game).perihelion$timer();
      timer.apply(
          controls.timerSpeed(), () -> gameTimer.timerSpeed, value -> gameTimer.timerSpeed = value);
      HotbarSelection selected = controls.hotbar().orElse(null);
      if (!inHotbarAction) {
        slot.apply(
            selected != null && selected.mode() == HotbarSelection.Mode.VISIBLE
                ? Optional.of(selected.slot())
                : Optional.empty(),
            () -> current.inventory.currentItem,
            value -> current.inventory.currentItem = value);
        if (selected != null && !selected.restore() && !controls.hasRestoringHotbarRequest())
          slot.commit(() -> current.inventory.currentItem);
        boolean synchronize = selected != null || hadHotbar;
        hadHotbar = selected != null;
        if (synchronize) syncHeldItem();
      }
      if (available() && player == current)
        sprint.apply(controls.sprinting(), current::isSprinting, current::setSprinting);
      perspective.apply(
          controls.perspective().map(CameraPerspective::ordinal),
          () -> game.gameSettings.thirdPersonView,
          value -> game.gameSettings.thirdPersonView = value);
    } finally {
      applying = false;
    }
  }

  private boolean prepare() {
    if (closed) return false;
    if (generation != runtime.tasks().generation()
        || player != game.thePlayer
        || !runtime.worldReady()) {
      releasePlayer();
      restoreTimer();
      generation = runtime.tasks().generation();
    }
    if (!runtime.isRunning()
        || !runtime.worldReady()
        || game.thePlayer == null
        || game.theWorld == null) return false;
    player = game.thePlayer;
    return true;
  }

  private boolean available() {
    return !closed
        && runtime.isRunning()
        && runtime.worldReady()
        && game.thePlayer != null
        && game.theWorld != null;
  }

  /**
   * Leaves vanilla's physical state intact; release/fault falls through to the current vanilla
   * value.
   */
  public boolean keyState(KeyBinding binding, boolean vanilla) {
    runtime.tasks().checkThread();
    if (!available() || !Display.isCreated() || !Display.isActive()) return vanilla;
    for (InputKey key : INPUT_KEYS) {
      if (binding == binding(key)) return controls.key(key).orElse(vanilla);
    }
    return vanilla;
  }

  /** Raw binding-aware input for InventoryMove-style modules; never reads the override. */
  public boolean physicalKeyDown(InputKey key) {
    runtime.tasks().checkThread();
    if (!Display.isCreated() || !Display.isActive()) return false;
    int code = binding(Objects.requireNonNull(key, "key")).getKeyCode();
    if (code < 0)
      return Mouse.isCreated()
          && code + 100 >= 0
          && code + 100 < Mouse.getButtonCount()
          && Mouse.isButtonDown(code + 100);
    return Keyboard.isCreated()
        && code > 0
        && code < Keyboard.KEYBOARD_SIZE
        && Keyboard.isKeyDown(code);
  }

  private KeyBinding binding(InputKey key) {
    switch (key) {
      case FORWARD:
        return game.gameSettings.keyBindForward;
      case BACK:
        return game.gameSettings.keyBindBack;
      case LEFT:
        return game.gameSettings.keyBindLeft;
      case RIGHT:
        return game.gameSettings.keyBindRight;
      case JUMP:
        return game.gameSettings.keyBindJump;
      case SNEAK:
        return game.gameSettings.keyBindSneak;
      case SPRINT:
        return game.gameSettings.keyBindSprint;
      case ATTACK:
        return game.gameSettings.keyBindAttack;
      case USE_ITEM:
        return game.gameSettings.keyBindUseItem;
      default:
        throw new AssertionError(key);
    }
  }

  /**
   * Called only from PlayerControllerMP.syncCurrentPlayItem, retaining vanilla C09 deduplication.
   */
  public int serverSlot(int vanilla) {
    runtime.tasks().checkThread();
    return available() ? controls.hotbar().map(HotbarSelection::slot).orElse(vanilla) : vanilla;
  }

  /** Vanilla mining must calculate hardness with the same tool advertised to the server. */
  public float miningStrength(
      net.minecraft.block.Block block,
      net.minecraft.world.World world,
      net.minecraft.util.BlockPos position) {
    runtime.tasks().checkThread();
    EntityPlayerSP current = game.thePlayer;
    int visible = current.inventory.currentItem, selected = serverSlot(visible);
    current.inventory.currentItem = selected;
    try {
      return block.getPlayerRelativeBlockHardness(current, world, position);
    } finally {
      if (current.inventory.currentItem == selected) current.inventory.currentItem = visible;
    }
  }

  private void syncHeldItem() {
    if (game.playerController != null && game.thePlayer != null && game.getNetHandler() != null) {
      ((HeldItemSync) game.playerController).perihelion$syncHeldItem();
    }
  }

  /**
   * Runs only for this runtime's winning hotbar claim. Supplies the matching stack and temporarily
   * aligns local held-item calculations for SPOOF. Always restores the visible slot, even on
   * failure. Does not replay, suppress or bypass packets. Call from an owned client-thread
   * callback.
   */
  public boolean withHotbar(ControlRequest<HotbarSelection> request, Consumer<ItemStack> action) {
    runtime.tasks().checkThread();
    Objects.requireNonNull(action, "action");
    if (inHotbarAction) throw new IllegalStateException("Nested hotbar action");
    if (!available() || !controls.ownsHotbar(request)) return false;
    apply();
    if (!available() || !controls.ownsHotbar(request)) return false;
    EntityPlayerSP actingPlayer = player;
    Object actionWorld = game.theWorld;
    Object actionConnection = game.getNetHandler();
    long actionGeneration = runtime.tasks().generation();
    int selected = request.value().get().slot();
    int original = actingPlayer.inventory.currentItem;
    inHotbarAction = true;
    actingPlayer.inventory.currentItem = selected;
    Throwable actionFailure = null;
    try {
      syncHeldItem();
      if (!available()
          || game.thePlayer != actingPlayer
          || game.theWorld != actionWorld
          || game.getNetHandler() != actionConnection
          || runtime.tasks().generation() != actionGeneration
          || !controls.ownsHotbar(request)) return false;
      action.accept(actingPlayer.inventory.getStackInSlot(selected));
      return true;
    } catch (RuntimeException | Error failure) {
      actionFailure = failure;
      throw failure;
    } finally {
      if (actingPlayer.inventory.currentItem == selected)
        actingPlayer.inventory.currentItem = original;
      inHotbarAction = false;
      try {
        // Shutdown advances the task generation too. The actual connection identities
        // decide whether vanilla needs a final slot sync after the temporary selection.
        if (game.thePlayer == actingPlayer
            && game.theWorld == actionWorld
            && game.getNetHandler() == actionConnection) {
          if (closed) syncHeldItem();
          else apply();
        }
      } catch (RuntimeException | Error restoreFailure) {
        Failures.rethrowFatal(restoreFailure);
        if (actionFailure == null) throw restoreFailure;
        if (actionFailure != restoreFailure) actionFailure.addSuppressed(restoreFailure);
      }
    }
  }

  public void beforeInput() {
    runtime.tasks().checkThread();
    // A failed vanilla walking pass may never reach its RETURN hook.
    motionOwner = null;
    motionRotation = null;
    motionPacket = null;
    appliedOwner = null;
    appliedRotation = null;
    appliedConnection = null;
    beforeWalking = walkingEpoch != inputEpoch;
    inputEpoch = walkingEpoch;
    if (!prepare() || player.isRiding()) return;
    inputActionPhase = true;
    try {
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
    } finally {
      inputActionPhase = false;
    }
    apply();
    if (available()) runtime.clientEvents().post(PlayerControlEvent.BEFORE_ACTIONS);
    apply();
    if (available()) applyView(controls.rotation().orElse(null));
  }

  public void beforeMotion() {
    runtime.tasks().checkThread();
    beforeWalking = false;
    motionRotation = null;
    motionOwner = null;
    appliedOwner = null;
    appliedRotation = null;
    appliedConnection = null;
    motionPacket = null;
    walkingCancelled = false;
    motionConnection = null;
    if (!prepare()) return;
    motionConnection = connection();
    walkingAirTicks = player.onGround ? 0 : walkingAirTicks + 1;
    reportedYaw = ((PlayerRotationAccess) player).perihelion$reportedYaw();
    reportedPitch = ((PlayerRotationAccess) player).perihelion$reportedPitch();
    motionActionPhase = true;
    try {
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
    } finally {
      motionActionPhase = false;
    }
    apply();
    if (!available()) return;
    // Capture the pair once: a network fault cannot split yaw/pitch midway through a packet.
    ControlRequest<Rotation> owner = controls.rotationRequest().orElse(null);
    Rotation rotation = owner == null ? null : owner.value().orElse(null);
    applyView(rotation);
    if (rotation != null && (rotation.mode() == Rotation.Mode.VIEW || supportsServerRotation())) {
      motionRotation = rotation;
      motionOwner = owner;
    }
    if (rotationResend && motionRotation != null) {
      ((PlayerRotationAccess) player).perihelion$forceRotationResend(motionRotation.yaw());
      rotationResend = false;
    }
    motionPacket =
        new perihelion.integration.minecraft189.event.MotionPacketEvent(
            player.getEntityBoundingBox().minY, player.onGround);
    runtime.clientEvents().post(motionPacket);
  }

  public void afterMotion() {
    runtime.tasks().checkThread();
    // Actions must belong to the request used by this walking pass. A higher-priority
    // action clearing its request must not unlock an unsent lower-priority aim.
    boolean current =
        !walkingCancelled
            && available()
            && player == game.thePlayer
            && generation == runtime.tasks().generation()
            && player.worldObj == game.theWorld
            && motionConnection != null
            && motionConnection == connection()
            && motionConnection.isChannelOpen()
            && game.getRenderViewEntity() == player;
    if (walkingCancelled && player == game.thePlayer)
      ((PlayerRotationAccess) player)
          .perihelion$restoreReportedRotation(
              motionRotation == null ? player.rotationYaw : motionRotation.yaw(),
              motionRotation == null ? player.rotationPitch : motionRotation.pitch(),
              reportedYaw,
              reportedPitch);
    appliedOwner = current ? motionOwner : null;
    appliedRotation = current ? motionRotation : null;
    appliedConnection = current ? motionConnection : null;
    previousOwner = appliedOwner;
    previousRotation =
        current
                && motionPacket != null
                && motionPacket.y() == player.getEntityBoundingBox().minY
                && plannedPositionMatchesPlayer()
            ? appliedRotation
            : null;
    if (previousRotation != null) {
      previousX = player.posX;
      previousY = player.posY;
      previousZ = player.posZ;
      previousWorld = game.theWorld;
      previousConnection = connection();
    } else {
      previousWorld = null;
      previousConnection = null;
    }
    walkingEpoch++;
    if (appliedRotation != null) {
      previousModelRotation =
          modelOwner == appliedOwner && modelRotation != null
              ? modelRotation
              : Rotation.server(player.rotationYaw, player.rotationPitch);
      modelRotation = appliedRotation;
      modelOwner = appliedOwner;
    }
    motionOwner = null;
    motionRotation = null;
    motionPacket = null;
    motionConnection = null;
    if (!available()) return;
    runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
    apply();
  }

  /** True only while the request still matches the rotation submitted by a walking pass. */
  public boolean isRotationApplied(ControlRequest<Rotation> request) {
    runtime.tasks().checkThread();
    return available()
        && player == game.thePlayer
        && generation == runtime.tasks().generation()
        && player.worldObj == game.theWorld
        && appliedConnection != null
        && appliedConnection == connection()
        && appliedConnection.isChannelOpen()
        && request != null
        && request == appliedOwner
        && request.isWinning()
        && request.value().orElse(null) == appliedRotation;
  }

  /** Previous walking aim, available only in the next input pass at the same player position. */
  public Optional<Rotation> previousRotation(ControlRequest<Rotation> request) {
    runtime.tasks().checkThread();
    if (!beforeWalking
        || rotationResend
        || !available()
        || player != game.thePlayer
        || generation != runtime.tasks().generation()
        || previousWorld != game.theWorld
        || player.worldObj != previousWorld
        || previousConnection == null
        || previousConnection != connection()
        || !previousConnection.isChannelOpen()
        || game.getRenderViewEntity() != player
        || request == null
        || request != previousOwner
        || previousRotation == null
        || player.posX != previousX
        || player.posY != previousY
        || player.posZ != previousZ) return Optional.empty();
    PlayerRotationAccess access = (PlayerRotationAccess) player;
    return access.perihelion$reportedYaw() == previousRotation.yaw()
            && access.perihelion$reportedPitch() == previousRotation.pitch()
        ? Optional.of(previousRotation)
        : Optional.empty();
  }

  public boolean isPreviousRotationApplied(ControlRequest<Rotation> request) {
    Rotation previous = previousRotation(request).orElse(null);
    return previous != null && request.isWinning() && request.value().orElse(null) == previous;
  }

  /**
   * Yuri PRE placement uses the current input aim before its walking packet. This is a preparation
   * capability, not a submitted-rotation receipt. A prior ordinary walk owned by this same
   * activation must establish the current position/world/connection first.
   */
  public boolean isInputRotationPrepared(ControlRequest<Rotation> request) {
    runtime.tasks().checkThread();
    return inputActionPhase
        && beforeWalking
        && !rotationResend
        && available()
        && player == game.thePlayer
        && generation == runtime.tasks().generation()
        && previousWorld == game.theWorld
        && player.worldObj == previousWorld
        && previousConnection != null
        && previousConnection == connection()
        && previousConnection.isChannelOpen()
        && game.getRenderViewEntity() == player
        && player.posX == previousX
        && player.posY == previousY
        && player.posZ == previousZ
        && request != null
        && request == previousOwner
        && request.isWinning()
        && request.value().isPresent()
        && (request.value().get().mode() == Rotation.Mode.VIEW || supportsServerRotation());
  }

  public boolean withInputRotation(ControlRequest<Rotation> request, Runnable action) {
    runtime.tasks().checkThread();
    Objects.requireNonNull(action, "action");
    if (inRotationAction) throw new IllegalStateException("Nested rotation action");
    if (!isInputRotationPrepared(request)) return false;
    return withActionRotation(request.value().get(), action);
  }

  /** PRE actions must use the identical previously submitted aim, never a new unsent angle. */
  public boolean withPreviousRotation(ControlRequest<Rotation> request, Runnable action) {
    runtime.tasks().checkThread();
    Objects.requireNonNull(action, "action");
    if (inRotationAction) throw new IllegalStateException("Nested rotation action");
    if (!isPreviousRotationApplied(request)) return false;
    return withActionRotation(previousRotation, action);
  }

  /** Temporarily claim the preceding walking aim; restore only a still-owned, unchanged value. */
  public boolean withPreviousAim(ControlRequest<Rotation> request, Consumer<Rotation> action) {
    runtime.tasks().checkThread();
    Objects.requireNonNull(action, "action");
    Rotation submitted = previousRotation(request).orElse(null);
    if (submitted == null || !request.isWinning()) return false;
    Rotation next = request.value().orElse(null);
    long actionGeneration = runtime.tasks().generation();
    EntityPlayerSP actionPlayer = game.thePlayer;
    request.setForTicks(submitted, 1);
    try {
      if (!isPreviousRotationApplied(request)) return false;
      action.accept(submitted);
      return true;
    } finally {
      if (game.thePlayer == actionPlayer
          && runtime.tasks().generation() == actionGeneration
          && request.value().orElse(null) == submitted) {
        if (next == null) request.clear();
        else request.setForTicks(next, 1);
      }
    }
  }

  /**
   * A PRE walking action may follow this tick's physics, using the identical aim submitted by the
   * preceding walking pass. Position is deliberately allowed to change here; no new angle or owner
   * can act until its own walking packet has been submitted. This does not relax PRE input actions.
   */
  public boolean withSubmittedMotionAim(
      ControlRequest<Rotation> request, Consumer<Rotation> action) {
    runtime.tasks().checkThread();
    Objects.requireNonNull(action, "action");
    if (inRotationAction) throw new IllegalStateException("Nested rotation action");
    if (!motionActionPhase
        || rotationResend
        || !available()
        || player != game.thePlayer
        || generation != runtime.tasks().generation()
        || previousWorld != game.theWorld
        || player.worldObj != previousWorld
        || previousConnection == null
        || previousConnection != connection()
        || !previousConnection.isChannelOpen()
        || game.getRenderViewEntity() != player
        || request == null
        || request != previousOwner
        || previousRotation == null
        || !request.isWinning()
        || request.value().orElse(null) != previousRotation) return false;
    PlayerRotationAccess access = (PlayerRotationAccess) player;
    if (access.perihelion$reportedYaw() != previousRotation.yaw()
        || access.perihelion$reportedPitch() != previousRotation.pitch()) return false;
    Rotation submitted = previousRotation;
    return withActionRotation(submitted, () -> action.accept(submitted));
  }

  private net.minecraft.network.NetworkManager connection() {
    return game.getNetHandler() == null ? null : game.getNetHandler().getNetworkManager();
  }

  public boolean withPlacementRotation(
      ControlRequest<Rotation> request, boolean previous, Runnable action) {
    return previous ? withPreviousRotation(request, action) : withRotation(request, action);
  }

  public boolean withPlacementRotation(
      ControlRequest<Rotation> request, boolean previous, boolean preparedInput, Runnable action) {
    return preparedInput
        ? withInputRotation(request, action)
        : withPlacementRotation(request, previous, action);
  }

  public void walkingPacketCancelled(boolean positional) {
    runtime.tasks().checkThread();
    if (motionPacket != null) {
      walkingCancelled = true;
      // Vanilla still caches the attempted position. A ground-only packet cannot repair it.
      if (positional) plannedPositionValid = false;
    }
  }

  public void requestRotationResend() {
    runtime.tasks().checkThread();
    rotationResend = true;
  }

  /** A fence consumes an ordinary right click. Bracket that placement with actual sneak state. */
  public void withSneaking(Runnable action) {
    runtime.tasks().checkThread();
    if (!prepare()) return;
    EntityPlayerSP current = player;
    if (current.isSneaking()) {
      action.run();
      return;
    }
    boolean original = current.movementInput.sneak;
    long scope = runtime.tasks().generation();
    current.movementInput.sneak = true;
    try {
      current.sendQueue.addToSendQueue(
          new net.minecraft.network.play.client.C0BPacketEntityAction(
              current,
              net.minecraft.network.play.client.C0BPacketEntityAction.Action.START_SNEAKING));
      action.run();
    } finally {
      current.movementInput.sneak = original;
      if (available() && current == game.thePlayer && scope == runtime.tasks().generation())
        current.sendQueue.addToSendQueue(
            new net.minecraft.network.play.client.C0BPacketEntityAction(
                current,
                net.minecraft.network.play.client.C0BPacketEntityAction.Action.STOP_SNEAKING));
    }
  }

  public perihelion.integration.minecraft189.network.PacketOrderBarrier<
          perihelion.integration.minecraft189.network.PacketBufferScope>
      outboundOrder() {
    return outboundOrder;
  }

  /** Align local item ray traces/orientation for an action without changing the user's camera. */
  public boolean withRotation(ControlRequest<Rotation> request, Runnable action) {
    runtime.tasks().checkThread();
    Objects.requireNonNull(action, "action");
    if (inRotationAction) throw new IllegalStateException("Nested rotation action");
    if (!isRotationApplied(request)) return false;
    return withActionRotation(appliedRotation, action);
  }

  private boolean withActionRotation(Rotation rotation, Runnable action) {
    EntityPlayerSP acting = player;
    float yaw = acting.rotationYaw, pitch = acting.rotationPitch;
    float temporaryYaw = yaw + MathHelper.wrapAngleTo180_float(rotation.yaw() - yaw);
    acting.rotationYaw = temporaryYaw;
    acting.rotationPitch = rotation.pitch();
    inRotationAction = true;
    try {
      action.run();
      return true;
    } finally {
      inRotationAction = false;
      if (acting.rotationYaw == temporaryYaw) acting.rotationYaw = yaw;
      if (acting.rotationPitch == rotation.pitch()) acting.rotationPitch = pitch;
    }
  }

  private boolean hasModelRotation() {
    return available()
        && player == game.thePlayer
        && generation == runtime.tasks().generation()
        && game.currentScreen == null
        && modelOwner != null
        && modelOwner.isWinning()
        && modelRotation != null
        && modelRotation.mode() == Rotation.Mode.SERVER;
  }

  /** Local third-person model only; camera and authoritative entity fields are untouched. */
  public float modelYaw(float vanilla, boolean previous) {
    if (!hasModelRotation()) {
      if (!hasLocalModelListener()) return vanilla;
      Rotation fallback =
          Rotation.server(vanilla, previous ? player.prevRotationPitch : player.rotationPitch);
      return modelOrientation(fallback, previous).yaw();
    }
    Rotation display = modelOrientation(previous);
    return previous
        ? modelRotation.yaw() + MathHelper.wrapAngleTo180_float(display.yaw() - modelRotation.yaw())
        : display.yaw();
  }

  public float modelPitch(float vanilla, boolean previous) {
    if (!hasModelRotation()) {
      if (!hasLocalModelListener()) return vanilla;
      return modelOrientation(
              Rotation.server(
                  previous ? player.prevRenderYawOffset : player.renderYawOffset, vanilla),
              previous)
          .pitch();
    }
    return modelOrientation(previous).pitch();
  }

  private Rotation modelOrientation(boolean previous) {
    return modelOrientation(previous ? previousModelRotation : modelRotation, previous);
  }

  private boolean hasLocalModelListener() {
    return available()
        && player == game.thePlayer
        && generation == runtime.tasks().generation()
        && game.currentScreen == null
        && runtime
            .clientEvents()
            .hasListeners(perihelion.integration.minecraft189.event.ModelRotationEvent.class);
  }

  private Rotation modelOrientation(Rotation base, boolean previous) {
    perihelion.integration.minecraft189.event.ModelRotationEvent event =
        new perihelion.integration.minecraft189.event.ModelRotationEvent(base, previous);
    runtime.clientEvents().post(event);
    return event.rotation();
  }

  private void applyView(Rotation rotation) {
    if (rotation == null || rotation.mode() != Rotation.Mode.VIEW) return;
    player.rotationYaw += MathHelper.wrapAngleTo180_float(rotation.yaw() - player.rotationYaw);
    player.rotationPitch = rotation.pitch();
  }

  /** Commit a render-frame view claim now, preserving vanilla mouse interpolation. */
  public boolean applyViewRequest(ControlRequest<Rotation> request) {
    runtime.tasks().checkThread();
    if (!prepare()
        || game.thePlayer != player
        || game.getRenderViewEntity() != player
        || generation != runtime.tasks().generation()
        || request == null
        || controls.rotationRequest().orElse(null) != request
        || !request.isWinning()) return false;
    Rotation submitted = request.value().orElse(null);
    if (submitted == null
        || submitted.mode() != Rotation.Mode.VIEW
        || controls.rotation().orElse(null) != submitted) return false;
    float yaw = player.rotationYaw, pitch = player.rotationPitch;
    applyView(submitted);
    player.prevRotationYaw += player.rotationYaw - yaw;
    player.prevRotationPitch += player.rotationPitch - pitch;
    return true;
  }

  /** False when another transformer removed either walking rotation hook. */
  public boolean supportsServerRotation() {
    return MotionHookStatus.supportsServerRotation();
  }

  public float motionYaw(float vanilla) {
    return motionRotation == null ? vanilla : motionRotation.yaw();
  }

  public float motionPitch(float vanilla) {
    return motionRotation == null ? vanilla : motionRotation.pitch();
  }

  public double motionY(double vanilla) {
    return motionPacket == null ? vanilla : motionPacket.y();
  }

  public boolean motionGround(boolean vanilla) {
    return motionPacket == null ? vanilla : motionPacket.grounded();
  }

  private void restoreTimer() {
    Timer gameTimer = ((MinecraftTimerAccess) game).perihelion$timer();
    timer.apply(
        Optional.empty(), () -> gameTimer.timerSpeed, value -> gameTimer.timerSpeed = value);
  }

  private void releasePlayer() {
    outboundOrder.clear();
    outgoingFacts.reset();
    clientFactConnection = null;
    clientActions = 0;
    plannedPositionKnown = false;
    plannedPositionValid = true;
    perspective.apply(
        Optional.empty(),
        () -> game.gameSettings.thirdPersonView,
        value -> game.gameSettings.thirdPersonView = value);
    EntityPlayerSP previous = player;
    boolean synchronize = hadHotbar && previous != null && previous == game.thePlayer;
    player = null;
    hadHotbar = false;
    motionRotation = null;
    motionOwner = null;
    appliedOwner = null;
    appliedRotation = null;
    appliedConnection = null;
    previousOwner = null;
    previousRotation = null;
    previousWorld = null;
    previousConnection = null;
    motionConnection = null;
    motionActionPhase = false;
    attemptedServerSlot = -1;
    walkingAirTicks = 0;
    beforeWalking = false;
    modelOwner = null;
    modelRotation = previousModelRotation = null;
    motionPacket = null;
    if (previous != null) {
      slot.apply(
          Optional.empty(),
          () -> previous.inventory.currentItem,
          value -> previous.inventory.currentItem = value);
      sprint.apply(Optional.empty(), previous::isSprinting, previous::setSprinting);
      if (synchronize) syncHeldItem();
    }
  }

  @Override
  public void close() {
    runtime.tasks().checkThread();
    if (closed) return;
    closed = true;
    try {
      releasePlayer();
    } finally {
      restoreTimer();
    }
  }
}
