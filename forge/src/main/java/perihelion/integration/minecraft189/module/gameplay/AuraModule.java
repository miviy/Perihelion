package perihelion.integration.minecraft189.module.gameplay;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.event.StrafeEvent;

final class AuraModule extends GameplayModule {
  enum Target {
    PLAYERS,
    HOSTILES,
    TEAMMATES,
    INVISIBLES,
    ANIMALS
  }

  enum Mode {
    SINGLE,
    SWITCH
  }

  enum Sort {
    DISTANCE,
    HEALTH,
    HURT_TIME,
    FOV
  }

  enum AutoBlock {
    FAKE,
    VANILLA,
    NCP,
    HYPIXEL,
    LEGIT,
    PREDICTIVE,
    NONE
  }

  enum Rotations {
    NORMAL,
    ML,
    NONE
  }

  enum MoveFix {
    NONE,
    STRICT,
    SILENT
  }

  private final TargetingModules.AntiBot antiBot;
  private final TargetingModules.Friends friends;
  private final Setting<Set<Target>> targets;
  private final Setting<Mode> mode;
  private final Setting<Sort> sort;
  private final Setting<Double> fov;
  private final Setting<Boolean> requirePress, weaponsOnly, allowTools, allowMining;
  private final Setting<AutoBlock> block;
  private final Setting<Rotations> rotations;
  private final Setting<MoveFix> moveFix;
  private final Setting<Integer> switchDelay, hurtTicks, lead, hold, history;
  private final Setting<Double> seek,
      attack,
      swing,
      blockRange,
      minCps,
      maxCps,
      minRotation,
      maxRotation,
      window;
  private final Setting<Boolean> mouse,
      walls,
      rayCast,
      onlyHurt,
      keepSprint,
      hypixelSprint,
      autoDisable;
  private final AuraAim variations;
  private AuraAim.State aimState;
  private EntityLivingBase target;
  private Rotation aim;
  private ControlRequest<Rotation> rotation;
  private ControlRequest<Boolean> use;
  private boolean blocking;
  private AutoBlock blockingMode = AutoBlock.NONE;
  private double nextAttack;
  private int releasedTicks = -1;
  private final AuraTargetCycle targetCycle = new AuraTargetCycle();
  private boolean attackReady = true;
  private boolean hypixelArmed, hypixelBlockPending;
  private net.minecraft.item.ItemStack ownedUse;
  private final SwingPrediction prediction = new SwingPrediction();

