// SPDX-License-Identifier: AGPL-3.0-only
// Native presentation adapters for the donor layouts recorded in docs/TARGET_HUD_DESIGNS.md.
package perihelion.integration.minecraft189.hud;

import static perihelion.integration.minecraft189.hud.TargetHudModule.Design.*;

import java.awt.Color;
import java.util.Locale;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.menu.MenuSurface;
import perihelion.integration.minecraft189.gui.modern.RoundedClip;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Layouts only: one shared target, two shared fonts, and no donor managers or global caches. */
final class TargetHudDesigns implements AutoCloseable {
  private static final int WHITE = 0xffffffff, MUTED = 0xffbebebe;
  private final HudRenderer owner;
  private final HudFont regular, bold;
  private final UiCanvas canvas = new UiCanvas();
  private final RoundedClip clip = new RoundedClip();
  private EntityLivingBase animated;
  private TargetHudModule.Design animatedDesign, design;
  private long lastFrame, damageAt;
  private float displayed, trail, lastHealth, flash, ox, oy, w, h;
  private int first, second;
  private float regularLine, boldLine;
  private float textPixels;
  private boolean bitmap;

  TargetHudDesigns(HudRenderer owner, HudFont shared) {
    this.owner = owner;
    regular = new HudFont(shared);
    bold = new HudFont(shared);
  }

  TargetHudModule module() {
    return owner.hud.targetHud;
  }

  boolean active() {
    return module() != null && module().donorDesign();
  }

  boolean glass() {
    if (!active() || !module().nativeEffects()) return false;
    if (owner.hud.styles.get(HudModule.Element.TARGET).background.get()
        != HudElementStyle.Background.AUTO) return false;
    TargetHudModule.Design d = module().design();
    return d == B4_MODERN || d == MYAU_RAVEN_MODERN || d == MYAU_BLUEDEV;
  }

  float radius() {
    if (!active()) return owner.hud.styles.get(HudModule.Element.TARGET).radius.get();
    switch (module().design()) {
      case B4_MODERN:
        return 5;
      case B4_LEGACY:
      case MYAU_RAVEN_MODERN:
      case MYAU_RAVEN_LEGACY:
        return 10;
      case YURI:
      case YURI_PULSIVE:
      case YURI_RAVEN_B4:
      case YURI_OLD_RAVEN_B4:
      case MYAU_TENACITY:
        return 6;
      case ARSENIC_FACE:
      case ARSENIC_SIMPLE:
      case MYAU_FACE:
      case MYAU_3D:
      case MYAU_SIMPLE:
      case MYAU_MOON:
      case MYAU_RISE:
        return 8;
      case MYAU_BLUEDEV:
        return 10;
      case COOLWARE_ISLAND:
        return 24;
      case MYAU_NEVERLOSE:
        return 4;
      default:
        return 0;
    }
  }

  private boolean pixelFont(TargetHudModule.Design d) {
    switch (d) {
      case YURI_ASTOLFO:
      case YURI_OLD_ASTOLFO:
      case YURI_NOVOLINE:
      case YURI_OLD_NOVOLINE:
      case YURI_OLD_EXHIBITION:
      case YURI_RAVEN_B4:
      case YURI_OLD_RAVEN_B4:
      case B4_MODERN:
      case B4_LEGACY:
      case MYAU_DEFAULT:
      case MYAU_RAVEN_MODERN:
      case MYAU_RAVEN_LEGACY:
      case MYAU_FACE:
      case MYAU_3D:
      case MYAU_SIMPLE:
      case MYAU_CIRCLE:
      case MYAU_BLUEDEV:
        return true;
      default:
        return false;
    }
  }

  private void configure() {
    configure(owner.pixels);
  }

  private void configure(float effectivePixels) {
    textPixels = effectivePixels;
    design = module().design();
    ClickGuiModule.Font font =
        module().theme() == TargetHudModule.Theme.ORIGINAL
            ? pixelFont(design) ? ClickGuiModule.Font.MINECRAFT : ClickGuiModule.Font.INTER
            : module().theme() == TargetHudModule.Theme.CLIENT
                ? GuiTheme.font(owner.hud.runtime)
                : owner.hud.styles.get(HudModule.Element.TARGET).font(owner.hud);
    regular.choice(
        font,
        module().theme() == TargetHudModule.Theme.CUSTOM
            && owner.hud.styles.get(HudModule.Element.TARGET).bold.get());
    bold.choice(font, true);
    bitmap = font == ClickGuiModule.Font.MINECRAFT;
    regular.raster(1, !bitmap);
    bold.raster(1, !bitmap);
    regularLine = bitmap ? 9 : regular.height();
    boldLine = bitmap ? 9 : bold.height();
    first = accent(false);
    second = accent(true);
    canvas.pixelScale = owner.pixels;
  }

