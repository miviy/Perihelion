package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.control.*;
import perihelion.core.event.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.render.*;

final class MinigameAimModule extends GameplayModule {
  enum Game {
    ZOMBIE,
    HALLOWEEN
  }

  enum Fire {
    AUTO_FIRE,
    ON_HELD
  }

  enum Hitbox {
    HITSCAN_HEAD,
    HEAD,
    CHEST,
    LEG
  }

  enum Priority {
    CLOSEST,
    MAX_HEALTH,
    LOWEST_HEALTH,
    FOV
  }

  private final Setting<Game> game;
  private final Setting<Fire> fire;
  private final Setting<Hitbox> hitbox;
  private final Setting<Priority> priority;
  private final Setting<Boolean> silent, prediction, showFov, heal, revive, ammo, hud;
  private final Setting<Integer> ticks, delay, buffer, health;
  private final Setting<Double> scale, fov, range, speed;

  MinigameAimModule(ClientRuntime r) {
    super(
        r,
        "minigameaim",
        "Minigame Aim",
        "Predict Zombies targets or interact with Halloween Simulator skulls.",
        ModuleCategory.MISC);
    game = mode("game", "Game", Game.ZOMBIE, Game.class);
    fire = mode("aimbot-mode", "Aimbot Mode", Fire.AUTO_FIRE, Fire.class);
    hitbox = mode("hitbox", "Hitbox", Hitbox.HITSCAN_HEAD, Hitbox.class);
    priority = mode("priority", "Priority", Priority.CLOSEST, Priority.class);
    silent = bool("silent", "Silent", true);
    prediction = bool("show-prediction", "Show Prediction", true);
    showFov = bool("show-fov", "Show FOV", true);
    heal = bool("auto-heal", "Auto Heal", true);
    revive = bool("auto-revive", "Auto Revive", true);
    ammo = bool("auto-ammo", "Auto Ammo", false);
    hud = bool("hud", "HUD", true);
    ticks = integer("prediction-ticks", "Prediction Ticks", 2, 0, 10);
    delay = integer("delay", "Delay", 4, 0, 20);
    buffer = integer("buffer", "Buffer", 3, 1, 10);
    health = integer("health", "Health", 3, 1, 20);
    scale = number("prediction-scale", "Prediction Scale", 1, 0, 2, 0.05);
    fov = number("fov", "FOV", 90, 1, 180, 0.1);
    range = number("range", "Range", 4.5, 1, 6, 0.5);
    speed = number("rotation-speed", "Rotation Speed", 10, 0.5, 10, 0.5);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    ControlRequest<Rotation> rotation = c.controls().rotation(80);
    ControlRequest<Boolean> sneak = c.controls().key(InputKey.SNEAK, 90);
    Map<EntityLivingBase, Deque<Vec3>> history = new WeakHashMap<>();
    Map<EntityLivingBase, Vec3> predictions = new LinkedHashMap<>();
    List<BlockPos> skulls = new ArrayList<>();
    EntityLivingBase[] target = {null};
    BlockPos[] skull = {null};
    Rotation[] desired = {null};
    int[] elapsed = {0};
    boolean[] alternate = {false};
    RenderState state = new RenderState();
    GameActions.movementFix(c, rotation, () -> true);
    c.listen(
        PlayerControlEvent.class,
        e -> {
          if (e == PlayerControlEvent.BEFORE_INPUT) {
            target[0] = null;
            skull[0] = null;
            desired[0] = null;
            predictions.clear();
            skulls.clear();
            elapsed[0]++;
            if (!interactionAllowed()) {
              rotation.clear();
              sneak.clear();
              return;
            }
            if (game.get() == Game.HALLOWEEN) {
              sneak.clear();
              ScoreObjective objective = mc().theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
              if (objective != null
                  && StringUtils.stripControlCodes(objective.getDisplayName())
                      .startsWith("HALLOWEEN SIMULATOR"))
                for (TileEntity tile : mc().theWorld.loadedTileEntityList)
                  if (tile instanceof TileEntitySkull
                      && ((TileEntitySkull) tile).getPlayerProfile() != null) {
                    BlockPos pos = tile.getPos();
                    skulls.add(pos);
                    if (skull[0] == null
                        && mc().thePlayer.getPositionEyes(1).squareDistanceTo(new Vec3(pos))
                            < range.get() * range.get()) skull[0] = pos;
                  }
              if (skull[0] != null)
                desired[0] =
                    GameActions.lookAt(mc().thePlayer, new Vec3(skull[0]).addVector(0.5, 0.5, 0.5));
            } else {
              support(sneak);
              Set<EntityLivingBase> current = new HashSet<>();
              double best = -Double.MAX_VALUE;
              for (Entity entity : mc().theWorld.loadedEntityList)
                if (entity instanceof EntityLivingBase
                    && entity instanceof IAnimals
                    && !(entity instanceof EntityVillager)
                    && ((EntityLivingBase) entity).getHealth() > 0) {
                  EntityLivingBase living = (EntityLivingBase) entity;
                  current.add(living);
                  Deque<Vec3> samples = history.computeIfAbsent(living, key -> new ArrayDeque<>());
                  Vec3 delta =
                      new Vec3(
                          living.posX - living.lastTickPosX,
                          living.posY - living.lastTickPosY,
                          living.posZ - living.lastTickPosZ);
                  if (Math.hypot(delta.xCoord, delta.zCoord) < 3) {
                    samples.addLast(delta);
                    while (samples.size() > buffer.get()) samples.removeFirst();
                  } else samples.clear();
                  Vec3 offset = predict(living, samples);
                  predictions.put(living, offset);
                  Vec3 point = hitPoint(living, offset);
                  if (point == null) continue;
                  Rotation aim = GameActions.lookAt(mc().thePlayer, point);
                  double angle =
                      Math.hypot(
                          MathHelper.wrapAngleTo180_float(aim.yaw() - mc().thePlayer.rotationYaw),
                          aim.pitch() - mc().thePlayer.rotationPitch);
                  if (angle > fov.get()) continue;
                  double weight = -mc().thePlayer.getDistanceToEntity(living) + 5;
                  switch (priority.get()) {
                    case MAX_HEALTH:
                      weight += living.getMaxHealth();
                      break;
                    case LOWEST_HEALTH:
                      weight -= living.getHealth();
                      break;
                    case FOV:
                      weight -= angle;
                      break;
                    default:
                      break;
                  }
                  if (weight > best) {
                    best = weight;
                    target[0] = living;
                    desired[0] = aim;
                  }
                }
              history.keySet().retainAll(current);
              if (fire.get() == Fire.ON_HELD && !mc().gameSettings.keyBindUseItem.isKeyDown()) {
                target[0] = null;
                desired[0] = null;
              }
            }
            if (desired[0] == null) {
              rotation.clear();
              return;
            }
            Rotation previous =
                runtime
                    .controls()
                    .rotation()
                    .orElse(
                        Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch));
            Rotation aim =
                Placement.smooth(
                    previous, desired[0], game.get() == Game.HALLOWEEN ? speed.get() : 4);
            rotation.setForTicks(
                game.get() == Game.ZOMBIE && !silent.get()
                    ? Rotation.view(aim.yaw(), aim.pitch())
                    : aim,
                1);
          } else if (e == PlayerControlEvent.BEFORE_ACTIONS && desired[0] != null) {
            if (!interactionAllowed()) {
              target[0] = null;
              skull[0] = null;
              desired[0] = null;
              rotation.clear();
              sneak.clear();
              return;
            }
            GameActions.previousAction(
                rotation,
                actual -> {
                  if (Math.abs(MathHelper.wrapAngleTo180_float(desired[0].yaw() - actual.yaw()))
                          >= 3
                      || Math.abs(desired[0].pitch() - actual.pitch()) >= 5) return;
                  if (game.get() == Game.HALLOWEEN && skull[0] != null) {
                    if (!alternate[0]) {
                      GameActions.send(
                          new C08PacketPlayerBlockPlacement(
                              skull[0], 1, mc().thePlayer.getHeldItem(), 0.5f, 0.5f, 0.5f));
                      mc().thePlayer.swingItem();
                    }
                    alternate[0] = !alternate[0];
                  } else if (target[0] != null && elapsed[0] >= delay.get()) {
                    perihelion.integration.minecraft189.HookBridge.gameControls()
                        .withPreviousRotation(
                            rotation, () -> ((GameplayAccess) mc()).perihelion$rightClickMouse());
                    elapsed[0] = 0;
                  }
                });
          }
        });
    c.listen(
        WorldRenderEvent.class,
        e -> {
          try (RenderState ignored = state.open()) {
            RenderStyle.lines(true, 1, 0x9900ff00);
            VisualDraw.worldOrigin(mc());
            if (game.get() == Game.ZOMBIE && prediction.get())
              for (Map.Entry<EntityLivingBase, Vec3> entry : predictions.entrySet()) {
                Vec3 p = entry.getValue();
                VisualDraw.box(
                    VisualDraw.bounds(entry.getKey(), e.partialTicks())
                        .offset(p.xCoord, p.yCoord, p.zCoord));
              }
            if (game.get() == Game.HALLOWEEN) {
              RenderStyle.lines(true, 1, 0x5000ff00);
              for (BlockPos pos : skulls) VisualDraw.fill(new AxisAlignedBB(pos, pos.add(1, 1, 1)));
            }
          }
        });
    c.listen(
        HudRenderEvent.class,
        e -> {
          if (game.get() != Game.ZOMBIE) return;
          try (RenderState ignored = state.open()) {
            if (showFov.get()) {
              RenderStyle.lines(true, 1, 0x66ffffff);
              double radius = Math.min(e.scaledWidth(), e.scaledHeight()) * fov.get() / 180;
              GL11.glBegin(GL11.GL_LINE_LOOP);
              for (int i = 0; i < 96; i++) {
                double a = i * Math.PI / 48;
                GL11.glVertex2d(
                    e.scaledWidth() / 2.0 + Math.cos(a) * radius,
                    e.scaledHeight() / 2.0 + Math.sin(a) * radius);
              }
              GL11.glEnd();
            }
            if (hud.get()) {
              RenderStyle.text();
              mc().fontRendererObj
                  .drawStringWithShadow(
                      "Minigame Aim: " + (target[0] == null ? "searching" : target[0].getName()),
                      8,
                      e.scaledHeight() - 32,
                      VisualDraw.ACCENT);
            }
          }
        });
  }

  private boolean interactionAllowed() {
    return mc().currentScreen == null
        && !active("scaffold")
        && !active("freecam")
        && mc().getRenderViewEntity() == mc().thePlayer;
  }

  private Vec3 hitPoint(EntityLivingBase target, Vec3 offset) {
    double[] heights =
        hitbox.get() == Hitbox.HITSCAN_HEAD
            ? new double[] {1, 0.5, 1.0 / 3}
            : new double[] {
              hitbox.get() == Hitbox.HEAD ? 1 : hitbox.get() == Hitbox.CHEST ? 0.5 : 1.0 / 3
            };
    for (double height : heights) {
      Vec3 point =
          target.getPositionVector().add(offset).addVector(0, target.getEyeHeight() * height, 0);
      if (mc().theWorld.rayTraceBlocks(mc().thePlayer.getPositionEyes(1), point, false, true, false)
          == null) return point;
    }
    return null;
  }

  private Vec3 predict(EntityLivingBase target, Deque<Vec3> samples) {
    if (samples.isEmpty() || ticks.get() == 0) return new Vec3(0, 0, 0);
    double dx = 0, dz = 0;
    for (Vec3 sample : samples) {
      dx += sample.xCoord;
      dz += sample.zCoord;
    }
    dx /= samples.size();
    dz /= samples.size();
    double dy = target.posY - target.lastTickPosY;
    if (Math.abs(Math.round(dy * 10) / 10.0) != 0 || !target.onGround)
      dy = (dy - 0.08) * 0.980000019;
    if (dy >= 0.5) dy = 0;
    double y = 0, x = 0, z = 0, stepY = 0;
    for (int i = 0; i < ticks.get(); i++) {
      x = dx * i;
      z = dz * i;
      AxisAlignedBB box = target.getEntityBoundingBox().offset(x, y, z);
      stepY = dy;
      for (AxisAlignedBB collision :
          mc().theWorld.getCollidingBoundingBoxes(target, box.addCoord(0, dy, 0)))
        stepY = collision.calculateYOffset(box, stepY);
      y += stepY;
      dy = (stepY - 0.08) * 0.980000019;
    }
    return new Vec3(x * scale.get(), stepY, z * scale.get());
  }

  private void support(ControlRequest<Boolean> sneak) {
    boolean down = false;
    if (revive.get())
      for (Entity e : mc().theWorld.loadedEntityList)
        if (e instanceof EntityArmorStand
            && e.hasCustomName()
            && mc().thePlayer.getDistanceToEntity(e) < 2.5) {
          String name =
              StringUtils.stripControlCodes(e.getDisplayName().getFormattedText())
                  .toLowerCase(Locale.ROOT);
          if (name.contains("downed")
              || name.contains("revive")
              || name.matches(".*\\d+(\\.\\d+)?s$")) {
            down = true;
            break;
          }
        }
    if (down) sneak.setForTicks(true, 1);
    else sneak.clear();
    if (mc().thePlayer.openContainer != mc().thePlayer.inventoryContainer
        || mc().thePlayer.inventory.getItemStack() != null) return;
    if (heal.get() && mc().thePlayer.getHealth() <= health.get())
      for (int i = 9; i < 45; i++) {
        ItemStack s = mc().thePlayer.inventoryContainer.getSlot(i).getStack();
        if (s != null && s.getItem() == Items.golden_apple) {
          int held = mc().thePlayer.inventory.currentItem;
          boolean swapped = i != held + 36;
          if (swapped) mc().playerController.windowClick(0, i, held, 2, mc().thePlayer);
          try {
            GameActions.send(new C08PacketPlayerBlockPlacement(mc().thePlayer.getHeldItem()));
          } finally {
            if (swapped) mc().playerController.windowClick(0, i, held, 2, mc().thePlayer);
          }
          break;
        }
      }
    if (ammo.get()
        && mc().thePlayer.getHeldItem() != null
        && mc().thePlayer.getHeldItem().getItem() instanceof ItemBow
        && GameActions.hotbar(mc(), s -> s.getItem() == Items.arrow) < 0) {
      int empty = -1;
      for (int i = 0; i < 9; i++)
        if (mc().thePlayer.inventory.getStackInSlot(i) == null) {
          empty = i;
          break;
        }
      if (empty >= 0) {
        int best = -1, bestSize = -1;
        for (int i = 9; i < 36; i++) {
          ItemStack s = mc().thePlayer.inventory.getStackInSlot(i);
          if (s != null && s.getItem() == Items.arrow && s.stackSize > bestSize) {
            best = i;
            bestSize = s.stackSize;
          }
        }
        if (best >= 0) mc().playerController.windowClick(0, best, empty, 2, mc().thePlayer);
      }
    }
  }
}
