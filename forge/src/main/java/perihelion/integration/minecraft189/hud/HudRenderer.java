package perihelion.integration.minecraft189.hud;

import static perihelion.integration.minecraft189.hud.HudModule.Element;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.InputEvent;
import perihelion.core.input.Keybind;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.GuiValues;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.*;

/** Tick snapshots; the render path only draws bounded, cached data in one GL scope. */
final class HudRenderer implements AutoCloseable {
  static final int TEXT = 0xffcccccc, MUTED = 0xffa8a8a8;
  private static final float LOGO_SCALE = 1.3f;
  private static final int BRAND_HEIGHT = 24;
  private static final int THEME_ELEMENT_RANGE = Math.max(1, Element.values().length - 1);
  final HudModule hud;
  final Minecraft mc = Minecraft.getMinecraft();
  private final RenderState state = new RenderState();
  HudFont text = new HudFont();
  private final HudFont baseFont = text;
  private final EnumMap<Element, HudFont> elementFonts = new EnumMap<>(Element.class);
  private final HudBlur blur = new HudBlur();
  private final perihelion.integration.minecraft189.gui.modern.RoundedClip backgroundClip =
      new perihelion.integration.minecraft189.gui.modern.RoundedClip();
  private Element draggingElement;
  private HudElementStyle activeStyle;
  private Box activeBox, activeScreenBox;
  private String moduleLabelsSeen = "";
  final perihelion.integration.minecraft189.branding.BrandTexture brand =
      new perihelion.integration.minecraft189.branding.BrandTexture();
  final HudNavigator navigation;
  HudNavigator.Layout navigationLayout;
  HudNavigator.SettingRows navigationSettings;
  HudNavigator.ModuleRows navigationModules;
  float pixels = 1;
  private float density = 1;
  private final UiCanvas canvas = new UiCanvas();
  final EnumMap<Element, Box> boxes = new EnumMap<>(Element.class);
  final List<ModuleRow> modules = new ArrayList<>();
  private final Map<ModuleEntry, Boolean> enabled = new IdentityHashMap<>();
  final List<String> effects = new ArrayList<>();
  final List<PlayerRow> players = new ArrayList<>();
  final Set<UUID> targets = new LinkedHashSet<>();
  final Set<UUID> pinned = new LinkedHashSet<>();
  private final Map<UUID, Long> observed =
      new LinkedHashMap<UUID, Long>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<UUID, Long> entry) {
          return size() > 256;
        }
      };
  final Deque<Notice> notices = new ArrayDeque<>();
  private final HudLayout.Clicks left = new HudLayout.Clicks(), right = new HudLayout.Clicks();
  final ItemStack[] armor = new ItemStack[4];
  final String[] durability = new String[4];
  private int ticks, updates;
  int playerColumnOffset, playerRowOffset;
  private final Set<UUID> nickNotices = new HashSet<>(), tagNotices = new HashSet<>();
  private long sampled = Long.MIN_VALUE;
  String watermark = "Perihelion",
      info = "",
      speed = "",
      targetName = "",
      targetInfo = "",
      targetDistance = "",
      cps = "0 / 0 CPS";
  float health, absorption, maximum;
  ResourceLocation skin;
  boolean hasTarget, hasArmor;
  private EntityLivingBase attackedTarget;
  private long attackedAt;
  private boolean notifying;
  private boolean classic;
  private boolean modern;
  final RavenHudRenderer raven;
  private int adjustDraws;
  private boolean hideVisualsSeen, suffixesSeen;
  private HudAppearance.Sort moduleSortSeen;
  private int primary, secondary, background;

  HudRenderer(HudModule hud) {
    this.hud = hud;
    navigation = new HudNavigator(hud.runtime.modules());
    raven = new RavenHudRenderer(this);
    notifying = show(Element.NOTIFICATIONS);
    for (Element e : Element.values()) {
      boxes.put(e, new Box());
      elementFonts.put(e, e == Element.MODULES ? baseFont : new HudFont(baseFont));
    }
    for (ModuleEntry e : hud.runtime.modules().entries())
      enabled.put(e, e.state() == ModuleState.ENABLED);
  }

  void input(InputEvent event) {
    if (show(Element.TAB_GUI)
        && event.pressed()
        && event.key().device() == Keybind.Device.KEYBOARD
        && mc.currentScreen == null
        && !mc.gameSettings.hideGUI
        && !(hud.hideDebug.get() && mc.gameSettings.showDebugInfo))
      navigate(event.key().code(), Keyboard.getEventCharacter());
    if (!hud.shown.get(Element.KEYS).get()
        || !event.pressed()
        || mc.currentScreen != null
        || event.key().device() != Keybind.Device.MOUSE) return;
    if (event.key().code() == 0) left.add(System.nanoTime());
    if (event.key().code() == 1) right.add(System.nanoTime());
  }

  void attacked(EntityLivingBase entity) {
    if (entity == null || entity == mc.thePlayer || entity.worldObj != mc.theWorld) return;
    attackedTarget = entity;
    attackedAt = System.nanoTime();
    sampled = Long.MIN_VALUE;
  }

  void update(boolean preview) {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    ScaledResolution resolution = new ScaledResolution(mc);
    density = GuiTheme.modern(hud.runtime) ? resolution.getScaledHeight() / 480f : 1;
    if (configureFont(mc.displayWidth / (float) resolution.getScaledWidth())) updateModules();
    // 5 Hz snapshots independent of frame rate. Editor uses its own isolated preview renderer.
    if (sampled != Long.MIN_VALUE && ++ticks % 4 != 0) return;
    sampled = System.nanoTime();
    updates++;
    boolean notifyNow = show(Element.NOTIFICATIONS);
    if (!notifyNow || !notifying) notices.clear();
    if (notifyNow && !notifying) {
      for (ModuleEntry entry : hud.runtime.modules().entries())
        enabled.put(entry, entry.state() == ModuleState.ENABLED);
    }
    notifying = notifyNow;
    if (preview || show(Element.WATERMARK)) {
      int ping = ping(mc.thePlayer);
      watermark = "Perihelion";
      if (hud.watermarkVersion.get() && !hud.version.isEmpty())
        watermark += "  \u00a77v" + hud.version;
      if (hud.watermarkClock.get())
        watermark +=
            "  \u00a77"
                + java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
      if (hud.watermarkInfo.get())
        watermark +=
            "  \u00a77"
                + Minecraft.getDebugFPS()
                + " FPS  "
                + (mc.isSingleplayer() ? "Local" : ping < 0 ? "-- ms" : ping + " ms");
    }
    if (preview || show(Element.INFO)) {
      info =
          "XYZ  \u00a7f"
              + MathHelper.floor_double(mc.thePlayer.posX)
              + "  "
              + MathHelper.floor_double(mc.thePlayer.posY)
              + "  "
              + MathHelper.floor_double(mc.thePlayer.posZ);
      double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX,
          dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
      speed =
          decimal(
                  Math.sqrt(dx * dx + dz * dz)
                      * 20
                      * ((perihelion.integration.minecraft189.control.MinecraftTimerAccess) mc)
                          .perihelion$timer()
                          .timerSpeed)
              + " BPS  \u00a77|  \u00a7f"
              + mc.thePlayer.getHorizontalFacing().getName().toUpperCase(Locale.ROOT);
    }
    if (preview || show(Element.MODULES) || show(Element.NOTIFICATIONS)) updateModules();
    if (preview || show(Element.TARGET)) {
      EntityLivingBase entity = attackedTarget;
      if (entity != null
          && (sampled - attackedAt >= 3_000_000_000L
              || entity.isDead
              || entity.getHealth() <= 0
              || entity.worldObj != mc.theWorld
              || mc.theWorld.getEntityByID(entity.getEntityId()) != entity)) {
        attackedTarget = null;
        entity = null;
      }
      if (preview && entity == null) entity = mc.thePlayer;
      hasTarget =
          entity != null
              && !entity.isDead
              && entity.getHealth() > 0
              && entity.worldObj == mc.theWorld
              && (preview || mc.theWorld.getEntityByID(entity.getEntityId()) == entity);
      skin = null;
      if (hasTarget) {
        health = Math.max(0, entity.getHealth());
        maximum = Math.max(1, entity.getMaxHealth());
        absorption = Math.max(0, entity.getAbsorptionAmount());
        targetName = entity.getName();
        targetDistance = decimal(mc.thePlayer.getDistanceToEntity(entity)) + "m";
        targetInfo =
            decimal(health)
                + " HP"
                + (absorption > 0 ? " +" + decimal(absorption) : "")
                + "  \u00a77|  "
                + targetDistance;
        if (entity instanceof AbstractClientPlayer)
          skin = ((AbstractClientPlayer) entity).getLocationSkin();
      } else {
        targetName = "";
        targetInfo = "";
        targetDistance = "";
      }
    }
    if (preview || show(Element.ARMOR)) {
      hasArmor = false;
      for (int i = 0; i < 4; i++) {
        ItemStack stack = mc.thePlayer.inventory.armorInventory[3 - i];
        armor[i] = stack == null ? null : stack.copy();
        hasArmor |= stack != null;
        durability[i] =
            stack == null
                ? "--"
                : stack.isItemStackDamageable()
                    ? Math.max(
                            0,
                            (int)
                                Math.ceil(
                                    100.0
                                        * (stack.getMaxDamage() - stack.getItemDamage())
                                        / Math.max(1, stack.getMaxDamage())))
                        + "%"
                    : "--";
      }
    }
    if (preview || show(Element.EFFECTS)) {
      effects.clear();
      List<PotionEffect> active = new ArrayList<>(mc.thePlayer.getActivePotionEffects());
      active.sort(Comparator.comparingInt(PotionEffect::getPotionID));
      for (PotionEffect effect : active) {
        if (effects.size() >= 12) break;
        int id = effect.getPotionID();
        Potion potion = id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
        if (potion != null)
          effects.add(
              I18n.format(potion.getName())
                  + (effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "")
                  + "  \u00a77"
                  + (effect.getIsPotionDurationMax()
                      ? "**:**"
                      : HudLayout.duration(effect.getDuration() / 20)));
      }
    }
    if (preview || show(Element.PLAYERS)) updatePlayers(preview);
    if (preview || show(Element.KEYS))
      cps = left.count(sampled) + " / " + right.count(sampled) + " CPS";
    while (!notices.isEmpty() && sampled - notices.peekFirst().time >= 3_000_000_000L)
      notices.removeFirst();
  }

  private void updateModules() {
    configureElementFont(Element.MODULES);
    moduleLabelsSeen = moduleLabelKey();
    hideVisualsSeen = hud.hideVisuals.get();
    suffixesSeen = hud.suffixes.get();
    moduleSortSeen = hud.moduleSort.get();
    List<ModuleRow> next = new ArrayList<>();
    for (ModuleEntry entry : hud.runtime.modules().entries()) {
      boolean on = entry.state() == ModuleState.ENABLED;
      Boolean was = enabled.put(entry, on);
      if (entry.info().id().equals("hud") || entry.info().category() == ModuleCategory.CLIENT)
        continue;
      if (was != null && was != on && show(Element.NOTIFICATIONS)) {
        if (notices.size() == 4) notices.removeFirst();
        notices.addLast(new Notice(HudNavigator.label(entry), on, sampled));
      }
      if (!on || (hideVisualsSeen && entry.info().category() == ModuleCategory.RENDER)) continue;
      String label = HudNavigator.label(entry);
      Setting<?> mode = entry.module().settings().get("mode");
      if (hud.suffixes.get() && mode != null && mode.get() instanceof Enum<?>)
        label = hud.moduleSuffix.get().join(label, GuiValues.readable(mode.get().toString()));
      if (hud.moduleLowercase.get()) label = label.toLowerCase(Locale.ROOT);
      next.add(new ModuleRow(label, text.width(label)));
    }
    // A stable sort and no render-frame sorting. Width changes (including font reload) refresh
    // ordering.
    next.sort(
        hud.moduleSort.get() == HudAppearance.Sort.ALPHABETICAL
            ? Comparator.comparing(row -> row.label.toLowerCase(Locale.ROOT))
            : Comparator.comparingInt((ModuleRow row) -> row.width)
                .reversed()
                .thenComparing(row -> row.label));
    modules.clear();
    modules.addAll(next);
  }

  private void updatePlayers(boolean preview) {
    players.clear();
    boolean tab =
        hud.overlay.source.get() == OverlaySettings.Source.TAB_LIST
            && OverlaySettings.hypixel()
            && mc.getNetHandler() != null;
    if (tab) {
      for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
        UUID id = info.getGameProfile().getId();
        String username = info.getGameProfile().getName();
        if (id == null
            || username == null
            || !username.matches("[A-Za-z0-9_]{1,16}")
            || id.version() != 4 && id.version() != 3
            || info.getGameType() == net.minecraft.world.WorldSettings.GameType.SPECTATOR
            || !hud.overlay.includeSelf.get() && id.equals(mc.thePlayer.getUniqueID())) continue;
        EntityPlayer loaded = mc.theWorld.getPlayerEntityByUUID(id);
        String name =
            info.getDisplayName() == null
                ? net.minecraft.scoreboard.ScorePlayerTeam.formatPlayerName(
                    info.getPlayerTeam(), username)
                : info.getDisplayName().getFormattedText();
        players.add(
            loaded == null
                ? new PlayerRow(
                    id,
                    username,
                    name,
                    "--",
                    info.getPlayerTeam() == null ? "" : info.getPlayerTeam().getRegisteredName(),
                    info.getLocationSkin(),
                    false,
                    "--",
                    "--",
                    info.getResponseTime(),
                    -1,
                    "--",
                    observed.computeIfAbsent(id, ignored -> sampled),
                    sampled)
                : row(loaded));
        if (players.size() >= 256) break;
      }
    } else {
      double range = hud.playerRange.get() * hud.playerRange.get();
      for (EntityPlayer p : mc.theWorld.playerEntities) {
        if (!hud.overlay.includeSelf.get() && p == mc.thePlayer
            || p.isDead
            || p.isInvisible()
            || p.isSpectator()
            || mc.thePlayer.getDistanceSqToEntity(p) > range) continue;
        players.add(row(p));
        if (players.size() >= 256) break;
      }
    }
    long now = System.currentTimeMillis();
    if (!preview) updateOverlayTracking(now);
    List<UUID> present = new ArrayList<>();
    for (PlayerRow row : players) present.add(row.id);
    if (!preview) hud.encounters.update(present, now);
    boolean automatic = !preview && hud.overlay.automatic.get() && hud.overlay.active();
    OverlayLookup.Options options = automatic ? hud.overlay.options() : null;
    for (PlayerRow row : players) {
      row.profile = hud.overlayLookup == null ? null : hud.overlayLookup.get(row.id);
      OverlayEncounters.Entry encounter = hud.encounters.get(row.id);
      row.encounters = encounter == null ? 1 : encounter.encounters;
      row.joined = encounter == null ? now : encounter.joined;
      for (HudModule.PlayerColumn column : hud.playerColumns.get())
        if (row.profile != null)
          row.values.put(column, row.profile.column(hud.overlay.mode.get(), column, now));
    }
    // Fetch the whole bounded roster, so statistics sorting/filtering does not strand rows below
    // the limit.
    if (automatic && hud.overlayLookup != null)
      for (PlayerRow row : players)
        if (!hud.overlay.hidden(row.id, row.username))
          hud.overlayLookup.request(row.id, row.username, options);
    if (!preview) notifyOverlay();
    players.removeIf(
        row ->
            hud.overlay.hidden(row.id, row.username)
                || !hud.overlay.matches(row.profile)
                || hud.overlay.removeFinals.get()
                    && hud.tracker != null
                    && hud.tracker.finalKilled.contains(row.username.toLowerCase(Locale.ROOT))
                || hud.overlay.whoOnly.get()
                    && hud.tracker != null
                    && !hud.tracker.who.isEmpty()
                    && !hud.tracker.who.contains(row.username.toLowerCase(Locale.ROOT)));
    players.sort(this::comparePlayers);
    if (players.size() > hud.playerLimit.get())
      players.subList(hud.playerLimit.get(), players.size()).clear();
  }

  private void updateOverlayTracking(long now) {
    if (hud.tracker == null) return;
    OverlayTracker tracker = hud.tracker;
    List<String> sidebar = hud.overlay.sidebar();
    boolean pregame = hud.overlay.active() && OverlayTracker.pregame(sidebar);
    if (pregame && !tracker.pregame && !tracker.playing) tracker.entered = now;
    tracker.pregame = pregame;
    if (pregame && tracker.playing && now - tracker.playingSince > 3000) {
      tracker.playing = false;
      tracker.warned = false;
      tracker.whoSent = false;
      tracker.partySent = false;
      tracker.finalKilled.clear();
      tracker.possibleParty.clear();
      tracker.who.clear();
      tracker.entered = now;
    }
    if (pregame && now - tracker.entered >= 1500) {
      if (hud.overlay.autoWho.get() && !tracker.whoSent) {
        tracker.whoSent = true;
        mc.thePlayer.sendChatMessage("/who");
      }
      if (hud.overlay.autoParty.get() && !tracker.partySent) {
        tracker.partySent = true;
        mc.thePlayer.sendChatMessage("/party list");
      }
    }
    if (tracker.playing && !OverlayTracker.game(sidebar) && !pregame && !tracker.resultSent) {
      tracker.resultSent = true;
      if (hud.overlay.gameResults.get())
        for (Map.Entry<String, int[]> entry : tracker.combat.entrySet()) {
          if (!entry.getKey().equalsIgnoreCase(mc.thePlayer.getName())
              && !tracker.party.contains(entry.getKey())) continue;
          int[] c = entry.getValue();
          local(
              entry.getKey()
                  + " game: "
                  + c[0]
                  + "/"
                  + c[1]
                  + " K/D, "
                  + c[2]
                  + "/"
                  + c[3]
                  + " finals, "
                  + c[4]
                  + " beds");
        }
    }
  }

  private void notifyOverlay() {
    if (!hud.overlay.active()) return;
    double fkdr = 0;
    int known = 0;
    for (PlayerRow row : players)
      if (row.profile != null) {
        OverlayProfile profile = row.profile;
        if (hud.overlay.nickAlerts.get()
            && profile.nicked
            && nickNotices.size() < 256
            && nickNotices.add(row.id)) local(row.username + ": suspected nick");
        if (hud.overlay.tagAlerts.get()
            && !profile.tags.isEmpty()
            && tagNotices.size() < 256
            && tagNotices.add(row.id))
          for (OverlayProfile.Tag tag : profile.tags)
            local(row.username + " - " + tag.source + ": " + tag.type + " " + tag.reason);
        double value = profile.fkdr(hud.overlay.mode.get());
        if (value >= 0) {
          fkdr += value;
          known++;
        }
      }
    if (hud.tracker != null
        && hud.tracker.pregame
        && hud.overlay.dodgeWarning.get()
        && !hud.tracker.warned
        && known >= 2
        && fkdr / known >= hud.overlay.dodgeThreshold.get()) {
      hud.tracker.warned = true;
      local(
          "Lobby FKDR "
              + String.format(Locale.ROOT, "%.2f", fkdr / known)
              + " across "
              + known
              + " known profiles");
    }
  }

  private void local(String message) {
    mc.thePlayer.addChatMessage(
        new net.minecraft.util.ChatComponentText("[Perihelion Overlay] " + message));
  }

  private PlayerRow row(EntityPlayer p) {
    String team = "";
    if (p.getTeam() instanceof net.minecraft.scoreboard.ScorePlayerTeam) {
      net.minecraft.util.EnumChatFormatting color =
          ((net.minecraft.scoreboard.ScorePlayerTeam) p.getTeam()).getChatFormat();
      team =
          color.isColor()
              ? color.toString() + GuiValues.readable(color.getFriendlyName())
              : p.getTeam().getRegisteredName();
    }
    EntityLivingBase target = hud.target.get();
    if (target == null
        && mc.objectMouseOver != null
        && mc.objectMouseOver.entityHit instanceof EntityLivingBase)
      target = (EntityLivingBase) mc.objectMouseOver.entityHit;
    return new PlayerRow(
        p.getUniqueID(),
        p.getName(),
        p.getDisplayName().getFormattedText(),
        Math.round((p.getHealth() + p.getAbsorptionAmount()) / Math.max(1, p.getMaxHealth()) * 100)
            + "%",
        team,
        p instanceof AbstractClientPlayer ? ((AbstractClientPlayer) p).getLocationSkin() : null,
        p == target,
        decimal(p.getHealth())
            + " / "
            + decimal(p.getMaxHealth())
            + (p.getAbsorptionAmount() > 0 ? " + " + decimal(p.getAbsorptionAmount()) : ""),
        decimal(mc.thePlayer.getDistanceToEntity(p)),
        ping(p),
        p.getTotalArmorValue(),
        p.getHeldItem() == null ? "None" : p.getHeldItem().getDisplayName(),
        observed.computeIfAbsent(p.getUniqueID(), ignored -> sampled),
        sampled);
  }

  private int comparePlayers(PlayerRow a, PlayerRow b) {
    int pin = Boolean.compare(pinned.contains(b.id), pinned.contains(a.id));
    if (pin != 0) return pin;
    OverlaySettings.Sort sort =
        OverlaySettings.hypixel() ? hud.overlay.sort.get() : OverlaySettings.Sort.DISTANCE;
    if (sort == OverlaySettings.Sort.NAME) return a.username.compareToIgnoreCase(b.username);
    double first = sortValue(a, sort), second = sortValue(b, sort);
    if (first < 0 || second < 0) {
      int missing = Boolean.compare(first < 0, second < 0);
      if (missing != 0) return missing;
    }
    int result = Double.compare(first, second);
    if (sort != OverlaySettings.Sort.DISTANCE
        && sort != OverlaySettings.Sort.JOIN_ORDER
        && hud.overlay.descending.get()) result = -result;
    return result != 0 ? result : a.id.compareTo(b.id);
  }

  private double sortValue(PlayerRow row, OverlaySettings.Sort sort) {
    switch (sort) {
      case JOIN_ORDER:
        return row.joined;
      case ENCOUNTERS:
        return row.encounters;
      case STARS:
        return row.profile == null || !row.profile.counts.containsKey("stars")
            ? -1
            : row.profile.counts.get("stars");
      case FKDR:
        return row.profile == null ? -1 : row.profile.fkdr(hud.overlay.mode.get());
      case WINSTREAK:
        return row.profile == null ? -1 : row.profile.numeric(hud.overlay.mode.get(), "winstreak");
      default:
        try {
          return Double.parseDouble(row.distance);
        } catch (NumberFormatException absent) {
          return -1;
        }
    }
  }

  private int ping(EntityPlayer p) {
    NetworkPlayerInfo info =
        mc.getNetHandler() == null ? null : mc.getNetHandler().getPlayerInfo(p.getUniqueID());
    return info == null ? -1 : Math.max(0, info.getResponseTime());
  }

  static String decimal(double value) {
    long tenths = Double.isFinite(value) ? Math.round(Math.max(0, value) * 10) : 0;
    return tenths / 10 + "." + tenths % 10;
  }

  boolean show(Element e) {
    return hud.shown.get(e).get();
  }

  private String moduleLabelKey() {
    return hud.moduleSuffix.get().name() + hud.moduleLowercase.get();
  }

  private boolean configureFont(float pixelScale) {
    pixels = pixelScale;
    return configureElementFont(Element.MODULES);
  }

  boolean configureElementFont(Element element) {
    text = elementFonts.get(element);
    HudElementStyle style = hud.styles.get(element);
    boolean changed =
        text.raster(
            pixels * style.scale.get().floatValue() * density, GuiTheme.modern(hud.runtime));
    boolean bold =
        style.font.get() == HudElementStyle.Font.CLIENT
            ? GuiTheme.modern(hud.runtime) && GuiTheme.bold(GuiTheme.preferences(hud.runtime))
            : style.bold.get();
    return text.choice(style.font(hud), bold) || changed;
  }

  HudFont elementFont(Element element) {
    return elementFonts.get(element);
  }

  void render(int width, int height, boolean preview, Element dragging, int dragX, int dragY) {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    draggingElement = dragging;
    classic = GuiTheme.style(hud.runtime) == ClickGuiModule.ClientStyle.RAVEN;
    modern = GuiTheme.modern(hud.runtime);
    pixels = mc.displayWidth / (float) width;
    density = modern ? height / 480f : 1;
    // Option changes apply to both the game HUD and the editor preview on this frame,
    // even between the slower world-data snapshots.
    if (configureFont(pixels)
        || hideVisualsSeen != hud.hideVisuals.get()
        || suffixesSeen != hud.suffixes.get()
        || moduleSortSeen != hud.moduleSort.get()
        || !moduleLabelsSeen.equals(moduleLabelKey())) updateModules();
    if (sampled == Long.MIN_VALUE) update(preview);
    density = modern ? height / 480f : 1;
    if (configureFont(pixels)) updateModules();
    long now = System.currentTimeMillis();
    canvas.pixelScale = pixels;
    canvas.modern = modern;
    if (modern) ModernTheme.palette(canvas, GuiTheme.palette(hud.runtime));
    boolean needsBlur = false;
    for (Element e : Element.values())
      if (show(e) && hud.styles.get(e).blur.get()) needsBlur = true;
    try (RenderState capture = state.open()) {
      if (needsBlur) blur.capture(mc);
      else blur.skip();
    }
    if (classic) {
      raven.render(width, height, preview, dragging, dragX, dragY);
      return;
    }
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      for (Element e : Element.values()) {
        Box b = boxes.get(e);
        b.visible = false;
        if (!show(e)) continue;
        configureElementFont(e);
        appearance(e, now);
        int w, h;
        switch (e) {
          case WATERMARK:
            if (brandedLogo()) {
              measureBrand(b);
              w = b.w;
              h = b.h;
              break;
            }
            if (modern) {
              w = text.width("\u00a7l" + watermark) + 2;
              h = text.height() + 4;
              break;
            }
            w =
                (int)
                    Math.ceil(
                        (text.width("\u00a7l" + watermark.substring(0, 1))
                                + text.width(watermark.substring(1))
                                + 2)
                            * LOGO_SCALE);
            h = (int) Math.ceil(11 * LOGO_SCALE);
            break;
          case TAB_GUI:
            // Position the permanent category rectangle first. Opening a pane must never
            // change the size used to resolve this saved position.
            w = HudNavigator.CATEGORY_WIDTH;
            h = HudNavigator.CATEGORIES.length * HudNavigator.ROW_HEIGHT;
            break;
          case MODULES:
            if (!preview && modules.isEmpty()) continue;
            w = 95;
            if (!modules.isEmpty()) {
              w = 0;
              for (ModuleRow row : modules)
                w = Math.max(w, row.width + hud.modulePadding.get() * 2);
            }
            h = Math.max(1, modules.size()) * moduleRowHeight();
            break;
          case INFO:
            w = Math.max(text.width(info), text.width(speed)) + 2;
            h = 21;
            break;
          case TARGET:
            if (!preview && !hasTarget) continue;
            w = 146;
            h = 38;
            break;
          case ARMOR:
            if (!preview && !hasArmor) continue;
            w = 100;
            h = 28;
            break;
          case EFFECTS:
            if (!preview && effects.isEmpty()) continue;
            w = Math.max(90, widest(effects) + 8);
            h = (modern ? 0 : 13) + Math.max(1, effects.size()) * 11;
            break;
          case KEYS:
            w = 90;
            h = 105;
            break;
          case PLAYERS:
            if (!preview
                && players.isEmpty()
                && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) continue;
            w = playerNameWidth();
            for (HudModule.PlayerColumn column : hud.playerColumns.get())
              w += playerColumnWidth(column);
            h = 11 + Math.max(1, players.size()) * 10;
            break;
          case NOTIFICATIONS:
            if (!preview && notices.isEmpty()) continue;
            w = 144;
            h = Math.max(1, notices.size()) * 25;
            break;
          default:
            continue;
        }
        placeStyledBox(e, b, w, h, width, height, dragging, dragX, dragY);
        if (e != Element.TAB_GUI || !navigation.expanded) drawBox(e, b, preview, width, height);
      }
      // Navigation is an interactive surface; other HUD cards must not cover its settings.
      Box tab = boxes.get(Element.TAB_GUI);
      if (navigation.expanded && tab.visible) {
        configureElementFont(Element.TAB_GUI);
        appearance(Element.TAB_GUI, now);
        drawBox(Element.TAB_GUI, tab, preview, width, height);
      }
    }
  }

  void placeStyledBox(
      Element e,
      Box b,
      int w,
      int h,
      int width,
      int height,
      Element dragging,
      int dragX,
      int dragY) {
    HudElementStyle style = hud.styles.get(e);
    b.scale = style.scale.get().floatValue() * density;
    b.padding = e == Element.TAB_GUI ? 0 : style.padding.get();
    if (b.content == null) b.content = new Box();
    b.arrayShape = null;
    b.content.x = b.content.y = b.padding;
    b.content.w = Math.max(1, Math.min(w, (int) ((width - 4) / b.scale) - b.padding * 2));
    b.content.h = Math.max(1, Math.min(h, (int) ((height - 4) / b.scale) - b.padding * 2));
    placeBox(
        e,
        b,
        (int) Math.ceil((b.content.w + b.padding * 2) * b.scale),
        (int) Math.ceil((b.content.h + b.padding * 2) * b.scale),
        width,
        height,
        dragging,
        dragX,
        dragY);
  }

  void placeBox(
      Element e,
      Box b,
      int w,
      int h,
      int width,
      int height,
      Element dragging,
      int dragX,
      int dragY) {
    b.w = Math.max(1, Math.min(width - 4, w));
    b.h = Math.max(1, Math.min(height - 4, h));
    b.x =
        e == dragging
            ? Math.max(0, Math.min(width - b.w, dragX))
            : HudLayout.position(hud.xs.get(e).get(), width, b.w);
    b.y =
        e == dragging
            ? Math.max(0, Math.min(height - b.h, dragY))
            : HudLayout.position(hud.ys.get(e).get(), height, b.h);
    if (e == Element.TAB_GUI
        && dragging != e
        && hud.xs.get(e).get() == 0
        && hud.ys.get(e).get() == 0
        && boxes.get(Element.WATERMARK).visible)
      b.y =
          Math.min(
              height - b.h, boxes.get(Element.WATERMARK).y + boxes.get(Element.WATERMARK).h + 2);
    if (e == Element.TAB_GUI) {
      b.anchorX = b.x;
      b.anchorY = b.y;
      b.anchorW = b.w;
      b.anchorH = b.h;
      navigationLayout =
          new HudNavigator.Layout(
              0,
              0,
              (int) ((width - b.anchorX) / b.scale),
              (int) ((height - b.anchorY) / b.scale),
              -(int) (b.anchorX / b.scale));
      if (navigation.expanded && navigationLayout.paneWidth > 0) {
        int paneHeight;
        if (navigation.settingsOpen) {
          navigationSettings =
              navigation.settingRows(
                  navigationLayout.paneWidth, navigationLayout.availableHeight, text::width);
          paneHeight = navigationSettings.height;
        } else {
          navigationModules = navigation.moduleRows(navigationLayout.availableHeight);
          paneHeight = navigationModules.height;
        }
        int left = Math.min(0, navigationLayout.paneX);
        int right =
            Math.max(
                HudNavigator.CATEGORY_WIDTH, navigationLayout.paneX + navigationLayout.paneWidth);
        b.x = b.anchorX + (int) Math.floor(left * b.scale);
        b.w = (int) Math.ceil((right - left) * b.scale);
        b.h =
            Math.max(
                b.h,
                (int) Math.ceil(Math.min(navigationLayout.availableHeight, paneHeight) * b.scale));
      }
    }
    if (e == Element.PLAYERS
        && dragging != e
        && hud.xs.get(e).get() == 0
        && hud.ys.get(e).get() == 0) {
      int bottom = 0;
      for (Element above : new Element[] {Element.WATERMARK, Element.TAB_GUI}) {
        Box anchor = boxes.get(above);
        if (anchor.visible && anchor.x < b.x + b.w && b.x < anchor.x + anchor.w)
          bottom = Math.max(bottom, anchor.y + anchor.h + 2);
      }
      b.y = Math.min(height - b.h, Math.max(b.y, bottom));
    }
    // Keep the original default anchor compatible while placing effects above the footer.
    // User-positioned elements remain exactly where the editor saved them.
    if (e == Element.EFFECTS
        && dragging != e
        && hud.xs.get(e).get() == Element.EFFECTS.x
        && hud.ys.get(e).get() == Element.EFFECTS.y) {
      int footer =
          show(Element.INFO)
                  && hud.xs.get(Element.INFO).get() == 0
                  && hud.ys.get(Element.INFO).get() == 1
              ? 24
              : 0;
      b.y = Math.max(2, height - 2 - footer - b.h);
    }
    if (e != Element.TAB_GUI) {
      b.anchorX = b.x;
      b.anchorY = b.y;
      b.anchorW = b.w;
      b.anchorH = b.h;
    }
    b.visible = true;
  }

  private void drawBox(Element element, Box b, boolean preview, int width, int height) {
    shadowBox(element, b);
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    double sx = mc.displayWidth / (double) width, sy = mc.displayHeight / (double) height;
    GL11.glScissor(
        Math.max(0, (int) Math.floor(b.x * sx) - 2),
        Math.max(0, (int) Math.floor((height - b.y - b.h) * sy) - 2),
        Math.min(mc.displayWidth, (int) Math.ceil(b.w * sx) + 4),
        Math.min(mc.displayHeight, (int) Math.ceil(b.h * sy) + 4));
    HudElementStyle style = hud.styles.get(element);
    blurBox(element, b, width, height);
    float previousPixels = pixels;
    activeStyle = style;
    activeBox = b.content;
    activeScreenBox = b;
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(b.anchorX, b.anchorY, 0);
      GlStateManager.scale(b.scale, b.scale, 1);
      pixels *= b.scale;
      canvas.pixelScale = pixels;
      canvas.cornerRadius = style.radius.get();
      if (style.background.get() != HudElementStyle.Background.AUTO
          && !(element == Element.MODULES && hud.moduleRows.get()))
        customBackground(
            b.content.x - b.padding,
            b.content.y - b.padding,
            b.content.w + b.padding * 2,
            b.content.h + b.padding * 2);
      if (element == Element.TAB_GUI
          && navigation.expanded
          && style.background.get() != HudElementStyle.Background.AUTO)
        customBackground(
            navigationLayout.paneX,
            0,
            navigationLayout.paneWidth,
            Math.min(navigationLayout.availableHeight, b.h / b.scale));
      draw(element, b.content, preview);
    } finally {
      pixels = previousPixels;
      canvas.pixelScale = pixels;
      GlStateManager.popMatrix();
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    if (preview && element == draggingElement) canvas.outline(b.x, b.y, b.w, b.h, 0x707d7771);
  }

  void shadowBox(Element element, Box b) {
    HudElementStyle style = hud.styles.get(element);
    if (!style.panelShadow.get()) return;
    if (element == Element.MODULES && hud.moduleRows.get())
      arrayShape(b)
          .shadow(
              canvas,
              b.anchorX,
              b.anchorY,
              b.scale,
              style.shadowSpread.get(),
              style.shadowOpacity.get().floatValue());
    else
      perihelion.integration.minecraft189.gui.modern.SurfaceShadow.draw(
          canvas,
          b.x,
          b.y,
          b.w,
          b.h,
          style.radius.get() * b.scale,
          style.shadowSpread.get() * b.scale,
          style.shadowOpacity.get().floatValue());
  }

  private HudArrayShape arrayShape(Box b) {
    int count = Math.max(1, Math.min(modules.size(), b.content.h / moduleRowHeight()));
    if (b.arrayShape != null) return b.arrayShape;
    return b.arrayShape =
        new HudArrayShape(
            modules.subList(0, Math.min(count, modules.size())),
            b.content.w,
            moduleRowHeight(),
            b.padding,
            hud.modulePadding.get(),
            hud.moduleAlignment.get() == HudAppearance.Alignment.LEFT,
            hud.styles.get(Element.MODULES).radius.get());
  }

  void blurBox(Element element, Box b, int width, int height) {
    HudElementStyle style = hud.styles.get(element);
    if (!style.blur.get()) return;
    if (element == Element.MODULES && hud.moduleRows.get())
      blur.drawShape(
          b.anchorX,
          b.anchorY,
          b.scale,
          arrayShape(b),
          style.blurStrength.get() * b.scale,
          width,
          height,
          pixels);
    else
      blur.draw(
          b.x,
          b.y,
          b.w,
          b.h,
          style.radius.get() * b.scale,
          style.blurStrength.get() * b.scale,
          width,
          height,
          pixels);
  }

  void prepareStyle(Element e, Box b) {
    activeStyle = hud.styles.get(e);
    activeBox = b.content;
    activeScreenBox = b;
    canvas.pixelScale = pixels;
    canvas.cornerRadius = activeStyle.radius.get();
  }

  void customElementBackground(Element e, Box b) {
    prepareStyle(e, b);
    if (activeStyle.background.get() != HudElementStyle.Background.AUTO
        && !(e == Element.MODULES && hud.moduleRows.get()))
      customBackground(0, 0, b.content.w + b.padding * 2, b.content.h + b.padding * 2);
  }

  private void customBackground(float x, float y, float w, float h) {
    if (activeStyle.background.get() == HudElementStyle.Background.NONE) return;
    long now = System.currentTimeMillis();
    int first = activeStyle.background(now, false), second = activeStyle.background(now, true);
    if (activeStyle.background.get() != HudElementStyle.Background.GRADIENT) {
      perihelion.integration.minecraft189.gui.menu.MenuSurface.rounded(
          canvas, x, y, w, h, activeStyle.radius.get(), first);
      return;
    }
    // A rounded alpha mask keeps the gradient and its hit bounds on the same rectangle.
    boolean clipped =
        backgroundClip.begin(
            x + activeScreenBox.anchorX / activeScreenBox.scale,
            y + activeScreenBox.anchorY / activeScreenBox.scale,
            w,
            h,
            activeStyle.radius.get(),
            pixels,
            mc.displayHeight);
    canvas.clip = clipped ? backgroundClip : null;
    try {
      if (activeStyle.direction.get() == HudElementStyle.Direction.HORIZONTAL)
        canvas.gradient(x, y, w, h, first, second, second, first);
      else canvas.gradient(x, y, w, h, first, first, second, second);
    } finally {
      if (clipped) backgroundClip.end();
      canvas.clip = null;
    }
  }

  private void appearance(Element element, long now) {
    activeStyle = hud.styles.get(element);
    primary = hud.accent.get().resolvedArgb(now);
    secondary = hud.secondary.get().resolvedArgb(now);
    background =
        GuiTheme.alpha(
            hud.opacity.get() * activeStyle.opacity.get() / .6, modern ? 0x171a1f : 0x080808);
    if (modern) {
      primary = canvas.accentAt(element.ordinal() / (float) THEME_ELEMENT_RANGE);
      secondary = ModernTheme.TEXT;
      background = GuiTheme.alpha(activeStyle.opacity.get(), 0x191817);
    }
    primary = activeStyle.color(hud, now, 0, primary);
    if (activeStyle.colors.get() != HudElementStyle.Colors.CLIENT)
      secondary = activeStyle.color(hud, now, 1, secondary);
  }

  private boolean textShadow() {
    return activeStyle != null ? activeStyle.shadow.get() : hud.shadow.get();
  }

  int moduleRowHeight() {
    HudFont font = elementFont(Element.MODULES);
    return Math.max(9, font.height()) + hud.moduleSpacing.get();
  }

  void drawStyledModules(Box b, int first, int second, int fill) {
    primary = first;
    secondary = second;
    background = fill;
    drawModules(b);
  }

  private void drawModules(Box b) {
    int rowHeight = moduleRowHeight(), padding = hud.modulePadding.get();
    boolean left = hud.moduleAlignment.get() == HudAppearance.Alignment.LEFT;
    if (hud.moduleRows.get() && activeStyle.background.get() != HudElementStyle.Background.NONE) {
      long now = System.currentTimeMillis();
      int first =
          activeStyle.background.get() == HudElementStyle.Background.AUTO
              ? background
              : activeStyle.background(now, false);
      int second =
          activeStyle.background.get() == HudElementStyle.Background.GRADIENT
              ? activeStyle.background(now, true)
              : first;
      arrayShape(activeScreenBox)
          .fill(
              canvas,
              0,
              0,
              first,
              second,
              activeStyle.direction.get() == HudElementStyle.Direction.HORIZONTAL);
    }
    if (modules.isEmpty())
      rowLabel(
          "Array list", b.x + padding, b.y, b.w - padding * 2, rowHeight, modern ? TEXT : primary);
    for (int i = 0; i < modules.size() && (i + 1) * rowHeight <= b.h; i++) {
      ModuleRow row = modules.get(i);
      int w = Math.min(b.w, row.width + padding * 2), x = left ? b.x : b.x + b.w - w;
      int top = b.y + i * rowHeight;
      float position = i / (float) Math.max(1, modules.size() - 1);
      int color =
          activeStyle.color(
              hud,
              System.currentTimeMillis(),
              position,
              modern
                  ? HudAppearance.blend(canvas.accentAt(position), ModernTheme.TEXT, .32f)
                  : HudAppearance.blend(secondary, primary, position));
      if (!hud.moduleRows.get()
          && activeStyle.background.get() == HudElementStyle.Background.AUTO
          && !modern) canvas.rect(b.x, top, b.w, rowHeight, background);
      int accent =
          activeStyle.color(
              hud,
              System.currentTimeMillis(),
              position,
              modern ? canvas.accentAt(position) : primary);
      switch (hud.moduleBorder.get()) {
        case LEFT:
          canvas.verticalHairline(x, top, rowHeight, accent);
          break;
        case RIGHT:
          canvas.verticalHairline(x + w - 1 / pixels, top, rowHeight, accent);
          break;
        case TOP:
          if (i == 0) canvas.hairline(x, top, w, accent);
          break;
        case OUTLINE:
          canvas.outline(x, top, w, rowHeight, accent);
          break;
        default:
          break;
      }
      text.draw(
          text.trim(row.label, w - padding * 2),
          x + padding,
          text.centeredY(top, rowHeight - hud.moduleSpacing.get()),
          color,
          textShadow(),
          pixels);
    }
  }

  private int widest(List<String> rows) {
    int result = 0;
    for (String s : rows) result = Math.max(result, text.width(s));
    return result;
  }

  boolean brandedLogo() {
    return hud.logoDesign.get() != HudModule.LogoDesign.TEXT;
  }

  void measureBrand(Box b) {
    boolean name = hud.logoDesign.get() == HudModule.LogoDesign.EMBLEM_AND_NAME;
    b.w = (int) Math.ceil(brand.emblemWidth(BRAND_HEIGHT)) + 2;
    b.h = BRAND_HEIGHT + 2;
    if (name) {
      b.w += 5 + (int) Math.ceil(brand.nameWidth(14));
      if (hud.watermarkInfo.get()) {
        b.w = Math.max(b.w, text.width(brandDetails()) + 2);
        b.h += 11;
      }
    }
  }

  void drawBrand(Box b, int emblemColor, int nameColor) {
    brand.emblem(b.x + 1, b.y + 1, BRAND_HEIGHT, emblemColor);
    if (hud.logoDesign.get() == HudModule.LogoDesign.EMBLEM_AND_NAME) {
      brand.name(b.x + 1 + brand.emblemWidth(BRAND_HEIGHT) + 5, b.y + 6, 14, nameColor);
      if (hud.watermarkInfo.get())
        rowLabel(brandDetails(), b.x + 1, b.y + BRAND_HEIGHT + 2, b.w - 2, 11, MUTED);
    }
  }

  private String brandDetails() {
    return watermark.startsWith("Perihelion")
        ? watermark.substring("Perihelion".length()).trim()
        : watermark;
  }

  private void panel(Box b) {
    surface(b.x, b.y, b.w, b.h, background);
  }

  private void surface(float x, float y, float w, float h, int fill) {
    if (activeStyle != null && activeStyle.background.get() != HudElementStyle.Background.AUTO)
      return;
    if (modern) ModernTheme.surface(canvas, x, y, w, h, fill);
    else canvas.rect(x, y, w, h, fill);
  }

  private int ink(int color) {
    if (activeStyle != null
        && activeStyle.colors.get() != HudElementStyle.Colors.CLIENT
        && (color == TEXT
            || color == MUTED
            || color == ModernTheme.TEXT
            || color == ModernTheme.MUTED
            || color == primary))
      return activeStyle.color(hud, System.currentTimeMillis(), 0, color);
    if (!modern) return color;
    return color == TEXT ? ModernTheme.TEXT : color == MUTED ? ModernTheme.MUTED : color;
  }

  private void label(String value, int x, int y, int width, int color) {
    if (width <= 0) return;
    text.draw(text.trim(value, width), x, y, ink(color), textShadow(), pixels);
  }

  private void rowLabel(String value, int x, int top, int width, int height, int color) {
    if (width <= 0) return;
    if (activeStyle != null
        && activeStyle.colors.get() != HudElementStyle.Colors.CLIENT
        && activeBox != null)
      color =
          activeStyle.color(
              hud,
              System.currentTimeMillis(),
              (top - activeBox.y) / (float) Math.max(1, activeBox.h - height),
              color);
    text.draw(
        text.trim(value, width), x, text.centeredY(top, height), ink(color), textShadow(), pixels);
  }

  private void draw(Element element, Box b, boolean preview) {
    if (classic) throw new IllegalStateException("Raven must use its own HUD renderer");
    adjustDraws++;
    int x = b.x, y = b.y;
    if (element != Element.MODULES
        && (element == Element.TARGET
            || element == Element.EFFECTS && !modern
            || element == Element.PLAYERS)) {
      panel(b);
    }
    switch (element) {
      case WATERMARK:
        if (brandedLogo()) {
          drawBrand(b, primary, TEXT);
          break;
        }
        if (modern) {
          rowLabel("\u00a7l" + watermark, x + 1, y, b.w - 2, b.h,
              activeStyle.colors.get() == HudElementStyle.Colors.CLIENT
                  ? HudAppearance.blend(primary, ModernTheme.TEXT, .32f) : primary);
          break;
        }
        GlStateManager.pushMatrix();
        try {
          GlStateManager.translate(x, y, 0);
          GlStateManager.scale(LOGO_SCALE, LOGO_SCALE, 1);
          String initial = "\u00a7l" + watermark.substring(0, 1);
          int initialWidth = text.width(initial);
          float logoY = text.centeredY(0, b.h / LOGO_SCALE);
          text.draw(initial, 1, logoY, primary, textShadow(), pixels * LOGO_SCALE);
          text.draw(
              text.trim(watermark.substring(1), (int) (b.w / LOGO_SCALE) - initialWidth - 2),
              initialWidth + 1,
              logoY,
              TEXT,
              textShadow(),
              pixels * LOGO_SCALE);
        } finally {
          GlStateManager.popMatrix();
        }
        break;
      case TAB_GUI:
        drawNavigation(b);
        break;
      case INFO:
        if (modern) {
          rowLabel(info, x + 1, y, b.w - 2, 11, TEXT);
          rowLabel(speed, x + 1, y + 11, b.w - 2, 10, MUTED);
        } else {
          rowLabel(speed, x + 1, y, b.w - 2, 10, MUTED);
          rowLabel(info, x + 1, y + 10, b.w - 2, 11, MUTED);
        }
        break;
      case MODULES:
        drawModules(b);
        break;
      case TARGET:
        if (skin != null) face(skin, x + 4, y + 4, 24);
        rowLabel(hasTarget ? targetName : "Target", x + 32, y + 4, b.w - 36, 12, TEXT);
        rowLabel(hasTarget ? targetInfo : "20.0 HP | 3.0m", x + 32, y + 16, b.w - 36, 12, MUTED);
        canvas.rect(x + 4, y + 32, b.w - 8, 2, modern ? ModernTheme.BORDER : 0xff303030);
        canvas.rect(
            x + 4,
            y + 32,
            (int) ((b.w - 8) * HudLayout.clamp(hasTarget ? health / maximum : 1)),
            2,
            primary);
        if (hasTarget && absorption > 0)
          canvas.rect(
              x + 4,
              y + 35,
              (int) ((b.w - 8) * HudLayout.clamp(absorption / maximum)),
              1,
              0xffddbb66);
        break;
      case ARMOR:
        for (int i = 0; i < 4; i++) {
          int col = x + i * 25;
          if (armor[i] != null) item(armor[i], col + 4, y);
          String value = durability[i] == null ? "--" : durability[i];
          rowLabel(value, col + (25 - text.width(value)) / 2, y + 16, 25, 12, MUTED);
        }
        break;
      case EFFECTS:
        if (modern) {
          if (effects.isEmpty()) rowLabel("No active effects", x + 1, y, b.w - 2, 11, MUTED);
          for (int i = 0; i < effects.size(); i++)
            rowLabel(effects.get(i), x + 1, y + i * 11, b.w - 2, 11, TEXT);
        } else rows(b, "Effects", effects, "No active effects");
        break;
      case PLAYERS:
        drawPlayers(b);
        break;
      case KEYS:
        key("W", x + 31, y, 28, mc.gameSettings.keyBindForward.isKeyDown(), 0);
        key("A", x, y + 30, 28, mc.gameSettings.keyBindLeft.isKeyDown(), .2f);
        key("S", x + 31, y + 30, 28, mc.gameSettings.keyBindBack.isKeyDown(), .4f);
        key("D", x + 62, y + 30, 28, mc.gameSettings.keyBindRight.isKeyDown(), .6f);
        key("LMB", x, y + 60, 43, Mouse.isButtonDown(0), .8f);
        key("RMB", x + 47, y + 60, 43, Mouse.isButtonDown(1), 1);
        surface(x, y + 90, 90, 15, background);
        rowLabel(cps, x + (90 - text.width(cps)) / 2, y + 90, 84, 15, TEXT);
        break;
      case NOTIFICATIONS:
        if (notices.isEmpty()) {
          panel(b);
          rowLabel("Notifications", x + 3, y, b.w - 6, 12, TEXT);
          rowLabel("Module state changes", x + 3, y + 12, b.w - 6, 11, TEXT);
        }
        int row = 0;
        for (Notice notice : notices) {
          if (System.nanoTime() - notice.time >= 3_000_000_000L) continue;
          surface(x, y + row * 25, b.w, 23, background);

          rowLabel(notice.label, x + 3, y + row * 25, b.w - 6, 12, modern ? TEXT : primary);
          int stateColor = modern ? (notice.enabled ? primary : MUTED) : TEXT;
          rowLabel(
              notice.enabled ? "Enabled" : "Disabled",
              x + 3,
              y + row * 25 + 12,
              b.w - 6,
              11,
              stateColor);
          row++;
        }
        break;
    }
  }

  private void rows(Box b, String title, List<String> rows, String empty) {
    rowLabel(title, b.x + (b.w - text.width(title)) / 2, b.y, b.w, 13, TEXT);
    if (rows.isEmpty()) rowLabel(empty, b.x + 3, b.y + 13, b.w - 6, 11, MUTED);
    for (int i = 0; i < rows.size() && 13 + i * 11 + 9 <= b.h; i++)
      rowLabel(rows.get(i), b.x + 3, b.y + 13 + i * 11, b.w - 6, 11, TEXT);
  }

  void face(ResourceLocation skin, int x, int y, int size) {
    RenderStyle.text();
    mc.getTextureManager().bindTexture(skin);
    Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
    Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
  }

  private int playerNameWidth() {
    if (classic) return raven.playerNameWidth();
    int width = 96;
    for (PlayerRow row : players) width = Math.max(width, Math.min(150, text.width(row.name) + 22));
    return width;
  }

  private int playerColumnWidth(HudModule.PlayerColumn column) {
    if (classic) return raven.playerColumnWidth(column);
    String sample;
    switch (column) {
      case DISTANCE:
        sample = "128m";
        break;
      case HEALTH:
        sample = "100%";
        break;
      case TEAM:
        sample = "Yellow";
        break;
      case KD:
      case FKD:
      case WL:
        sample = "00.00";
        break;
      case WS:
      case PING:
        sample = "999";
        break;
      case OBSERVED:
        sample = "59m 59s";
        break;
      default:
        sample = "now";
        break;
    }
    return Math.max(column.width, Math.max(text.width(column.heading), text.width(sample)) + 4);
  }

  List<HudModule.PlayerColumn> visiblePlayerColumns(Box b) {
    List<HudModule.PlayerColumn> columns = new ArrayList<>(hud.playerColumns.get());
    playerColumnOffset = Math.max(0, Math.min(playerColumnOffset, Math.max(0, columns.size() - 1)));
    List<HudModule.PlayerColumn> visible = new ArrayList<>();
    int room = b.w - Math.min(playerNameWidth(), b.w);
    for (int i = playerColumnOffset; i < columns.size(); i++) {
      int width = playerColumnWidth(columns.get(i));
      if (width > room) break;
      visible.add(columns.get(i));
      room -= width;
    }
    return visible;
  }

  boolean scrollPlayers(int x, int y, int wheel) {
    Box b = boxes.get(Element.PLAYERS);
    if (!b.contains(x, y) || wheel == 0) return false;
    configureElementFont(Element.PLAYERS);
    int localY = (int) ((y - b.anchorY) / b.scale) - b.padding;
    if (localY < 11) {
      int all = playerNameWidth();
      for (HudModule.PlayerColumn column : hud.playerColumns.get())
        all += playerColumnWidth(column);
      if (all <= b.content.w) return false;
      playerColumnOffset =
          Math.max(
              0,
              Math.min(
                  Math.max(0, hud.playerColumns.get().size() - 1),
                  playerColumnOffset + (wheel > 0 ? -1 : 1)));
    } else {
      int rows =
          Math.max(1, (Math.min(b.content.h, (int) (b.h / b.scale) - b.padding * 2) - 11) / 10);
      if (players.size() <= rows) return false;
      playerRowOffset =
          Math.max(0, Math.min(players.size() - rows, playerRowOffset + (wheel > 0 ? -1 : 1)));
    }
    return true;
  }

  private void drawPlayers(Box b) {
    surface(b.x, b.y, b.w, b.h, background);
    {
      surface(b.x, b.y, b.w, 10, modern ? ModernTheme.HEADER : 0x90151515);
      canvas.hairline(b.x, b.y + 10, b.w, modern ? ModernTheme.BORDER : 0x706b7078);
    }
    List<HudModule.PlayerColumn> visibleColumns = visiblePlayerColumns(b);
    int nameWidth = Math.min(playerNameWidth(), b.w);
    int availableRows = Math.max(0, (b.h - 11) / 10);
    playerRowOffset =
        Math.max(0, Math.min(playerRowOffset, Math.max(0, players.size() - availableRows)));
    String heading =
        "Players "
            + players.size()
            + (visibleColumns.size() < hud.playerColumns.get().size() ? " <>" : "");
    rowLabel(heading, b.x + (nameWidth - text.width(heading)) / 2, b.y, nameWidth - 6, 11, TEXT);
    int columnX = b.x + nameWidth;
    for (HudModule.PlayerColumn column : visibleColumns) {
      rowLabel(
          column.heading,
          columnX + (playerColumnWidth(column) - text.width(column.heading)) / 2,
          b.y,
          playerColumnWidth(column),
          11,
          TEXT);
      columnX += playerColumnWidth(column);
    }
    for (int i = 0; i < availableRows && i + playerRowOffset < players.size(); i++) {
      PlayerRow row = players.get(i + playerRowOffset);
      int top = b.y + 11 + i * 10;
      boolean tagged = targets.contains(row.id);
      if (i % 2 == 0) canvas.rect(b.x, top, b.w, 10, modern ? 0x08ffffff : 0x18808080);
      if (hud.overlay.highlights.get() && row.profile != null) {
        if (row.profile.nicked) canvas.rect(b.x, top, b.w, 10, 0x40c9a134);
        if (!row.profile.tags.isEmpty()) canvas.rect(b.x, top, b.w, 10, 0x40be4646);
      }
      if (hud.tracker != null) {
        String username = row.username.toLowerCase(Locale.ROOT);
        if (hud.tracker.party.contains(username)) canvas.rect(b.x, top, b.w, 10, 0x2852b878);
        else if (hud.overlay.partyDetector.get() && hud.tracker.possibleParty.contains(username))
          canvas.rect(b.x, top, b.w, 10, 0x2052b878);
      }
      if (row.target || tagged) canvas.rect(b.x, top, b.w, 10, 0x604f4f4f);
      if (tagged) canvas.rect(b.x, top, 1, 10, primary);
      if (row.skin != null) face(row.skin, b.x + 2, top + 1, 8);
      rowLabel(
          (pinned.contains(row.id) ? "\u00a7e* \u00a7r" : "") + row.name,
          b.x + 13,
          top,
          nameWidth - 15,
          10,
          TEXT);
      columnX = b.x + nameWidth;
      for (HudModule.PlayerColumn column : visibleColumns) {
        String value = playerValue(row, column);
        rowLabel(value, columnX + 1, top, playerColumnWidth(column) - 2, 10, TEXT);
        columnX += playerColumnWidth(column);
      }
    }
    if (players.isEmpty()) rowLabel("No nearby players", b.x + 3, b.y + 11, b.w - 6, 10, MUTED);
  }

  String playerValue(PlayerRow row, HudModule.PlayerColumn column) {
    String value = "--";
    switch (column) {
      case DISTANCE:
        value = row.distance.equals("--") ? "--" : row.distance + "m";
        break;
      case HEALTH:
        value = row.health;
        break;
      case TEAM:
        value = row.team;
        break;
      case PING:
        value = row.ping < 0 ? "--" : String.valueOf(row.ping);
        break;
      case OBSERVED:
        long seconds = Math.max(0, (row.seenAt - row.observedAt) / 1_000_000_000L);
        value = seconds / 60 + "m " + seconds % 60 + "s";
        break;
      case ENCOUNTERS:
        value = String.valueOf(row.encounters);
        break;
      case SEEN:
        value = "now";
        break;
      default:
        value = row.values.getOrDefault(column, "--");
        HypixelLookup.Result result = hud.stats == null ? null : hud.stats.get(row.id);
        if (value.equals("--") && result != null && result.stats != null)
          value = result.stats.column(hud.hypixelGame.get(), column);
        break;
    }
    return value;
  }

  PlayerRow playerAt(int x, int y) {
    Box b = boxes.get(Element.PLAYERS);
    int localY = (int) ((y - b.anchorY) / b.scale) - b.padding;
    if (!b.contains(x, y) || localY < 11) return null;
    int row = (localY - 11) / 10;
    int index = row + playerRowOffset;
    return index < players.size() && 11 + row * 10 + 10 <= b.content.h ? players.get(index) : null;
  }

  PlayerRow player(UUID id) {
    for (PlayerRow row : players) if (row.id.equals(id)) return row;
    return null;
  }

  boolean pinned(UUID id) {
    return pinned.contains(id);
  }

  void togglePin(UUID id) {
    if (!pinned.remove(id) && pinned.size() < 64) pinned.add(id);
    updatePlayers(false);
  }

  boolean tagged(UUID id) {
    return targets.contains(id);
  }

  void toggleTarget(UUID id) {
    if (!targets.remove(id) && targets.size() < 64) targets.add(id);
  }

  void popup(
      PlayerRow player, HypixelStats profile, List<String> rows, Box b, int scroll, int hover) {
    if (GuiTheme.style(hud.runtime) == ClickGuiModule.ClientStyle.RAVEN) {
      raven.popup(player, profile, rows, b, scroll, hover);
      return;
    }
    modern = GuiTheme.modern(hud.runtime);
    configureElementFont(Element.PLAYERS);
    canvas.modern = modern;
    if (modern) ModernTheme.palette(canvas, GuiTheme.palette(hud.runtime));
    appearance(Element.PLAYERS, System.currentTimeMillis());
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      canvas.pixelScale = pixels;
      perihelion.integration.minecraft189.gui.menu.MenuSurface.rounded(
          canvas, b.x, b.y, b.w, b.h, 3, modern ? ModernTheme.PANEL : 0xf8141414);
      if (player.skin != null) face(player.skin, b.x + 5, b.y + 5, 64);
      int identityX = b.x + 74;
      float identityScale = modern ? 1 : 1.15f;
      GlStateManager.pushMatrix();
      try {
        GlStateManager.translate(identityX, b.y + 5, 0);
        GlStateManager.scale(identityScale, identityScale, 1);
        text.draw(
            text.trim(player.name, (int) ((b.w - 89) / identityScale)),
            0,
            0,
            primary,
            textShadow(),
            pixels * identityScale);
      } finally {
        GlStateManager.popMatrix();
      }
      label("Language: --", identityX, b.y + 17, b.w - 79, MUTED);
      label(
          player.ping < 0 ? "Ping hidden" : player.ping + " ms",
          identityX,
          b.y + 29,
          b.w - 79,
          TEXT);
      label("First login", identityX, b.y + 41, b.w - 79, MUTED);
      OverlayProfile extra = hud.overlayLookup == null ? null : hud.overlayLookup.get(player.id);
      long firstLogin =
          extra != null && extra.counts.containsKey("firstLogin")
              ? extra.counts.get("firstLogin")
              : profile == null ? 0 : profile.firstLogin;
      String first =
          firstLogin <= 0
              ? "-- (load Statistics)"
              : java.time.Instant.ofEpochMilli(firstLogin)
                  .atZone(java.time.ZoneOffset.UTC)
                  .toLocalDate()
                  .toString();
      label(first, identityX, b.y + 52, b.w - 79, TEXT);
      label("x", b.x + b.w - 12, b.y + 3, 8, MUTED);
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      double sx = mc.displayWidth / (double) Math.max(1, mc.currentScreen.width),
          sy = mc.displayHeight / (double) Math.max(1, mc.currentScreen.height);
      GL11.glScissor(
          (int) (b.x * sx),
          (int) ((mc.currentScreen.height - b.y - b.h) * sy),
          (int) (b.w * sx),
          (int) (b.h * sy));
      int count = Math.max(1, (b.h - 76) / 12);
      for (int i = scroll; i < rows.size() && i < scroll + count; i++) {
        int top = b.y + 76 + (i - scroll) * 12;
        boolean heading =
            rows.get(i).equals("Tags")
                || rows.get(i).equals("Analysis")
                || rows.get(i).equals("Hypixel");
        if (heading) {
          canvas.rect(b.x + 1, top, b.w - 2, 12, modern ? ModernTheme.HEADER : 0x382b2b2b);
          canvas.hairline(b.x + 4, top, b.w - 8, modern ? ModernTheme.BORDER : 0x406b7078);
        } else if (i == hover)
          canvas.rect(b.x + 1, top, b.w - 2, 12, modern ? ModernTheme.HOVER : 0x505f646d);
        rowLabel(rows.get(i), b.x + (heading ? 5 : 10), top, b.w - 15, 12, heading ? TEXT : MUTED);
      }
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      if (rows.size() > count) {
        float thumb = (b.h - 76f) * count / rows.size();
        canvas.verticalHairline(
            b.x + b.w - 2,
            b.y + 76 + (b.h - 76f - thumb) * scroll / Math.max(1, rows.size() - count),
            thumb,
            primary);
      }
    }
  }

  private void navigate(int key, char typed) {
    navigation.key(key, typed);
    // Settings own native GUI focus: edit keys must never jump, move or toggle game bindings.
    if (navigation.settingsOpen && mc.currentScreen == null) {
      KeyBinding.unPressAllKeys();
      hud.runtime.input().setAllowed(false, Collections.emptyList());
      mc.displayGuiScreen(new NavigationInput());
    }
  }

  boolean navigationFocused() {
    return mc.currentScreen instanceof NavigationInput
        && ((NavigationInput) mc.currentScreen).owner == this;
  }

  private final TabGuiView.Painter navigationPainter =
      new TabGuiView.Painter() {
        public void surface(int x, int y, int width, int height) {
          HudRenderer.this.surface(x, y, width, height, background);
        }

        public void highlight(
            int x,
            int y,
            int width,
            int height,
            boolean selected,
            boolean editing,
            boolean enabled) {
          canvas.rect(
              x, y, width, height, GuiTheme.alpha(editing ? .28 : selected ? .24 : .16, primary));
        }

        public void label(String value, int x, int y, int width, int height, TabGuiView.Tone tone) {
          rowLabel(
              value,
              x,
              y,
              width,
              height,
              tone == TabGuiView.Tone.ERROR
                  ? 0xffffaaaa
                  : tone == TabGuiView.Tone.MUTED ? MUTED : TEXT);
        }

        public void checkbox(int x, int y, int width, int height, boolean on) {
          if (modern) ModernTheme.checkboxRow(canvas, x + width, y, height, on);
          else {
            float top = y + (height - 5) / 2f;
            canvas.rect(x + width - 17, top, 12, 5, on ? primary : 0xff424242);
            canvas.rect(x + width - (on ? 9 : 16), top + 1, 3, 3, TEXT);
          }
        }

        public int checkboxWidth() {
          return 23;
        }

        public int textWidth(String value) {
          return text.width(value);
        }
      };

  private void drawNavigation(Box b) {
    TabGuiView.draw(
        navigation, navigationLayout, navigationModules, navigationSettings, navigationPainter);
  }

  /** A transparent focus owner for settings; the existing HUD draws its keyboard view. */
  private final class NavigationInput extends GuiScreen {
    private final HudRenderer owner = HudRenderer.this;
    private final Object world = HudRenderer.this.mc.theWorld,
        player = HudRenderer.this.mc.thePlayer;
    private final long generation = hud.runtime.tasks().generation();
    private boolean previousRepeat, repeatCaptured;

    @Override
    public void initGui() {
      if (!repeatCaptured) {
        previousRepeat = Keyboard.areRepeatEventsEnabled();
        repeatCaptured = true;
      }
      Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void updateScreen() {
      if (!hud.runtime.isRunning()
          || mc.theWorld != world
          || mc.thePlayer != player
          || hud.runtime.tasks().generation() != generation
          || !show(Element.TAB_GUI)) mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
      return false;
    }

    @Override
    protected void keyTyped(char typed, int key) {
      navigation.key(key, typed);
      if (!navigation.settingsOpen) mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
      try {
        navigation.cancel();
        navigation.settingsOpen = false;
      } finally {
        if (repeatCaptured) {
          Keyboard.enableRepeatEvents(previousRepeat);
          repeatCaptured = false;
        }
      }
    }
  }

  @Override
  public void close() {
    if (navigationFocused()) mc.displayGuiScreen(null);
    navigation.cancel();
    for (HudFont font : elementFonts.values()) if (font != baseFont) font.close();
    baseFont.close();
    blur.close();
    backgroundClip.close();
    brand.close();
    players.clear();
    observed.clear();
    pinned.clear();
    nickNotices.clear();
    tagNotices.clear();
    targets.clear();
    modules.clear();
    effects.clear();
    notices.clear();
    skin = null;
    attackedTarget = null;
    hasTarget = false;
  }

  private void key(String label, int x, int y, int w, boolean pressed, float palettePosition) {
    int fill = background;
    if (pressed)
      fill = modern ? canvas.rowAccentAt(palettePosition) : mix(primary, 0xff16121c, .45f);
    surface(x, y, w, 28, fill);
    if (pressed && activeStyle.background.get() != HudElementStyle.Background.AUTO)
      canvas.rect(x, y, w, 28, GuiTheme.alpha(.22, primary));
    rowLabel(label, x + (w - text.width(label)) / 2, y, w, 28, TEXT);
  }

  void item(ItemStack stack, int x, int y) {
    float previous = mc.getRenderItem().zLevel;
    try {
      RenderStyle.text();
      GlStateManager.enableDepth();
      GlStateManager.depthMask(true);
      RenderHelper.enableGUIStandardItemLighting();
      mc.getRenderItem().zLevel = 0;
      mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
    } finally {
      mc.getRenderItem().zLevel = previous;
      RenderHelper.disableStandardItemLighting();
      RenderStyle.text();
    }
  }

  private static int mix(int a, int b, float f) {
    return 0xff000000
        | ((int) ((a >> 16 & 255) * (1 - f) + (b >> 16 & 255) * f) << 16)
        | ((int) ((a >> 8 & 255) * (1 - f) + (b >> 8 & 255) * f) << 8)
        | (int) ((a & 255) * (1 - f) + (b & 255) * f);
  }

  static final class Box {
    int x, y, w, h;
    float scale = 1;
    int padding;
    Box content;
    HudArrayShape arrayShape;
    // Persist/drag the permanent anchor, independently of transient navigation panes.
    int anchorX, anchorY, anchorW, anchorH;
    boolean visible;

    boolean contains(int mx, int my) {
      return visible && mx >= x && mx < x + w && my >= y && my < y + h;
    }
  }

  static final class ModuleRow {
    final String label;
    final int width;

    ModuleRow(String label, int width) {
      this.label = label;
      this.width = width;
    }
  }

  static final class PlayerRow {
    final UUID id;
    OverlayProfile profile;
    int encounters = 1;
    long joined;
    final Map<HudModule.PlayerColumn, String> values = new EnumMap<>(HudModule.PlayerColumn.class);
    final String username, name, health, team;
    final String healthPoints, distance, held;
    final int ping, armor;
    final long observedAt, seenAt;
    final ResourceLocation skin;
    final boolean target;

    PlayerRow(
        UUID id,
        String username,
        String name,
        String health,
        String team,
        ResourceLocation skin,
        boolean target,
        String healthPoints,
        String distance,
        int ping,
        int armor,
        String held,
        long observedAt,
        long seenAt) {
      this.id = id;
      this.username = username;
      this.name = name;
      this.health = health;
      this.team = team;
      this.skin = skin;
      this.target = target;
      this.healthPoints = healthPoints;
      this.distance = distance;
      this.ping = ping;
      this.armor = armor;
      this.held = held;
      this.observedAt = observedAt;
      this.seenAt = seenAt;
    }
  }

  static final class Notice {
    final String label;
    final long time;
    final boolean enabled;

    Notice(String label, boolean enabled, long time) {
      this.label = label;
      this.enabled = enabled;
      this.time = time;
    }
  }
}