  private int accent(boolean end) {
    if (module().theme() == TargetHudModule.Theme.CLIENT)
      return GuiTheme.palette(owner.hud.runtime).color(end ? 1 : 0);
    if (module().theme() == TargetHudModule.Theme.CUSTOM)
      return module().customColor(end, System.currentTimeMillis());
    String family = design.name();
    if (family.startsWith("YURI")) return end ? 0xff37125a : 0xffa152e6;
    if (family.startsWith("B4")) return end ? 0xffd2e1e6 : 0xfffdfdfd;
    if (family.startsWith("ARSENIC")) return 0xffdd425e;
    if (family.startsWith("COOLWARE")) return 0xffb5a6f2;
    if (design == MYAU_BLUEDEV) return end ? 0xff0078ff : 0xff00dcff;
    return end ? 0xff4db5ff : 0xff0096ff;
  }

  int width() {
    configure(owner.targetMeasurePixels());
    float name = width(name(), 9, false), result = design.width;
    switch (design) {
      case B4_MODERN:
      case B4_LEGACY:
      case MYAU_RAVEN_MODERN:
      case MYAU_RAVEN_LEGACY:
        result = Math.max(result, width(b4Title(), 9, false) + 24);
        break;
      case YURI_RAVEN_B4:
      case YURI_OLD_RAVEN_B4:
        result = Math.max(result, width(ravenTitle(), 9, false) + 8);
        break;
      case YURI_OLD_NOVOLINE:
        result = Math.max(result, name + 41);
        break;
      case YURI_EXHIBITION:
        result = Math.max(result, width(name(), 8, false) + 68);
        break;
      case MYAU_ASTOLFO:
        result = Math.max(result, name + 60);
        break;
      case MYAU_EXHIBITION:
        result = Math.max(result, width(name(), 8, true) + 55);
        break;
      case MYAU_MOON:
        result = Math.max(result, width(name(), 8, false) + 68);
        break;
      case MYAU_RISE:
        result = Math.max(result, name + 75);
        break;
      case MYAU_NEVERLOSE:
        result = Math.max(result, width(name(), 8, false) + 70);
        break;
      case MYAU_TENACITY:
        result = Math.max(result, width(name(), 10, false) + 50);
        break;
      case COOLWARE_FLAT:
        result = Math.max(result, width(name(), 13, false) + width("WINNING", 9, false) + 64);
        break;
      case COOLWARE_ISLAND:
        result = Math.max(result, Math.max(width(name(), 12, false), width(info(), 9, false)) + 56);
        break;
      case MYAU_DEFAULT:
        result = Math.max(result, name + 52);
        break;
      default:
        break;
    }
    return (int) Math.ceil(Math.min(280, result));
  }

  int height() {
    return module().design().height;
  }

  private String name() {
    return owner.hasTarget ? owner.targetName : "Target";
  }

  private float hp() {
    return owner.hasTarget ? owner.health : 20;
  }

  private float maxHp() {
    return owner.hasTarget ? Math.max(1, owner.maximum) : 20;
  }

  private float ratio() {
    return (float) HudLayout.clamp(hp() / maxHp());
  }

  private float ownHp() {
    return owner.mc.thePlayer == null ? 20 : owner.mc.thePlayer.getHealth();
  }

  private String distance() {
    return owner.hasTarget ? owner.targetDistance : "3.0m";
  }

  private String info() {
    return HudRenderer.decimal(hp()) + " HP  ·  " + distance();
  }

  private String diff() {
    return String.format(Locale.ROOT, "%+.1f", ownHp() - hp());
  }

  private String status() {
    return Math.abs(ownHp() - hp()) <= .25 ? "N" : ownHp() > hp() ? "W" : "L";
  }

  private int healthColor() {
    if (module().theme() != TargetHudModule.Theme.ORIGINAL) return first;
    return Color.HSBtoRGB(ratio() / 3, 1, 1) | 0xff000000;
  }

  private int armorValue() {
    return owner.targetEntity == null ? 0 : owner.targetEntity.getTotalArmorValue();
  }

