package perihelion.integration.minecraft189.account;

import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/** Names, skins, favorites and local overrides share one small secondary screen. */
public final class AccountProfileScreen extends AccountUi {
  private int tab;
  private Input value, favorite;
  private boolean slim;
  private AccountProfiles.Skin skin;
  private ResourceLocation preview;
  private List<String> favorites = Collections.emptyList();
  private String selected = "";
  private int scroll;

  public AccountProfileScreen(AccountService service, GuiScreen parent) {
    super(service, parent);
    panelHeight = 272;
  }

  @Override
  protected String title() {
    return "Profile tools";
  }

  @Override
  protected void setup() {
    value = input(false, 1024);
    favorite = input(false, 48);
  }

  private String token() {
    return mc.getSession().getToken();
  }

  private boolean online() {
    return !token().isEmpty() && !token().equals("0");
  }

  @Override
  protected void content(float mx, float my) {
    String[] tabs = {"Username", "Skin", "Favorites", "Local override"};
    for (int i = 0; i < tabs.length; i++) {
      final int n = i;
      navigation(
          tabs[i],
          16,
          43 + i * 28,
          124,
          22,
          tab == i,
          idle(),
          () -> {
            tab = n;
            status = "";
            if (n == 2) refreshFavorites();
          });
    }
    label("Active: " + mc.getSession().getUsername(), 156, 43, 280, canvas.enabled);
    if (tab == 0) {
      label("Microsoft applies name-change limits and cooldowns.", 156, 65, 280, canvas.muted);
      field(value, "New username", 156, 92, 280);
      button(
          "Check availability",
          156,
          121,
          136,
          idle() && online(),
          () -> {
            String name = value.text().trim(), token = token();
            start(
                service.submit(
                    "Checking name...",
                    j -> service.profiles.available(name, token),
                    this::result));
          });
      button(
          "Change name",
          300,
          121,
          136,
          idle() && online() && mc.theWorld == null,
          () -> {
            String name = value.text().trim(), token = token();
            confirm(
                "Change your Minecraft username to " + name + "? Microsoft's cooldown applies.",
                () ->
                    start(
                        service.submit(
                            "Changing name...",
                            j -> service.profiles.rename(name, token),
                            a -> {
                              if (token.equals(token()) && mc.theWorld == null)
                                service.switchSession(a);
                              result("Username changed to " + a.name + ".");
                            })));
          });
      button(
          "Look up name history",
          156,
          155,
          280,
          idle(),
          () -> {
            String name = value.text().trim();
            if (name.isEmpty()) name = mc.getSession().getUsername();
            final String lookup = name;
            start(
                service.submit(
                    "Looking up public history...",
                    j -> service.profiles.history(lookup),
                    this::result));
          });
      label("History uses the public Ashcon profile service.", 156, 183, 280, canvas.muted);
      return;
    }
    if (tab == 2) {
      for (int i = scroll; i < Math.min(favorites.size(), scroll + 5); i++) {
        String name = favorites.get(i);
        button(
            name,
            156,
            68 + (i - scroll) * 23,
            280,
            20,
            name.equals(selected),
            idle(),
            () -> {
              selected = name;
              start(
                  service.submit(
                      "Loading favorite...", j -> service.profiles.favorite(name), this::preview));
            });
      }
      button(
          "Apply",
          156,
          190,
          88,
          idle() && online() && !selected.isEmpty(),
          () -> applyFavorite(false));
      button("Local", 252, 190, 88, idle() && !selected.isEmpty(), () -> applyFavorite(true));
      button(
          "Remove",
          348,
          190,
          88,
          idle() && !selected.isEmpty(),
          () ->
              confirm(
                  "Remove favorite " + selected + "?",
                  () -> {
                    String name = selected;
                    start(
                        service.submit(
                            "Removing favorite...",
                            j -> {
                              service.profiles.removeFavorite(name);
                              return service.profiles.favorites();
                            },
                            list -> {
                              favorites = list;
                              selected = "";
                            }));
                  }));
      button(
          "Default for new accounts",
          156,
          217,
          180,
          idle() && !selected.isEmpty(),
          () -> {
            String name = selected;
            confirm(
                "Apply favorite "
                    + name
                    + " to each newly added online account automatically? This changes its"
                    + " Minecraft skin.",
                () ->
                    start(
                        service.submit(
                            "Saving default...",
                            j -> {
                              service.profiles.defaultFavorite(name);
                              return "Default skin saved.";
                            },
                            this::result)));
          });
      button(
          "Clear default",
          344,
          217,
          92,
          idle(),
          () ->
              start(
                  service.submit(
                      "Clearing default...",
                      j -> {
                        service.profiles.clearDefault();
                        return "Default cleared.";
                      },
                      this::result)));
      return;
    }
    label("Username, HTTPS image URL, or PNG file path", 156, 66, 280, canvas.muted);
    field(value, "Skin source", 156, 85, 280);
    button(
        slim ? "Model: slim" : "Model: classic",
        156,
        112,
        136,
        idle(),
        () -> {
          slim = !slim;
          if (skin != null)
            try {
              skin = new AccountProfiles.Skin(skin.png, slim, skin.label);
            } catch (Exception ignored) {
            }
        });
    button(
        "Preview",
        296,
        112,
        140,
        idle(),
        () -> {
          String source = value.text();
          boolean model = slim;
          start(
              service.submit(
                  "Loading skin...", j -> service.profiles.skin(source, model), this::preview));
        });
    if (preview != null) {
      mc.getTextureManager().bindTexture(preview);
      net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
      Gui.drawScaledCustomSizeModalRect(170, 143, 8, 8, 8, 8, 40, 40, 64, 64);
      Gui.drawScaledCustomSizeModalRect(170, 143, 40, 8, 8, 8, 40, 40, 64, 64);
      label(skin == null ? "" : skin.label, 224, 149, 206, canvas.text);
      label(
          skin != null && skin.slim ? "Slim model" : "Classic model", 224, 164, 206, canvas.muted);
    }
    if (tab == 1) {
      button(
          "Apply to account", 156, 194, 280, idle() && online() && skin != null, this::applySkin);
      field(favorite, "Favorite name", 156, 220, 180);
      button(
          "Save favorite",
          344,
          221,
          92,
          idle() && skin != null,
          () -> {
            String name = favorite.text().trim();
            AccountProfiles.Skin chosen = skin;
            start(
                service.submit(
                    "Saving favorite...",
                    j -> {
                      service.profiles.favorite(name, chosen);
                      return "Favorite saved.";
                    },
                    this::result));
          });
    } else {
      button(
          "Use locally",
          156,
          194,
          136,
          idle() && skin != null,
          () -> {
            AccountProfiles.Skin chosen = skin;
            start(
                service.submit(
                    "Saving local skin...",
                    j -> {
                      service.profiles.saveLocal(chosen);
                      return chosen;
                    },
                    s -> {
                      try {
                        service.localSkin.apply(s.image(), s.slim);
                        result("Local override enabled.");
                      } catch (Exception e) {
                        result("Could not upload the local skin texture.");
                      }
                    }));
          });
      button(
          "Disable",
          300,
          194,
          136,
          idle(),
          () ->
              start(
                  service.submit(
                      "Disabling local skin...",
                      j -> {
                        service.profiles.disableLocal();
                        return true;
                      },
                      ignored -> {
                        service.localSkin.close();
                        result("Local override disabled.");
                      })));
      label("Only you see the local override. It survives restarts.", 156, 222, 280, canvas.muted);
    }
  }

