package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import org.junit.Test;

public class PlayerReportTest {
  @Test
  public void usesExactHypixelHostsAndValidatedUsernames() {
    assertTrue(PlayerReport.hypixel("mc.hypixel.net:25565"));
    assertTrue(PlayerReport.hypixel("HYPIXEL.NET"));
    assertFalse(PlayerReport.hypixel("hypixel.net.example.com"));
    assertFalse(PlayerReport.hypixel("fakehypixel.net"));
    assertFalse(PlayerReport.hypixel(null));
    assertEquals("/report Test_Player", PlayerReport.command(true, "Test_Player"));
    assertNull(PlayerReport.command(false, "Test_Player"));
    assertNull(PlayerReport.command(true, "Test_Player\n/report SomeoneElse"));
    assertNull(PlayerReport.command(true, "\u00a7cDisplay name"));
    assertNull(PlayerReport.command(true, null));
  }
}