  private String ravenTitle() {
    String team = "";
    EntityLivingBase entity = owner.targetEntity;
    if (entity != null && entity.getTeam() != null) {
      String registered =
          EnumChatFormatting.getTextWithoutFormattingCodes(entity.getTeam().getRegisteredName());
      if (registered != null && !registered.trim().isEmpty()) {
        String prefix =
            entity.getTeam() instanceof ScorePlayerTeam
                ? ((ScorePlayerTeam) entity.getTeam()).getColorPrefix()
                : "";
        team = prefix + registered.trim().substring(0, 1).toUpperCase(Locale.ROOT) + " §r";
      }
    }
    String hpColor = hp() <= 5 ? "§c" : hp() <= 9 ? "§6" : hp() <= 17 ? "§e" : "§a";
    return team
        + name()
        + " "
        + hpColor
        + HudRenderer.decimal(hp())
        + " "
        + (status().equals("W") ? "§a" : status().equals("L") ? "§c" : "§e")
        + status();
  }

  private String b4Title() {
    return b4Title(Float.MAX_VALUE);
  }

  private String b4Title(float available) {
    String color = ratio() < .3 ? "§c" : ratio() < .5 ? "§6" : ratio() < .7 ? "§e" : "§a";
    String suffix =
        " " + color + HudRenderer.decimal(hp()) + " " + (ownHp() >= hp() ? "§aW" : "§cL");
    float room = Math.max(0, available - width(suffix, 9, false));
    float scale = textScale(9, false);
    return regular.trim(name(), (int) Math.min(Integer.MAX_VALUE, room / scale)) + suffix;
  }

  private void animate() {
    long now = System.nanoTime();
    if (animated != owner.targetEntity || animatedDesign != design) {
      animated = owner.targetEntity;
      animatedDesign = design;
      displayed = trail = lastHealth = hp();
      damageAt = 0;
      lastFrame = now;
    }
    if (hp() < lastHealth) damageAt = now;
    float dt = Math.min(.1f, Math.max(0, (now - lastFrame) / 1_000_000_000f));
    displayed += (hp() - displayed) * (1 - (float) Math.exp(-dt * 14));
    trail = Math.max(hp(), trail + (hp() - trail) * (1 - (float) Math.exp(-dt * 4)));
    flash = damageAt == 0 ? 0 : Math.max(0, 1 - (now - damageAt) / 300_000_000f);
    lastHealth = hp();
    lastFrame = now;
  }

  void draw(HudRenderer.Box box) {
    configure();
    animate();
    ox = box.x;
    oy = box.y;
    w = box.w;
    h = box.h;
    switch (design) {
      case YURI:
        yuri();
        break;
      case YURI_PULSIVE:
        pulsive();
        break;
      case YURI_ADJUST:
        compact(false);
        break;
      case YURI_ATMOSPHERE:
        compact(true);
        break;
      case YURI_ASTOLFO:
        astolfo(false);
        break;
      case YURI_OLD_ASTOLFO:
        astolfo(true);
        break;
      case YURI_NOVOLINE:
        novoline(false);
        break;
      case YURI_OLD_NOVOLINE:
        novoline(true);
        break;
      case YURI_EXHIBITION:
        exhibition(0);
        break;
      case YURI_OLD_EXHIBITION:
        exhibition(1);
        break;
      case MYAU_EXHIBITION:
        exhibition(2);
        break;
      case YURI_RAVEN_B4:
        raven(false);
        break;
      case YURI_OLD_RAVEN_B4:
        raven(true);
        break;
      case B4_MODERN:
      case MYAU_RAVEN_MODERN:
        b4(false);
        break;
      case B4_LEGACY:
      case MYAU_RAVEN_LEGACY:
        b4(true);
        break;
      case ARSENIC_FACE:
      case MYAU_FACE:
      case MYAU_3D:
        classicFace();
        break;
      case ARSENIC_SIMPLE:
      case MYAU_SIMPLE:
        simple();
        break;
      case COOLWARE_FLAT:
        coolware(false);
        break;
      case COOLWARE_ISLAND:
        coolware(true);
        break;
      case MYAU_DEFAULT:
        myauDefault();
        break;
      case MYAU_CIRCLE:
        circle();
        break;
      case MYAU_BLUEDEV:
        blueDev();
        break;
      case MYAU_ASTOLFO:
        myauAstolfo();
        break;
      case MYAU_MOON:
      case MYAU_RISE:
      case MYAU_TENACITY:
        myauRounded();
        break;
      case MYAU_NEVERLOSE:
        neverlose();
        break;
      default:
        throw new IllegalStateException("Not a donor design: " + design);
    }
  }

  private void yuri() {
    outline(0, 0, w, h, 6, .6f, first, 0x82000000);
    portrait(4, 4, 28, 6);
    centered(name(), 36, 3, w - 40, 9, true, WHITE);
    bar(36, 17, w - 40, 3, 1.5f, first, first, false);
    centered(info(), 36, 24, w - 40, 7, false, MUTED);
  }

