package perihelion.integration.minecraft189.gui.panel;

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
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.InlineTextFieldView;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Named preset browser. Disk operations are explicit and asynchronous, never per frame. */
final class ConfigPanel {
  private final ClientRuntime runtime;
  private final GuiTextField name;
  private final InlineTextFieldView editor = new InlineTextFieldView();
  private final List<Button> buttons = new ArrayList<>();
  private List<String> profiles = Collections.emptyList();
  private CompletableFuture<List<String>> listing;
  private CompletableFuture<?> operation;
  private String selected = "", status = "", success = "", confirm = "";
  private boolean error, details = true;
  private float windowHeight;
  private static final float LIST_ROW = 13;
  private float listRow = LIST_ROW, messageLine = 11;
  private float listPaneWidth = 104, detailPaneWidth = 224, saveWidth = 58;
  private float left, top, width, listTop, listHeight, listWidth, fieldX, fieldY, fieldWidth;
  private int offset;
  private long caretEpoch;

  ConfigPanel(ClientRuntime runtime) {
    this.runtime = runtime;
    name = new GuiTextField(19, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 14);
    name.setEnableBackgroundDrawing(false);
    name.setMaxStringLength(48);
  }

  void refresh() {
    if (listing != null || operation != null) return;
    listing = runtime.config().store().listProfiles();
    status = "Reading configs";
    error = false;
  }

  void update() {
    name.updateCursorCounter();
    if (operation != null && operation.isDone()) {
      CompletableFuture<?> completed = operation;
      operation = null;
      try {
        completed.join();
        status = success;
        error = false;
      } catch (RuntimeException failure) {
        fail(failure);
      }
      listing = runtime.config().store().listProfiles();
    }
    if (listing != null && listing.isDone()) {
      CompletableFuture<List<String>> completed = listing;
      listing = null;
      try {
        profiles = completed.join();
        if (!profiles.contains(selected)) selected = "";
        offset = Math.max(0, Math.min(offset, profiles.size() - visibleRows()));
        if (status.equals("Reading configs")) status = profiles.size() + " saved configs";
      } catch (RuntimeException failure) {
        fail(failure);
      }
    }
  }

  private void fail(Throwable failure) {
    while (failure.getCause() != null) failure = failure.getCause();
    status = failure.getMessage() == null ? "Config operation failed" : failure.getMessage();
    error = true;
  }

  private boolean busy() {
    return operation != null || listing != null;
  }

  private int visibleRows() {
    return Math.max(1, (int) (listHeight / listRow));
  }

  float width() {
    return width;
  }

  float preferredWidth(UiCanvas canvas) {
    listRow =
        canvas.modern ? Math.max(LIST_ROW, (float) Math.ceil(canvas.font.height()) + 4) : LIST_ROW;
    messageLine = canvas.modern ? Math.max(11, (float) Math.ceil(canvas.font.height()) + 2) : 11;
    listPaneWidth = Math.max(104, canvas.sectionFont.width("Configuration") + 12);
    listPaneWidth = Math.max(listPaneWidth, canvas.font.width("Manage presets") + 14);
    for (String profile : profiles)
      listPaneWidth = Math.max(listPaneWidth, canvas.font.width(profile) + 12);
    saveWidth = Math.max(58, canvas.font.width("Save new") + 8);
    detailPaneWidth = Math.max(224, canvas.font.width(selected) + 12);
    detailPaneWidth = Math.max(detailPaneWidth, canvas.font.width(name.getText()) + saveWidth + 32);
    return details ? listPaneWidth + 4 + detailPaneWidth : listPaneWidth;
  }

  private String message() {
    return status.isEmpty() ? "Select a preset to load, update or share." : status;
  }

  float preferredHeight(UiCanvas canvas) {
    if (!details)
      return GuiTheme.HEADER + 4 + Math.max(1, Math.min(10, profiles.size())) * listRow + 20;
    return Math.max(
        196,
        170 + UiCanvas.wrap(canvas.font, message(), detailPaneWidth - 12).size() * messageLine + 6);
  }

