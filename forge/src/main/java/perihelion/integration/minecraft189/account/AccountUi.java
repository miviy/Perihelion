package perihelion.integration.minecraft189.account;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.*;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.menu.MenuFontScale;
import perihelion.integration.minecraft189.gui.menu.MenuSurface;
import perihelion.integration.minecraft189.gui.menu.MenuTheme;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.*;
import perihelion.integration.minecraft189.render.*;

/** Shared compact account chrome uses the same live preferences as Modules, Configs and Adjust. */
public abstract class AccountUi extends GuiScreen {
  protected final AccountService service;
  protected final GuiScreen parent;
  protected final UiCanvas canvas = new UiCanvas();
  protected final GuiFonts fonts = new GuiFonts();
  private final RenderState state = new RenderState();
  protected final List<Input> inputs = new ArrayList<>();
  private final List<Action> actions = new ArrayList<>();
  protected AccountService.Job job;
  protected String status = "";
  protected float scale = 1, left, top, panelWidth = 452, panelHeight = 294;
  private boolean setup, previousRepeat, opened;
  private MenuFontScale menuBody, menuSection;
  private String confirmation;
  private Runnable confirmed;

  protected static final class Input {
    final GuiTextField field, masked;
    final InlineTextFieldView view = new InlineTextFieldView();
    final boolean secret;
    float x, y, w, h;
    boolean shown;

    Input(GuiTextField field, GuiTextField masked, boolean secret) {
      this.field = field;
      this.masked = masked;
      this.secret = secret;
    }

    public String text() {
      return field.getText();
    }

    public void text(String value) {
      field.setText(value);
      view.reset();
    }
  }

  private static final class Action {
    float x, y, w, h;
    boolean enabled;
    Runnable run;