  private void pulsive() {
    if (nativeBackground()) {
      // One outer silhouette; only the top of the header and bottom of the body curve.
      boolean clipped = owner.beginTargetClip(clip, ox, oy, w, h, 6);
      try {
        canvas.clip = clipped ? clip : null;
        if (clipped) {
          rect(0, 0, w, 16, 0x6428282c);
          rect(0, 16, w, h - 16, 0x96121214);
        } else {
          round(0, 0, w, h, 6, 0x96121214);
          rect(0, 0, w, 16, 0x6428282c);
        }
      } finally {
        canvas.clip = null;
        if (clipped) clip.end();
      }
    }
    centered("§ltarget §rhud", 0, 3, w, 9, false, WHITE);
    portrait(8, 20, 22, 6);
    text(name(), 35, 22, 9, true, WHITE, w - 43);
    text("Health: " + HudRenderer.decimal(hp()), 35, 34, 7, false, MUTED, w - 43);
    bar(8, 45, w - 16, 5, 2.5f, first, first, false);
  }

  private void compact(boolean atmosphere) {
    if (nativeBackground()) rect(0, 0, w, h, atmosphere ? 0xaa232323 : 0xa00f0f0f);
    int tx = atmosphere ? 32 : 24;
    portrait(atmosphere ? 2 : 3, atmosphere ? 2 : 3, atmosphere ? 27 : 20, 0);
    text(name(), tx, 2, 9, true, WHITE, w - tx - 3);
    equipment(tx, atmosphere ? 11 : 12, 10, true);
    right(diff(), w - 3, atmosphere ? 16 : 19, 6, false, 0xffaaaaaa);
    bar(atmosphere ? tx : 2, 25, atmosphere ? w - tx - 3 : w - 4, 4, 0, first, second, true);
  }

  private void astolfo(boolean old) {
    if (nativeBackground()) rect(0, 0, w, h, old ? 0xaa000000 : 0x96000000);
    model(1, 1, old ? 26 : 30, h - 3);
    int tx = old ? 28 : 38;
    text(name(), tx, old ? 4 : 2, 9, false, WHITE, w - tx - 4);
    if (old) {
      rect(28, 14, 94 * ratio(), 12, healthColor());
      centered(HudRenderer.decimal(hp() / 2), 28, 16, 94, 8, false, WHITE);
      equipment(28, 28, 14, false);
    } else {
      text(HudRenderer.decimal(hp()) + " ❤", 38, 12, 13.5f, false, first, w - 40);
      bar(37, 26, 52, 6, 0, first, first, false);
    }
  }

  private void novoline(boolean old) {
    if (old) {
      if (nativeBackground()) rect(0, 0, w, h, 0x64000000);
      model(1, 1, 24, 26);
      text(name(), 28, 4, 9, false, WHITE, w - 31);
      equipment(24, 12, 12, false);
      bar(0, h - 2, w, 2, 0, first, first, false);
    } else {
      if (nativeBackground()) {
        rect(0, 0, w, h, 0xff151515);
        rect(1, 1, w - 2, h - 2, 0xff2d2d2d);
      }
      portrait(3, 3, 24, 0);
      text(name(), 29, 5, 9, false, WHITE, w - 33);
      bar(29, 16, w - 33, 11, 0, first, first, false);
      centered(
          String.format(Locale.ROOT, "%.1f%%", ratio() * 100), 29, 17, w - 33, 8, false, WHITE);
    }
  }

  private void exhibition(int mode) {
    if (mode == 1) {
      if (nativeBackground()) rect(0, 0, w, h, 0xaa000000);
      model(1, 1, 30, 30);
      text(name(), 33, 2.5f, 7.65f, false, WHITE, w - 37);
      segmented(33, 12, w - 43, 3, 9);
      text("HP: " + (int) hp() + " | Dist: " + distance(), 33, 18, 5, false, WHITE, w - 35);
      EntityLivingBase entity = owner.targetEntity;
      text(
          "G: "
              + (entity == null || entity.onGround)
              + " CV: "
              + (entity == null || entity.onGround),
          33,
          23,
          5,
          false,
          WHITE,
          w - 35);
      text(
          "TCG: "
              + (entity == null ? 0 : entity.ticksExisted)
              + " HURT: "
              + (entity == null ? 0 : entity.hurtTime),
          33,
          28,
          5,
          false,
          WHITE,
          w - 35);
      return;
    }
    if (nativeBackground()) {
      rect(0, 0, w, h, 0xff0a0a0a);
      rect(1, 1, w - 2, h - 2, 0xff222222);
      rect(3, 3, w - 6, h - 6, 0xff2c2c2c);
      rect(4, 4, w - 8, h - 8, 0xff161616);
    }
    float side = mode == 2 ? 36 : 30, tx = side + 12;
    canvas.outline(ox + 7, oy + 7, side, side, 0xff303030);
    model(8, 8, side - 2, side - 2);
    text(name(), tx, mode == 2 ? 6 : 8, 8, mode == 2, WHITE, w - tx - 6);
    segmented(tx, mode == 2 ? 15 : 17, w - tx - 8, 4, mode == 2 ? 1 : 11);
    text(
        "HP: " + Math.round(hp() + owner.absorption) + " | Dist: " + distance(),
        tx,
        mode == 2 ? 23 : 24,
        6,
        false,
        WHITE,
        w - tx - 5);
    if (mode == 0) equipment(tx, 31, 11, true);
  }

