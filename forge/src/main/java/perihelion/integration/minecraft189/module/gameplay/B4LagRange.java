package perihelion.integration.minecraft189.module.gameplay;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.Potion;
import perihelion.core.event.RenderTickEvent;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.network.PacketBufferScope;

/** B4's default outbound approach policy; native bounded snapshots own transport and replay. */
final class B4LagRange {
  private final CombatTimingModules.LagRange module;
  private final Setting<CombatTimingModules.LagRange.Mode> mode;
  private final B4LagRangeState state = new B4LagRangeState();
  private final PacketBufferScope buffer;
  private final Random random = new Random();
  private final AtomicLong releaseQueued = new AtomicLong(-1);
  private final Object captureState = new Object();
  private EntityPlayer target;
  private volatile int targetId, targetHurt;
  private volatile boolean flushNext;

  B4LagRange(CombatTimingModules.LagRange module, ModuleContext context,
      Setting<CombatTimingModules.LagRange.Mode> mode) {
    this.module = module; this.mode = mode;
    buffer = new PacketBufferScope(module.runtime, context,
        (packet, outbound) -> capture(packet, outbound, context), state::delay);
    context.observe(mode, (s, before, after) -> stop());
    context.onCleanup(() -> { synchronized (captureState) {
      state.stop(); releaseQueued.set(-1); target = null; flushNext = false;
    } });
    context.listen(PlayerControlEvent.class, event -> {
      if (event == PlayerControlEvent.BEFORE_INPUT && mode.get() == CombatTimingModules.LagRange.Mode.B4) tick();
    });
    context.listen(RenderTickEvent.class, event -> {
      if (event.phase() == RenderTickEvent.Phase.START && state.active()) buffer.flush(false);
    });
  }

  private boolean capture(Packet<?> packet, boolean outbound, ModuleContext context) {
    if (!outbound) return false;
    synchronized (captureState) {
    if (!state.active()) return false;
    // PacketBufferScope calls this only for its own eligible capture, while holding its
    // capture/order locks. Bind release authority here rather than to any deferred event
    // observed later. No per-thread storage survives packet serialization or owner faults.
    long session = state.session();
    if (!state.current(session)) return false;
    final boolean release;
    try { release = interaction(packet); }
    catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    if (release && releaseQueued.compareAndSet(-1, session)) {
      if (!context.deferForWorld(() -> {
        releaseQueued.compareAndSet(session, -1);
        if (state.current(session)) stop();
      })) releaseQueued.compareAndSet(session, -1);
    }
    return true;
    }
  }

  private void stop() {
    synchronized (captureState) {
      state.stop(); releaseQueued.set(-1); target = null; flushNext = false;
    }
    // Never hold captureState while acquiring the buffer lock: raw capture takes
    // the buffer lock before it takes captureState.
    buffer.flush(true);
  }

  private boolean allowed(boolean lagging) {
    Minecraft mc = module.mc();
    if (mc.thePlayer == null || mc.theWorld == null || mc.isSingleplayer()
        || ((ScaffoldModule) module.runtime.modules().require("scaffold").module()).buildingActive()
        || mc.thePlayer.capabilities.allowFlying || !mc.thePlayer.capabilities.allowEdit) return false;
    ItemStack stack = mc.thePlayer.getHeldItem();
    if (stack != null) {
      Item item = stack.getItem();
      if (item instanceof ItemFood || item instanceof ItemPotion && !ItemPotion.isSplash(stack.getMetadata())
          || item instanceof ItemBucket || item == Items.bow || item == Items.fire_charge || item == Items.fishing_rod) return false;
    }
    if (!lagging) {
      if (!mc.thePlayer.onGround && mc.thePlayer.hurtTime > 0 && mc.thePlayer.motionY > 0) return false;
      if (mc.thePlayer.movementInput.moveForward == 0 && mc.thePlayer.movementInput.moveStrafe == 0) return false;
      int forward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindBack.isKeyDown() ? 1 : 0);
      if (forward < 0) return false;
    }
    return true;
  }

  private void tick() {
    Minecraft mc = module.mc();
    long now = GameplayModule.now();
    if (!allowed(state.active())) { if (state.active()) stop(); return; }
    int speedLevel = mc.thePlayer.isPotionActive(Potion.moveSpeed) ? mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1 : 0;
    double range = B4LagRangeState.range(speedLevel);
    double speed = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
    if (state.active()) {
      boolean present = target != null && mc.theWorld.getEntityByID(target.getEntityId()) == target;
      double distance = present ? mc.thePlayer.getDistanceSqToEntity(target) : 0;
      double dot = present ? B4LagRangeState.directionDot(mc.thePlayer.motionX, mc.thePlayer.motionZ, target.posX - mc.thePlayer.posX, target.posZ - mc.thePlayer.posZ) : 0;
      if (state.shouldStop(now, present, distance, range, speed, dot)) stop();
      else if (present) { synchronized (captureState) { targetHurt = target.hurtTime; } }
      return;
    }
    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == mc.thePlayer || player.deathTime != 0 || player.isInvisible()) continue;
      double distance = mc.thePlayer.getDistanceSqToEntity(player);
      if (distance > range * range || !B4CombatTargets.eligible(module, player) || B4GameContext.sameTeam(mc, player)) continue;
      double dot = B4LagRangeState.directionDot(mc.thePlayer.motionX, mc.thePlayer.motionZ, player.posX - mc.thePlayer.posX, player.posZ - mc.thePlayer.posZ);
      if (!B4LagRangeState.approaches(speed, dot)) continue;
      synchronized (captureState) {
        target = player; targetId = player.getEntityId(); targetHurt = player.hurtTime;
        state.begin(now, distance, random.nextInt(25) - 12, random.nextInt(909) - 454);
      }
      return;
    }
  }

  private boolean interaction(Packet<?> packet) throws IOException {
    if (packet instanceof C01PacketChatMessage) flushNext = true;
    else if (packet instanceof C02PacketUseEntity) {
      C02PacketUseEntity attack = (C02PacketUseEntity) packet;
      if (attack.getAction() == C02PacketUseEntity.Action.ATTACK && (entityId(attack) != targetId || targetHurt <= 2)) flushNext = true;
    } else if (packet instanceof C07PacketPlayerDigging) {
      C07PacketPlayerDigging.Action action = ((C07PacketPlayerDigging) packet).getStatus();
      if (action == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK || action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
          || action == C07PacketPlayerDigging.Action.DROP_ITEM || action == C07PacketPlayerDigging.Action.DROP_ALL_ITEMS) flushNext = true;
    } else if (packet instanceof C08PacketPlayerBlockPlacement) {
      ItemStack stack = ((C08PacketPlayerBlockPlacement) packet).getStack();
      if (stack == null || !(stack.getItem() instanceof ItemSword)) flushNext = true;
    } else if (flushNext && packet instanceof C03PacketPlayer) { flushNext = false; return true; }
    return false;
  }

  private int entityId(C02PacketUseEntity packet) throws IOException {
    PacketBuffer bytes = new PacketBuffer(Unpooled.buffer(16));
    try { packet.writePacketData(bytes); return bytes.readVarIntFromBuffer(); }
    finally { bytes.release(); }
  }
}