  boolean details() {
    return details;
  }

  boolean focused() {
    return name.isFocused();
  }

  void blur() {
    name.setFocused(false);
    confirm = "";
  }

  boolean contains(float mx, float my, UtilityLayout.Box box) {
    return UiCanvas.inside(
        mx, my, box.x, box.y, width, box.collapsed ? GuiTheme.HEADER : windowHeight);
  }

  void draw(
      UiCanvas canvas,
      float viewWidth,
      float viewHeight,
      float mx,
      float my,
      UtilityLayout.Box box) {
    width = preferredWidth(canvas);
    float height = preferredHeight(canvas);
    windowHeight = height;
    box.recover(viewWidth, viewHeight, width, box.collapsed ? GuiTheme.HEADER : height);
    left = box.x;
    top = box.y;
    if (canvas.modern) {
      left = box.x = ModernTheme.snap(canvas, left);
      top = box.y = ModernTheme.snap(canvas, top);
    }
    buttons.clear();
    if (canvas.classic)
      perihelion.integration.minecraft189.gui.raven.RavenTheme.frame(
          canvas,
          left,
          top,
          listPaneWidth,
          box.collapsed ? GuiTheme.HEADER : height,
          !box.collapsed);
    else if (canvas.modern)
      ModernTheme.surface(
          canvas, left, top, listPaneWidth, box.collapsed ? GuiTheme.HEADER : height, canvas.panel);
    else
      canvas.rect(left, top, listPaneWidth, box.collapsed ? GuiTheme.HEADER : height, canvas.panel);
    GuiTheme.header(canvas, "Configuration", left, top, listPaneWidth);
    if (box.collapsed) return;
    listWidth = listPaneWidth - 12;
    listTop = top + GuiTheme.HEADER + 3;
    listHeight = height - GuiTheme.HEADER - 23;
    if (profiles.isEmpty())
      canvas.label(
          busy() ? "Loading" : "No presets", left + 6, listTop + 2, listWidth, canvas.muted);
    int rows = visibleRows();
    for (int row = 0; row < rows && offset + row < profiles.size(); row++) {
      String item = profiles.get(offset + row);
      float y = listTop + row * listRow;
      boolean active = item.equals(selected),
          hover = UiCanvas.inside(mx, my, left + 3, y, listPaneWidth - 6, listRow);
      if (active || hover) {
        if (canvas.modern)
          ModernTheme.surface(
              canvas,
              left + 3,
              y,
              listPaneWidth - 6,
              listRow,
              active ? canvas.selection : canvas.hover);
        else
          canvas.rect(
              left + 3, y, listPaneWidth - 6, listRow, active ? canvas.selection : canvas.hover);
      }
      canvas.label(
          item,
          left + 6,
          canvas.font.centeredY(y, listRow),
          listWidth,
          active ? canvas.selectedText : canvas.text);
    }
    button(
        canvas,
        "manage",
        details ? "Hide actions" : "Manage presets",
        left + 3,
        top + height - 17,
        listPaneWidth - 6,
        14,
        true,
        mx,
        my);
    if (!details) return;
    float detailX = left + listPaneWidth + 4, detailWidth = width - listPaneWidth - 4;
    if (canvas.classic)
      perihelion.integration.minecraft189.gui.raven.RavenTheme.frame(
          canvas, detailX, top, detailWidth, height, true);
    else if (canvas.modern)
      ModernTheme.surface(canvas, detailX, top, detailWidth, height, canvas.panel);
    else canvas.rect(detailX, top, detailWidth, height, canvas.panel);
    GuiTheme.header(canvas, "Preset actions", detailX, top, detailWidth);
    float right = detailX + 6, rightWidth = detailWidth - 12;
    canvas.section("New preset", right, top + 21, rightWidth, false);
    fieldX = right;
    fieldY = top + 34;
    fieldWidth = rightWidth - saveWidth - 4;
    if (canvas.modern) {
      fieldX = ModernTheme.snap(canvas, fieldX);
      fieldY = ModernTheme.snap(canvas, fieldY);
      fieldWidth = ModernTheme.snap(canvas, fieldWidth);
      ModernTheme.input(canvas, fieldX, fieldY, fieldWidth, GuiTheme.ROW, name.isFocused());
    } else canvas.rect(fieldX, fieldY, fieldWidth, GuiTheme.ROW, GuiTheme.alpha(.3, 0x080808));
    if (name.isFocused()) {
      editor.draw(
          canvas,
          name,
          fieldX + 4,
          canvas.font.centeredY(fieldY, GuiTheme.ROW) - 1,
          fieldWidth - 8,
          caretEpoch);
    } else
      canvas.label(
          name.getText().isEmpty() ? "Name" : name.getText(),
          fieldX + 4,
          canvas.font.centeredY(fieldY, GuiTheme.ROW),
          fieldWidth - 8,
          name.getText().isEmpty() ? canvas.muted : canvas.text);
    button(
        canvas,
        "save",
        "Save new",
        right + rightWidth - saveWidth,
        fieldY,
        saveWidth,
        GuiTheme.ROW,
        !busy() && !name.getText().trim().isEmpty(),
        mx,
        my);
    canvas.section("Selected preset", right, top + 60, rightWidth, false);
    canvas.label(
        selected.isEmpty() ? "Select a preset on the left" : selected,
        right,
        top + 73,
        rightWidth,
        canvas.text);
    String[] ids = {"load", "update", "delete"};
    String[] labels = {"Load", "Update", "Delete"};
    float bw = (rightWidth - 4) / 3;
    for (int i = 0; i < ids.length; i++)
      button(
          canvas,
          ids[i],
          confirm.equals(ids[i]) ? "Confirm" : labels[i],
          right + i * (bw + 2),
          top + 89,
          bw,
          GuiTheme.ROW,
          !busy() && !selected.isEmpty(),
          mx,
          my);
    String[] share = {"file", "folder", "copy", "refresh"};
    String[] shareLabels = {"Open file", "Open folder", "Copy path", "Refresh"};
    bw = (rightWidth - 2) / 2;
    for (int i = 0; i < share.length; i++)
      button(
          canvas,
          share[i],
          shareLabels[i],
          right + (i % 2) * (bw + 2),
          top + 110 + (i / 2) * 19,
          bw,
          GuiTheme.ROW,
          !busy() && (i == 1 || i == 3 || !selected.isEmpty()),
          mx,
          my);
    if (height >= 192)
      canvas.label("Current setup autosaves.", right, top + 157, rightWidth, canvas.muted);
    List<String> messageLines = UiCanvas.wrap(canvas.font, message(), detailWidth - 12);
    for (int i = 0; i < messageLines.size(); i++)
      canvas.text(
          messageLines.get(i),
          detailX + 6,
          top + 170 + i * messageLine,
          error ? 0xffed9991 : canvas.muted);
  }