    Action(float x, float y, float w, float h, boolean enabled, Runnable run) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.enabled = enabled;
      this.run = run;
    }
  }

  protected AccountUi(AccountService service, GuiScreen parent) {
    this.service = service;
    this.parent = parent;
  }

  protected abstract String title();

  protected void setup() {}

  protected abstract void content(float mx, float my);

  protected void contentClick(float x, float y, int button) {}

  protected void shortcut(char c, int key) {}

  protected void wheel(int delta) {}

  protected float preferredHeight() {
    return panelHeight;
  }

  @Override
  public void initGui() {
    if (!opened) {
      previousRepeat = Keyboard.areRepeatEventsEnabled();
      opened = true;
    }
    Keyboard.enableRepeatEvents(true);
    KeyBinding.unPressAllKeys();
    service.runtime.input().setAllowed(false, Collections.emptyList());
    if (!setup) {
      setup = true;
      setup();
    }
  }

  protected Input input(boolean secret, int limit) {
    GuiTextField field = new GuiTextField(inputs.size(), mc.fontRendererObj, 0, 0, 100, 16);
    field.setMaxStringLength(limit);
    field.setEnableBackgroundDrawing(false);
    GuiTextField masked = new GuiTextField(100 + inputs.size(), mc.fontRendererObj, 0, 0, 100, 16);
    masked.setMaxStringLength(limit);
    Input input = new Input(field, masked, secret);
    inputs.add(input);
    return input;
  }

  protected void field(Input input, String hint, float x, float y, float w) {
    x = snap(x);
    y = snap(y);
    w = snap(w);
    input.x = x;
    input.y = y;
    input.w = w;
    input.h = 20;
    input.shown = true;
    if (canvas.modern) ModernTheme.input(canvas, x, y, w, 20, input.field.isFocused());
    else {
      canvas.rect(x, y, w, 20, canvas.panel);
      if (input.field.isFocused()) canvas.rect(x, y, w, 20, canvas.hover);
    }
    if (input.text().isEmpty() && !input.field.isFocused())
      label(hint, x + 8, canvas.font.centeredY(y, 20), w - 16, canvas.muted);
    else if (input.field.isFocused()) {
      GuiTextField display = input.field;
      if (input.secret) {
        input.masked.setText(mask(input.text().length()));
        input.masked.setCursorPosition(input.field.getCursorPosition());
        input.masked.setSelectionPos(input.field.getSelectionEnd());
        display = input.masked;
      }
      input.view.draw(canvas, display, x + 8, canvas.font.centeredY(y, 20) - 1, w - 16, 0);
    } else
      label(
          input.secret ? mask(Math.min(30, input.text().length())) : input.text(),
          x + 8,
          canvas.font.centeredY(y, 20),
          w - 16,
          canvas.text);
  }

  private static String mask(int length) {
    char[] text = new char[length];
    Arrays.fill(text, '*');
    return new String(text);
  }

  protected void label(String value, float x, float y, float w, int color) {
    canvas.label(value, x, y, w, color);
  }

  protected float snap(float value) {
    return canvas.modern ? ModernTheme.snap(canvas, value) : value;
  }

  /** Account child screens share the same surfaces without replacing their input controllers. */
  protected void surface(float x, float y, float w, float h, int color) {
    if (canvas.modern) ModernTheme.surface(canvas, x, y, w, h, color);
    else canvas.rect(x, y, w, h, color);
  }

  protected void button(String name, float x, float y, float w, boolean enabled, Runnable action) {
    button(name, x, y, w, 18, false, enabled, action);
  }

  protected void button(
      String name,
      float x,
      float y,
      float w,
      float h,
      boolean selected,
      boolean enabled,
      Runnable action) {
    x = snap(x);
    y = snap(y);
    w = snap(w);
    h = snap(h);
    if (canvas.modern) {
      ModernTheme.button(
          canvas, x, y, w, h, selected, enabled, UiCanvas.inside(mouseX, mouseY, x, y, w, h));
      canvas.center(
          name, x + 4, canvas.font.centeredY(y, h), w - 8, enabled ? canvas.text : canvas.muted);
    } else if (MenuTheme.active(this)) {
      MenuSurface.button(
          canvas,
          x,
          y,
          w,
          h,
          selected,
          enabled,
          UiCanvas.inside(mouseX, mouseY, x, y, w, h) ? 1 : 0);
      canvas.center(
          name, x + 4, canvas.font.centeredY(y, h), w - 8, enabled ? canvas.text : canvas.muted);
    } else
      GuiTheme.button(
          canvas, name, x, y, w, h, selected, enabled, UiCanvas.inside(mouseX, mouseY, x, y, w, h));
    actions.add(new Action(x, y, w, h, enabled, action));
  }

  private float mouseX, mouseY;

  protected void navigation(
      String name,
      float x,
      float y,
      float w,
      float h,
      boolean selected,
      boolean enabled,
      Runnable action) {
    x = snap(x);
    y = snap(y);
    w = snap(w);
    h = snap(h);
    boolean hover = UiCanvas.inside(mouseX, mouseY, x, y, w, h);
    if (canvas.modern) ModernTheme.button(canvas, x, y, w, h, selected, enabled, hover);
    else if (MenuTheme.active(this))
      MenuSurface.button(canvas, x, y, w, h, selected, enabled, hover ? 1 : 0);
    else if (selected || hover && enabled)
      canvas.rect(x, y, w, h, selected ? canvas.selection : canvas.hover);
    canvas.label(
        name,
        x + 8,
        canvas.font.centeredY(y, h),
        w - 16,
        !enabled ? canvas.muted : selected ? canvas.selectedText : canvas.text);
    actions.add(new Action(x, y, w, h, enabled, action));
  }

  protected void start(AccountService.Job next) {
    if (next != null) {
      service.cancel(job);
      job = next;
      status = "";
    }
  }

  protected boolean idle() {
    return service.ready() && !service.busy();
  }

  protected void confirm(String text, Runnable action) {
    confirmation = text;
    confirmed = action;
    for (Input i : inputs) i.field.setFocused(false);
  }

  protected void result(String text) {
    status = text;
  }

  protected void back() {
    mc.displayGuiScreen(parent);
  }

  @Override
  public final void drawScreen(int x, int y, float partialTicks) {
    if (!service.runtime.isRunning()) {
      mc.displayGuiScreen(null);
      return;
    }
    ClickGuiModule preferences = GuiTheme.preferences(service.runtime);
    if (MenuTheme.active(this)) MenuTheme.background(this);
    panelHeight = preferredHeight();
    scale =
        Math.min(
            preferences.scale.get().floatValue(),
            Math.min(width / (panelWidth + 12), height / (panelHeight + 12)));
    left = (width / scale - panelWidth) / 2;
    top = (height / scale - panelHeight) / 2;
    if (preferences.modern()) {
      float pixels = new ScaledResolution(mc).getScaleFactor() * scale;
      left = Math.round(left * pixels) / pixels;
      top = Math.round(top * pixels) / pixels;
    }
    mouseX = x / scale - left;
    mouseY = y / scale - top;
    actions.clear();
    for (Input i : inputs) i.shown = false;
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      GlStateManager.scale(scale, scale, 1);
      GuiTheme.configure(
          canvas, fonts, preferences, new ScaledResolution(mc).getScaleFactor() * scale);
      if (MenuTheme.active(this) && !preferences.modern()) {
        if (menuBody == null || menuBody.source != canvas.font)
          menuBody = new MenuFontScale(canvas.font, 1.2f);
        if (menuSection == null || menuSection.source != canvas.sectionFont)
          menuSection = new MenuFontScale(canvas.sectionFont, 1.2f);
        canvas.font = menuBody;
        canvas.sectionFont = menuSection;
      }
      if (MenuTheme.active(this) && !preferences.modern() && !preferences.customTextStyle.get())
        MenuTheme.accountPalette(canvas);
      canvas.rect(
          0,
          0,
          width / scale,
          height / scale,
          GuiTheme.alpha(preferences.background.get(), canvas.modern ? 0x11100f : 0x090b0e));
      GlStateManager.translate(left, top, 0);
      if (canvas.modern) ModernTheme.surface(canvas, 0, 0, panelWidth, panelHeight, canvas.panel);
      else if (MenuTheme.active(this))
        MenuSurface.rounded(canvas, 0, 0, panelWidth, panelHeight, 5, canvas.panel);
      else canvas.rect(0, 0, panelWidth, panelHeight, canvas.panel);
      if (canvas.modern) {
        ModernTheme.surface(canvas, 0, 0, panelWidth, 28, canvas.header);
        canvas.rect(0, 24, panelWidth, 4, canvas.header);
      } else
        canvas.rect(
            0,
            0,
            panelWidth,
            28,
            GuiTheme.alpha((canvas.header >>> 24) / 255.0 * .18, canvas.header));
      canvas.section(
          title(),
          16,
          canvas.modern ? canvas.sectionFont.centeredY(0, 28) : 11,
          panelWidth - 96,
          false);
      canvas.hairline(16, 26, panelWidth - 32, GuiTheme.alpha(.24, canvas.accent));
      boolean working = job != null && !job.done;
      navigation(
          working ? "Cancel" : "Done",
          panelWidth - 80,
          6,
          64,
          18,
          false,
          true,
          () -> {
            if (working) service.cancel(job);
            else back();
          });
      content(mouseX, mouseY);
      String message =
          !service.ready()
              ? service.storageStatus()
              : !status.isEmpty() ? status : job == null ? "" : job.status;
      if (job != null && !job.notice.isEmpty()) message += " " + job.notice;
      if (message.equals("Done.")) message = "";
      List<String> lines = UiCanvas.wrap(canvas.font, message, panelWidth - 32);
      for (int i = 0; i < Math.min(2, lines.size()); i++)
        label(
            lines.get(i),
            16,
            panelHeight - 25 + i * (canvas.font.height() + 2),
            panelWidth - 32,
            canvas.muted);
      if (confirmation != null) {
        actions.clear();
        for (Input i : inputs) i.shown = false;
        float overlayTop = canvas.modern ? 28 : 13;
        surface(0, overlayTop, panelWidth, panelHeight - overlayTop, GuiTheme.overlay(canvas));
        List<String> wrapped = UiCanvas.wrap(canvas.font, confirmation, panelWidth - 48);
        for (int i = 0; i < wrapped.size(); i++)
          label(wrapped.get(i), 24, 60 + i * 13, panelWidth - 48, canvas.text);
        button(
            "Confirm",
            panelWidth / 2 - 94,
            panelHeight - 65,
            88,
            true,
            () -> {
              Runnable run = confirmed;
              confirmation = null;
              confirmed = null;
              run.run();
            });
        button(
            "Cancel",
            panelWidth / 2 + 6,
            panelHeight - 65,
            88,
            true,
            () -> {
              confirmation = null;
              confirmed = null;
            });
      }
    }
  }

  public final void click(float x, float y, int button) {
    for (int i = actions.size() - 1; i >= 0; i--) {
      Action a = actions.get(i);
      if (UiCanvas.inside(x, y, a.x, a.y, a.w, a.h)) {
        if (button == 0 && a.enabled) a.run.run();
        return;
      }
    }
    if (confirmation != null) return;
    for (Input input : inputs) {
      boolean hit = input.shown && UiCanvas.inside(x, y, input.x, input.y, input.w, input.h);
      input.field.setFocused(hit);
      if (hit && button == 0) {
        GuiTextField display = input.secret ? input.masked : input.field;
        if (input.secret) {
          input.masked.setText(mask(input.text().length()));
          input.masked.setCursorPosition(input.field.getCursorPosition());
        }
        input.view.placeCursor(canvas, display, input.x + 8, input.w - 16, x);
        input.field.setCursorPosition(display.getCursorPosition());
        input.field.setSelectionPos(display.getSelectionEnd());
      }
    }
    contentClick(x, y, button);
  }

  @Override
  protected final void mouseClicked(int x, int y, int button) throws IOException {
    click(x / scale - left, y / scale - top, button);
  }

  public final void type(char c, int key) {
    if (confirmation != null) {
      if (key == Keyboard.KEY_ESCAPE) {
        confirmation = null;
        confirmed = null;
      }
      return;
    }
    if (key == Keyboard.KEY_ESCAPE) {
      back();
      return;
    }
    if (key == Keyboard.KEY_TAB) {
      List<Input> shown = new ArrayList<>();
      for (Input i : inputs) if (i.shown) shown.add(i);
      int index = -1;
      for (int i = 0; i < shown.size(); i++) if (shown.get(i).field.isFocused()) index = i;
      for (Input i : inputs) i.field.setFocused(false);
      if (!shown.isEmpty()) shown.get((index + 1) % shown.size()).field.setFocused(true);
      return;
    }
    for (Input input : inputs)
      if (input.shown && input.field.isFocused()) {
        input.field.textboxKeyTyped(c, key);
        shortcut(c, key);
        return;
      }
    shortcut(c, key);
  }

  @Override
  protected final void keyTyped(char c, int key) throws IOException {
    type(c, key);
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int d = Mouse.getEventDWheel();
    if (d != 0 && confirmation == null) wheel(d > 0 ? -1 : 1);
  }

  @Override
  public void updateScreen() {
    for (Input i : inputs) i.field.updateCursorCounter();
  }

  @Override
  public void onGuiClosed() {
    opened = false;
    service.cancel(job);
    fonts.close();
    for (Input i : inputs) {
      i.field.setFocused(false);
      if (i.secret) {
        i.text("");
        i.masked.setText("");
      }
    }
    Keyboard.enableRepeatEvents(previousRepeat);
    KeyBinding.unPressAllKeys();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
