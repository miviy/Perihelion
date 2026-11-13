package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.Test;
import perihelion.integration.minecraft189.event.ClientSendFactEvent;

public class B4SendFactTest {
  @Test
  public void useSnapshotCannotBeChangedByTheSenderOrAnotherObserver() {
    ItemStack sent = new ItemStack(new Item(), 3);
    ClientSendFactEvent fact = ClientSendFactEvent.from(new C08PacketPlayerBlockPlacement(sent));
    assertEquals(ClientSendFactEvent.Action.USE_ITEM, fact.action());
    sent.stackSize = 0;
    assertEquals(3, fact.stack().stackSize);
    fact.stack().stackSize = 7;
    assertEquals(3, fact.stack().stackSize);
    assertFalse((Object) fact instanceof perihelion.core.event.CancellableEvent);
  }

  @Test
  public void walkingHasNoStackAndHeldChangesRemainSeparateObservedScalarFacts() {
    ClientSendFactEvent fact = ClientSendFactEvent.from(new C03PacketPlayer(true));
    assertEquals(ClientSendFactEvent.Action.WALK, fact.action());
    assertNull(fact.stack());
    assertEquals(
        ClientSendFactEvent.Action.SLOT,
        ClientSendFactEvent.from(new C09PacketHeldItemChange(1)).action());
  }

  @Test
  public void placementCarriesADefensiveStackSnapshotAndReleaseIsNotMining() {
    ItemStack stack = new ItemStack(new Item(), 4);
    ClientSendFactEvent place =
        ClientSendFactEvent.from(
            new C08PacketPlayerBlockPlacement(BlockPos.ORIGIN, 1, stack, .5f, 1, .5f));
    assertEquals(ClientSendFactEvent.Action.PLACE, place.action());
    stack.stackSize = 0;
    assertEquals(4, place.stack().stackSize);
    place.stack().stackSize = 9;
    assertEquals(4, place.stack().stackSize);
    assertEquals(
        ClientSendFactEvent.Action.RELEASE_USE_ITEM,
        ClientSendFactEvent.from(
                new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN,
                    EnumFacing.DOWN))
            .action());
    assertEquals(
        ClientSendFactEvent.Action.DIG,
        ClientSendFactEvent.from(
                new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
                    BlockPos.ORIGIN,
                    EnumFacing.DOWN))
            .action());
  }

  @Test
  public void onlyInventoryOpenStatusIsAnInventoryAction() {
    assertEquals(
        ClientSendFactEvent.Action.INVENTORY,
        ClientSendFactEvent.from(
                new C16PacketClientStatus(
                    C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT))
            .action());
    assertEquals(
        ClientSendFactEvent.Action.INVENTORY,
        ClientSendFactEvent.from(new C0DPacketCloseWindow(0)).action());
    assertEquals(
        ClientSendFactEvent.Action.INVENTORY,
        ClientSendFactEvent.from(new C0EPacketClickWindow()).action());
    assertEquals(
        ClientSendFactEvent.Action.SWING,
        ClientSendFactEvent.from(new C0APacketAnimation()).action());
    assertNull(
        ClientSendFactEvent.from(
            new C16PacketClientStatus(C16PacketClientStatus.EnumState.REQUEST_STATS)));
  }
}