  private void raven(boolean old) {
    if (old) outline(0, 0, w, h, 6, .6f, first, 0x78141414);
    else round(0, 0, w, h, 6, 0x78141414);
    text(ravenTitle(), 3, 6, 9, false, WHITE, w - 6);
    int color =
        hp() <= 5 ? 0xffff4949 : hp() <= 9 ? 0xfff88c52 : hp() <= 17 ? 0xfff8e34d : 0xff60f360;
    if (module().theme() != TargetHudModule.Theme.ORIGINAL) color = first;
    bar(3, 19, w - 6, 3.5f, 1.5f, color, color, true);
  }

  private void b4(boolean legacy) {
    boolean myau = design == MYAU_RAVEN_MODERN || design == MYAU_RAVEN_LEGACY;
    if (legacy) outline(0, 0, w, h, 10, 1, first, myau ? 0x6e000000 : 0x64000000);
    else round(0, 0, w, h, myau ? 8 : 5, myau ? 0xd2000000 : 0x64000000);
    centered(b4Title(w - 16), 8, 8, w - 16, 9, false, 0xffdcdcdc);
    bar(6, 24, w - 12, 5, 2, alpha(first, 210), alpha(second, 210), !legacy);
  }

  private void classicFace() {
    boolean arsenic = design == ARSENIC_FACE;
    int background = HudAppearance.blend(0x801a1a1a, 0x80ff0000, flash);
    if (arsenic) outline(0, 0, w, h, 8, 2, flash > 0 ? 0xffff0000 : first, background);
    else round(0, 0, w, h, 8, background);
    if (design == MYAU_3D) model(5, 3, 30, 32);
    else portrait(5, 5, 30, 0);
    text(name(), 40, 6, 9, false, WHITE, w - 45);
    text(
        HudRenderer.decimal(displayed) + "/" + HudRenderer.decimal(maxHp()),
        40,
        16,
        7,
        false,
        0xffcccccc,
        w - 45);
    int health = themedHealth(arsenic);
    bar(40, 25, w - 45, 8, 4, health, health, false);
    progress(
        40,
        35,
        w - 45,
        4,
        2,
        armorValue() / 20f,
        arsenic ? first : module().theme() == TargetHudModule.Theme.ORIGINAL ? 0xff00bfff : second,
        arsenic ? first : module().theme() == TargetHudModule.Theme.ORIGINAL ? 0xff00bfff : second);
  }

  private void simple() {
    boolean arsenic = design == ARSENIC_SIMPLE;
    int background = HudAppearance.blend(0x801a1a1a, 0x80ff0000, flash);
    if (arsenic) outline(0, 0, w, h, 8, 2, flash > 0 ? 0xffff0000 : first, background);
    else round(0, 0, w, h, 8, background);
    String healthText = Math.round(displayed) + "/" + Math.round(maxHp());
    text(name(), 5, 5, 9, false, WHITE, w - width(healthText, 9, false) - 15);
    right(healthText, w - 5, 5, 9, false, 0xffcccccc);
    int health = themedHealth(arsenic);
    bar(5, 19, w - 10, 8, 4, health, health, false);
  }

  private int themedHealth(boolean arsenic) {
    if (module().theme() != TargetHudModule.Theme.ORIGINAL) return first;
    return ratio() > .5 ? arsenic ? first : 0xff00ff00 : ratio() > .25 ? 0xffffff00 : 0xffff0000;
  }

