package perihelion.integration.minecraft189.gui.modern;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.widget.InlineTextFieldView;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Inline preset settings inside the category list; disk work stays on the native worker. */
final class ConfigActions {
  static final String NEW = "@new", TOOLS = "@tools";
  private final ClientRuntime runtime;
  private final GuiTextField name;
  private final InlineTextFieldView editor = new InlineTextFieldView();
  private final List<Action> actions = new ArrayList<>();
  private List<String> profiles = Collections.emptyList();
  private CompletableFuture<List<String>> listing;
  private CompletableFuture<?> operation;
  private String selected = "", target = "", confirm = "", message = "", success = "";
  private boolean expanded,
      creating,
      loadVisuals,
      loadKeybinds,
      error,
      refreshPending,
      closeAfterOperation;
  private float x, y, w, h, fieldY, fieldHeight, wantedHeight, naturalHeight, lineHeight;
  private long caretEpoch;

  ConfigActions(ClientRuntime runtime) {
    this.runtime = runtime;
    name = new GuiTextField(29, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 16);
    name.setEnableBackgroundDrawing(false);
    name.setMaxStringLength(48);
  }

  List<String> profiles() {
    return profiles;
  }

  String selected() {
    return selected;
  }

  boolean busy() {
    return listing != null || operation != null || runtime.config().isReloading();
  }

  boolean reading() {
    return listing != null;
  }

  String message() {
    return message;
  }

  boolean error() {
    return error;
  }

  void refresh() {
    if (busy()) {
      refreshPending = true;
      return;
    }
    try {
      refreshPending = false;
      listing = runtime.config().store().listProfiles();
    } catch (RuntimeException failure) {
      fail(failure);
    }
  }

  void update() {
    name.updateCursorCounter();
    if (operation != null && operation.isDone()) {
      CompletableFuture<?> done = operation;
      operation = null;
      try {
        done.join();
        message = success;
        error = false;
        if (closeAfterOperation) close();
      } catch (RuntimeException failure) {
        fail(failure);
      }
      refresh();
    }
    if (listing != null && listing.isDone()) {
      CompletableFuture<List<String>> done = listing;
      listing = null;
      try {
        profiles = new ArrayList<>(done.join());
        if (!profiles.contains(selected)) selected = "";
        if (expanded && !creating && !target.equals(TOOLS) && !profiles.contains(target)) close();
      } catch (RuntimeException failure) {
        fail(failure);
      }
    }
    if (refreshPending && !busy()) refresh();
  }

  boolean expanded(String id) {
    return expanded && target.equals(id);
  }

  float height(String id) {
    return target.equals(id) ? h : 0;
  }

  void open(String profile) {
    if (busy()) return;
    boolean same = target.equals(profile);
    expanded = !same || !expanded;
    if (!same) h = 0;
    target = profile;
    creating = profile.equals(NEW);
    name.setFocused(false);
    confirm = "";
  }

  void create() {
    open(NEW);
    if (!expanded) return;
    name.setText("");
    name.setFocused(true);
    editor.reset();
    caretEpoch = System.currentTimeMillis();
  }

  void blur() {
    name.setFocused(false);
  }

  void close() {
    expanded = creating = false;
    h = wantedHeight = 0;
    name.setFocused(false);
    confirm = "";
    actions.clear();
  }

  void measure(GuiFonts.Face face, float width) {
    w = width;
    lineHeight = Math.max(8, (float) Math.ceil(face.height()) + 1);
    fieldHeight = Math.max(17, (float) Math.ceil(face.height()) + 5);
    actions.clear();
    String[] ids, labels;
    if (creating) {
      ids = new String[] {"save", "cancel"};
      labels = new String[] {"Save new", "Cancel"};
    } else if (target.equals(TOOLS)) {
      ids = new String[] {"folder", "refresh"};
      labels = new String[] {"Open folder", "Refresh list"};
    } else {
      ids = new String[] {"visuals", "bindings", "load", "update", "delete", "file", "copy"};
      labels =
          new String[] {
            "Load visuals",
            "Load keybinds",
            "Load config",
            confirm.equals("update") ? "Confirm save" : "Save current",
            confirm.equals("delete") ? "Confirm delete" : "Delete config",
            "Open file",
            "Copy path"
          };
    }
    wantedHeight = creating ? fieldHeight : 0;
    for (int i = 0; i < ids.length; i++) {
      boolean toggle = ids[i].equals("visuals") || ids[i].equals("bindings");
      List<String> lines = UiCanvas.wrap(face, labels[i], width - 8 - (toggle ? 20 : 6));
      float height =
          Math.max(14, (float) Math.ceil((lines.size() - 1) * lineHeight + face.height() + 5));
      actions.add(new Action(ids[i], lines, height, toggle));
      wantedHeight += height;
    }
    naturalHeight = wantedHeight;
    if (!expanded) wantedHeight = 0;
  }

  float arrow(String id) {
    return target.equals(id) ? Math.min(1, h / Math.max(1, naturalHeight)) : 0;
  }

  float height() {
    return h;
  }

  void advance(float weight) {
    h = GuiMotion.height(h, wantedHeight, weight);
  }

  void place(float left, float top) {
    x = left;
    y = top;
    fieldY = top;
    float ay = top + (creating ? fieldHeight : 0);
    for (Action action : actions) {
      action.y = ay;
      ay += action.h;
    }
  }