  private void button(
      UiCanvas canvas,
      String id,
      String label,
      float x,
      float y,
      float w,
      float h,
      boolean enabled,
      float mx,
      float my) {
    if (canvas.modern) {
      x = ModernTheme.snap(canvas, x);
      y = ModernTheme.snap(canvas, y);
      w = ModernTheme.snap(canvas, w);
      h = ModernTheme.snap(canvas, h);
    }
    buttons.add(new Button(id, x, y, w, h, enabled));
    GuiTheme.button(
        canvas,
        label,
        x,
        y,
        w,
        h,
        id.equals(confirm),
        enabled,
        UiCanvas.inside(mx, my, x, y, w, h));
  }

  void click(UiCanvas canvas, float mx, float my, int button) {
    if (button != 0) return;
    if (details && UiCanvas.inside(mx, my, fieldX, fieldY, fieldWidth, GuiTheme.ROW)) {
      name.setFocused(true);
      editor.placeCursor(canvas, name, fieldX + 4, fieldWidth - 8, mx);
      caretEpoch = System.currentTimeMillis();
      confirm = "";
      return;
    }
    name.setFocused(false);
    for (Button action : buttons)
      if (action.enabled && UiCanvas.inside(mx, my, action.x, action.y, action.w, action.h)) {
        action(action.id);
        return;
      }
    if (!busy() && UiCanvas.inside(mx, my, left + 6, listTop, listWidth, listHeight)) {
      int index = offset + (int) ((my - listTop) / listRow);
      if (index < profiles.size()) {
        selected = profiles.get(index);
        details = true;
        confirm = "";
        status = selected + ".json";
        error = false;
      }
    }
  }

