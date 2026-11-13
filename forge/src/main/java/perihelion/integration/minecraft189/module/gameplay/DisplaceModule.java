// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri DisplaceModule, revision 1cb86ad94ce1f01aa9b715577163c8ecc47b7def.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.Rotation;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.control.MinecraftControls;
import perihelion.integration.minecraft189.control.PlayerRotationAccess;
import perihelion.integration.minecraft189.event.AttackClickEvent;
import perihelion.integration.minecraft189.event.MovementInputEvent;
import perihelion.integration.minecraft189.event.MovementYawEvent;
import perihelion.integration.minecraft189.event.PlayerAttackEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.event.StrafeEvent;
import perihelion.integration.minecraft189.render.RenderState;

/** Aura-owned targets with a short, independently arbitrated knockback angle override. */
final class DisplaceModule extends GameplayModule {
  enum Activation {
    AURA,
    B4_MANUAL,
    BOTH
  }

  enum Mode {
    LEFT,
    RIGHT,
    RANDOM,
    VOID
  }

  private final AuraModule aura;
  private final Setting<Mode> mode;
  private final Setting<Activation> activation;
  private final Setting<Double> angle, range, cooldown;
  private final Setting<Integer> maxHurtTime;
  private final Setting<Boolean> onlyKnockbackItems, playersOnly, renderArrow;
  private final Random random = new Random();
  private final DisplaceBehavior.Hold hold = new DisplaceBehavior.Hold();
  private final RenderState renderState = new RenderState();
  private EntityLivingBase hitTarget;
  private ControlRequest<Rotation> rotation;
  private Rotation aim;
  private float heldYaw, heldPitch;
  private long lastAt = -1;
  private boolean arrow;
  private long arrowAt;
  private double arrowX, arrowY, arrowZ, dirX, dirZ;
  private boolean manualArmed, manualReplay;
  private int manualAge, previousDropSide = -1;
  private float manualOffset;

  DisplaceModule(ClientRuntime runtime, AuraModule aura) {
    super(
        runtime,
        "displace",
        "Displace",
        "Offsets hit angles to change target knockback direction.",
        ModuleCategory.COMBAT);
    this.aura = aura;
    mode = mode("mode", "Mode", Mode.VOID, Mode.class);
    activation = mode("activation", "Activation", Activation.AURA, Activation.class);
    angle = number("angle", "Angle", 45, 5, 180, 1);
    range = number("range", "Range", 3, 3, 6, .1);
    cooldown = number("cooldown", "Cooldown", 1, 0, 5, .1);
    maxHurtTime = integer("max-hurt-time", "Max Hurt Time", 3, 0, 10);
    onlyKnockbackItems = bool("only-knockback-items", "Only Knockback Items", false);
    playersOnly = bool("players-only", "Players Only", true);
    renderArrow = bool("render-arrow", "Render Arrow", true);
    aura.displacement(() -> hold.overriding() ? rotation : null);
  }

  @Override
  protected void onEnable(ModuleContext context) {
    reset();
    arrow = false;
    previousDropSide = -1;
    manualReplay = false;
    rotation = context.controls().rotation(55);
    ActionWindow packets = new ActionWindow(context);
    context.observe(activation, (s, before, after) -> reset());
    context.observe(
        mode,
        (s, before, after) -> {
          reset();
          previousDropSide = -1;
        });
    context.listen(AttackClickEvent.class, 100, false, e -> manualClick(e, packets));
    context.onCleanup(
        () -> {
          reset();
          arrow = false;
          rotation = null;
          previousDropSide = -1;
          manualReplay = false;
        });
    // Aura selects a current target first. The newly armed override waits for a walking packet.
    context.listen(
        PlayerControlEvent.class,
        -20,
        false,
        event -> {
          if (event == PlayerControlEvent.BEFORE_INPUT) prepare(packets);
          else if (event == PlayerControlEvent.BEFORE_MOTION && hold.overriding()) {
            if (!validSession() || !validHeldTarget()) reset();
            else if (manualArmed) {
              heldYaw = mc().thePlayer.rotationYaw + manualOffset;
              heldPitch = mc().thePlayer.rotationPitch;
              aim = Rotation.serverUnwrapped(heldYaw, heldPitch);
              rotation.setForTicks(aim, 1);
            }
          } else if (event == PlayerControlEvent.AFTER_MOTION && hold.finishing()) reset();
        });
    // Observe only attacks that remain accepted after combat-policy cancellation listeners.
    context.listen(PlayerAttackEvent.class, -1000, false, this::attack);
    context.listen(
        MovementInputEvent.class,
        event -> {
          if (!ownsAim()) return;
          float[] input =
              manualArmed
                  ? perihelion.integration.minecraft189.module.B4MovementCorrection.align(
                      event.strafe(), event.forward(), mc().thePlayer.rotationYaw, aim.yaw())
                  : perihelion.integration.minecraft189.module.YuriMovementCorrection.align(
                      event.strafe(), event.forward(), mc().thePlayer.rotationYaw, aim.yaw());
          event.input(input[0], input[1]);
        });
    context.listen(
        StrafeEvent.class,
        10,
        false,
        event -> {
          if (!ownsAim()) return;
          event.yaw(aim.yaw());
        });
    context.listen(
        MovementYawEvent.class,
        -10,
        false,
        event -> {
          if (ownsAim()) event.yaw(aim.yaw());
        });
    context.listen(WorldRenderEvent.class, event -> render());
  }

