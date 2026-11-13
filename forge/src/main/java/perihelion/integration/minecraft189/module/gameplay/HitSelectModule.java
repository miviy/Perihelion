// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

/** Yuri 1.3.1 counter-hit and trade prediction with native lifecycle subscriptions. */
final class HitSelectModule extends GameplayModule {
  private static final int PREDICTION_TICKS = 2;
  private final AuraModule aura;
  private final Setting<Double> range, searchRange, hurtTime, tradeTimeout, tradeHurtTime;
  private final Setting<Boolean> counterHit;
  private EntityLivingBase target;
  private boolean allowClick = true;
  private int trackedHurt;
  private double lastDamage;
  private long lastHurtAt;

  HitSelectModule(ClientRuntime r, AuraModule aura) {
    super(
        r,
        "hitselect",
        "Hit Select",
        "Gate attacks using predicted counter hits and trades.",
        ModuleCategory.COMBAT);
    this.aura = aura;
    range = number("active-range", "Active Range", 3, 3, 6, .1);
    searchRange = number("search-range", "Target Search Range", 3.5, 3, 6, .1);
    hurtTime = number("on-hurt-time", "On Hurt Time", 3, 0, 10, 1);
    tradeTimeout = number("trade-timeout", "Trade Timeout", 5, 0, 10, 1);
    tradeHurtTime = number("trade-hurt-time", "Trade Hurt Time", 2, -5, 10, 1);
    counterHit = bool("counter-hit", "Counter Hit", true);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    reset();
    c.onCleanup(this::reset);
    c.listen(
        ClientTickEvent.class,
        e -> {
          if (e == ClientTickEvent.START) tick();
        });
    c.listen(PlayerAttackEvent.class, 90, false, this::attack);
  }

  private void tick() {
    if (mc().thePlayer == null || mc().theWorld == null) {
      return;
    }

    if (aura.target() instanceof EntityLivingBase) {
      target = (EntityLivingBase) aura.target();
    }

    if (target != null
        && (target.isDead
            || boxDistance(mc().thePlayer.getPositionEyes(1.0f), target.getEntityBoundingBox())
                > searchRange.get())) {
      target = null;
    }

    if (mc().thePlayer.hurtTime != 0) {
      lastHurtAt = now();
    }

    if (target == null) {
      allowClick = true;
      return;
    }

    if (target.hurtTime == 0) {
      trackedHurt--;
    } else {
      trackedHurt = target.hurtTime;
    }

    allowClick = evaluate();
  }

  private void attack(PlayerAttackEvent event) {
    if (mc().thePlayer == null || mc().theWorld == null) {
      return;
    }

    Entity entity = aura.target();
    if (entity == null && mc().objectMouseOver != null) {
      entity = mc().objectMouseOver.entityHit;
    }
    if (!(entity instanceof EntityLivingBase)) {
      return;
    }

    target = (EntityLivingBase) entity;

    double damage = currentDamage();

    if (allowClick) {
      lastDamage = damage;
      return;
    }

    if (damage > lastDamage) {
      lastDamage = damage;
    } else {
      event.cancel();
    }
  }

  private double currentDamage() {
    float base =
        (float)
            mc().thePlayer
                .getEntityAttribute(SharedMonsterAttributes.attackDamage)
                .getAttributeValue();
    float bonus =
        EnchantmentHelper.getModifierForCreature(
            mc().thePlayer.getHeldItem(), target.getCreatureAttribute());
    boolean crit =
        mc().thePlayer.fallDistance > 0.0f
            && !mc().thePlayer.onGround
            && !mc().thePlayer.isOnLadder()
            && !mc().thePlayer.isInWater()
            && !mc().thePlayer.isPotionActive(Potion.blindness)
            && mc().thePlayer.ridingEntity == null;

    if (crit && base > 0.0f) {
      base *= 1.5f;
    }

    return base + bonus;
  }

  private boolean evaluate() {
    EntityPlayerSP self = mc().thePlayer;
    Vec3 eyes = self.getPositionEyes(1.0f);

    PlayerSimulation sim = simulation();
    for (int i = 0; i < PREDICTION_TICKS; i++) {
      sim.tick();
    }

    double distance = boxDistance(eyes, target.getEntityBoundingBox());
    boolean nearGround =
        target instanceof EntityPlayer && (target.onGround || groundDistance(target) < 0.7);

    boolean targetHittable = target.hurtTime <= hurtTime.get().intValue();
    boolean selfHittable =
        nearGround
            && boxDistance(target.getPositionEyes(1.0f), self.getEntityBoundingBox()) < 3.0
            && hittableBy(target);

    if (counterHit.get()) {
      double dx = target.posX - target.lastTickPosX;
      double dy = target.posY - target.lastTickPosY;
      double dz = target.posZ - target.lastTickPosZ;
      AxisAlignedBB predictedBox = target.getEntityBoundingBox().offset(dx, dy, dz);

      if (boxDistance(eyes, predictedBox) < range.get()
          && distance > 1.0
          && !target.isSwingInProgress
          && movingTowards(target, self)
          && facingDifference(target) < 45.0f
          && !falling()
          && nearGround
          && target.hurtTime == 0) {
        return false;
      }
    }

    Vec3 simEyes = sim.getPos().addVector(0.0, self.getEyeHeight(), 0.0);
    boolean midTrade =
        rayDistance(simEyes, self.getLook(1.0f), target.getEntityBoundingBox()) <= range.get()
            && self.getHealth() >= target.getHealth()
            && !nearGround
            && distance < range.get() - 0.4;

    boolean recentlyHurt = now() - lastHurtAt < tradeTimeout.get().intValue() * 50L;

    if (recentlyHurt && midTrade) {
      int ticksToGround = ticksToGround();
      int low = tradeHurtTime.get().intValue();
      int high = hurtTime.get().intValue();
      int threshold = (int) (low + (high - low) * (ticksToGround / 5.0f));

      if (trackedHurt > threshold && trackedHurt <= high) {
        return false;
      }
    }

    boolean targetOnFire = !target.isPotionActive(Potion.fireResistance) && target.isBurning();

    return targetHittable || recentlyHurt || selfHittable || targetOnFire;
  }

