package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

final class ScaffoldModule extends GameplayModule {
  enum Mode {
    NORMAL,
    TELLY,
    HYPIXEL,
    BREEZILY,
    GOD_BRIDGE
  }

  enum Rotations {
    NORMAL,
    RANDOMIZED,
    OLD
  }

  enum Sprint {
    NONE,
    VANILLA,
    UNIVERSAL,
    NCP,
    LEGIT
  }

  enum Tower {
    NONE,
    VANILLA,
    POLAR,
    HYPIXEL,
    NCP
  }

  enum Ray {
    NORMAL,
    NONE,
    STRICT
  }

  enum Swap {
    CLIENT,
    SERVER
  }

  enum StackChoice {
    FIRST,
    CURRENT,
    LARGEST
  }

  private final Setting<Boolean> requireUse, holdingBlocks, pitchCondition;
  private final Setting<Double> minimumPitch, maximumPitch;
  private final Setting<StackChoice> stackChoice;
  private final Setting<Boolean> keepYPress, jumpPotion, eagle, counter, highlight;
  private final Setting<Double> edge;
  private final Setting<Mode> mode;
  private final Setting<Rotations> rotations;
  private final Setting<ScaffoldRotations.Search> search;
  private final Setting<Sprint> sprint;
  private final Setting<Tower> tower;
  private final Setting<Ray> ray;
  private final Setting<Swap> swap;
  private final Setting<Boolean> hypixelTelly,
      hypixelLarp,
      towerMove,
      moveFix,
      autoJump,
      keepY,
      sneak,
      safe,
      autoDisable;
  private final Setting<Integer> straight, diagonal, jumpDown, sneakEvery, delay;
  private final Setting<Double> expand, minRotation, maxRotation, randomMin, randomMax;

