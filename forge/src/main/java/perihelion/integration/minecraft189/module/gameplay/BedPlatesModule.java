package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.util.*;
import perihelion.core.event.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.render.*;

final class BedPlatesModule extends GameplayModule {
  private final Setting<Integer> range, height, layers, budget;
  private final Setting<Boolean> distance;

  BedPlatesModule(ClientRuntime r) {
    super(
        r,
        "bedplates",
        "Bed Plates",
        "Show nearby bed defense materials with incremental loaded-world scanning.",
        ModuleCategory.RENDER);
    range = integer("range", "Range", 24, 4, 48);
    height = integer("vertical-range", "Vertical Range", 12, 2, 32);
    layers = integer("layers", "Layers", 2, 1, 4);
    budget = integer("scan-budget", "Blocks Per Tick", 1024, 128, 4096);
    distance = bool("show-distance", "Show Distance", true);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    ScanCursor scan = new ScanCursor(range.get(), height.get());
    BlockPos[] origin = {mc().thePlayer.getPosition()};
    Map<BlockPos, String> beds = new LinkedHashMap<>();
    Map<BlockPos, double[]> projected = new LinkedHashMap<>();
    VisualDraw.Projection projection = new VisualDraw.Projection();
    RenderState state = new RenderState();
    Runnable reset =
        () -> {
          scan.reset(range.get(), height.get());
          origin[0] = mc().thePlayer.getPosition();
          beds.clear();
          projected.clear();
        };
    c.observe(range, (s, before, after) -> reset.run());
    c.observe(height, (s, before, after) -> reset.run());
    c.observe(layers, (s, before, after) -> reset.run());
    c.listen(
        PlayerControlEvent.class,
        e -> {
          if (e != PlayerControlEvent.BEFORE_INPUT) return;
          if (origin[0].distanceSq(mc().thePlayer.getPosition()) > 64) reset.run();
          beds.keySet()
              .removeIf(
                  pos ->
                      !mc().theWorld.isBlockLoaded(pos)
                          || !(mc().theWorld.getBlockState(pos).getBlock() instanceof BlockBed)
                          || mc().thePlayer.getDistanceSq(pos)
                              > range.get() * range.get() + height.get() * height.get());
          int found = 0;
          for (int i = 0; i < budget.get() && found < 4; i++) {
            BlockPos pos = origin[0].add(scan.x(), scan.centeredY(), scan.z());
            scan.advance();
            if (pos.getY() < 0 || pos.getY() >= 256 || !mc().theWorld.isBlockLoaded(pos)) continue;
            IBlockState block = mc().theWorld.getBlockState(pos);
            if (!(block.getBlock() instanceof BlockBed)
                || block.getValue(BlockBed.PART) != BlockBed.EnumPartType.HEAD) continue;
            beds.put(pos, materials(pos, block));
            found++;
            if (beds.size() > 128) beds.remove(beds.keySet().iterator().next());
          }
        });
    c.listen(
        WorldRenderEvent.class,
        e -> {
          projected.clear();
          projection.capture(mc());
          List<BlockPos> nearby = new ArrayList<>(beds.keySet());
          nearby.sort(Comparator.comparingDouble(mc().thePlayer::getDistanceSq));
          for (BlockPos pos : nearby) {
            double[] point = projection.point(new Vec3(pos).addVector(.5, 1.25, .5));
            if (point != null) projected.put(pos, point);
            if (projected.size() >= 32) break;
          }
        });
    c.listen(
        HudRenderEvent.class,
        e -> {
          try (RenderState ignored = state.open()) {
            for (Map.Entry<BlockPos, double[]> entry : projected.entrySet()) {
              double[] point = entry.getValue();
              if (point[0] < 0
                  || point[0] > e.scaledWidth()
                  || point[1] < 0
                  || point[1] > e.scaledHeight()) continue;
              String label = beds.get(entry.getKey());
              if (label == null) continue;
              if (distance.get())
                label +=
                    String.format(
                        Locale.ROOT,
                        "  %.0fm",
                        Math.sqrt(mc().thePlayer.getDistanceSq(entry.getKey())));
              double half = mc().fontRendererObj.getStringWidth(label) / 2.;
              RenderStyle.lines(true, 1, 0xa0000000);
              VisualDraw.rect(
                  point[0] - half - 3, point[1] - 2, point[0] + half + 3, point[1] + 10);
              RenderStyle.text();
              mc().fontRendererObj
                  .drawStringWithShadow(label, (float) (point[0] - half), (float) point[1], -1);
            }
          }
        });
  }

  private String materials(BlockPos head, IBlockState bed) {
    BlockPos foot = head.offset(bed.getValue(BlockBed.FACING).getOpposite());
    Set<String> names = new LinkedHashSet<>();
    int radius = layers.get();
    for (BlockPos center : new BlockPos[] {head, foot})
      for (int x = -radius; x <= radius; x++)
        for (int y = 0; y <= radius; y++)
          for (int z = -radius; z <= radius; z++) {
            BlockPos pos = center.add(x, y, z);
            if (!mc().theWorld.isBlockLoaded(pos)) continue;
            IBlockState state = mc().theWorld.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof BlockBed
                || block.getMaterial().isLiquid()
                || block.isReplaceable(mc().theWorld, pos)) continue;
            Item item = Item.getItemFromBlock(block);
            if (item != null && names.size() < 8)
              names.add(new ItemStack(item, 1, block.damageDropped(state)).getDisplayName());
          }
    return names.isEmpty() ? "Uncovered" : String.join(", ", names);
  }
}