  private void refreshFavorites() {
    start(
        service.submit(
            "Loading favorites...",
            j -> service.profiles.favorites(),
            list -> {
              favorites = list;
              scroll = 0;
            }));
  }

  private void applySkin() {
    AccountProfiles.Skin chosen = skin;
    String token = token();
    String name = mc.getSession().getUsername();
    confirm(
        "Change the Minecraft skin for " + name + "?",
        () ->
            start(
                service.submit(
                    "Changing skin...", j -> service.profiles.apply(chosen, token), this::result)));
  }

  private void applyFavorite(boolean local) {
    String name = selected;
    String token = token();
    confirm(
        local
            ? "Use " + name + " as your local skin?"
            : "Apply " + name + " to the active Minecraft account?",
        () ->
            start(
                service.submit(
                    "Applying favorite...",
                    j -> {
                      AccountProfiles.Skin s = service.profiles.favorite(name);
                      if (local) service.profiles.saveLocal(s);
                      else service.profiles.apply(s, token);
                      return s;
                    },
                    s -> {
                      if (local)
                        try {
                          service.localSkin.apply(s.image(), s.slim);
                        } catch (Exception ignored) {
                        }
                      result(
                          local ? "Local override enabled." : "Skin changed. Rejoin to refresh.");
                    })));
  }

  private void preview(AccountProfiles.Skin s) {
    try {
      if (preview != null) mc.getTextureManager().deleteTexture(preview);
      skin = s;
      slim = s.slim;
      preview =
          mc.getTextureManager()
              .getDynamicTextureLocation(
                  "perihelion-account-preview",
                  new DynamicTexture(
                      new net.minecraft.client.renderer.ImageBufferDownload()
                          .parseUserSkin(s.image())));
    } catch (Exception error) {
      result("Could not load skin preview.");
    }
  }

  @Override
  protected void wheel(int delta) {
    if (tab == 2) scroll = Math.max(0, Math.min(Math.max(0, favorites.size() - 5), scroll + delta));
  }

  @Override
  public void onGuiClosed() {
    super.onGuiClosed();
    if (preview != null) mc.getTextureManager().deleteTexture(preview);
    preview = null;
  }
}
