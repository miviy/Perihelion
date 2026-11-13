package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjgl.input.Mouse;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.ClientPacketEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** B4 AutoBlock's default non-lag profile, exposed as one existing Block Hit mode. */
final class B4ManualBlock {
  B4ManualBlock(
      GhostCombatModules.BlockHit module,
      ModuleContext context,
      Setting<GhostCombatModules.BlockHit.Mode> mode,
      Setting<Double> range) {
    B4GuardState state = new B4GuardState();
    ControlRequest<Boolean> use = context.controls().key(InputKey.USE_ITEM, 45);
    context.observe(
        mode,
        (s, before, after) -> {
          state.reset();
          use.clear();
        });
    context.listen(
        ClientPacketEvent.class,
        event -> {
          if (mode.get() == GhostCombatModules.BlockHit.Mode.B4_GUARD
              && event.packet() instanceof S12PacketEntityVelocity
              && ((S12PacketEntityVelocity) event.packet()).getEntityID()
                  == module.mc().thePlayer.getEntityId()
              && ((S12PacketEntityVelocity) event.packet()).getMotionY() > 0
              && ((S12PacketEntityVelocity) event.packet()).getMotionY() < 4500
              && state.knockback()) use.clear();
        });
    context.listen(
        PlayerControlEvent.class,
        event -> {
          if (event != PlayerControlEvent.AFTER_MOTION
              || mode.get() != GhostCombatModules.BlockHit.Mode.B4_GUARD) return;
          net.minecraft.client.Minecraft mc = module.mc();
          AuraModule aura = (AuraModule) module.runtime.modules().require("aura").module();
          Object auraBlock = aura.settings().get("auto-block").get();
          boolean packetAura =
              module.active("aura")
                  && aura.target() != null
                  && (auraBlock == AuraModule.AutoBlock.VANILLA
                      || auraBlock == AuraModule.AutoBlock.NCP
                      || auraBlock == AuraModule.AutoBlock.HYPIXEL);
          boolean block =
              mc.currentScreen == null
                  && mc.thePlayer.getHeldItem() != null
                  && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword
                  && !packetAura
                  && mc.thePlayer.hurtTime <= 2
                  && enemy(module, range.get());
          boolean manual = Mouse.isCreated() && Mouse.isButtonDown(1);
          B4GuardState.Action action = state.update(block, manual);
          if (action == B4GuardState.Action.PRESS) {
            use.set(true);
            // B4 KeyUtils.press queues a vanilla press as well as holding the key. A hold
            // alone is gated by rightClickDelayTimer and can outlive this three-tick guard.
            if (use.isWinning())
              net.minecraft.client.settings.KeyBinding.onTick(
                  mc.gameSettings.keyBindUseItem.getKeyCode());
          } else if (action == B4GuardState.Action.RELEASE) use.clear();
        });
  }

  private boolean enemy(GameplayModule module, double range) {
    net.minecraft.client.Minecraft mc = module.mc();
    double broad = (range + 1.2) * (range + 1.2);
    for (Entity entity : mc.theWorld.loadedEntityList) {
      if (!(entity instanceof EntityLivingBase)) continue;
      EntityLivingBase target = (EntityLivingBase) entity;
      if (!B4CombatTargets.eligible(module, target)
          || mc.thePlayer.getDistanceSqToEntity(target) > broad
          || !B4CombatTargets.rayHits(module, target, range)
          || B4CombatTargets.sameTeam(mc, target)) continue;
      return true;
    }
    return false;
  }
}
