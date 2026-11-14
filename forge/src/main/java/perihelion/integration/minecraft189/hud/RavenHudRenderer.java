package perihelion.integration.minecraft189.hud;

import java.util.*;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.hud.HudModule.Element;
import perihelion.integration.minecraft189.hud.HudRenderer.Box;
import perihelion.integration.minecraft189.hud.HudRenderer.ModuleRow;
import perihelion.integration.minecraft189.hud.HudRenderer.Notice;
import perihelion.integration.minecraft189.hud.HudRenderer.PlayerRow;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Independent Raven presentation; only snapshots, primitives and native interaction are shared. */
final class RavenHudRenderer {
  private static final int WHITE = 0xffeeeeee, GRAY = 0xffaaaaaa, GOLD = 0xffffcf62;
  private final HudRenderer owner;
  private final HudModule hud;
  private HudFont text;
  private HudElementStyle style;
  private final UiCanvas canvas = new UiCanvas();
  private final RenderState state = new RenderState();
  private final boolean[] keyDown = new boolean[6];
  private final long[] keyChanged = new long[6];
  private int accent, secondary, background;
  private long now;
  // Native smoke probes verify all elements are handled here without entering Adjust.draw().
  final EnumSet<Element> drawn = EnumSet.noneOf(Element.class);
  long frames;

  RavenHudRenderer(HudRenderer owner) {
    this.owner = owner;
    hud = owner.hud;
    text = owner.text;
  }