  private void action(String id) {
    if (id.equals("manage")) {
      details = !details;
      name.setFocused(false);
      return;
    }
    try {
      if ((id.equals("delete") || id.equals("update")) && !confirm.equals(id)) {
        confirm = id;
        status = "Confirm " + id + " of " + selected + ".json";
        error = false;
        return;
      }
      confirm = "";
      error = false;
      if (id.equals("refresh")) {
        refresh();
        return;
      }
      if (id.equals("copy")) {
        GuiScreen.setClipboardString(runtime.config().store().profilePath(selected).toString());
        status = "File path copied. Share the JSON file from its folder.";
        return;
      }
      if (id.equals("save")) {
        String value = name.getText().trim();
        operation = runtime.config().saveProfile(value, false);
        selected =
            runtime
                .config()
                .store()
                .profilePath(value)
                .getFileName()
                .toString()
                .replaceFirst("\\.json$", "");
        success = "Created " + selected + ".json";
      } else if (id.equals("load")) {
        operation = runtime.config().loadProfile(selected);
        success = "Loaded " + selected + ". Changes autosave to your current setup.";
      } else if (id.equals("update")) {
        operation = runtime.config().saveProfile(selected, true);
        success = "Updated " + selected + ". Previous version retained as .bak.";
      } else if (id.equals("delete")) {
        operation = runtime.config().store().deleteProfile(selected);
        success = "Deleted " + selected + ". Recovery copy retained in the folder.";
      } else if (id.equals("file") || id.equals("folder")) {
        operation =
            runtime
                .config()
                .store()
                .openProfilePath(id.equals("folder") ? null : selected)
                .thenCompose(
                    path ->
                        CompletableFuture.runAsync(
                            () -> {
                              try {
                                if (!Desktop.isDesktopSupported()
                                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                                  throw new IllegalStateException(
                                      "Opening files is unavailable. Folder: "
                                          + runtime.config().store().profilesDirectory());
                                Desktop.getDesktop().open(path.toFile());
                              } catch (Exception failure) {
                                throw new CompletionException(failure);
                              }
                            }));
        success = "Opened " + (id.equals("file") ? selected + ".json" : "config folder");
      }
      status = "Working...";
    } catch (RuntimeException failure) {
      fail(failure);
    }
  }

  void type(char character, int code) {
    if (busy()) return;
    if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
      if (name.isFocused() && !name.getText().trim().isEmpty()) action("save");
      return;
    }
    if (name.isFocused() && name.textboxKeyTyped(character, code)) {
      caretEpoch = System.currentTimeMillis();
      confirm = "";
    }
  }

  void scroll(float delta) {
    offset =
        Math.max(
            0,
            Math.min(Math.max(0, profiles.size() - visibleRows()), offset + (delta > 0 ? 3 : -3)));
  }

  private static final class Button {
    final String id;
    final float x, y, w, h;
    final boolean enabled;

    Button(String id, float x, float y, float w, float h, boolean enabled) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.enabled = enabled;
    }
  }
}