  private void coolware(boolean island) {
    if (island) {
      if (nativeBackground()) {
        round(0, 0, w, h, h / 2, 0x14ffffff);
        round(.5f, .5f, w - 1, h - 1, h / 2, HudAppearance.blend(0xe40c0c0f, 0xe4480609, flash));
        rect(h / 2, 1, w - h, .7f, 0x1cffffff);
      }
      portrait(10, 10, 28, 4);
      text(name(), 46, 8, 12, false, WHITE, w - 56);
      bar(46, 24, w - 56, 2, 1, first, first, false);
      text(info(), 46, 31, 9, false, MUTED, w - 56);
    } else {
      if (nativeBackground()) {
        rect(0, 0, w, h, 0xd20d0d15);
        rect(0, h - 1, w, 1, alpha(first, 180));
      }
      portrait(6, 6, 32, 0);
      String state = status().equals("W") ? "WINNING" : status().equals("L") ? "LOSING" : "EVEN";
      text(name(), 44, 6, 13, false, 0xffe6e6eb, w - 58 - width(state, 9, false));
      right(
          state,
          w - 6,
          8,
          9,
          false,
          status().equals("W") ? 0xff50d278 : status().equals("L") ? 0xffdc4646 : 0xffa0a0af);
      bar(44, 22, w - 50, 3.5f, 0, first, first, false);
      text(
          HudRenderer.decimal(hp()) + "  -  " + distance() + " ❤",
          44,
          28,
          10,
          false,
          0xff9b9baf,
          w - 50);
    }
  }

  private void myauDefault() {
    if (nativeBackground()) rect(0, 0, w, h, 0x40000000);
    portrait(2, 2, 23, 0);
    text(name(), 27, 2, 9, false, WHITE, w - 40);
    right(status().equals("N") ? "D" : status(), w - 2, 2, 9, true, healthColor());
    text(HudRenderer.decimal(hp() / 2) + " §c❤", 27, 12, 9, false, WHITE, w - 55);
    right(
        String.format(Locale.ROOT, "%+.1f", (ownHp() - hp()) / 2),
        w - 2,
        12,
        9,
        false,
        healthColor());
    bar(27, 22, w - 29, 3, 0, healthColor(), healthColor(), false);
  }

  private void circle() {
    portrait(5, 5, 30, 15);
    boolean original = module().theme() == TargetHudModule.Theme.ORIGINAL;
    arc(20, 20, 17.5f, 3, -90, 180 * ratio(), original ? 0xffff0000 : first);
    arc(
        20,
        20,
        17.5f,
        3,
        90,
        180 * Math.min(1, armorValue() / 20f),
        original ? 0xff00bfff : second);
    text(name(), 40, 8, 9, false, WHITE, w - 42);
    text(
        HudRenderer.decimal(displayed) + "/" + HudRenderer.decimal(maxHp()),
        40,
        22,
        9,
        false,
        0xffcccccc,
        w - 42);
  }

  private void blueDev() {
    round(0, 0, w, h, 10, HudAppearance.blend(0xb40f1623, 0xb4730000, flash));
    portrait(6, 6, 32, 0);
    text(name(), 45, 7, 9, false, WHITE, w - 51);
    bar(45, 24, 110, 6, 3, first, second, false);
    progress(45, 34, 110, 4, 2, armorValue() / 20f, 0xff46aaff, 0xff46aaff);
    text(HudRenderer.decimal(displayed) + " ❤", 45, 43, 8, false, 0xffdcdcdc, 55);
    text(armorValue() + " armor", 105, 43, 8, false, 0xff78c8ff, w - 109);
  }

  private void myauAstolfo() {
    if (nativeBackground()) rect(0, 0, w, h, 0xb4000000);
    model(3, 2, 42, 47);
    text(name(), 50, 6, 9, false, WHITE, w - 54);
    text(HudRenderer.decimal(displayed) + " ❤", 50, 22, 13.5f, false, first, w - 54);
    bar(48, 42, w - 54, 7, 0, first, second, false);
  }

  private void myauRounded() {
    boolean moon = design == MYAU_MOON, rise = design == MYAU_RISE;
    round(0, 0, w, h, rise || moon ? 8 : 6, moon ? 0xb4141414 : rise ? 0xb40a0a0a : 0xb4000000);
    if (rise) {
      portrait(4, 3, 33, 6);
      text(name(), 42, 9, 9, false, WHITE, w - 80);
      right(HudRenderer.decimal(displayed), w - 8, 9, 9, false, first);
      bar(42, 22, w - 48, 6, 3, first, second, false);
    } else if (moon) {
      portrait(3, 3, 35, 8);
      text(name(), 40, 6, 8, false, WHITE, w - 44);
      text(HudRenderer.decimal(displayed) + "HP", 40, 17, 6, false, WHITE, w - 44);
      bar(42, 26.5f, w - 48, 8.5f, 4, first, second, false);
    } else {
      portrait(4, 4, 34, 6);
      text(name(), 43, 10, 10, false, WHITE, w - 47);
      text("HP: " + HudRenderer.decimal(displayed), 43, 22, 6, false, WHITE, w - 47);
      bar(44, 30, w - 52, 6, 3, first, second, false);
    }
  }

