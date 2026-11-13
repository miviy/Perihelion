package perihelion.integration.minecraft189.module.gameplay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;

/** Read-only B4 Hypixel detection/team predicates; no donor global manager or /locraw work. */
final class B4GameContext {
  enum State { NOT_HYPIXEL, UNKNOWN, LOBBY, PIT, BEDWARS_PREGAME, BEDWARS_GAME_BED, BEDWARS_GAME_NO_BED }
  private B4GameContext() {}

  static boolean onHypixel(Minecraft mc) {
    if (mc.isSingleplayer() || mc.getCurrentServerData() == null) return false;
    if (mc.getCurrentServerData().serverIP.toLowerCase(java.util.Locale.ROOT).endsWith("hypixel.net")) return true;
    try {
      java.lang.reflect.Field field;
      try { field = GuiPlayerTabOverlay.class.getDeclaredField("header"); }
      catch (NoSuchFieldException mapped) { field = GuiPlayerTabOverlay.class.getDeclaredField("field_175256_i"); }
      field.setAccessible(true);
      Object header = field.get(mc.ingameGUI.getTabList());
      return header instanceof IChatComponent
          && ((IChatComponent) header).getUnformattedText().toLowerCase(java.util.Locale.ROOT).contains("hypixel.net");
    } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
  }

  static List<String> sidebar(Minecraft mc) {
    if (mc.theWorld == null) return null;
    Scoreboard board = mc.theWorld.getScoreboard();
    if (board == null) return null;
    ScoreObjective objective = board.getObjectiveInDisplaySlot(1);
    if (objective == null) return null;
    List<Score> scores = new ArrayList<>(board.getSortedScores(objective));
    List<String> out = new ArrayList<>();
    out.add(objective.getDisplayName());
    for (int i = scores.size() - 1; i >= 0 && out.size() <= 15; i--) {
      Score score = scores.get(i);
      if (score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#")) continue;
      out.add(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()), score.getPlayerName()));
    }
    return out;
  }

  static State state(boolean hypixel, List<String> lines) {
    if (!hypixel) return State.NOT_HYPIXEL;
    if (lines == null || lines.isEmpty()) return State.UNKNOWN;
    boolean bedwars = false;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (i == 0) {
        String title = StringUtils.stripControlCodes(line);
        if (title.equals("THE HYPIXEL PIT")) return State.PIT;
        bedwars = title.equals("BED WARS");
        continue;
      }
      if (i == 1 && line.contains("  \u00a78L")) return State.LOBBY;
      if (bedwars && i >= 5) {
        if (i == 8 && line.startsWith("Mode")) return State.BEDWARS_PREGAME;
        if (line.endsWith(" YOU")) return line.indexOf('\u2713') != -1 ? State.BEDWARS_GAME_BED : State.BEDWARS_GAME_NO_BED;
      }
    }
    return State.UNKNOWN;
  }

  static State state(Minecraft mc) { return state(onHypixel(mc), sidebar(mc)); }
  static boolean bedwars(Minecraft mc) {
    State state = state(mc);
    return state == State.BEDWARS_GAME_BED || state == State.BEDWARS_GAME_NO_BED;
  }

  static boolean sameTeam(Minecraft mc, EntityLivingBase target) {
    State state = state(mc);
    if (state == State.NOT_HYPIXEL || state == State.PIT) return false;
    String own = mc.thePlayer.getDisplayName().getUnformattedText();
    if (target instanceof EntitySilverfish) {
      if (target.getName().length() > 5 && own.length() > 5 && target.getName().substring(0, 5).equals(own.substring(0, 5))) return true;
    } else if (target instanceof EntityIronGolem) {
      Entity tag = mc.theWorld.getEntityByID(target.getEntityId() + 1);
      if (tag != null && tag.getName().length() > 2 && own.length() > 2 && tag.getName().substring(0, 2).equals(own.substring(0, 2))) return true;
    } else if (!(target instanceof EntityPlayer)) {
      return !(target instanceof EntityHorse) && (state == State.BEDWARS_GAME_BED || state == State.BEDWARS_GAME_NO_BED);
    }
    if (!(target.getTeam() instanceof ScorePlayerTeam) || !(mc.thePlayer.getTeam() instanceof ScorePlayerTeam)) return false;
    return ((ScorePlayerTeam) target.getTeam()).getColorPrefix().equals(((ScorePlayerTeam) mc.thePlayer.getTeam()).getColorPrefix());
  }
}
