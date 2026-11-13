package perihelion.integration.minecraft189.module.gameplay;

import java.util.Locale;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.BlockPos;
import net.minecraft.util.StringUtils;
import net.minecraft.world.World;
import perihelion.core.event.EventOwner;
import perihelion.core.event.WorldContextEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.event.ClientPacketEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Runtime-owned match observation, independent of Breaker's enable/disable state. */
final class HomeBedTracker implements AutoCloseable {
  private static final int SCAN_BUDGET = 4096, RETRY_TICKS = 20;
  private final ClientRuntime runtime;
  private final EventOwner owner;
  private final HomeBedState state = new HomeBedState();
  private final ScanCursor scan = new ScanCursor(HomeBedState.RANGE, HomeBedState.RANGE);
  private World world;
  private long generation, ticks, announcementTick = Long.MIN_VALUE;
  private boolean ready;
  private int remaining, cooldown;
  private BlockPos candidate;
  private double nearest;

  HomeBedTracker(ClientRuntime runtime) {
    this.runtime = runtime;
    runtime.tasks().checkThread();
    generation = runtime.tasks().generation();
    owner = new EventOwner("home-bed-tracker", failure -> close());
    runtime.own(this);
    runtime.clientEvents().subscribe(owner, WorldContextEvent.class, event -> {
      generation = event.generation();
      ready = event.worldReady();
      announcementTick = Long.MIN_VALUE;
      resetMatch();
    });
    runtime.clientEvents().subscribe(owner, ClientPacketEvent.class, 1000, false, event -> {
      if (!current() || !(event.packet() instanceof S02PacketChat)) return;
      S02PacketChat packet = (S02PacketChat) event.packet();
      if (packet.getType() != 1) return;
      String text = StringUtils.stripControlCodes(
          packet.getChatComponent().getUnformattedText()).toLowerCase(Locale.ROOT);
      if (!text.contains("protect your bed")) return;
      // Repeated copies of the start announcement must not recenter a just-acquired home.
      if (announcementTick != Long.MIN_VALUE && ticks - announcementTick < RETRY_TICKS) return;
      announcementTick = ticks;
      resetMatch();
    });
    runtime.clientEvents().subscribe(owner, PlayerControlEvent.class, 1000, false, event -> {
      if (event == PlayerControlEvent.BEFORE_INPUT) tick();
    });
    owner.activate();
  }

  private boolean current() {
    return owner.isActive() && ready && runtime.isRunning() && runtime.worldReady()
        && generation == runtime.tasks().generation();
  }

  private void resetMatch() {
    state.reset();
    world = null;
    candidate = null;
    remaining = cooldown = 0;
  }

  private void tick() {
    ticks++;
    if (!current()) return;
    Minecraft game = Minecraft.getMinecraft();
    if (game.theWorld == null || game.thePlayer == null || game.thePlayer.isDead
        || game.thePlayer.isSpectator()) return;
    if (world != null && world != game.theWorld) resetMatch();
    world = game.theWorld;
    if (state.anchor() == null) state.anchor(position(game.thePlayer.getPosition()));
    if (state.acquired()) return;
    if (remaining == 0) {
      if (cooldown > 0) { cooldown--; return; }
      scan.reset(HomeBedState.RANGE, HomeBedState.RANGE);
      remaining = scan.volume();
      candidate = null;
      nearest = HomeBedState.RANGE * HomeBedState.RANGE + 1.;
    }
    HomeBedState.Position anchor = state.anchor();
    for (int work = 0; work < SCAN_BUDGET && remaining > 0; work++, remaining--) {
      int dx = scan.x(), dy = scan.centeredY(), dz = scan.z();
      scan.advance();
      double distance = dx * dx + dy * dy + dz * dz;
      if (distance > HomeBedState.RANGE * HomeBedState.RANGE || distance >= nearest) continue;
      BlockPos pos = new BlockPos(anchor.x + dx, anchor.y + dy, anchor.z + dz);
      if (head(pos) != null) { candidate = pos; nearest = distance; }
    }
    if (remaining == 0) {
      BlockPos head = candidate == null ? null : head(candidate);
      if (head != null) state.capture(position(candidate), position(head));
      candidate = null;
      cooldown = RETRY_TICKS;
    }
  }

  /** Only complete, coherent loaded beds can establish the match's home. */
  private BlockPos head(BlockPos foot) {
    if (foot.getY() < 0 || foot.getY() >= 256 || !world.isBlockLoaded(foot)) return null;
    IBlockState bed = world.getBlockState(foot);
    if (!(bed.getBlock() instanceof BlockBed)
        || bed.getValue(BlockBed.PART) != BlockBed.EnumPartType.FOOT) return null;
    BlockPos head = foot.offset(bed.getValue(BlockBed.FACING));
    if (!world.isBlockLoaded(head)) return null;
    IBlockState other = world.getBlockState(head);
    return other.getBlock() == bed.getBlock()
        && other.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD
        && other.getValue(BlockBed.FACING) == bed.getValue(BlockBed.FACING) ? head : null;
  }

  private static HomeBedState.Position position(BlockPos pos) {
    return new HomeBedState.Position(pos.getX(), pos.getY(), pos.getZ());
  }

  boolean acquired() { return current() && state.acquired(); }
  long revision() { return state.revision(); }
  boolean protects(BlockPos pos) { return acquired() && state.protects(position(pos)); }

  @Override public void close() {
    runtime.tasks().checkThread();
    owner.close();
    ready = false;
    resetMatch();
  }
}
