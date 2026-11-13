// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.awt.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.render.RenderState;

final class ArmorRatioModule extends GameplayModule {
  private final AuraModule aura;
  private final Setting<Double> size, height;
  private final Setting<Boolean> includeWeapon, outline, throughWalls;

  ArmorRatioModule(ClientRuntime r, AuraModule aura) {
    super(
        r,
        "armorratio",
        "Armor Ratio",
        "Compare equipment strength above the current target.",
        ModuleCategory.RENDER);
    this.aura = aura;
    size = number("size", "Size", .12, .05, .5, .01);
    height = number("height", "Height", .3, 0, 1.5, .05);
    includeWeapon = bool("include-weapon", "Include Weapon", true);
    outline = bool("outline", "Outline", true);
    throughWalls = bool("through-walls", "Through Walls", true);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    RenderState render = new RenderState();
    c.listen(
        WorldRenderEvent.class,
        e -> {
          EntityLivingBase target = aura.target();
          if (target == null || !target.isEntityAlive()) return;
          float pt = e.partialTicks();
          double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * pt;
          double y =
              target.lastTickPosY
                  + (target.posY - target.lastTickPosY) * pt
                  + target.height
                  + height.get();
          double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * pt;
          double diff = score(target) - score(mc().thePlayer);
          Color color =
              Math.abs(diff) < .01
                  ? new Color(255, 220, 40)
                  : diff < 0 ? new Color(60, 255, 90) : new Color(255, 60, 60);
          float radius =
              size.get().floatValue() * Math.max(1, mc().thePlayer.getDistanceToEntity(target) / 6);
          try (RenderState ignored = render.open()) {
            GlStateManager.pushMatrix();
            try {
              GlStateManager.translate(
                  x - mc().getRenderManager().viewerPosX,
                  y - mc().getRenderManager().viewerPosY,
                  z - mc().getRenderManager().viewerPosZ);
              GlStateManager.rotate(-mc().getRenderManager().playerViewY, 0, 1, 0);
              GlStateManager.rotate(
                  mc().getRenderManager().playerViewX,
                  mc().gameSettings.thirdPersonView == 2 ? -1 : 1,
                  0,
                  0);
              GlStateManager.disableTexture2D();
              GlStateManager.enableBlend();
              GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
              GlStateManager.disableLighting();
              GlStateManager.disableCull();
              GlStateManager.depthMask(false);
              if (throughWalls.get()) GlStateManager.disableDepth();
              if (outline.get()) drawDot(radius * 1.3f, Color.BLACK);
              drawDot(radius, color);
            } finally {
              GlStateManager.popMatrix();
            }
          }
        });
  }

  private void drawDot(float radius, Color color) {
    GlStateManager.color(
        color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1.0f);
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    GL11.glVertex2f(0.0f, 0.0f);
    for (int i = 0; i <= 40; i++) {
      double angle = i * Math.PI * 2.0 / 40.0;
      GL11.glVertex2f((float) (Math.cos(angle) * radius), (float) (Math.sin(angle) * radius));
    }
    GL11.glEnd();
  }

  private double score(EntityLivingBase entity) {
    double total = 0.0;

    for (int slot = 1; slot <= 4; slot++) {
      ItemStack stack = entity.getEquipmentInSlot(slot);
      if (stack == null || !(stack.getItem() instanceof ItemArmor)) continue;
      total += ((ItemArmor) stack.getItem()).damageReduceAmount;
      total += EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
    }

    if (includeWeapon.get()) {
      ItemStack held = entity.getHeldItem();
      if (held != null && held.getItem() instanceof ItemSword) {
        total += ((ItemSword) held.getItem()).getDamageVsEntity();
        total += EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, held) * 1.25;
      }
    }

    return total;
  }
}