  private int ticksToGround() {
    PlayerSimulation sim = simulation();

    for (int i = 0; i < 5; i++) {
      sim.tick();
      if (sim.onGround) {
        return i + 1;
      }
    }

    return 5;
  }

  private boolean falling() {
    if (mc().thePlayer.fallDistance > 0.0f) {
      return true;
    }

    PlayerSimulation sim = simulation();
    sim.tick();
    return sim.fallDistance > 0.0f;
  }

  private boolean hittableBy(EntityLivingBase attacker) {
    EntityPlayerSP self = mc().thePlayer;
    double pred = ping(attacker) / 50.0;

    Vec3 from =
        attacker
            .getPositionEyes(1.0f)
            .addVector(
                (attacker.posX - attacker.lastTickPosX) * pred,
                (attacker.posY - attacker.lastTickPosY) * pred,
                (attacker.posZ - attacker.lastTickPosZ) * pred);
    Vec3 direction = attacker.getLook(1.0f);
    Vec3 to =
        from.addVector(direction.xCoord * 3.0, direction.yCoord * 3.0, direction.zCoord * 3.0);

    AxisAlignedBB predictedBox =
        self.getEntityBoundingBox()
            .contract(0.01, 0.01, 0.01)
            .offset(
                (self.posX - self.lastTickPosX) * pred,
                (self.posY - self.lastTickPosY) * pred,
                (self.posZ - self.lastTickPosZ) * pred);

    MovingObjectPosition hit = predictedBox.calculateIntercept(from, to);

    return hit != null
        && hit.hitVec != null
        && predictedBox.expand(0.06, 0.06, 0.06).isVecInside(hit.hitVec);
  }

  private PlayerSimulation simulation() {
    MovementYawEvent heading = new MovementYawEvent(mc().thePlayer.rotationYaw);
    perihelion.integration.minecraft189.HookBridge.clientEvent(heading);
    return PlayerSimulation.fromClientPlayer(mc(), heading.yaw());
  }

  private int ping(Entity entity) {
    if (entity instanceof EntityPlayer && mc().getNetHandler() != null) {
      NetworkPlayerInfo info = mc().getNetHandler().getPlayerInfo(entity.getUniqueID());
      return info == null ? 50 : info.getResponseTime();
    }

    return 50;
  }

  private double rayDistance(Vec3 eyes, Vec3 look, AxisAlignedBB box) {
    if (box.isVecInside(eyes)) {
      return 0.0;
    }

    Vec3 end = eyes.addVector(look.xCoord * 64.0, look.yCoord * 64.0, look.zCoord * 64.0);
    MovingObjectPosition hit = box.calculateIntercept(eyes, end);

    return hit == null ? Double.MAX_VALUE : eyes.distanceTo(hit.hitVec);
  }

  private double boxDistance(Vec3 from, AxisAlignedBB box) {
    double dx = Math.max(Math.max(box.minX - from.xCoord, 0.0), from.xCoord - box.maxX);
    double dy = Math.max(Math.max(box.minY - from.yCoord, 0.0), from.yCoord - box.maxY);
    double dz = Math.max(Math.max(box.minZ - from.zCoord, 0.0), from.zCoord - box.maxZ);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  private double groundDistance(Entity entity) {
    AxisAlignedBB box = entity.getEntityBoundingBox();

    for (double d = 0.05; d <= 6.0; d += 0.1) {
      if (!mc().theWorld.getCollidingBoundingBoxes(entity, box.offset(0.0, -d, 0.0)).isEmpty()) {
        return d;
      }
    }

    return 6.0;
  }

  private float facingDifference(EntityLivingBase source) {
    Vec3 from = source.getPositionEyes(1.0f);
    AxisAlignedBB box = mc().thePlayer.getEntityBoundingBox();
    double dx = (box.minX + box.maxX) / 2.0 - from.xCoord;
    double dy = (box.minY + box.maxY) / 2.0 - from.yCoord;
    double dz = (box.minZ + box.maxZ) / 2.0 - from.zCoord;

    float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
    float pitch = (float) -(Math.atan2(dy, Math.hypot(dx, dz)) * 180.0 / Math.PI);

    float yawDiff = MathHelper.wrapAngleTo180_float(yaw - source.rotationYawHead);
    float pitchDiff = pitch - source.rotationPitch;

    return (float) Math.hypot(yawDiff, pitchDiff);
  }

  private boolean movingTowards(EntityLivingBase source, EntityLivingBase destination) {
    double mx = source.posX - source.lastTickPosX;
    double mz = source.posZ - source.lastTickPosZ;
    double movementLength = Math.sqrt(mx * mx + mz * mz);

    if (movementLength == 0.0) {
      return false;
    }

    mx /= movementLength;
    mz /= movementLength;

    double tx = destination.posX - source.posX;
    double tz = destination.posZ - source.posZ;
    double targetLength = Math.sqrt(tx * tx + tz * tz);

    if (targetLength == 0.0) {
      return false;
    }

    tx /= targetLength;
    tz /= targetLength;

    return mx * tx + mz * tz >= Math.cos(Math.toRadians(60.0));
  }

  private void reset() {
    target = null;
    allowClick = true;
    trackedHurt = 0;
    lastDamage = 0.0;
    lastHurtAt = 0L;
  }
}