  ScaffoldModule(ClientRuntime r) {
    super(
        r,
        "scaffold",
        "Scaffold",
        "Place hotbar blocks beneath movement with bridging and tower modes.",
        ModuleCategory.WORLD);
    requireUse = bool("require-use", "Only While Right Clicking", false);
    holdingBlocks = bool("holding-blocks", "Only While Holding Blocks", false);
    pitchCondition = bool("pitch-condition", "Limit View Pitch", false);
    minimumPitch =
        number("minimum-pitch", "Minimum View Pitch", 35, -90, 90, 1)
            .visibleWhen(pitchCondition::get);
    maximumPitch =
        number("maximum-pitch", "Maximum View Pitch", 90, -90, 90, 1)
            .visibleWhen(pitchCondition::get);
    stackChoice = mode("block-selection", "Block Selection", StackChoice.FIRST, StackChoice.class);
    keepYPress = bool("keep-y-on-press", "Keep Y While Use Held", false);
    jumpPotion = bool("no-keep-y-jump-boost", "No Keep Y With Jump Boost", false);
    eagle = bool("edge-sneak", "Edge Sneak", false);
    edge = number("edge-distance", "Edge Distance", .13, 0, .5, .01).visibleWhen(eagle::get);
    counter = bool("block-counter", "Block Counter", false);
    highlight = bool("placement-highlight", "Placement Highlight", false);
    search =
        mode(
            "search-algorithm",
            "Search Algorithm",
            ScaffoldRotations.Search.NORMAL,
            ScaffoldRotations.Search.class);
    autoDisable = bool("auto-disable", "Auto Disable", false);
    mode = mode("mode", "Mode", Mode.NORMAL, Mode.class);
    rotations = mode("rotations", "Rotations", Rotations.NORMAL, Rotations.class);
    randomMin =
        number("randomized-speed-min", "Randomized Speed Min", 3, 0, 10, 0.5)
            .visibleWhen(() -> rotations.get() == Rotations.RANDOMIZED);
    randomMax =
        number("randomized-speed-max", "Randomized Speed Max", 7, 0, 10, 0.5)
            .visibleWhen(() -> rotations.get() == Rotations.RANDOMIZED);
    sprint = mode("sprint", "Sprint", Sprint.NONE, Sprint.class);
    tower = mode("tower", "Tower", Tower.NONE, Tower.class);
    ray = mode("ray-cast", "Ray Cast", Ray.NORMAL, Ray.class);
    swap = mode("swap-mode", "Swap Mode", Swap.CLIENT, Swap.class);
    hypixelTelly =
        bool("hypixel-telly", "Hypixel Telly", false).visibleWhen(() -> mode.get() == Mode.TELLY);
    hypixelLarp =
        bool("hypixel-larp", "Hypixel Larp Mode", true)
            .visibleWhen(() -> mode.get() == Mode.HYPIXEL);
    towerMove = bool("tower-move", "Tower Move", true).visibleWhen(() -> tower.get() != Tower.NONE);
    moveFix = bool("move-fix", "Move Fix", true);
    autoJump = bool("auto-jump", "Auto Jump", false);
    keepY = bool("keep-y", "Keep Y", false);
    sneak = bool("sneak", "Sneak", false);
    safe = bool("safe-walk", "Safe Walk", false);
    straight =
        integer("telly-straight", "Telly Straight Ticks", 6, 0, 8)
            .visibleWhen(() -> mode.get() == Mode.TELLY && !hypixelTelly.get());
    diagonal =
        integer("telly-diagonal", "Telly Diagonal Ticks", 4, 0, 8)
            .visibleWhen(() -> mode.get() == Mode.TELLY && !hypixelTelly.get());
    jumpDown =
        integer("telly-jump-down", "Telly Jump Down Ticks", 1, 0, 8)
            .visibleWhen(() -> mode.get() == Mode.TELLY && !hypixelTelly.get());
    sneakEvery = integer("sneak-every", "Sneak Every", 1, 0, 10).visibleWhen(sneak::get);
    delay = integer("place-delay", "Place Delay", 0, 0, 10);
    expand = number("expand", "Expand", 0, 0, 4, 1);
    minRotation = number("min-rotation", "Min Rotation Speed", 3, 0, 10, .5);
    maxRotation = number("max-rotation", "Max Rotation Speed", 7, 0, 10, .5);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    ControlRequest<HotbarSelection> slot = c.controls().hotbar(100);
    ControlRequest<Rotation> rotation = c.controls().rotation(100);
    ControlRequest<Boolean> sprinting = c.controls().sprinting(100),
        sneaking = c.controls().key(InputKey.SNEAK, 100),
        attack = c.controls().key(InputKey.ATTACK, 100);
    ControlRequest<Boolean> left = c.controls().key(InputKey.LEFT, 100),
        right = c.controls().key(InputKey.RIGHT, 100),
        jumpKey = c.controls().key(InputKey.JUMP, 100),
        sprintKey = c.controls().key(InputKey.SPRINT, 100);
    TellyTiming telly = new TellyTiming(now());
    boolean[] tellyPlace = {true};
    boolean[] tellyReturning = {false};
    c.observe(
        mode,
        (setting, before, after) -> {
          telly.reset(now());
          jumpKey.clear();
          sprintKey.clear();
          tellyReturning[0] = false;
        });
    Placement[] plan = {null};
    float reportedYaw =
        ((perihelion.integration.minecraft189.control.PlayerRotationAccess) mc().thePlayer)
            .perihelion$reportedYaw();
    Rotation[] aim = {
      Rotation.serverUnwrapped(
          reportedYaw + MathHelper.wrapAngleTo180_float(mc().thePlayer.rotationYaw - reportedYaw),
          mc().thePlayer.rotationPitch)
    };
    Rotation[] goal = {Rotation.server(mc().thePlayer.rotationYaw - 180, 90)},
        previousGoal = {goal[0]};
    boolean[] placementReady = {false};
    int[] placeTimer = {0};
    c.listen(
        ModelRotationEvent.class,
        e -> {
          if (mode.get() == Mode.HYPIXEL
              && hypixelLarp.get()
              && !active("derp")
              && rotation.isWinning())
            e.angles(
                (e.previous() ? mc().thePlayer.prevRotationYaw : mc().thePlayer.rotationYaw) + 125,
                (e.previous() ? previousGoal[0] : goal[0]).pitch());
        });
    int[] plane = {MathHelper.floor_double(mc().thePlayer.posY) - 1},
        air = {0},
        placed = {0},
        directionTicks = {0};
    long[] last = {0};

    // Yuri PRE prepares the current aim before input, jumping and movement.
    // Committing PRE placement uses a distinct native input-phase capability.
    Runnable preparePlacement =
        () -> {
          placementReady[0] = false;
          boolean moving = GameActions.moving(mc().thePlayer),
              jumping = mc().gameSettings.keyBindJump.isKeyDown();
          boolean sameY =
              (keepY.get() || active("speed"))
                  && moving
                  && !jumping
                  && Math.abs(mc().thePlayer.posY - plane[0] - 1) <= 3
                  && (!keepYPress.get()
                      || perihelion.integration.minecraft189.HookBridge.gameControls()
                          .physicalKeyDown(InputKey.USE_ITEM))
                  && (!jumpPotion.get()
                      || !mc().thePlayer.isPotionActive(net.minecraft.potion.Potion.jump));
          BlockPos expansion = expandedOffset(expansionDirection());
          plan[0] = ScaffoldPlacement.nearby(mc(), expansion, sameY ? plane[0] : null, aim[0]);
          boolean canPlace = clearBelow(expansion, 1);
          if (sameY)
            canPlace &=
                clearBelow(expansion, 2) && clearBelow(expansion, 3) && clearBelow(expansion, 4);
          if (mode.get() == Mode.TELLY) {
            canPlace &= tellyPlace[0];
          }

          placementReady[0] = plan[0] != null && canPlace;
        };
    Runnable updateAim =
        () -> {
          if (plan[0] == null) return;
          boolean moving = GameActions.moving(mc().thePlayer),
              jumping = mc().gameSettings.keyBindJump.isKeyDown();
          boolean canPlace = placementReady[0];
          Rotation target = goal[0];
          if (mode.get() == Mode.HYPIXEL
              && canPlace
              && !mc().gameSettings.keyBindPickBlock.isKeyDown())
            target =
                ScaffoldPlacement.stableAim(
                    mc(),
                    plan[0],
                    ScaffoldRotations.watchdog(
                        mc(), plan[0], goal[0], rotations.get() == Rotations.OLD));
          else if (mode.get() != Mode.BREEZILY
              && canPlace
              && !mc().gameSettings.keyBindPickBlock.isKeyDown()
              && !plan[0].ray(mc(), aim[0], 4.5, true))
            target =
                rotations.get() == Rotations.OLD
                    ? GameActions.lookAt(mc().thePlayer, plan[0].hit)
                    : ScaffoldRotations.find(mc(), plan[0], goal[0], search.get(), true);
          if (mode.get() == Mode.BREEZILY && canPlace) {
            target =
                Rotation.server(
                    plan[0].face == EnumFacing.UP
                        ? target.yaw()
                        : (float)
                                Math.toDegrees(
                                    Math.atan2(
                                        -plan[0].face.getFrontOffsetZ(),
                                        -plan[0].face.getFrontOffsetX()))
                            - 90,
                    plan[0].face == EnumFacing.UP ? 90 : 80);
          }
          if (mode.get() == Mode.GOD_BRIDGE) {
            directionTicks[0]++;
            perihelion.integration.minecraft189.control.PlayerRotationAccess access =
                (perihelion.integration.minecraft189.control.PlayerRotationAccess) mc().thePlayer;
            if (Math.abs(
                    MathHelper.wrapAngleTo180_float(target.yaw() - access.perihelion$reportedYaw()))
                > 10) directionTicks[0] = (int) (Math.random() * 4);
          }
          if (sprint.get() == Sprint.UNIVERSAL && placed[0] >= 3) {
            target = Rotation.server(mc().thePlayer.rotationYaw, target.pitch());
            placed[0] = 0;
          }
          double speed =
              (float)
                  (Math.min(minRotation.get(), maxRotation.get())
                      + Math.random() * Math.abs(maxRotation.get() - minRotation.get()));
          if (mode.get() == Mode.HYPIXEL)
            speed =
                ScaffoldPolicy.hypixelRotationSpeed(
                    mc().thePlayer.isPotionActive(net.minecraft.potion.Potion.moveSpeed),
                    isDiagonal(),
                    jumping);
          if (mode.get() == Mode.TELLY) {
            speed =
                TellyTiming.rotationSpeed(
                    hypixelTelly.get(),
                    isDiagonal(),
                    jumping,
                    tellyReturning[0],
                    telly.starting(now()),
                    speed);
            if (tellyReturning[0])
              target = Rotation.server(mc().thePlayer.rotationYaw, target.pitch());
          }
          if (rotations.get() == Rotations.RANDOMIZED && moving) {
            float amountRandom =
                (float)
                    (Math.min(randomMin.get(), randomMax.get())
                        + Math.random() * Math.abs(randomMax.get() - randomMin.get()));
            Rotation noisy =
                Rotation.server(
                    target.yaw() + (float) ((Math.random() - .5) * 2 * amountRandom),
                    MathHelper.clamp_float(
                        target.pitch() + (float) ((Math.random() - .5) * .5 * amountRandom),
                        -90,
                        90));
            if (plan[0].ray(mc(), noisy, 4.5, ray.get() == Ray.STRICT)) target = noisy;
          }
          previousGoal[0] = goal[0];
          goal[0] = target;
          if (speed != 0) {
            aim[0] = RotationDynamics.smooth(mc(), aim[0], target, speed);
          }
        };
    java.util.Map<BlockPos, Long> highlights = new java.util.LinkedHashMap<>();
    perihelion.integration.minecraft189.render.RenderState renderState =
        new perihelion.integration.minecraft189.render.RenderState();
    c.listen(
        perihelion.core.event.HudRenderEvent.class,
        e -> {
          if (!counter.get()) return;
          int count = 0;
          for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = mc().thePlayer.inventory.getStackInSlot(i);
            if (stack != null && InventoryPolicy.buildingBlock(stack)) count += stack.stackSize;
          }
          try (perihelion.integration.minecraft189.render.RenderState ignored =
              renderState.open()) {
            perihelion.integration.minecraft189.render.RenderStyle.text();
            String label = "Blocks: " + count;
            mc().fontRendererObj
                .drawStringWithShadow(
                    label,
                    (e.scaledWidth() - mc().fontRendererObj.getStringWidth(label)) / 2f,
                    e.scaledHeight() / 2f + 18,
                    -1);
          }
        });
    c.listen(
        perihelion.core.event.WorldRenderEvent.class,
        e -> {
          highlights.entrySet().removeIf(entry -> now() - entry.getValue() > 750);
          if (!highlight.get() || highlights.isEmpty()) return;
          try (perihelion.integration.minecraft189.render.RenderState ignored =
              renderState.open()) {
            perihelion.integration.minecraft189.render.RenderStyle.lines(
                false, 1, VisualDraw.ACCENT);
            org.lwjgl.opengl.GL11.glPushMatrix();
            try {
              VisualDraw.worldOrigin(mc());
              for (BlockPos pos : highlights.keySet())
                VisualDraw.box(new AxisAlignedBB(pos, pos.add(1, 1, 1)));
            } finally {
              org.lwjgl.opengl.GL11.glPopMatrix();
            }
          }
        });
    c.listen(
        ClientPacketEvent.class,
        e -> {
          if (autoJump.get()
              && e.packet() instanceof net.minecraft.network.play.server.S02PacketChat
              && ((net.minecraft.network.play.server.S02PacketChat) e.packet())
                  .getChatComponent()
                  .getUnformattedText()
                  .contains("You are not allowed to place blocks here!")) c.requestDisable();
        });
    c.listen(
        SafeWalkEvent.class,
        e -> {
          if (safe.get() && mc().thePlayer.onGround) e.cancel();
        });
    GameActions.movementFix(c, rotation, moveFix::get);
    // Move Fix chooses the input direction. Physics must always use the walking yaw,
    // including when the user disables that input correction.
    c.listen(
        StrafeEvent.class,
        e -> {
          if (rotation.isWinning()) e.yaw(rotation.value().get().yaw());
        });
    c.listen(
        MovementYawEvent.class,
        e -> {
          if (rotation.isWinning()) e.yaw(rotation.value().get().yaw());
        });
    c.listen(
        MovementInputEvent.class,
        e -> {
          if (mode.get() != Mode.HYPIXEL && sprint.get() == Sprint.VANILLA)
            perihelion.integration.minecraft189.HookBridge.gameControls()
                .finishInputSprint(sprinting);
        });
    c.listen(
        StrafeEvent.class,
        e -> {
          if (!slot.isWinning() || !rotation.isWinning()) return;
          boolean moving = GameActions.moving(mc().thePlayer);
          boolean jumping = mc().gameSettings.keyBindJump.isKeyDown();
          // Yuri jumps here, after vanilla has sampled input and established sprint eligibility.
          if (ScaffoldPolicy.autoJump(
              autoJump.get() || mode.get() == Mode.TELLY,
              jumping,
              mc().thePlayer.onGround,
              moving,
              active("speed"))) mc().thePlayer.jump();
          if (mode.get() == Mode.GOD_BRIDGE && !jumping && !autoJump.get() && placed[0] >= 8) {
            if (mc().thePlayer.onGround) mc().thePlayer.jump();
            placed[0] = 0;
          }
        });
    c.listen(
        PlayerControlEvent.class,
        100,
        false,
        e -> {
          if (e == PlayerControlEvent.BEFORE_INPUT) {
            if (placeTimer[0] > 0) placeTimer[0]--;
            left.clear();
            right.clear();
            jumpKey.clear();
            sprintKey.clear();
            attack.clear();
            if (mc().currentScreen != null
                || mc().thePlayer.isDead
                || active("freecam")
                || !conditionsMet()) {
              plan[0] = null;
              placementReady[0] = false;
              slot.clear();
              rotation.clear();
              sprinting.clear();
              sneaking.clear();
              return;
            }
            if (autoDisable.get()) {
              for (net.minecraft.entity.Entity entity : mc().theWorld.loadedEntityList) {
                if (!(entity instanceof net.minecraft.entity.projectile.EntityFireball)
                    || mc().thePlayer.getDistanceToEntity(entity) >= 6) continue;
                aim[0] =
                    RotationDynamics.smooth(
                        mc(),
                        aim[0],
                        GameActions.yuriLookAt(mc().thePlayer, AuraAim.normalPoint(mc(), entity)),
                        10);
                rotation.setForTicks(aim[0], 1);
                if (mc().thePlayer.getDistanceToEntity(entity) <= 5) c.requestDisable();
                return;
              }
            }
            if (mode.get() == Mode.HYPIXEL && sprint.get() != Sprint.LEGIT)
              sprint.set(Sprint.LEGIT);
            if (mode.get() == Mode.TELLY && !autoJump.get()) autoJump.set(true);
            attack.setForTicks(false, 1);
            int found = selectBlocks();
            if (found < 0) {
              c.requestDisable();
              return;
            }
            slot.setForTicks(
                swap.get() == Swap.CLIENT || ray.get() == Ray.STRICT
                    ? HotbarSelection.visible(found)
                    : HotbarSelection.spoof(found),
                1);
            perihelion.integration.minecraft189.control.MinecraftControls controls =
                perihelion.integration.minecraft189.HookBridge.gameControls();
            jumpKey.clear();
            boolean moving = GameActions.moving(mc().thePlayer),
                jumping =
                    runtime
                        .controls()
                        .key(InputKey.JUMP)
                        .orElse(
                            mode.get() == Mode.TELLY
                                ? perihelion.integration.minecraft189.HookBridge.gameControls()
                                    .physicalKeyDown(InputKey.JUMP)
                                : mc().gameSettings.keyBindJump.isKeyDown());
            if (mode.get() == Mode.HYPIXEL) {
              float offset = RotationDynamics.movementYaw(mc()) + 50;
              float difference = Math.abs(MathHelper.wrapAngleTo180_float(offset - goal[0].yaw()));
              if (placeTimer[0] == (difference > 60 ? 8 : 9) && !jumping && mc().thePlayer.onGround)
                goal[0] = Rotation.server(offset, goal[0].pitch());
            }
            perihelion.integration.minecraft189.control.GroundTickHistory groundHistory =
                ((perihelion.integration.minecraft189.control.PlayerGroundAccess) mc().thePlayer)
                    .perihelion$groundTicks();
            air[0] = groundHistory.beforeAir();
            if (mode.get() == Mode.TELLY) {
              int threshold =
                  jumping ? jumpDown.get() : isDiagonal() ? diagonal.get() : straight.get();
              tellyPlace[0] =
                  telly.placement(
                      now(),
                      air[0],
                      groundHistory.ground(),
                      hypixelTelly.get(),
                      isDiagonal(),
                      threshold);
            }
            // Auto-jump and tower run before the next walking pass. Use the preceding aim
            // for their jump impulse, while the final placement aim is still recomputed later.
            rotation.setForTicks(aim[0], 1);
            if (ScaffoldPolicy.resetHeight(
                mc().thePlayer.posY,
                plane[0] + 1,
                jumping,
                mc().thePlayer.onGround,
                groundHistory.beforeGround() <= 1,
                moving,
                mode.get() == Mode.HYPIXEL))
              plane[0] = MathHelper.floor_double(mc().thePlayer.posY) - 1;
            tower(air[0], moving, jumping);
            preparePlacement.run();
            boolean pulse =
                sneak.get() && placed[0] >= sneakEvery.get() && !sneaking.value().orElse(false);
            if (pulse) placed[0] = 0;
            sneaking.setForTicks(
                pulse
                    || eagle.get() && nearEdge()
                    || mode.get() == Mode.GOD_BRIDGE && directionTicks[0] <= 10,
                1);
            if (mode.get() == Mode.HYPIXEL) {
              // Legit sprint leaves vanilla's physical sprint decision intact unless backward.
              if (!moveFix.get()
                  && Math.abs(
                          MathHelper.wrapAngleTo180_float(
                              mc().thePlayer.rotationYaw - aim[0].yaw()))
                      > 90) {
                sprinting.setForTicks(false, 1);
                sprintKey.setForTicks(false, 1);
              } else sprinting.clear();
            } else if (sprint.get() == Sprint.UNIVERSAL || sprint.get() == Sprint.LEGIT) {
              if (!moveFix.get()
                  && Math.abs(
                          MathHelper.wrapAngleTo180_float(
                              mc().thePlayer.rotationYaw - aim[0].yaw()))
                      > 90) {
                sprinting.setForTicks(false, 1);
                sprintKey.setForTicks(false, 1);
              } else sprinting.clear();
            } else if (sprint.get() == Sprint.VANILLA) {
              // Yuri sets sprint before input. Retire this write at the input hook so vanilla
              // can stop it after corrected backward input instead of reasserting it later.
              sprinting.setForTicks(moving, 1);
            } else {
              sprinting.setForTicks(false, 1);
              sprintKey.setForTicks(false, 1);
            }
            boolean inputSprint =
                runtime.controls().sprinting().orElse(mc().thePlayer.isSprinting());
            tellyReturning[0] = mc().thePlayer.onGround && moving && (!inputSprint || jumping);
            if (mode.get() != Mode.HYPIXEL && sprint.get() == Sprint.NCP)
              GameActions.strafe(mc().thePlayer, GameActions.baseSpeed(mc().thePlayer) / 1.84f);
            if (mode.get() == Mode.BREEZILY && plan[0] == null) {
              plan[0] = null;
              rotation.clear();
              return;
            }
            if (mode.get() == Mode.BREEZILY) {
              if (mc().gameSettings.keyBindForward.isKeyDown() && !jumping) {
                EnumFacing facing = mc().thePlayer.getHorizontalFacing();
                double value =
                    facing.getAxis() == EnumFacing.Axis.Z
                        ? mc().thePlayer.posX
                        : mc().thePlayer.posZ;
                double offset = value - Math.floor(value);
                if (facing == EnumFacing.SOUTH || facing == EnumFacing.WEST) offset = 1 - offset;
                double speed =
                    Math.abs(
                        facing.getAxis() == EnumFacing.Axis.Z
                            ? mc().thePlayer.motionZ
                            : mc().thePlayer.motionX);
                if (!(speed < .086 && Math.abs(offset - .5) < .4 && delay.get() <= 1)) {
                  boolean rightward = offset < .5 + (Math.random() - .5) / 10;
                  left.setForTicks(!rightward, 1);
                  right.setForTicks(rightward, 1);
                }
              }
            }
            updateAim.run();
            rotation.setForTicks(aim[0], 1);
            if (controls.isInputRotationPrepared(rotation)
                && plan[0] != null
                && placementReady[0]
                && now() - last[0] >= delay.get() * 20L) {
              Rotation prepared = rotation.value().get();
              if ((ray.get() == Ray.NONE
                      || plan[0].ray(mc(), prepared, 4.5, ray.get() == Ray.STRICT))
                  && new Placement(
                          plan[0].target,
                          plan[0].support,
                          plan[0].face,
                          ScaffoldPlacement.hit(mc(), plan[0], prepared),
                          plan[0].range)
                      .placeInput(mc(), slot, rotation, false, ray.get() == Ray.STRICT)) {
                if (highlight.get()) {
                  highlights.put(plan[0].target, now());
                  if (highlights.size() > 64)
                    highlights.remove(highlights.keySet().iterator().next());
                }
                placed[0]++;
                placeTimer[0] = 10;
                last[0] = now();
              }
            }
          }
        });
  }

  /** Current building eligibility, independent of whether this tick needs another block. */
  boolean buildingActive() {
    return active("scaffold")
        && mc().thePlayer != null
        && mc().theWorld != null
        && mc().currentScreen == null
        && !mc().thePlayer.isDead
        && !active("freecam")
        && conditionsMet()
        && selectBlocks() >= 0;
  }

  private boolean conditionsMet() {
    if (requireUse.get()
        && !perihelion.integration.minecraft189.HookBridge.gameControls()
            .physicalKeyDown(InputKey.USE_ITEM)) return false;
    if (holdingBlocks.get() && !InventoryPolicy.buildingBlock(mc().thePlayer.getHeldItem()))
      return false;
    return !pitchCondition.get()
        || mc().thePlayer.rotationPitch >= Math.min(minimumPitch.get(), maximumPitch.get())
            && mc().thePlayer.rotationPitch <= Math.max(minimumPitch.get(), maximumPitch.get());
  }

  private int selectBlocks() {
    int current = mc().thePlayer.inventory.currentItem;
    if (stackChoice.get() == StackChoice.CURRENT
        && InventoryPolicy.buildingBlock(mc().thePlayer.inventory.getStackInSlot(current)))
      return current;
    int best = -1, count = -1;
    for (int i = 0; i < 9; i++) {
      net.minecraft.item.ItemStack stack = mc().thePlayer.inventory.getStackInSlot(i);
      if (!InventoryPolicy.buildingBlock(stack)) continue;
      if (stackChoice.get() != StackChoice.LARGEST) return i;
      if (stack.stackSize > count || stack.stackSize == count && i == current) {
        best = i;
        count = stack.stackSize;
      }
    }
    return best;
  }

  private double expansionDirection() {
    // Yuri explicitly uses camera yaw and raw key directions for expansion geometry.
    return GameActions.direction(
        mc().thePlayer.rotationYaw,
        mc().gameSettings.keyBindForward.isKeyDown()
            ? 1
            : mc().gameSettings.keyBindBack.isKeyDown() ? -1 : 0,
        mc().gameSettings.keyBindRight.isKeyDown()
            ? -1
            : mc().gameSettings.keyBindLeft.isKeyDown() ? 1 : 0);
  }

  private BlockPos expandedOffset(double direction) {
    if (expand.get().intValue() == 0) return BlockPos.ORIGIN;
    for (int distance = 0; distance <= expand.get().intValue(); distance++) {
      BlockPos ahead =
          new BlockPos(
              mc().thePlayer.posX - Math.sin(direction) * distance,
              mc().thePlayer.posY - .5,
              mc().thePlayer.posZ + Math.cos(direction) * distance);
      if (mc().theWorld.getBlockState(ahead).getBlock() instanceof net.minecraft.block.BlockAir) {
        int[] offset =
            ScaffoldPolicy.expansionOffset(
                mc().thePlayer.posX, mc().thePlayer.posZ, direction, distance + 1);
        return new BlockPos(offset[0], 0, offset[1]);
      }
    }
    return BlockPos.ORIGIN;
  }

  private boolean clearBelow(BlockPos offset, int down) {
    return Placement.replaceable(
        mc(),
        new BlockPos(
            mc().thePlayer.posX + offset.getX(),
            mc().thePlayer.posY - down,
            mc().thePlayer.posZ + offset.getZ()));
  }

  private boolean nearEdge() {
    if (!mc().thePlayer.onGround || !GameActions.moving(mc().thePlayer)) return false;
    double direction = GameActions.direction(mc().thePlayer);
    AxisAlignedBB ahead =
        mc().thePlayer
            .getEntityBoundingBox()
            .offset(-Math.sin(direction) * edge.get(), -.5, Math.cos(direction) * edge.get());
    return mc().theWorld.getCollidingBoundingBoxes(mc().thePlayer, ahead).isEmpty();
  }

  private boolean isDiagonal() {
    // Use uncorrected key intent: movementInput is already in the silent rotation's basis.
    return ScaffoldPolicy.diagonal(
        mc().thePlayer.rotationYaw,
        (mc().gameSettings.keyBindForward.isKeyDown() ? 1 : 0)
            - (mc().gameSettings.keyBindBack.isKeyDown() ? 1 : 0),
        (mc().gameSettings.keyBindLeft.isKeyDown() ? 1 : 0)
            - (mc().gameSettings.keyBindRight.isKeyDown() ? 1 : 0));
  }

  private void tower(int air, boolean moving, boolean jumping) {
    if (!jumping
        || !towerMove.get() && moving
        || mc().theWorld
            .getCollidingBoundingBoxes(
                mc().thePlayer, mc().thePlayer.getEntityBoundingBox().offset(0, -2, 0))
            .isEmpty()) return;
    switch (tower.get()) {
      case VANILLA:
        mc().thePlayer.motionY = 0.42;
        break;
      case NCP:
        double fraction = mc().thePlayer.posY - Math.floor(mc().thePlayer.posY);
        if (fraction <= 0.00153598) {
          mc().thePlayer
              .setPosition(
                  mc().thePlayer.posX, Math.floor(mc().thePlayer.posY), mc().thePlayer.posZ);
          mc().thePlayer.motionY = 0.41998;
        } else if (fraction < 0.1 && mc().thePlayer.onGround)
          mc().thePlayer
              .setPosition(
                  mc().thePlayer.posX, Math.floor(mc().thePlayer.posY), mc().thePlayer.posZ);
        break;
      case HYPIXEL:
        if (!ScaffoldPolicy.hypixelTower(
            mc().thePlayer.isPotionActive(net.minecraft.potion.Potion.jump),
            mc().theWorld
                .getBlockState(new BlockPos(mc().thePlayer.getPositionVector().subtract(0, 1, 0)))
                .getBlock()
                .isFullBlock())) break;
        if (mc().thePlayer.onGround
            && Math.abs(mc().thePlayer.moveForward) < .8f
            && Math.abs(mc().thePlayer.moveStrafing) < .8f) mc().thePlayer.jump();
        mc().thePlayer.motionY +=
            ScaffoldPolicy.hypixelTowerAdjustment(air, moving, GameActions.speed(mc().thePlayer));
        break;
      case POLAR:
        if (!moving
            && mc().thePlayer.motionY < 0
            && !mc().theWorld
                .getCollidingBoundingBoxes(
                    mc().thePlayer, mc().thePlayer.getEntityBoundingBox().offset(0, -1.26, 0))
                .isEmpty()) mc().thePlayer.motionY -= 0.091;
        break;
      default:
        break;
    }
  }
}