  AuraModule(ClientRuntime r, TargetingModules.AntiBot antiBot, TargetingModules.Friends friends) {
    super(
        r,
        "aura",
        "Aura",
        "Automatic targeting, rotations, attacks and sword blocking.",
        ModuleCategory.COMBAT);
    this.antiBot = antiBot;
    this.friends = friends;
    targets =
        setting(
            Setting.multiSelect(
                "targets",
                "Targets",
                "Allowed entity categories.",
                EnumSet.of(Target.PLAYERS, Target.HOSTILES, Target.TEAMMATES, Target.INVISIBLES),
                Target.class,
                guard));
    sort = mode("sort", "Target Priority", Sort.DISTANCE, Sort.class);
    fov = number("fov", "FOV", 360, 10, 360, 5);
    requirePress = bool("require-press", "Require Attack Press", false);
    weaponsOnly = bool("weapons-only", "Weapons Only", false);
    allowTools = bool("allow-tools", "Allow Tools", false).visibleWhen(weaponsOnly::get);
    allowMining = bool("allow-mining", "Allow Mining", true);
    mode = mode("mode", "Mode", Mode.SINGLE, Mode.class);
    switchDelay =
        integer("switch-delay", "Switch Speed", 0, 0, 10)
            .visibleWhen(() -> mode.get() == Mode.SWITCH);
    seek = number("seek-range", "Seek Range", 6, 3, 6, 0.1);
    mouse = bool("simulate-mouse-clicks", "Simulate Mouse Clicks", true);
    attack = number("attack-range", "Attack Range", 3, 3, 6, 0.1).visibleWhen(() -> !mouse.get());
    swing = number("swing-range", "Swing Range", 6, 3, 6, 0.1);
    blockRange = number("block-range", "Block Range", 6, 3, 6, 0.1);
    minCps = number("min-cps", "Min CPS", 9, 1, 20, 0.1);
    maxCps = number("max-cps", "Max CPS", 13, 1, 20, 0.1);
    block = mode("auto-block", "Auto Block", AutoBlock.FAKE, AutoBlock.class);
    onlyHurt = bool("only-block-if-hurt", "Only Block If Hurt", false);
    hurtTicks =
        integer("block-on-hurt-ticks", "Block On Hurt Ticks", 4, 0, 10).visibleWhen(onlyHurt::get);
    lead =
        integer("predict-lead", "Predict Lead", 3, 0, 10)
            .visibleWhen(() -> block.get() == AutoBlock.PREDICTIVE);
    hold =
        integer("predict-hold", "Predict Hold", 3, 0, 10)
            .visibleWhen(() -> block.get() == AutoBlock.PREDICTIVE);
    history =
        integer("predict-history", "Predict History", 5, 2, 10)
            .visibleWhen(() -> block.get() == AutoBlock.PREDICTIVE);
    window =
        number("predict-window", "Predict Window", 1.5, 0, 4, 0.1)
            .visibleWhen(() -> block.get() == AutoBlock.PREDICTIVE);
    walls = bool("through-walls", "Through Walls", false);
    rayCast = bool("ray-cast", "Ray Cast", true);
    rotations = mode("rotations", "Rotations", Rotations.NORMAL, Rotations.class);
    minRotation = number("min-rotation-speed", "Min Rotation Speed", 3, 0.1, 10, 0.1);
    maxRotation = number("max-rotation-speed", "Max Rotation Speed", 7, 0.1, 10, 0.1);
    keepSprint = bool("keep-sprint", "Keep Sprint", false);
    hypixelSprint = bool("hypixel-keep-sprint", "Hypixel Keep Sprint", false);
    autoDisable = bool("auto-disable", "Auto Disable", true);
    variations = new AuraAim(this);
    moveFix = mode("move-fix", "Move Fix", MoveFix.SILENT, MoveFix.class);
  }

  EntityLivingBase target() {
    return target;
  }

  boolean blocking() {
    return blocking;
  }

  private java.util.function.BooleanSupplier itemRoutine = () -> false;
  private java.util.function.BooleanSupplier buildingRoutine = () -> false;
  private java.util.function.Supplier<ControlRequest<Rotation>> displacement = () -> null;

  void displacement(java.util.function.Supplier<ControlRequest<Rotation>> request) {
    displacement = java.util.Objects.requireNonNull(request, "request");
  }

  private ControlRequest<Rotation> actionRotation() {
    ControlRequest<Rotation> external = displacement.get();
    return external == null ? rotation : external;
  }

  void buildingRoutine(java.util.function.BooleanSupplier busy) {
    buildingRoutine = busy;
  }

  boolean combatBlockedByBuilding() {
    return active("scaffold")
        || active("blockin")
        || active("beddefender")
        || active("clutch") && mc().gameSettings.keyBindSneak.isKeyDown()
        || buildingRoutine.getAsBoolean();
  }

