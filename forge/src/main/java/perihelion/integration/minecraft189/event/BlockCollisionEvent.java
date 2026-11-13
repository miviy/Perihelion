package perihelion.integration.minecraft189.event;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

public final class BlockCollisionEvent {
    private final Block block;
    private final BlockPos position;
    private AxisAlignedBB box;

    public BlockCollisionEvent(Block block, BlockPos position, AxisAlignedBB box) {
        this.block = block;
        this.position = position;
        this.box = box;
    }

    public Block block() {
        return block;
    }

    public BlockPos position() {
        return position;
    }

    public AxisAlignedBB box() {
        return box;
    }

    public void box(AxisAlignedBB value) {
        box = value;
    }
}