  private void neverlose() {
    round(0, 0, w, h, 4, 0xb40a0a10);
    portrait(3, 3, 26, 4);
    arc(w - 15, 16, 12, 3, -90, 360, 0x64000000);
    arc(w - 15, 16, 12, 3, -90, 360 * ratio(), first);
    text(name(), 34, 8, 8, false, WHITE, w - 66);
    text("Dist: " + distance(), 34, 20, 6, false, first, w - 66);
  }

  private void rect(float x, float y, float width, float height, int color) {
    if (width > 0 && height > 0) canvas.rect(ox + x, oy + y, width, height, color);
  }

  private void round(float x, float y, float width, float height, float radius, int color) {
    if (x == 0 && y == 0 && width == w && height == h && !nativeBackground()) return;
    MenuSurface.rounded(canvas, ox + x, oy + y, width, height, radius, color);
  }

  private boolean nativeBackground() {
    return owner.hud.styles.get(HudModule.Element.TARGET).background.get()
        == HudElementStyle.Background.AUTO;
  }

  private void outline(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float border,
      int color,
      int fill) {
    if (!nativeBackground()) return;
    round(x, y, width, height, radius, fill);
    RenderStyle.text();
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    float fringe = .8f / Math.max(.1f, owner.pixels);
    float thickness = Math.max(border, 1.25f / Math.max(.1f, owner.pixels));
    try {
      outlineBand(x, y, width, height, radius, fringe, 0, color & 0xffffff, color);
      outlineBand(x, y, width, height, radius, 0, -thickness, color, color);
      outlineBand(
          x, y, width, height, radius, -thickness, -thickness - fringe, color, color & 0xffffff);
    } finally {
      GlStateManager.shadeModel(GL11.GL_FLAT);
    }
  }

  private void outlineBand(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float outer,
      float inner,
      int outerColor,
      int innerColor) {
    GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
    try {
      for (int i = 0; i <= 52; i++) {
        int point = i % 52, corner = point / 13;
        double angle = Math.toRadians(corner * 90 + point % 13 * 7.5);
        float cx = ox + x + (corner == 0 || corner == 3 ? width - radius : radius);
        float cy = oy + y + (corner < 2 ? height - radius : radius);
        strokeVertex(cx, cy, angle, radius + outer, outerColor);
        strokeVertex(cx, cy, angle, radius + inner, innerColor);
      }
    } finally {
      GL11.glEnd();
    }
  }

  private static void strokeVertex(float x, float y, double angle, float radius, int color) {
    GL11.glColor4f(
        (color >> 16 & 255) / 255f,
        (color >> 8 & 255) / 255f,
        (color & 255) / 255f,
        (color >>> 24) / 255f);
    GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
  }

  private void gradient(
      float x, float y, float width, float height, float radius, int start, int end) {
    if (width <= 0 || height <= 0) return;
    boolean clipped =
        radius > 0 && owner.beginTargetClip(clip, ox + x, oy + y, width, height, radius);
    try {
      canvas.clip = clipped ? clip : null;
      if (radius > 0 && !clipped) round(x, y, width, height, radius, start);
      else canvas.gradient(ox + x, oy + y, width, height, start, end, end, start);
    } finally {
      canvas.clip = null;
      if (clipped) clip.end();
    }
  }

  private void bar(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int start,
      int end,
      boolean delayed) {
    round(x, y, width, height, radius, 0x70202020);
    if (delayed)
      gradient(
          x,
          y,
          width * (float) HudLayout.clamp(trail / maxHp()),
          height,
          radius,
          dark(start),
          dark(end));
    gradient(
        x, y, width * (float) HudLayout.clamp(displayed / maxHp()), height, radius, start, end);
  }

  private void progress(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float fraction,
      int start,
      int end) {
    round(x, y, width, height, radius, 0x40404040);
    gradient(x, y, width * (float) HudLayout.clamp(fraction), height, radius, start, end);
  }

  private void segmented(float x, float y, float width, float height, int segments) {
    rect(x, y, width, height, 0xff080808);
    rect(x + .5f, y + .5f, Math.max(0, width - 1) * ratio(), height - 1, healthColor());
    for (int i = 1; i < segments; i++) rect(x + width * i / segments, y, .6f, height, 0xff080808);
  }