  void load(String profile) {
    if (busy()) return;
    selected = profile;
    try {
      // ClickGUI is a Misc utility, while HUD and visual modules use Render.
      final boolean visuals = loadVisuals;
      operation =
          runtime
              .config()
              .loadProfile(
                  profile,
                  loadKeybinds,
                  entry ->
                      visuals
                          || (entry.info().category() != ModuleCategory.RENDER
                              && !entry.info().id().equals("clickgui")
                              && !entry.info().id().equals("themes")));
      closeAfterOperation = false;
      success = "Loaded " + profile;
      message = "Loading " + profile + "...";
      error = false;
    } catch (RuntimeException failure) {
      fail(failure);
    }
  }

  void draw(UiCanvas c, float mx, float my) {
    c.rect(x, y, w, h, ModernTheme.SETTINGS);
    if (creating) {
      ModernTheme.input(c, x + 4, fieldY + 1, w - 8, fieldHeight - 2, name.isFocused());
      editor.draw(
          c,
          name,
          x + 7,
          c.font.centeredY(fieldY, fieldHeight) - 1,
          w - 14,
          caretEpoch,
          name.isFocused());
      if (name.getText().isEmpty() && !name.isFocused())
        c.label("Name", x + 7, c.font.centeredY(fieldY, fieldHeight), w - 14, c.muted);
    }
    for (Action action : actions) {
      if (action.y >= y + h) break;
      boolean enabled = !busy() && (!action.id.equals("save") || !name.getText().trim().isEmpty());
      if (enabled && UiCanvas.inside(mx, my, x + 4, action.y, w - 8, action.h))
        c.rect(x + 4, action.y, w - 8, action.h, c.hover);
      if (confirm.equals(action.id)) c.rect(x + 4, action.y, w - 8, action.h, c.selection);
      float labelY = c.font.centeredY(action.y, action.h - (action.lines.size() - 1) * lineHeight);
      for (String line : action.lines) {
        c.text(line, x + 7, labelY, enabled ? c.text : c.muted);
        labelY += lineHeight;
      }
      if (action.toggle)
        ModernControls.checkboxRow(
            c,
            x + w - 4,
            action.y,
            action.h,
            action.id.equals("visuals") ? loadVisuals : loadKeybinds);
    }
  }

  boolean click(UiCanvas c, float mx, float my, int button) {
    if (!expanded || !UiCanvas.inside(mx, my, x, y, w, h)) return false;
    if (button != 0 || busy()) return true;
    if (creating && UiCanvas.inside(mx, my, x + 4, fieldY, w - 8, fieldHeight)) {
      name.setFocused(true);
      editor.placeCursor(c, name, x + 7, w - 14, mx);
      caretEpoch = System.currentTimeMillis();
      return true;
    }
    for (Action action : actions)
      if ((!action.id.equals("save") || !name.getText().trim().isEmpty())
          && UiCanvas.inside(mx, my, x + 4, action.y, w - 8, action.h)) {
        act(action.id);
        break;
      }
    return true;
  }

  boolean type(char character, int code) {
    if (!creating || !name.isFocused()) return false;
    if (code == Keyboard.KEY_ESCAPE) close();
    else if (!busy()) {
      if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
        if (!name.getText().trim().isEmpty()) act("save");
      } else if (name.textboxKeyTyped(character, code)) caretEpoch = System.currentTimeMillis();
    }
    return true;
  }

  private void act(String id) {
    if (busy()) return;
    if (id.equals("cancel")) {
      close();
      return;
    }
    if (id.equals("visuals")) {
      loadVisuals = !loadVisuals;
      confirm = "";
      return;
    }
    if (id.equals("bindings")) {
      loadKeybinds = !loadKeybinds;
      confirm = "";
      return;
    }
    if (id.equals("load")) {
      load(target);
      return;
    }
    if ((id.equals("update") || id.equals("delete")) && !confirm.equals(id)) {
      confirm = id;
      return;
    }
    confirm = "";
    try {
      error = false;
      if (id.equals("refresh")) {
        close();
        refresh();
        message = "Refreshing configs...";
        return;
      }
      if (id.equals("copy")) {
        GuiScreen.setClipboardString(runtime.config().store().profilePath(target).toString());
        message = "Copied path for " + target;
        return;
      }
      closeAfterOperation = false;
      if (id.equals("save")) {
        String value = name.getText().trim();
        operation = runtime.config().saveProfile(value, false);
        selected = value;
        closeAfterOperation = true;
        success = "Created " + value;
      } else if (id.equals("update")) {
        operation = runtime.config().saveProfile(target, true);
        success = "Saved current settings to " + target;
      } else if (id.equals("delete")) {
        operation = runtime.config().store().deleteProfile(target);
        closeAfterOperation = true;
        success = "Deleted " + target;
      } else if (id.equals("file") || id.equals("folder")) {
        operation =
            runtime
                .config()
                .store()
                .openProfilePath(id.equals("folder") ? null : target)
                .thenCompose(
                    path ->
                        CompletableFuture.runAsync(
                            () -> {
                              try {
                                if (!Desktop.isDesktopSupported()
                                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                                  throw new IllegalStateException(
                                      "Use Copy file path to open the config manually");
                                Desktop.getDesktop().open(path.toFile());
                              } catch (Exception failure) {
                                throw new CompletionException(failure);
                              }
                            }));
        success = "Opened " + (id.equals("folder") ? "config folder" : target);
      }
      message = "Working...";
    } catch (RuntimeException failure) {
      fail(failure);
    }
  }

  private void fail(Throwable failure) {
    while (failure.getCause() != null) failure = failure.getCause();
    message = failure.getMessage() == null ? "Config operation failed" : failure.getMessage();
    error = true;
  }

  private static final class Action {
    final String id;
    final List<String> lines;
    final float h;
    final boolean toggle;
    float y;

    Action(String id, List<String> lines, float h, boolean toggle) {
      this.id = id;
      this.lines = lines;
      this.h = h;
      this.toggle = toggle;
    }
  }
}