  private boolean validSession() {
    return mc().thePlayer != null
        && mc().theWorld != null
        && (manualArmed || activation.get() != Activation.B4_MANUAL && active("aura"))
        && (!active("aura") || !aura.combatBlockedByBuilding())
        && (!manualArmed
            || !((ScaffoldModule) runtime.modules().require("scaffold").module()).buildingActive()
                && !((BlockInModule) runtime.modules().require("blockin").module())
                    .buildingActive())
        && !active("freecam")
        && mc().currentScreen == null
        && !mc().thePlayer.isDead
        && mc().thePlayer.getHealth() > 0;
  }

  private boolean validHeldTarget() {
    return hitTarget != null
        && !hitTarget.isDead
        && hitTarget.getHealth() > 0
        && mc().theWorld.getEntityByID(hitTarget.getEntityId()) == hitTarget
        && mc().thePlayer.getDistanceToEntity(hitTarget) <= (manualArmed ? 6 : range.get() + 1);
  }

  private void prepare(ActionWindow packets) {
    if (manualArmed) {
      if (!validSession() || !validHeldTarget() || ++manualAge > 3 || packets.bad()) {
        reset();
        return;
      }
      if (hold.finishing()) return;
      MinecraftControls controls = HookBridge.gameControls();
      // Tick-scoped claims expire before the next input pass. Renew the identical
      // held value before testing its ownership against the preceding walking packet.
      if (aim != null) rotation.setForTicks(aim, 1);
      if (controls != null && controls.isPreviousRotationApplied(rotation)) {
        // B4 replays one ordinary queued click; the current crosshair still decides the hit.
        if (mc().objectMouseOver == null || mc().objectMouseOver.entityHit != hitTarget) {
          reset();
          return;
        }
        manualReplay = true;
        try {
          controls.withPreviousRotation(
              rotation, () -> ((GameplayAccess) mc()).perihelion$clickMouse());
        } finally {
          manualReplay = false;
        }
        // The queued manual click consumes the prior offset. B4's next provider pass
        // returns to Silent/camera before physics and the next walking packet.
        reset();
      }
      return;
    }
    if (!validSession()) {
      reset();
      return;
    }
    if (hold.finishing()) return;
    if (hold.armed()) {
      if (!hold.hold(validHeldTarget(), packets.bad())) {
        reset();
        return;
      }
      submit();
      return;
    }
    EntityLivingBase target = aura.target();
    if (target == null || !canDisplace(target) || packets.bad()) return;
    float[] targetAim = aim(target);
    float offset = pickOffset(target, targetAim[0]);
    if (Float.isNaN(offset)) return;
    heldYaw = targetAim[0] + offset;
    heldPitch = targetAim[1];
    hitTarget = target;
    lastAt = now();
    hold.arm();
    submit();
  }