  void render(int width, int height, boolean preview, Element dragging, int dragX, int dragY) {
    frames++;
    drawn.clear();
    palette();
    canvas.pixelScale = owner.pixels;
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      for (Element element : Element.values()) {
        Box box = owner.boxes.get(element);
        box.visible = false;
        if (!owner.renders(element, preview)) continue;
        owner.configureElementFont(element);
        text = owner.text;
        style = hud.styles.get(element);
        background = GuiTheme.alpha(hud.opacity.get() * .62 * style.opacity.get() / .6, 0);
        if (!measure(element, box, preview)) continue;
        owner.placeStyledBox(element, box, box.w, box.h, width, height, dragging, dragX, dragY);
        if (element != Element.TAB_GUI || !owner.navigation.expanded)
          drawBox(element, box, preview, width, height);
      }
      Box tab = owner.boxes.get(Element.TAB_GUI);
      if (tab.visible && owner.navigation.expanded) {
        owner.configureElementFont(Element.TAB_GUI);
        text = owner.text;
        style = hud.styles.get(Element.TAB_GUI);
        background = GuiTheme.alpha(hud.opacity.get() * .62 * style.opacity.get() / .6, 0);
        drawBox(Element.TAB_GUI, tab, preview, width, height);
      }
    }
  }

  private void palette() {
    now = System.currentTimeMillis();
    // OpenB4's default HUD theme is Frost. Explicit native color/rainbow choices still win.
    accent =
        hud.accent.get().equals(hud.accent.defaultValue())
            ? 0xfffdfdfd
            : hud.accent.get().resolvedArgb(now);
    secondary =
        hud.secondary.get().equals(hud.secondary.defaultValue())
            ? 0xffd2e1e6
            : hud.secondary.get().resolvedArgb(now);
    background = GuiTheme.alpha(hud.opacity.get() * .62, 0x000000);
  }

  private boolean measure(Element element, Box box, boolean preview) {
    switch (element) {
      case WATERMARK:
        if (owner.brandedLogo()) {
          owner.measureBrand(box);
          return true;
        }
        box.w = Math.max(text.width(welcome()), text.width("Perihelion / Raven")) + 2;
        if (hud.watermarkInfo.get()) box.w = Math.max(box.w, text.width(details()) + 2);
        box.h = hud.watermarkInfo.get() ? 33 : 22;
        return true;
      case TAB_GUI:
        box.w = HudNavigator.CATEGORY_WIDTH;
        box.h = HudNavigator.CATEGORIES.length * HudNavigator.ROW_HEIGHT;
        return true;
      case MODULES:
        if (!preview && owner.modules.isEmpty()) return false;
        box.w = 94;
        if (!owner.modules.isEmpty()) {
          box.w = 0;
          for (ModuleRow row : owner.modules) box.w = Math.max(box.w, row.width + 7);
        }
        box.h = Math.max(1, owner.modules.size()) * moduleHeight() + 1;
        return true;
      case INFO:
        box.w = Math.max(text.width(owner.info), text.width(owner.speed)) + 2;
        box.h = 22;
        return true;
      case TARGET:
        if (!preview && !owner.hasTarget) return false;
        box.w = Math.max(122, Math.min(230, text.width(targetTitle()) + 18));
        box.h = 36;
        return true;
      case ARMOR:
        if (!preview && !owner.hasArmor) return false;
        box.w = armorCellWidth() * 4;
        box.h = 31;
        return true;
      case EFFECTS:
        if (!preview && owner.effects.isEmpty()) return false;
        box.w = 96;
        for (String effect : owner.effects) box.w = Math.max(box.w, text.width(effect) + 2);
        box.h = Math.max(1, owner.effects.size()) * 12;
        return true;
      case KEYS:
        box.w = 74;
        box.h = 86;
        return true;
      case PLAYERS:
        if (!preview && owner.players.isEmpty() && !(owner.mc.currentScreen instanceof GuiChat))
          return false;
        box.w = playerNameWidth();
        for (HudModule.PlayerColumn column : hud.playerColumns.get())
          box.w += playerColumnWidth(column);
        // These rows also define native chat hover, click and horizontal/vertical scrolling.
        box.h = 11 + Math.max(1, owner.players.size()) * 10;
        return true;
      case NOTIFICATIONS:
        if (!preview && owner.notices.isEmpty()) return false;
        box.w = 128;
        for (Notice notice : owner.notices) box.w = Math.max(box.w, text.width(notice.label) + 24);
        box.h = Math.max(1, owner.notices.size()) * 18;
        return true;
      default:
        throw new IllegalArgumentException("Unimplemented Raven HUD element: " + element);
    }
  }

  private void drawBox(Element element, Box box, boolean preview, int width, int height) {
    owner.shadowBox(element, box);
    clip(box, width, height);
    owner.blurBox(element, box, width, height);
    float pixels = owner.pixels;
    net.minecraft.client.renderer.GlStateManager.pushMatrix();
    try {
      net.minecraft.client.renderer.GlStateManager.translate(box.anchorX, box.anchorY, 0);
      net.minecraft.client.renderer.GlStateManager.scale(box.scale, box.scale, 1);
      owner.pixels *= box.scale;
      canvas.pixelScale = owner.pixels;
      owner.customElementBackground(element, box);
      draw(element, box.content);
      drawn.add(element);
    } finally {
      owner.pixels = pixels;
      net.minecraft.client.renderer.GlStateManager.popMatrix();
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
  }

  private void clip(Box box, int width, int height) {
    double sx = owner.mc.displayWidth / (double) width,
        sy = owner.mc.displayHeight / (double) height;
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    GL11.glScissor(
        Math.max(0, (int) Math.floor(box.x * sx) - 2),
        Math.max(0, (int) Math.floor((height - box.y - box.h) * sy) - 2),
        Math.min(owner.mc.displayWidth, (int) Math.ceil(box.w * sx) + 4),
        Math.min(owner.mc.displayHeight, (int) Math.ceil(box.h * sy) + 4));
  }

  private void draw(Element element, Box box) {
    switch (element) {
      case WATERMARK:
        watermark(box);
        break;
      case TAB_GUI:
        navigation();
        break;
      case MODULES:
        modules(box);
        break;
      case INFO:
        label(owner.info, box.x + 1, box.y, box.w - 2, 11, WHITE);
        label(owner.speed, box.x + 1, box.y + 11, box.w - 2, 11, GRAY);
        break;
      case TARGET:
        target(box);
        break;
      case ARMOR:
        armor(box);
        break;
      case EFFECTS:
        if (owner.effects.isEmpty()) label("No active effects", box.x, box.y, box.w, 12, GRAY);
        for (int i = 0; i < owner.effects.size() && (i + 1) * 12 <= box.h; i++) {
          String value = owner.effects.get(i);
          label(
              value,
              box.x + Math.max(0, box.w - text.width(value)),
              box.y + i * 12,
              box.w,
              12,
              WHITE);
        }
        break;
      case KEYS:
        keys(box);
        break;
      case PLAYERS:
        players(box);
        break;
      case NOTIFICATIONS:
        notices(box);
        break;
      default:
        throw new IllegalArgumentException("Unimplemented Raven HUD element: " + element);
    }
  }

  private String welcome() {
    return "welcome, \u00a7f" + owner.mc.thePlayer.getName();
  }

  private String details() {
    return owner.watermark.startsWith("Perihelion")
        ? owner.watermark.substring("Perihelion".length()).trim()
        : owner.watermark;
  }

  private void watermark(Box box) {
    if (owner.brandedLogo()) {
      owner.drawBrand(box, accent, WHITE);
      return;
    }
    label(welcome(), box.x + 1, box.y, box.w - 2, 11, GRAY);
    String name = "Perihelion";
    int x = box.x + 1;
    for (int i = 0; i < name.length(); i++) {
      String character = name.substring(i, i + 1);
      text.draw(
          character,
          x,
          text.centeredY(box.y + 11, 11),
          color(i / 9f),
          style == null ? hud.shadow.get() : style.shadow.get(),
          owner.pixels);
      x += text.width(character);
    }
    label(" \u00a77/ Raven", x, box.y + 11, box.w - (x - box.x), 11, WHITE);
    if (hud.watermarkInfo.get()) label(details(), box.x + 1, box.y + 22, box.w - 2, 11, GRAY);
  }

  private int moduleHeight() {
    return owner.moduleRowHeight();
  }

  private int color(float amount) {
    return HudAppearance.blend(accent, secondary, amount);
  }

  private void modules(Box box) {
    owner.drawStyledModules(box, accent, secondary, background);
  }

  private String targetTitle() {
    float ratio = owner.hasTarget ? owner.health / owner.maximum : 1;
    String healthColor = ratio > .66f ? "\u00a7a" : ratio > .33f ? "\u00a7e" : "\u00a7c";
    return (owner.hasTarget ? owner.targetName : "Target")
        + "  "
        + healthColor
        + HudRenderer.decimal(owner.hasTarget ? owner.health : 20)
        + " \u2764";
  }

  private void target(Box box) {
    if (style.background.get() == HudElementStyle.Background.AUTO)
      rounded(box.x, box.y, box.w, box.h, background, accent);
    center(targetTitle(), box.x + 6, box.y + 3, box.w - 12, 12, WHITE);
    center(
        (owner.hasTarget ? owner.targetDistance : "3.0m")
            + (owner.absorption > 0 ? "  \u00a7e+" + HudRenderer.decimal(owner.absorption) : ""),
        box.x + 6,
        box.y + 15,
        box.w - 12,
        10,
        GRAY);
    int width = Math.max(0, box.w - 12);
    canvas.rect(box.x + 6, box.y + 27, width, 5, 0x70000000);
    float health = (float) HudLayout.clamp(owner.hasTarget ? owner.health / owner.maximum : 1);
    canvas.gradient(
        box.x + 6,
        box.y + 27,
        width * health,
        5,
        accent,
        color(1 - health),
        color(1 - health),
        accent);
  }

  private int armorCellWidth() {
    int width = 22;
    for (String value : owner.durability)
      width = Math.max(width, text.width(value == null ? "--" : value) + 2);
    return width;
  }

  private void armor(Box box) {
    int cell = box.w / 4;
    for (int i = 0; i < 4; i++) {
      int x = box.x + i * cell, itemX = x + (cell - 16) / 2;
      if (owner.armor[i] != null) owner.item(owner.armor[i], itemX, box.y);
      String value = owner.durability[i] == null ? "--" : owner.durability[i];
      center(value, x + 1, box.y + 20, cell - 2, 11, WHITE);
      if (owner.armor[i] != null && owner.armor[i].isItemStackDamageable()) {
        float fraction =
            (float)
                HudLayout.clamp(
                    (owner.armor[i].getMaxDamage() - owner.armor[i].getItemDamage())
                        / (double) Math.max(1, owner.armor[i].getMaxDamage()));
        canvas.rect(itemX, box.y + 17, 16, 2, 0x90000000);
        canvas.rect(
            itemX,
            box.y + 17,
            16 * fraction,
            2,
            fraction > .5f ? 0xff66dd66 : fraction > .2f ? GOLD : 0xffff5555);
      }
    }
  }

  private void keys(Box box) {
    key(
        0,
        keyName(owner.mc.gameSettings.keyBindForward),
        box.x + 26,
        box.y + 2,
        22,
        owner.mc.gameSettings.keyBindForward.isKeyDown());
    key(
        1,
        keyName(owner.mc.gameSettings.keyBindLeft),
        box.x + 2,
        box.y + 26,
        22,
        owner.mc.gameSettings.keyBindLeft.isKeyDown());
    key(
        2,
        keyName(owner.mc.gameSettings.keyBindBack),
        box.x + 26,
        box.y + 26,
        22,
        owner.mc.gameSettings.keyBindBack.isKeyDown());
    key(
        3,
        keyName(owner.mc.gameSettings.keyBindRight),
        box.x + 50,
        box.y + 26,
        22,
        owner.mc.gameSettings.keyBindRight.isKeyDown());
    key(4, "LMB", box.x + 2, box.y + 50, 34, Mouse.isButtonDown(0));
    key(5, "RMB", box.x + 38, box.y + 50, 34, Mouse.isButtonDown(1));
    center(owner.cps, box.x, box.y + 75, box.w, 11, WHITE);
  }

  private String keyName(KeyBinding key) {
    String name = key.getKeyCode() > 0 ? Keyboard.getKeyName(key.getKeyCode()) : "?";
    return name == null ? "?" : name;
  }

  private void key(int index, String label, int x, int y, int width, boolean down) {
    if (keyDown[index] != down) {
      keyDown[index] = down;
      keyChanged[index] = now;
    }
    float progress = Math.min(1, Math.max(0, now - keyChanged[index]) / 120f);
    float brightness = down ? progress : 1 - progress;
    if (style.background.get() == HudElementStyle.Background.AUTO || down)
      canvas.rect(x, y, width, 22, HudAppearance.blend(background, 0xc0eeeeee, brightness));
    canvas.outline(x, y, width, 22, GuiTheme.alpha(.35, accent));
    center(label, x + 1, y, width - 2, 22, brightness > .55f ? 0xff151515 : WHITE);
  }

  int playerNameWidth() {
    int width = 104;
    for (PlayerRow player : owner.players)
      width = Math.max(width, Math.min(164, text.width(player.name) + 22));
    return width;
  }

  int playerColumnWidth(HudModule.PlayerColumn column) {
    return Math.max(column.width, Math.max(text.width(column.heading), text.width("999.99")) + 6);
  }

  private void players(Box box) {
    if (style.background.get() == HudElementStyle.Background.AUTO)
      canvas.rect(box.x, box.y, box.w, box.h, background);
    if (style.background.get() == HudElementStyle.Background.AUTO)
      canvas.rect(box.x, box.y, box.w, 11, 0xb0000000);
    List<HudModule.PlayerColumn> columns = owner.visiblePlayerColumns(box);
    int nameWidth = Math.min(playerNameWidth(), box.w);
    label(
        "Players \u00a77["
            + owner.players.size()
            + "]"
            + (columns.size() < hud.playerColumns.get().size() ? " <>" : ""),
        box.x + 3,
        box.y,
        nameWidth - 6,
        11,
        GOLD);
    int x = box.x + nameWidth;
    for (HudModule.PlayerColumn column : columns) {
      center(column.heading, x, box.y, playerColumnWidth(column), 11, GOLD);
      x += playerColumnWidth(column);
    }
    int count = Math.max(0, (box.h - 11) / 10);
    owner.playerRowOffset =
        Math.max(0, Math.min(owner.playerRowOffset, Math.max(0, owner.players.size() - count)));
    for (int i = 0; i < count && i + owner.playerRowOffset < owner.players.size(); i++) {
      PlayerRow player = owner.players.get(i + owner.playerRowOffset);
      int y = box.y + 11 + i * 10;
      boolean tagged = owner.targets.contains(player.id);
      int shade = i % 2 == 0 ? 0x18000000 : 0x30000000;
      if (hud.overlay.highlights.get() && player.profile != null) {
        if (player.profile.nicked) shade = 0x406e5818;
        if (!player.profile.tags.isEmpty()) shade = 0x406e1818;
      }
      if (hud.tracker != null) {
        String username = player.username.toLowerCase(Locale.ROOT);
        if (hud.tracker.party.contains(username)) shade = 0x40326142;
        else if (hud.overlay.partyDetector.get() && hud.tracker.possibleParty.contains(username))
          shade = 0x30326142;
      }
      if (player.target || tagged) shade = 0x60506b88;
      canvas.rect(box.x, y, box.w, 10, shade);
      if (tagged) canvas.rect(box.x, y, 2, 10, 0xffff6666);
      if (player.skin != null) owner.face(player.skin, box.x + 3, y + 1, 8);
      label(
          (owner.pinned.contains(player.id) ? "\u00a7e* \u00a7r" : "") + player.name,
          box.x + 14,
          y,
          nameWidth - 16,
          10,
          WHITE);
      x = box.x + nameWidth;
      for (HudModule.PlayerColumn column : columns) {
        center(
            owner.playerValue(player, column), x + 2, y, playerColumnWidth(column) - 4, 10, WHITE);
        x += playerColumnWidth(column);
      }
    }
    if (owner.players.isEmpty())
      label("No nearby players", box.x + 3, box.y + 11, box.w - 6, 10, GRAY);
  }

  private void notices(Box box) {
    if (owner.notices.isEmpty()) {
      canvas.rect(box.x, box.y, box.w, 16, background);
      label("\u00a7a+ \u00a7rNotifications", box.x + 4, box.y, box.w - 8, 15, WHITE);
    }
    int row = 0;
    for (Notice notice : owner.notices) {
      long age = System.nanoTime() - notice.time;
      if (age >= 3_000_000_000L) continue;
      int y = box.y + row++ * 18;
      canvas.rect(box.x, y, box.w, 16, background);
      label(
          (notice.enabled ? "\u00a7a+ " : "\u00a7c- ") + "\u00a7r" + notice.label,
          box.x + 4,
          y,
          box.w - 8,
          15,
          WHITE);
      canvas.rect(
          box.x,
          y + 15,
          box.w * (float) HudLayout.clamp(1 - age / 3_000_000_000.0),
          1,
          notice.enabled ? secondary : 0xffdd7777);
    }
  }

  private final TabGuiView.Painter navigationPainter =
      new TabGuiView.Painter() {
        public void surface(int x, int y, int width, int height) {
          if (style.background.get() == HudElementStyle.Background.AUTO)
            canvas.rect(x, y, width, height, background);
        }

        public void highlight(
            int x,
            int y,
            int width,
            int height,
            boolean selected,
            boolean editing,
            boolean enabled) {
          if (selected) selectRow(x, y, width, height);
        }

        public void label(String value, int x, int y, int width, int height, TabGuiView.Tone tone) {
          RavenHudRenderer.this.label(
              value,
              x,
              y,
              width,
              height,
              tone == TabGuiView.Tone.ERROR
                  ? 0xffff7777
                  : tone == TabGuiView.Tone.MUTED
                      ? GRAY
                      : tone == TabGuiView.Tone.ENABLED
                          ? secondary
                          : tone == TabGuiView.Tone.EDITING ? GOLD : WHITE);
        }

        public void checkbox(int x, int y, int width, int height, boolean on) {
          RavenHudRenderer.this.label(
              on ? "[x]" : "[ ]", x + width - 23, y, 21, height, on ? secondary : GRAY);
        }

        public int checkboxWidth() {
          return 27;
        }

        public int textWidth(String value) {
          return text.width(value);
        }
      };

  private void navigation() {
    TabGuiView.draw(
        owner.navigation,
        owner.navigationLayout,
        owner.navigationModules,
        owner.navigationSettings,
        navigationPainter);
  }

  private void selectRow(int x, int y, int width, int height) {
    canvas.rect(x, y, width, height, GuiTheme.alpha(.35, accent));
    canvas.rect(x, y, 2, height, accent);
  }

  void popup(
      PlayerRow player, HypixelStats profile, List<String> rows, Box box, int scroll, int hover) {
    owner.configureElementFont(Element.PLAYERS);
    text = owner.text;
    style = hud.styles.get(Element.PLAYERS);
    palette();
    canvas.pixelScale = owner.pixels;
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      canvas.rect(box.x, box.y, box.w, box.h, 0xf3000000);
      canvas.outline(box.x, box.y, box.w, box.h, 0xff555555);
      canvas.rect(box.x + 1, box.y + 1, box.w - 2, 12, 0xff222222);
      label("Player profile", box.x + 5, box.y + 1, box.w - 22, 11, GOLD);
      label("x", box.x + box.w - 12, box.y + 3, 8, 10, WHITE);
      if (player.skin != null) owner.face(player.skin, box.x + 7, box.y + 19, 48);
      int x = box.x + 63;
      label(player.name, x, box.y + 17, box.w - 70, 12, accent);
      label(
          player.ping < 0 ? "Ping hidden" : player.ping + " ms",
          x,
          box.y + 31,
          box.w - 70,
          11,
          WHITE);
      label("First login", x, box.y + 44, box.w - 70, 11, GRAY);
      OverlayProfile extra = hud.overlayLookup == null ? null : hud.overlayLookup.get(player.id);
      long login =
          extra != null && extra.counts.containsKey("firstLogin")
              ? extra.counts.get("firstLogin")
              : profile == null ? 0 : profile.firstLogin;
      String first =
          login <= 0
              ? "-- (load Statistics)"
              : java.time.Instant.ofEpochMilli(login)
                  .atZone(java.time.ZoneOffset.UTC)
                  .toLocalDate()
                  .toString();
      label(first, x, box.y + 56, box.w - 70, 11, WHITE);
      int count = Math.max(1, (box.h - 76) / 12);
      clip(
          box,
          Math.max(1, owner.mc.currentScreen.width),
          Math.max(1, owner.mc.currentScreen.height));
      for (int i = scroll; i < rows.size() && i < scroll + count; i++) {
        int top = box.y + 76 + (i - scroll) * 12;
        boolean heading =
            rows.get(i).equals("Tags")
                || rows.get(i).equals("Analysis")
                || rows.get(i).equals("Hypixel");
        if (heading) canvas.rect(box.x + 1, top, box.w - 2, 12, 0xff1a1a1a);
        else if (i == hover) selectRow(box.x + 1, top, box.w - 2, 12);
        label(rows.get(i), box.x + (heading ? 5 : 10), top, box.w - 15, 12, heading ? GOLD : WHITE);
      }
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      if (rows.size() > count) {
        float thumb = (box.h - 76f) * count / rows.size();
        canvas.rect(
            box.x + box.w - 3,
            box.y + 76 + (box.h - 76f - thumb) * scroll / Math.max(1, rows.size() - count),
            2,
            thumb,
            accent);
      }
    }
  }

  private void rounded(int x, int y, int width, int height, int fill, int line) {
    canvas.rect(x, y + 2, width, Math.max(0, height - 4), fill);
    canvas.rect(x + 2, y, Math.max(0, width - 4), 2, fill);
    canvas.rect(x + 2, y + height - 2, Math.max(0, width - 4), 2, fill);
    canvas.rect(x + 2, y, Math.max(0, width - 4), 1, line);
    canvas.rect(x + 2, y + height - 1, Math.max(0, width - 4), 1, secondary);
    canvas.rect(x, y + 2, 1, Math.max(0, height - 4), line);
    canvas.rect(x + width - 1, y + 2, 1, Math.max(0, height - 4), secondary);
  }

  private void label(String value, int x, int top, int width, int height, int color) {
    if (width <= 0) return;
    color = style.color(hud, System.currentTimeMillis(), 0, color);
    text.draw(
        text.trim(value, width),
        x,
        text.centeredY(top, height),
        color,
        style == null ? hud.shadow.get() : style.shadow.get(),
        owner.pixels);
  }

  private void center(String value, int x, int top, int width, int height, int color) {
    if (width <= 0) return;
    color = style.color(hud, System.currentTimeMillis(), 0, color);
    String fitted = text.trim(value, width);
    text.draw(
        fitted,
        x + Math.max(0, (width - text.width(fitted)) / 2),
        text.centeredY(top, height),
        color,
        style == null ? hud.shadow.get() : style.shadow.get(),
        owner.pixels);
  }
}