  private static int dark(int color) {
    return HudAppearance.blend(color, color & 0xff000000, .65f);
  }

  private static int alpha(int color, int alpha) {
    return alpha << 24 | color & 0xffffff;
  }

  private float width(String value, float size, boolean heavy) {
    HudFont font = heavy ? bold : regular;
    float scale = textScale(size, heavy);
    return font.width(value) * scale;
  }

  private float textScale(float size, boolean heavy) {
    float scale = size / (heavy ? boldLine : regularLine);
    // Measure and rasterize after every enclosing transform, including the design's text size.
    // Native bitmap faces then quantize texels once; vector faces upload at final framebuffer size.
    (heavy ? bold : regular).raster(textPixels * scale, !bitmap);
    return scale;
  }

  private void text(
      String value, float x, float y, float size, boolean heavy, int color, float available) {
    if (available <= 0) return;
    HudFont font = heavy ? bold : regular;
    float scale = textScale(size, heavy);
    String trimmed = font.trim(value, (int) (available / scale));
    RenderStyle.text();
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(
          owner.snapTargetText(ox + x, true), owner.snapTargetText(oy + y, false), 0);
      GlStateManager.scale(scale, scale, 1);
      boolean shadow =
          module().theme() != TargetHudModule.Theme.CUSTOM
              || owner.hud.styles.get(HudModule.Element.TARGET).shadow.get();
      font.draw(trimmed, 0, 0, color, shadow, owner.pixels * scale);
    } finally {
      GlStateManager.popMatrix();
    }
  }

  private void right(String value, float x, float y, float size, boolean heavy, int color) {
    float width = width(value, size, heavy);
    text(value, x - width, y, size, heavy, color, width + 1);
  }

  private void centered(
      String value, float x, float y, float available, float size, boolean heavy, int color) {
    float width = Math.min(available, width(value, size, heavy));
    text(value, x + (available - width) / 2, y, size, heavy, color, width + 1);
  }

  private void portrait(int x, int y, int size, int radius) {
    boolean clipped =
        owner.skin != null
            && radius > 0
            && owner.beginTargetClip(clip, ox + x, oy + y, size, size, radius);
    try {
      if (clipped) clip.texture(true);
      owner.targetPortrait((int) ox + x, (int) oy + y, size);
    } finally {
      if (clipped) clip.end();
    }
    if (flash > 0) round(x, y, size, size, radius, alpha(0xffff3030, (int) (flash * 80)));
  }

  private void model(int x, int y, float width, float height) {
    owner.targetModel((int) ox + x, (int) oy + y, (int) width, (int) height);
  }

  private void equipment(float x, float y, float size, boolean held) {
    EntityLivingBase entity = owner.targetEntity;
    if (entity == null) return;
    float scale = size / 16;
    int count = held ? 5 : 4;
    for (int i = 0; i < count; i++) {
      net.minecraft.item.ItemStack stack =
          held && i == 0 ? entity.getHeldItem() : entity.getCurrentArmor(3 - (held ? i - 1 : i));
      if (stack == null) continue;
      GlStateManager.pushMatrix();
      try {
        GlStateManager.translate(ox + x + i * (size + 2), oy + y, 0);
        GlStateManager.scale(scale, scale, 1);
        owner.item(stack, 0, 0);
      } finally {
        GlStateManager.popMatrix();
      }
    }
  }

  private void arc(
      float x, float y, float radius, float thickness, float start, float degrees, int color) {
    if (degrees <= 0) return;
    RenderStyle.text();
    GlStateManager.disableTexture2D();
    GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
    try {
      GL11.glColor4f(
          (color >> 16 & 255) / 255f,
          (color >> 8 & 255) / 255f,
          (color & 255) / 255f,
          (color >>> 24) / 255f);
      int steps = Math.max(1, (int) Math.ceil(degrees / 5));
      for (int i = 0; i <= steps; i++) {
        double angle = Math.toRadians(start + degrees * i / steps);
        GL11.glVertex2d(
            ox + x + Math.cos(angle) * (radius - thickness / 2),
            oy + y + Math.sin(angle) * (radius - thickness / 2));
        GL11.glVertex2d(
            ox + x + Math.cos(angle) * (radius + thickness / 2),
            oy + y + Math.sin(angle) * (radius + thickness / 2));
      }
    } finally {
      GL11.glEnd();
    }
  }

  void reset() {
    animated = null;
    animatedDesign = null;
    lastFrame = damageAt = 0;
  }

  @Override
  public void close() {
    reset();
    regular.close();
    bold.close();
    clip.close();
  }
}