  private void manualClick(AttackClickEvent event, ActionWindow packets) {
    if (activation.get() == Activation.AURA
        || manualReplay
        || mc().thePlayer == null
        || mc().theWorld == null
        || mc().currentScreen != null
        || !mc().inGameHasFocus
        || active("freecam")
        || packets.bad()) return;
    if (((ScaffoldModule) runtime.modules().require("scaffold").module()).buildingActive()
        || ((BlockInModule) runtime.modules().require("blockin").module()).buildingActive()) return;
    AimAssistModule assist = (AimAssistModule) runtime.modules().require("aimassist").module();
    EntityLivingBase target = active("aimassist") ? assist.silentTarget() : null;
    boolean fromSilent = target != null;
    if (target == null
        && mc().objectMouseOver != null
        && mc().objectMouseOver.entityHit instanceof EntityLivingBase)
      target = (EntityLivingBase) mc().objectMouseOver.entityHit;
    if (target == null) return;
    boolean basic =
        mc().theWorld.getEntityByID(target.getEntityId()) == target
            && !target.isDead
            && target.getHealth() > 0
            && (!playersOnly.get() || target instanceof EntityPlayer)
            && (mc().thePlayer.isSprinting()
                || EnchantmentHelper.getKnockbackModifier(mc().thePlayer) != 0)
            && (!onlyKnockbackItems.get()
                || EnchantmentHelper.getKnockbackModifier(mc().thePlayer) != 0);
    if (!basic) return;
    float offset = manualOffset(target);
    if (Float.isNaN(offset)) return;
    // B4's default Override Attacks also consumes otherwise eligible cooldown/hurt-time clicks.
    event.cancel();
    if (manualArmed
        || target.hurtTime > maxHurtTime.get()
        || !DisplaceBehavior.cooldownReady(now(), lastAt, cooldown.get())) return;
    reset();
    hitTarget = target;
    manualArmed = true;
    manualAge = 0;
    manualOffset = offset;
    lastAt = now();
    hold.arm();
    aim =
        Rotation.serverUnwrapped(mc().thePlayer.rotationYaw + offset, mc().thePlayer.rotationPitch);
    rotation.setForTicks(aim, 1);
    if (fromSilent) assist.snapForDisplace();
  }

  ControlRequest<Rotation> manualSelectionOwner(EntityLivingBase entity) {
    return manualArmed
            && hitTarget == entity
            && validSession()
            && validHeldTarget()
            && rotation != null
            && rotation.isWinning()
        ? rotation
        : null;
  }

  private float manualOffset(EntityLivingBase target) {
    boolean left;
    switch (mode.get()) {
      case LEFT:
        left = true;
        break;
      case RIGHT:
        left = false;
        break;
      case RANDOM:
        left = random.nextBoolean();
        break;
      default:
        int side =
            B4DisplacePolicy.side(
                mc().thePlayer.posX,
                mc().thePlayer.posZ,
                target.posX,
                target.posY,
                target.posZ,
                previousDropSide,
                (x, y, z) -> {
                  BlockPos top = new BlockPos(x, y, z), bottom = new BlockPos(x, y - 6, z);
                  return !mc().theWorld.isBlockLoaded(top)
                      || !mc().theWorld.isBlockLoaded(bottom)
                      || mc().theWorld
                              .rayTraceBlocks(
                                  new net.minecraft.util.Vec3(x, y, z),
                                  new net.minecraft.util.Vec3(x, y - 6, z),
                                  true)
                          != null;
                });
        previousDropSide = side;
        if (side < 0) return Float.NaN;
        left = side == 0;
    }
    return DisplaceBehavior.offset(left, angle.get().floatValue(), random.nextFloat());
  }

  private boolean canDisplace(EntityLivingBase target) {
    return mc().theWorld.getEntityByID(target.getEntityId()) == target
        && DisplaceBehavior.canDisplace(
            target.isDead || target.getHealth() <= 0,
            target instanceof EntityPlayer,
            playersOnly.get(),
            mc().thePlayer.getDistanceToEntity(target),
            range.get(),
            target.hurtTime,
            maxHurtTime.get(),
            mc().thePlayer.isSprinting(),
            EnchantmentHelper.getKnockbackModifier(mc().thePlayer),
            onlyKnockbackItems.get(),
            DisplaceBehavior.cooldownReady(now(), lastAt, cooldown.get()));
  }

  private float[] aim(EntityLivingBase target) {
    return DisplaceBehavior.aim(
        mc().thePlayer.posX,
        mc().thePlayer.posY,
        mc().thePlayer.posZ,
        mc().thePlayer.getEyeHeight(),
        mc().thePlayer.rotationYaw,
        mc().thePlayer.rotationPitch,
        target.posX,
        target.posY,
        target.posZ,
        target.getEyeHeight());
  }

  private float pickOffset(EntityLivingBase target, float baseYaw) {
    boolean left;
    switch (mode.get()) {
      case LEFT:
        left = true;
        break;
      case RIGHT:
        left = false;
        break;
      case RANDOM:
        left = random.nextBoolean();
        break;
      default:
        int side =
            DisplaceBehavior.voidSide(
                target.posX,
                target.posY,
                target.posZ,
                baseYaw,
                angle.get().floatValue(),
                (x, y, z) -> {
                  BlockPos pos = new BlockPos(x, y, z);
                  // Unknown terrain cannot establish a void direction.
                  return !mc().theWorld.isBlockLoaded(pos)
                      || mc().theWorld.getBlockState(pos).getBlock().getMaterial() != Material.air;
                });
        if (side == -1) return Float.NaN;
        left = side == 0;
    }
    return DisplaceBehavior.offset(left, angle.get().floatValue(), random.nextFloat());
  }

