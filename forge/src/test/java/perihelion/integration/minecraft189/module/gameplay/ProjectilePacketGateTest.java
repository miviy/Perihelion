package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import static perihelion.integration.minecraft189.module.gameplay.ProjectilePacketGate.Kind.*;

import org.junit.Test;

public class ProjectilePacketGateTest {
  @Test
  public void walkingSeparatesSwordAttackAndInventoryWindows() {
    for (ProjectilePacketGate.Kind kind :
        new ProjectilePacketGate.Kind[] {SWORD_USE, ATTACK, INVENTORY}) {
      ProjectilePacketGate gate = new ProjectilePacketGate();
      assertTrue(gate.accept(kind, false));
      assertFalse(gate.canThrow());
      gate.accept(WALK, false);
      assertTrue(gate.canThrow());
    }
  }

  @Test
  public void ordinaryBlockTrafficDoesNotGateSourceAttackInventoryWindow() {
    ProjectilePacketGate gate = new ProjectilePacketGate();
    assertTrue(gate.accept(BLOCK, false));
    assertTrue(gate.canThrow());
    gate.accept(SLOT, false);
    assertTrue(gate.canThrow());
  }

  @Test
  public void ownUnblockDoesNotCreateASecondWaitOrChangeForeignPacketFacts() {
    ProjectilePacketGate gate = new ProjectilePacketGate();
    gate.initialBlocking(true);
    assertTrue(gate.accept(SWORD_RELEASE, true));
    assertTrue(gate.canThrow());
    assertTrue(gate.serverBlocking());
    gate.released();
    assertFalse(gate.serverBlocking());
    assertTrue(gate.canThrow());
  }

  @Test
  public void forceResetCannotKeepPriorActivationTrafficOrSuppression() {
    ProjectilePacketGate gate = new ProjectilePacketGate();
    gate.accept(SWORD_USE, false);
    gate.accept(ATTACK, false);
    gate.accept(INVENTORY, false);
    gate.suppress(true);
    gate.reset();
    assertTrue(gate.canThrow());
    assertFalse(gate.serverBlocking());
    assertTrue(gate.accept(SWORD_USE, false));
  }

  @Test
  public void releaseMustHaveARealBlockAndCannotRepeat() {
    ProjectilePacketGate gate = new ProjectilePacketGate();
    assertFalse(gate.accept(SWORD_RELEASE, false));
    gate.accept(SWORD_USE, false);
    assertTrue(gate.serverBlocking());
    assertTrue(gate.accept(SWORD_RELEASE, false));
    assertFalse(gate.serverBlocking());
    assertFalse(gate.accept(SWORD_RELEASE, false));
  }

  @Test
  public void foreignSwordPacketsYieldThroughoutThrowButOwnReleasePasses() {
    ProjectilePacketGate gate = new ProjectilePacketGate();
    gate.accept(SWORD_USE, false);
    gate.suppress(true);
    assertFalse(gate.accept(SWORD_RELEASE, false));
    assertFalse(gate.accept(SWORD_USE, false));
    assertTrue(gate.accept(SWORD_RELEASE, true));
    assertTrue(gate.accept(OTHER, false));
    gate.suppress(false);
    assertTrue(gate.accept(SWORD_USE, false));
  }

  @Test
  public void slotChangeClearsServerBlockWithoutAuthorizingRedundantRelease() {
    ProjectilePacketGate gate = new ProjectilePacketGate();
    gate.accept(SWORD_USE, false);
    gate.accept(SLOT, false);
    assertFalse(gate.serverBlocking());
    assertFalse(gate.accept(SWORD_RELEASE, false));
  }
}
