package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.EntityStateAccess;
import perihelion.integration.minecraft189.event.ClientPacketEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Combat jump input without cancelling velocity, rewriting motion, or submitting attacks. */
final class JumpResetModule extends GameplayModule {
  enum Mode {
    STANDARD,
    B4
  }

  private static final String[] MOVEMENT_OWNERS = {
    "scaffold", "clutch", "speed", "flight", "longjump", "stasis", "freecam"
  };
  private final CombatModules.Velocity velocity;
  private final Setting<Double> chance;
  private final Setting<Boolean> sprint, fire, nearby;
  private final Setting<Mode> mode;

  JumpResetModule(ClientRuntime runtime, CombatModules.Velocity velocity) {
    super(
        runtime,
        "jumpreset",
        "Jump Reset",
        "Jump once on grounded combat knockback using normal input. Works without Aura.",
        ModuleCategory.COMBAT);
    this.velocity = velocity;
    mode = mode("mode", "Mode", Mode.STANDARD, Mode.class);
    chance =
        setting(
            Setting.decimal(
                "chance",
                "Chance",
                "Chance per knockback packet, in percent.",
                100,
                0,
                100,
                1,
                guard));
    sprint =
        bool("only-sprinting", "Only Sprinting", "Require sprinting for the jump attempt.", false);
    fire = bool("ignore-on-fire", "Ignore On Fire", "Skip jump attempts while burning.", true);
    nearby =
        bool(
            "require-nearby-player",
            "Nearby Player",
            "Require another living player within 4.5 blocks.",
            true);
    sprint.visibleWhen(() -> mode.get() != Mode.B4);
    fire.visibleWhen(() -> mode.get() != Mode.B4);
    nearby.visibleWhen(() -> mode.get() != Mode.B4);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    KnockbackJumpState state = new KnockbackJumpState();
    ControlRequest<Boolean> jump = c.controls().key(InputKey.JUMP, 50);
    Runnable reset =
        () -> {
          state.clear();
          jump.clear();
        };
    c.observe(chance, (s, before, after) -> reset.run());
    c.observe(mode, (s, before, after) -> reset.run());
    c.observe(sprint, (s, before, after) -> reset.run());
    c.observe(fire, (s, before, after) -> reset.run());
    c.observe(nearby, (s, before, after) -> reset.run());
    c.observe(velocity.knockbackMode(), (s, before, after) -> reset.run());
    boolean[] explosion = {false};
    double[] knockback = {0, 0};
    c.observe(mode, (s, before, after) -> explosion[0] = false);
    c.listen(
        ClientPacketEvent.class,
        e -> {
          if (mode.get() == Mode.B4 && e.packet() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) e.packet();
            if (mc().thePlayer.getDistanceSq(packet.getX(), packet.getY(), packet.getZ()) <= 36)
              explosion[0] = true;
            return;
          }
          if (!(e.packet() instanceof S12PacketEntityVelocity)) return;
          S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.packet();
          if (packet.getEntityID() != mc().thePlayer.getEntityId()) return;
          if (mode.get() == Mode.B4) {
            // B4 defers grounded/sprint/chance checks until the input opportunity.
            if (explosion[0] || !B4JumpResetPolicy.acceptsVerticalVelocity(packet.getMotionY()))
              return;
            knockback[0] = packet.getMotionX() / 8000.0;
            knockback[1] = packet.getMotionZ() / 8000.0;
            state.receive(now(), true);
          } else {
            state.receive(
                now(), candidate(packet) && eligible() && Math.random() * 100 < chance.get());
          }
        });
    c.listen(
        PlayerControlEvent.class,
        e -> {
          if (e != PlayerControlEvent.BEFORE_INPUT) return;
          jump.clear();
          boolean pending = state.consume(now());
          if (mode.get() == Mode.B4) {
            if (pending
                && eligible()
                && mc().thePlayer.isSprinting()
                && (chance.get() == 100 || Math.random() < chance.get() / 100)
                && B4JumpResetPolicy.helps(knockback[0], knockback[1], movementYaw()))
              jump.setForTicks(true, 1);
            explosion[0] = false;
          } else if (pending && eligible()) jump.setForTicks(true, 1);
        });
  }

  private float movementYaw() {
    perihelion.integration.minecraft189.event.MovementYawEvent event =
        new perihelion.integration.minecraft189.event.MovementYawEvent(
            mc().thePlayer.rotationYaw, true);
    perihelion.integration.minecraft189.HookBridge.clientEvent(event);
    return event.yaw();
  }

  private boolean eligible() {
    if (active("velocity") && !velocity.acceptsJumpReset()) return false;
    if (!eligible(this, mode.get() != Mode.B4 && sprint.get(), mode.get() != Mode.B4 && fire.get()))
      return false;
    if (mode.get() == Mode.B4 || !nearby.get()) return true;
    for (EntityPlayer other : mc().theWorld.playerEntities)
      if (other != mc().thePlayer
          && !other.isDead
          && other.getHealth() > 0
          && mc().thePlayer.getDistanceToEntity(other) <= 4.5) return true;
    return false;
  }

  static boolean candidate(S12PacketEntityVelocity packet) {
    return packet.getMotionY() > 0 && (packet.getMotionX() != 0 || packet.getMotionZ() != 0);
  }

  static boolean eligible(GameplayModule module, boolean onlySprint, boolean ignoreFire) {
    EntityPlayerSP p = module.mc().thePlayer;
    if (p == null
        || module.mc().currentScreen != null
        || p.isDead
        || p.getHealth() <= 0
        || !p.onGround
        || p.isRiding()
        || p.isSneaking()
        || p.isUsingItem()
        || p.capabilities.isFlying
        || GameActions.liquid(p)
        || ((EntityStateAccess) p).perihelion$inWeb()
        || p.isOnLadder()
        || p.isPotionActive(Potion.jump)
        || onlySprint && !p.isSprinting()
        || ignoreFire && p.isBurning()) return false;
    for (String id : MOVEMENT_OWNERS) if (module.active(id)) return false;
    return true;
  }
}