  private void submit() {
    PlayerRotationAccess player = (PlayerRotationAccess) mc().thePlayer;
    Rotation origin =
        Rotation.server(player.perihelion$reportedYaw(), player.perihelion$reportedPitch());
    Rotation previous = aim == null ? runtime.controls().rotation().orElse(origin) : aim;
    // Yuri requests speed 10 (180 degrees per tick), then applies its sensitivity patch.
    aim = RotationDynamics.smooth(mc(), previous, Rotation.server(heldYaw, heldPitch), 10);
    rotation.setForTicks(aim, 1);
  }

  private boolean ownsAim() {
    return hold.overriding()
        && rotation != null
        && aim != null
        && rotation.isWinning()
        && rotation.value().orElse(null) == aim;
  }

  private void attack(PlayerAttackEvent event) {
    if (!hold.armed() || event.target() != hitTarget || !validSession() || !validHeldTarget())
      return;
    MinecraftControls controls = HookBridge.gameControls();
    if (controls == null
        || !rotation.isWinning()
        || !(controls.isPreviousRotationApplied(rotation) || controls.isRotationApplied(rotation)))
      return;
    if (renderArrow.get()) setArrow(hitTarget, heldYaw);
    hold.attacked();
  }

  private void reset() {
    hold.reset();
    manualArmed = false;
    manualAge = 0;
    hitTarget = null;
    aim = null;
    if (rotation != null) rotation.clear();
  }

  private void setArrow(EntityLivingBase entity, float yaw) {
    double radians = Math.toRadians(yaw);
    dirX = -Math.sin(radians);
    dirZ = Math.cos(radians);
    arrowX = entity.posX + dirX * .8;
    arrowY = entity.posY + 1.2;
    arrowZ = entity.posZ + dirZ * .8;
    arrowAt = now();
    arrow = true;
  }

  private void render() {
    if (!arrow || !renderArrow.get() || mc().thePlayer == null) return;
    DisplaceBehavior.ArrowFrame frame = DisplaceBehavior.ArrowFrame.at(now() - arrowAt);
    if (frame == null) {
      arrow = false;
      return;
    }
    try (RenderState ignored = renderState.open()) {
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GlStateManager.pushMatrix();
      GlStateManager.disableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
      GlStateManager.disableCull();
      GlStateManager.depthMask(false);
      GlStateManager.disableDepth();
      GlStateManager.translate(
          arrowX - mc().getRenderManager().viewerPosX + dirX * frame.distance,
          arrowY - mc().getRenderManager().viewerPosY + frame.bob,
          arrowZ - mc().getRenderManager().viewerPosZ + dirZ * frame.distance);
      GlStateManager.rotate((float) Math.toDegrees(Math.atan2(-dirZ, dirX)), 0, 1, 0);
      GlStateManager.pushMatrix();
      GlStateManager.translate(.08 * .48, -.08 * .24, -.02);
      color((int) (140 * frame.alpha) << 24 | 0x05081A);
      shaft(1.01, GL11.GL_QUADS);
      head(1.01, GL11.GL_TRIANGLES);
      GlStateManager.popMatrix();
      color((int) (225 * frame.alpha) << 24 | 0xFFFFFF);
      shaft(1, GL11.GL_QUADS);
      head(1, GL11.GL_TRIANGLES);
      GL11.glEnable(GL11.GL_LINE_SMOOTH);
      GL11.glLineWidth(2);
      color((int) (255 * frame.alpha) << 24 | 0xF6F6F6);
      shaft(1, GL11.GL_LINE_LOOP);
      head(1, GL11.GL_LINE_LOOP);
      GlStateManager.popMatrix();
    }
  }

  private static void shaft(double scale, int mode) {
    GL11.glBegin(mode);
    GL11.glVertex3d(-.2784 * scale, -.0816 * scale, 0);
    GL11.glVertex3d(0, -.0288 * scale, 0);
    GL11.glVertex3d(0, .0288 * scale, 0);
    GL11.glVertex3d(-.2784 * scale, .0816 * scale, 0);
    GL11.glEnd();
  }

  private static void head(double scale, int mode) {
    GL11.glBegin(mode);
    GL11.glVertex3d(0, -.144 * scale, 0);
    GL11.glVertex3d(.288 * scale, 0, 0);
    GL11.glVertex3d(0, .144 * scale, 0);
    GL11.glEnd();
  }

  private static void color(int argb) {
    GlStateManager.color(
        (argb >> 16 & 255) / 255f,
        (argb >> 8 & 255) / 255f,
        (argb & 255) / 255f,
        (argb >>> 24) / 255f);
  }
}
