package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** B4 target-box math using native Friends/AntiBot ownership rather than donor globals. */
final class B4CombatTargets {
  private B4CombatTargets() {}

  static boolean eligible(GameplayModule module, EntityLivingBase target) {
    Minecraft mc = module.mc();
    if (target == mc.thePlayer || target.deathTime != 0) return false;
    if (target instanceof EntityPlayer) {
      TargetingModules.Friends friends =
          (TargetingModules.Friends) module.runtime.modules().require("friends").module();
      TargetingModules.AntiBot bots =
          (TargetingModules.AntiBot) module.runtime.modules().require("antibot").module();
      return !friends.friend(target.getName()) && !bots.bot((EntityPlayer) target);
    }
    return target instanceof EntityMob
        || target instanceof EntityGolem
        || target instanceof EntitySlime;
  }

  static boolean sameTeam(Minecraft mc, EntityLivingBase target) {
    return B4GameContext.sameTeam(mc, target);
  }

  static boolean rayHits(GameplayModule module, EntityLivingBase target, double range) {
    Minecraft mc = module.mc();
    if (!target.canBeCollidedWith()) return false;
    Vec3 eyes = mc.thePlayer.getPositionEyes(1);
    float border = target.getCollisionBorderSize();
    AxisAlignedBB box = target.getEntityBoundingBox().expand(border, border, border);
    perihelion.core.control.Rotation rotation = module.runtime.controls().rotation().orElse(null);
    float baseYaw = rotation == null ? mc.thePlayer.rotationYaw : rotation.yaw();
    float basePitch = rotation == null ? mc.thePlayer.rotationPitch : rotation.pitch();
    Vec3 direction =
        direction(
            eyes, box, baseYaw, basePitch, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    return box.calculateIntercept(
            eyes,
            eyes.addVector(
                direction.xCoord * range, direction.yCoord * range, direction.zCoord * range))
        != null;
  }

  static Vec3 direction(
      Vec3 eyes,
      AxisAlignedBB box,
      float baseYaw,
      float basePitch,
      float viewYaw,
      float viewPitch) {
    float[] angle = angles(eyes, box, baseYaw, basePitch, viewYaw, viewPitch);
    return vector(angle[0], angle[1]);
  }

  static float[] angles(
      Vec3 eyes,
      AxisAlignedBB box,
      float baseYaw,
      float basePitch,
      float viewYaw,
      float viewPitch) {
    if (box.isVecInside(eyes)) return new float[] {viewYaw, viewPitch};
    double cx = (box.minX + box.maxX) / 2,
        cy = box.minY + (box.maxY - box.minY) * .825,
        cz = (box.minZ + box.maxZ) / 2;
    // B4 Settings/Aim closest point default = 90%; not the geometrical nearest point.
    Vec3 point =
        new Vec3(
            cx + (MathHelper.clamp_double(eyes.xCoord, box.minX + .03, box.maxX - .03) - cx) * .9,
            cy + (MathHelper.clamp_double(eyes.yCoord, box.minY + .03, box.maxY - .03) - cy) * .9,
            cz + (MathHelper.clamp_double(eyes.zCoord, box.minZ + .03, box.maxZ - .03) - cz) * .9);
    double dx = point.xCoord - eyes.xCoord,
        dy = point.yCoord - eyes.yCoord,
        dz = point.zCoord - eyes.zCoord;
    float yaw = (float) (Math.atan2(dz, dx) * (double) 57.29578f) - 90.0f;
    float pitch = (float) (-(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (double) 57.29578f));
    yaw = baseYaw + MathHelper.wrapAngleTo180_float(yaw - baseYaw);
    pitch =
        MathHelper.clamp_float(
            basePitch + MathHelper.wrapAngleTo180_float(pitch - basePitch), -90, 90);
    return new float[] {yaw, pitch};
  }

  static Vec3 vector(float yaw, float pitch) {
    float cosYaw = MathHelper.cos((yaw *= -((float) Math.PI / 180)) - (float) Math.PI);
    float sinYaw = MathHelper.sin(yaw - (float) Math.PI);
    float cosPitch = -MathHelper.cos(pitch *= -((float) Math.PI / 180));
    float sinPitch = MathHelper.sin(pitch);
    return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
  }
}
