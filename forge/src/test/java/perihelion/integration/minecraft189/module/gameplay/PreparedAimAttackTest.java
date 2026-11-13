package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.core.control.Rotation;

public class PreparedAimAttackTest {
  @Test
  public void singleTickOpportunitySurvivesUntilItsWalkingAimIsSubmitted() {
    PreparedAimAttack<String> plan = new PreparedAimAttack<>();
    Rotation aim = Rotation.server(90, 30);
    assertNull(plan.submitted(null));
    plan.prepare("first", aim);
    assertTrue(plan.pending());
    PreparedAimAttack.Action<String> ready = plan.submitted(aim);
    assertEquals("first", ready.target);
    assertSame(aim, ready.aim);
    assertFalse(plan.pending());
    assertNull(plan.submitted(aim));
  }

  @Test
  public void equivalentAnglesFromAnotherWalkingOwnerCannotAuthorizeAnAttack() {
    PreparedAimAttack<String> plan = new PreparedAimAttack<>();
    Rotation aim = Rotation.server(90, 30);
    plan.prepare("first", aim);
    assertNull(plan.submitted(Rotation.server(90, 30)));
    assertFalse(plan.pending());
    assertNull(plan.submitted(aim));
  }

  @Test
  public void preparingNextTargetCannotChangeThePreviouslySubmittedAction() {
    PreparedAimAttack<String> plan = new PreparedAimAttack<>();
    Rotation oldAim = Rotation.server(90, 30), nextAim = Rotation.server(-90, 30);
    plan.prepare("first", oldAim);
    PreparedAimAttack.Action<String> ready = plan.submitted(oldAim);
    plan.prepare("second", nextAim);
    assertEquals("first", ready.target);
    assertSame(oldAim, ready.aim);
    assertSame(nextAim, plan.aim());
    plan.clear();
    assertNull(plan.submitted(nextAim));
  }
}