  /** Velocity's independent close-range target rules, using native friend and bot policy. */
  boolean velocityTarget(Entity entity) {
    if (entity == mc().thePlayer
        || !(entity instanceof EntityLivingBase)
        || entity instanceof net.minecraft.entity.item.EntityArmorStand
        || entity.isDead
        || ((EntityLivingBase) entity).getHealth() <= 0
        || mc().theWorld.getEntityByID(entity.getEntityId()) != entity) return false;
    if (entity instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) entity;
      if (friends.friend(player.getName()) || antiBot.bot(player) || sameTeam(player)) return false;
    }
    return mc().thePlayer.canEntityBeSeen(entity);
  }

  private static final java.util.regex.Pattern TEAM_COLOR =
      java.util.regex.Pattern.compile("\u00a7(.).*\u00a7r");

  private boolean sameTeam(EntityPlayer player) {
    String own = mc().thePlayer.getDisplayName().getFormattedText();
    java.util.regex.Matcher matcher = TEAM_COLOR.matcher(own);
    String color = "\u00a7" + (matcher.find() ? matcher.group(1) : "f");
    return own.contains(color) && player.getDisplayName().getFormattedText().contains(color);
  }

  private java.util.concurrent.atomic.AtomicBoolean blockSinceWalk;

  void itemRoutine(java.util.function.BooleanSupplier busy) {
    itemRoutine = busy;
  }

  double seekRange() {
    return seek.get();
  }

  boolean releaseForItemRoutine() {
    boolean released =
        ownedUse != null
            || blocking && (blockingMode == AutoBlock.VANILLA || blockingMode == AutoBlock.NCP);
    unblock();
    return released;
  }

  boolean canAttack() {
    return attackEligible() && now() >= nextAttack;
  }

  boolean itemRoutineBusy() {
    return itemRoutine.getAsBoolean();
  }

  boolean attackEligible() {
    return target != null && attackReady && !itemRoutineBusy();
  }

  /** Velocity's PRE attacks must use an aim already sent to the server. */
  boolean reduceKnockback(boolean respectDelay) {
    if (!active("aura")
        || paused()
        || !attackEligible()
        || respectDelay && !canAttack()
        || blocking
        || mc().thePlayer.isUsingItem()
        || target.isDead
        || mc().theWorld.getEntityByID(target.getEntityId()) != target
        || !eligible(target, 3, targets.get(), walls.get())
        || mc().thePlayer.getDistanceToEntity(target) > 3
        || angle(target) > fov.get() / 2
        || blockSinceWalk.get()) return false;
    if (rotations.get() == Rotations.NONE && displacement.get() == null) {
      if (rayCast.get() && ray(3).entityHit != target) return false;
      mc().thePlayer.swingItem();
      mc().playerController.attackEntity(mc().thePlayer, target);
      return true;
    }
    perihelion.integration.minecraft189.control.MinecraftControls controls =
        perihelion.integration.minecraft189.HookBridge.gameControls();
    ControlRequest<Rotation> acting = actionRotation();
    Rotation previous = controls.previousRotation(acting).orElse(null);
    if (previous == null || !acting.isWinning()) return false;
    Rotation next = acting.value().orElse(null), desired = aim;
    long generation = runtime.tasks().generation();
    boolean[] attacked = {false};
    try {
      acting.setForTicks(previous, 1);
      aim = previous;
      controls.withPreviousRotation(
          acting,
          () -> {
            // The previous aim can now miss a moved/switched target, even with Ray Cast disabled.
            if (ray(3).entityHit != target) return;
            mc().thePlayer.swingItem();
            mc().playerController.attackEntity(mc().thePlayer, target);
            attacked[0] = true;
          });
    } finally {
      if (active("aura")
          && actionRotation() == acting
          && runtime.tasks().generation() == generation) {
        aim = desired;
        if (acting.value().orElse(null) == previous) {
          if (next == null) acting.clear();
          else acting.setForTicks(next, 1);
        }
      }
    }
    return attacked[0];
  }

  // Own release packets must not create an extra blocked attack tick after Hypixel unblocks.
  private final ThreadLocal<C07PacketPlayerDigging> hypixelRelease = new ThreadLocal<>();

  private double attackDelay() {
    if (block.get() == AutoBlock.LEGIT) return 200;
    if (block.get() == AutoBlock.HYPIXEL) return 125;
    return configuredAttackDelay();
  }

  private double configuredAttackDelay() {
    double low = Math.min(minCps.get(), maxCps.get()), high = Math.max(minCps.get(), maxCps.get());
    return (long) (1000 / Math.max(1, low + Math.random() * (high - low)));
  }

  @Override
  protected void onEnable(ModuleContext c) {
    if (stopAfterWorldChange(c, autoDisable.get())) return;
    aimState = variations.activate(c);
    c.listen(
        perihelion.integration.minecraft189.event.HitSlowdownEvent.class,
        e -> {
          if (!keepSprint.get()) return;
          if (!hypixelSprint.get()) {
            e.sprint(true);
            e.factor(1);
          } else if (!mc().thePlayer.isCollidedHorizontally
              && mc().thePlayer.isSprinting()
              && mc().thePlayer.moveForward > 0
              && mc().thePlayer.hurtTime <= 4) e.sprint(true);
        });
    target = null;
    aim = null;
    blocking = false;
    nextAttack = now() + configuredAttackDelay();
    targetCycle.reset();
    prediction.reset();
    attackReady = true;
    releasedTicks = -1;
    hypixelArmed = hypixelBlockPending = false;
    ownedUse = null;
    blockSinceWalk = new java.util.concurrent.atomic.AtomicBoolean();
    java.util.concurrent.atomic.AtomicBoolean packetWindow = blockSinceWalk;
    c.listen(
        perihelion.integration.minecraft189.event.ClientSendFactEvent.class,
        -900,
        false,
        event -> {
          if (event.action()
              == perihelion.integration.minecraft189.event.ClientSendFactEvent.Action.WALK)
            packetWindow.set(false);
          else if (event.action()
                  == perihelion.integration.minecraft189.event.ClientSendFactEvent.Action.PLACE
              || event.action()
                  == perihelion.integration.minecraft189.event.ClientSendFactEvent.Action.USE_ITEM
              || event.action()
                  == perihelion.integration.minecraft189.event.ClientSendFactEvent.Action.DIG
              || event.action()
                      == perihelion.integration.minecraft189.event.ClientSendFactEvent.Action
                          .RELEASE_USE_ITEM
                  && hypixelRelease.get() == null) packetWindow.set(true);
        });
    rotation = c.controls().rotation(50);
    use = c.controls().key(InputKey.USE_ITEM, 50);
    Object world = mc().theWorld;
    c.onCleanup(
        () -> {
          if (mc().theWorld == world && mc().thePlayer != null && mc().getNetHandler() != null)
            unblock();
          target = null;
          aim = null;
          aimState = null;
          blocking = false;
          prediction.reset();
        });
    c.observe(
        block,
        (s, before, after) -> {
          unblock();
          prediction.reset();
          nextAttack = now() + attackDelay();
        });
    c.observe(
        mode,
        (s, before, after) -> {
          clearCombat();
          targetCycle.reset();
        });
    c.listen(
        perihelion.integration.minecraft189.event.SwordPoseEvent.class,
        e -> {
          if (blocking && target != null) e.cancel();
        });
    c.listen(
        PlayerControlEvent.class,
        e -> {
          if (e == PlayerControlEvent.BEFORE_INPUT) {
            prepare();
          }
          if (e == PlayerControlEvent.BEFORE_ACTIONS) performBeforeInput();
        });
    c.listen(
        perihelion.integration.minecraft189.event.MovementInputEvent.class,
        e -> {
          if (moveFix.get() != MoveFix.SILENT
              || target == null
              || aim == null
              || displacement.get() != null
              || rotations.get() == Rotations.NONE
              || !rotation.isWinning()) return;
          float[] input =
              perihelion.integration.minecraft189.module.YuriMovementCorrection.align(
                  e.strafe(), e.forward(), mc().thePlayer.rotationYaw, aim.yaw());
          e.input(input[0], input[1]);
        });
    c.listen(
        StrafeEvent.class,
        20,
        false,
        e -> {
          if (target == null
              || aim == null
              || displacement.get() != null
              || rotations.get() == Rotations.NONE
              || moveFix.get() == MoveFix.NONE
              || !rotation.isWinning()) return;
          e.yaw(aim.yaw());
        });
    c.listen(
        perihelion.integration.minecraft189.event.MovementYawEvent.class,
        e -> {
          if (moveFix.get() != MoveFix.NONE
              && displacement.get() == null
              && target != null
              && aim != null
              && rotations.get() != Rotations.NONE
              && rotation.isWinning()) e.yaw(aim.yaw());
        });
  }

  private double angle(EntityLivingBase entity) {
    return angle(entity, mc().thePlayer.rotationYaw);
  }

  private double angle(EntityLivingBase entity, float viewYaw) {
    return Math.abs(
        MathHelper.wrapAngleTo180_float(
            GameActions.lookAt(mc().thePlayer, GameActions.nearest(mc().thePlayer, entity)).yaw()
                - viewYaw));
  }

  private double priorityAngle(EntityLivingBase entity) {
    double targetYaw =
        Math.toDegrees(
                Math.atan2(entity.posZ - mc().thePlayer.posZ, entity.posX - mc().thePlayer.posX))
            - 90;
    double delta = Math.abs(mc().thePlayer.rotationYaw - targetYaw) % 360;
    return delta > 180 ? 360 - delta : delta;
  }

  private boolean inputEligible() {
    if (requirePress.get()
        && !perihelion.integration.minecraft189.HookBridge.gameControls()
            .physicalKeyDown(InputKey.ATTACK)) return false;
    net.minecraft.item.ItemStack held = mc().thePlayer.getHeldItem();
    if (weaponsOnly.get()
        && (held == null
            || !(held.getItem() instanceof ItemSword)
                && !(allowTools.get() && held.getItem() instanceof net.minecraft.item.ItemTool)))
      return false;
    return allowMining.get()
        || mc().objectMouseOver == null
        || mc().objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
        || !perihelion.integration.minecraft189.HookBridge.gameControls()
            .physicalKeyDown(InputKey.ATTACK);
  }

  private boolean paused() {
    net.minecraft.item.ItemStack held = mc().thePlayer.getHeldItem();
    return !inputEligible()
        || active("freecam")
        || combatBlockedByBuilding()
        || mc().currentScreen != null
        || mc().thePlayer.isDead
        || mc().thePlayer.getHealth() <= 0
        || mc().thePlayer.isUsingItem() && (held == null || !(held.getItem() instanceof ItemSword));
  }

  private void clearCombat() {
    target = null;
    aim = null;
    prediction.reset();
    targetCycle.reset();
    rotation.clear();
    unblock();
  }

  private void prepare() {
    if (paused()) {
      clearCombat();
      return;
    }
    List<EntityLivingBase> candidates = candidates(seek.get());
    candidates.removeIf(entity -> angle(entity) > fov.get() / 2);
    java.util.Comparator<EntityLivingBase> ordering = null;
    switch (sort.get()) {
      case DISTANCE:
        ordering = java.util.Comparator.comparingDouble(mc().thePlayer::getDistanceSqToEntity);
        break;
      case HEALTH:
        ordering = java.util.Comparator.comparingDouble(EntityLivingBase::getHealth);
        break;
      case HURT_TIME:
        ordering = java.util.Comparator.comparingInt(entity -> entity.hurtTime);
        break;
      case FOV:
        ordering = java.util.Comparator.comparingDouble(this::priorityAngle);
        break;
      default:
        break;
    }
    EntityLivingBase previous = target;
    if (candidates.isEmpty()) {
      target = null;
      targetCycle.reset();
    } else if (mode.get() == Mode.SWITCH) {
      candidates.sort(java.util.Comparator.comparingInt(Entity::getEntityId));
      List<Integer> ids = new ArrayList<>();
      for (EntityLivingBase candidate : candidates) ids.add(candidate.getEntityId());
      int selected =
          targetCycle.select(
              ids, previous == null ? -1 : previous.getEntityId(), now(), switchDelay.get());
      target = candidates.get(selected);
    } else {
      // List.sort is stable, matching Yuri's original loaded-entity order for equal priorities.
      candidates.sort(ordering);
      target = candidates.get(0);
    }
    if (target != previous) prediction.reset();
    attackReady = true;
    if (target == null) {
      rotation.clear();
      aim = null;
      unblock();
      return;
    }
    if (itemRoutine.getAsBoolean()) {
      rotation.clear();
      unblock();
      attackReady = false;
      return;
    }
    ControlRequest<Rotation> external = displacement.get();
    if (external != null) {
      rotation.clear();
      aim = external.value().orElse(null);
    } else if (rotations.get() == Rotations.NONE) {
      rotation.clear();
      aim = Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch);
    } else {
      Rotation desired = aimState.aim(mc(), target, rotations.get() == Rotations.ML, attack.get());
      float yaw = aim == null ? mc().thePlayer.rotationYaw : aim.yaw(),
          pitch = aim == null ? mc().thePlayer.rotationPitch : aim.pitch();
      double low = Math.min(minRotation.get(), maxRotation.get()),
          high = Math.max(minRotation.get(), maxRotation.get());
      aim =
          RotationDynamics.smooth(
              mc(),
              Rotation.serverUnwrapped(yaw, pitch),
              desired,
              (float) (low + Math.random() * (high - low)));
      rotation.setForTicks(aim, 1);
    }
    boolean sword =
        mc().thePlayer.getHeldItem() != null
            && mc().thePlayer.getHeldItem().getItem() instanceof ItemSword;
    if (!sword
        || mc().thePlayer.getDistanceToEntity(target) > blockRange.get()
        || onlyHurt.get() && mc().thePlayer.hurtTime < hurtTicks.get()) {
      unblock();
      return;
    }
    blockingMode = block.get();
    switch (block.get()) {
      case FAKE:
        blocking = true;
        break;
      case VANILLA:
        GameActions.send(new C08PacketPlayerBlockPlacement(mc().thePlayer.getHeldItem()));
        blocking = true;
        break;
      case LEGIT:
        boolean legitHolding =
            mc().thePlayer.hurtTime >= 6
                && mc().thePlayer.hurtTime <= 10
                && mc().thePlayer.getDistanceToEntity(target) <= 3;
        use.setForTicks(legitHolding, 1);
        releasedTicks = legitHolding || mc().thePlayer.isUsingItem() ? 0 : releasedTicks + 1;
        attackReady = !legitHolding && !mc().thePlayer.isUsingItem() && releasedTicks >= 1;
        blocking = true;
        break;
      case HYPIXEL:
        releaseHypixel();
        use.setForTicks(false, 1);
        hypixelBlockPending = false;
        if (mc().thePlayer.getDistanceToEntity(target) <= 3) {
          if (nextAttack - now() <= 50) hypixelArmed = true;
          else if (hypixelArmed) {
            hypixelBlockPending = true;
            hypixelArmed = false;
          }
        } else hypixelArmed = false;
        attackReady = true;
        blocking = true;
        break;
      case PREDICTIVE:
        // Sampling must not short-circuit on attack-ready ticks: a wrap can happen on one.
        boolean incoming =
            prediction.update(
                target.swingProgress, history.get(), lead.get(), hold.get(), window.get());
        boolean holding = now() < nextAttack && incoming;
        use.setForTicks(holding, 1);
        if (holding || mc().thePlayer.isUsingItem()) releasedTicks = 0;
        else releasedTicks++;
        attackReady =
            !holding && !mc().thePlayer.isUsingItem() && (!incoming || releasedTicks >= 1);
        blocking = true;
        break;
      case NONE:
        unblock();
        break;
      default:
        break;
    }
  }

  List<EntityLivingBase> candidates(double range) {
    return candidates(range, targets.get(), walls.get());
  }

  List<EntityLivingBase> candidates(double range, Set<Target> allowed, boolean throughWalls) {
    List<EntityLivingBase> result = new ArrayList<>();
    for (Entity entity : mc().theWorld.loadedEntityList)
      if (eligible(entity, range, allowed, throughWalls)) result.add((EntityLivingBase) entity);
    return result;
  }

  private boolean eligible(Entity entity, double range, Set<Target> allowed, boolean throughWalls) {
    if (entity == mc().thePlayer
        || !(entity instanceof EntityLivingBase)
        || entity instanceof net.minecraft.entity.item.EntityArmorStand
        || entity.isDead
        || ((EntityLivingBase) entity).getHealth() <= 0
        || mc().thePlayer.getDistanceToEntity(entity) > range
        || entity.isInvisible() && !allowed.contains(Target.INVISIBLES)) return false;
    boolean valid =
        entity instanceof net.minecraft.entity.monster.IMob && allowed.contains(Target.HOSTILES)
            || entity instanceof net.minecraft.entity.passive.IAnimals
                && !(entity instanceof net.minecraft.entity.monster.IMob)
                && allowed.contains(Target.ANIMALS);
    if (entity instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) entity;
      valid =
          allowed.contains(sameTeam(player) ? Target.TEAMMATES : Target.PLAYERS)
              && !friends.friend(player.getName())
              && !antiBot.bot(player);
    }
    return valid && (throughWalls || mc().thePlayer.canEntityBeSeen(entity));
  }

  /** Swing/attack and their hit slowdown precede movement, using an already submitted aim. */
  private void performBeforeInput() {
    if (target == null || rotations.get() == Rotations.NONE && displacement.get() == null) {
      perform();
      return;
    }
    perihelion.integration.minecraft189.control.MinecraftControls controls =
        perihelion.integration.minecraft189.HookBridge.gameControls();
    ControlRequest<Rotation> acting = actionRotation();
    Rotation previous = controls.previousRotation(acting).orElse(null);
    if (previous == null || !acting.isWinning()) return;
    Rotation next = acting.value().orElse(null), desired = aim;
    EntityLivingBase preparedTarget = target;
    float viewYaw = mc().thePlayer.rotationYaw;
    long generation = runtime.tasks().generation();
    try {
      acting.setForTicks(previous, 1);
      aim = previous;
      controls.withPreviousRotation(acting, () -> perform(viewYaw));
    } finally {
      if (active("aura")
          && actionRotation() == acting
          && target == preparedTarget
          && runtime.tasks().generation() == generation) {
        aim = desired;
        if (acting.value().orElse(null) == previous) {
          if (next == null) acting.clear();
          else acting.setForTicks(next, 1);
        }
      }
    }
  }

  private void perform() {
    perform(mc().thePlayer.rotationYaw);
  }

  private void perform(float viewYaw) {
    // Revalidate even when a callback changes input, the entity or the world during preparation.
    if (paused()
        || target != null
            && (mc().theWorld.getEntityByID(target.getEntityId()) != target
                || !eligible(target, seek.get(), targets.get(), walls.get())
                || angle(target, viewYaw) > fov.get() / 2)) {
      clearCombat();
      return;
    }
    if (itemRoutine.getAsBoolean()) {
      unblock();
      attackReady = false;
      return;
    }
    if ((rotations.get() != Rotations.NONE || displacement.get() != null)
        && target != null
        && !perihelion.integration.minecraft189.HookBridge.gameControls()
            .isPreviousRotationApplied(actionRotation())) {
      unblock();
      return;
    }
    if (target == null) return;
    if (block.get() == AutoBlock.HYPIXEL
        && ownedUse != null
        && !mc().thePlayer.isUsingItem()
        && mc().thePlayer.getHeldItem() == ownedUse
        && use.isWinning()) mc().thePlayer.setItemInUse(ownedUse, ownedUse.getMaxItemUseDuration());
    if (attackReady
        && now() >= nextAttack
        && (block.get() != AutoBlock.HYPIXEL || !blockSinceWalk.get())) {
      // Yuri resets its timer when a tick becomes due; late ticks do not retain old phase.
      nextAttack = now() + attackDelay();
      if (block.get() == AutoBlock.NCP) unblock();
      if (!mc().thePlayer.isUsingItem()) {
        double distance = mc().thePlayer.getDistanceToEntity(target);
        if (!mouse.get() && distance <= attack.get()) {
          if (!rayCast.get() || ray(blockRange.get()).entityHit == target) {
            mc().thePlayer.swingItem();
            mc().playerController.attackEntity(mc().thePlayer, target);
          }
        } else if (distance <= swing.get()) {
          MovingObjectPosition previous = mc().objectMouseOver;
          try {
            mc().objectMouseOver = ray(mc().playerController.extendedReach() ? 6 : 3);
            ((GameplayAccess) mc()).perihelion$clickMouse();
          } finally {
            mc().objectMouseOver = previous;
          }
        }
      }
    }
    if (block.get() == AutoBlock.NCP
        && !blocking
        && (!onlyHurt.get() || mc().thePlayer.hurtTime >= hurtTicks.get())
        && mc().thePlayer.getHeldItem() != null
        && mc().thePlayer.getHeldItem().getItem() instanceof ItemSword
        && mc().thePlayer.getDistanceToEntity(target) <= blockRange.get()) {
      GameActions.send(new C08PacketPlayerBlockPlacement(mc().thePlayer.getHeldItem()));
      blocking = true;
      blockingMode = AutoBlock.NCP;
    }
    if (block.get() == AutoBlock.HYPIXEL
        && hypixelBlockPending
        && mc().thePlayer.getDistanceToEntity(target) <= 3
        && use.isWinning()
        && runtime
            .controls()
            .hotbar()
            .map(value -> value.slot() == mc().thePlayer.inventory.currentItem)
            .orElse(true)
        && mc().thePlayer.getDistanceToEntity(target) <= blockRange.get()
        && (!onlyHurt.get() || mc().thePlayer.hurtTime >= hurtTicks.get())
        && mc().thePlayer.getHeldItem() != null
        && mc().thePlayer.getHeldItem().getItem() instanceof ItemSword
        && !mc().thePlayer.isUsingItem()) {
      hypixelBlockPending = false;
      ownedUse = mc().thePlayer.getHeldItem();
      GameActions.send(new C08PacketPlayerBlockPlacement(ownedUse));
      mc().thePlayer.setItemInUse(ownedUse, ownedUse.getMaxItemUseDuration());
    }
  }

  private MovingObjectPosition ray(double range) {
    Vec3 eyes = mc().thePlayer.getPositionEyes(1);
    Rotation direction =
        aim == null
            ? Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch)
            : aim;
    double yaw = Math.toRadians(direction.yaw()), pitch = Math.toRadians(direction.pitch());
    Vec3 end =
        eyes.addVector(
            -Math.sin(yaw) * Math.cos(pitch) * range,
            -Math.sin(pitch) * range,
            Math.cos(yaw) * Math.cos(pitch) * range);
    MovingObjectPosition hit = mc().theWorld.rayTraceBlocks(eyes, end, false, true, false);
    double closest = hit == null || walls.get() ? range : eyes.distanceTo(hit.hitVec);
    Entity selected = null;
    Vec3 selectedPoint = end;
    for (Entity entity :
        mc().theWorld
            .getEntitiesWithinAABBExcludingEntity(
                mc().thePlayer,
                mc().thePlayer
                    .getEntityBoundingBox()
                    .addCoord(
                        end.xCoord - eyes.xCoord,
                        end.yCoord - eyes.yCoord,
                        end.zCoord - eyes.zCoord)
                    .expand(1, 1, 1))) {
      if (!entity.canBeCollidedWith()) continue;
      AxisAlignedBB box =
          entity
              .getEntityBoundingBox()
              .expand(
                  entity.getCollisionBorderSize(),
                  entity.getCollisionBorderSize(),
                  entity.getCollisionBorderSize());
      MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
      double d =
          box.isVecInside(eyes)
              ? 0
              : intercept == null ? Double.POSITIVE_INFINITY : eyes.distanceTo(intercept.hitVec);
      if (d < closest) {
        closest = d;
        selected = entity;
        selectedPoint = intercept == null ? eyes : intercept.hitVec;
      }
    }
    if (selected != null) return new MovingObjectPosition(selected, selectedPoint);
    return hit == null
        ? new MovingObjectPosition(
            MovingObjectPosition.MovingObjectType.MISS, end, EnumFacing.UP, new BlockPos(end))
        : hit;
  }

  private void unblock() {
    releaseHypixel();
    hypixelArmed = hypixelBlockPending = false;
    if (use != null) use.clear();
    if (blocking && (blockingMode == AutoBlock.VANILLA || blockingMode == AutoBlock.NCP))
      GameActions.send(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    blocking = false;
    blockingMode = AutoBlock.NONE;
    releasedTicks = -1;
    attackReady = true;
  }

  private void releaseHypixel() {
    if (ownedUse == null) return;
    C07PacketPlayerDigging release =
        new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN);
    C07PacketPlayerDigging enclosing = hypixelRelease.get();
    hypixelRelease.set(release);
    try {
      GameActions.send(release);
    } finally {
      if (enclosing == null) hypixelRelease.remove();
      else hypixelRelease.set(enclosing);
    }
    if (mc().thePlayer.getItemInUse() == ownedUse) mc().thePlayer.stopUsingItem();
    ownedUse = null;
  }
}
