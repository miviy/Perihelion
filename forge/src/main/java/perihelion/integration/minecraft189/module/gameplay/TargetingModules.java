package perihelion.integration.minecraft189.module.gameplay;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

final class TargetingModules {
  private TargetingModules() {}

  static final class AntiBot extends GameplayModule {
    private static final java.util.regex.Pattern VALID_NAME =
        java.util.regex.Pattern.compile("[a-zA-Z0-9_]{1,16}");
    private final Setting<Boolean> tab, name, age, latency, fov;
    private final Setting<Integer> maxFov;

    AntiBot(ClientRuntime r) {
      super(
          r,
          "antibot",
          "Anti Bot",
          "Filter targets using tablist, name, age, latency and FOV.",
          ModuleCategory.COMBAT);
      tab = bool("tablist-check", "Tablist Check", true);
      name = bool("check-valid-name", "Check Valid Name", true);
      age = bool("check-age", "Check Age", true);
      latency = bool("check-latency", "Check Latency", true);
      fov = bool("fov-check", "FOV Check", true);
      maxFov = integer("max-fov", "Max FOV", 90, 0, 180);
    }

    boolean bot(EntityPlayer p) {
      if (!active("antibot") || p == mc().thePlayer) return false;
      NetworkPlayerInfo info = mc().getNetHandler().getPlayerInfo(p.getUniqueID());
      if (age.get() && p.ticksExisted < 20
          || tab.get() && info == null
          || latency.get() && info != null && info.getResponseTime() == 0) return true;
      if (name.get() && info != null) {
        String n = info.getGameProfile().getName();
        if (n == null || !VALID_NAME.matcher(n).matches() || n.contains("NPC")) return true;
      }
      if (!fov.get()) return false;
      Vec3 look = mc().thePlayer.getLook(1).normalize();
      Vec3 delta = p.getPositionEyes(1).subtract(mc().thePlayer.getPositionEyes(1)).normalize();
      return fov.get()
          && Math.toDegrees(Math.acos(MathHelper.clamp_double(look.dotProduct(delta), -1, 1)))
              > maxFov.get();
    }
  }

  static final class Friends extends GameplayModule {
    private final Setting<Boolean> middle;
    private final Setting<String> names;
    private String parsedNames;
    private Set<String> parsedValues = java.util.Collections.emptySet();

    Friends(ClientRuntime r) {
      super(
          r,
          "friends",
          "Friends",
          "Exclude named friends; toggle with middle click.",
          ModuleCategory.COMBAT);
      middle = bool("mid-click-add", "Mid-Click Add", true);
      names =
          setting(
              Setting.string(
                  "names",
                  "Names",
                  "Comma-separated player names, saved in Perihelion config.",
                  "",
                  4096,
                  guard));
    }

    boolean friend(String name) {
      return values().contains(name.toLowerCase(Locale.ROOT));
    }

    void toggle(String name) {
      if (name == null || !name.matches("[a-zA-Z0-9_]{1,16}")) return;
      Set<String> values = new TreeSet<>(values());
      name = name.toLowerCase(Locale.ROOT);
      if (!values.remove(name)) values.add(name);
      String next = String.join(",", values);
      if (next.length() <= 4096) runtime.modules().setSetting("friends", names, next);
    }

    private Set<String> values() {
      String current = names.get();
      if (!current.equals(parsedNames)) {
        Set<String> values = new TreeSet<>();
        for (String n : current.split(","))
          if (!n.trim().isEmpty()) values.add(n.trim().toLowerCase(Locale.ROOT));
        parsedValues = java.util.Collections.unmodifiableSet(values);
        parsedNames = current;
      }
      return parsedValues;
    }

    @Override
    protected void onEnable(ModuleContext c) {
      boolean[] held = {false};
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            boolean down = Mouse.isCreated() && Mouse.isButtonDown(2);
            boolean edge = down && !held[0];
            held[0] = down;
            if (!edge
                || !middle.get()
                || mc().currentScreen != null
                || mc().objectMouseOver == null
                || !(mc().objectMouseOver.entityHit instanceof EntityPlayer)) return;
            String name = mc().objectMouseOver.entityHit.getName().toLowerCase(Locale.ROOT);
            toggle(name);
          });
    }
  }
}
