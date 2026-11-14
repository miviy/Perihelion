package perihelion.integration.minecraft189.hud;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.panel.UtilityLayout;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.hud.HudModule.Element;
import perihelion.integration.minecraft189.render.*;

/** Screen-owned previews and chrome; drag positions still commit through native HUD settings. */
public final class HudEditor extends GuiScreen {
  private final HudModule hud;
  private final GuiScreen parent;
  private final boolean arrangeOnly;
  private HudRenderer preview;
  private Element dragging;
  private int offsetX, offsetY, dragX, dragY;
  private GuiButton toggle;
  private HudCustomization customization;
  private boolean previousRepeat, initialized;
  private boolean elementsMenu;
  private final RenderState state = new RenderState();
  private final GuiFonts fonts = new GuiFonts();
  private final UiCanvas canvas = new UiCanvas();
  private int elementsX, elementsY, elementsWidth;
  private ClickGuiModule.Font fontSeen;
  private boolean boldSeen, modernSeen;
  private float pixelsSeen;
  private final UtilityLayout utilities = new UtilityLayout();
  private UtilityLayout.Box toolbar;
  private boolean toolbarDragging;
  private int toolbarOffsetX, toolbarOffsetY;
  private static final int TOOLBAR_WIDTH = 122;

  HudEditor(HudModule hud, GuiScreen parent) {
    this(hud, parent, false);
  }

  HudEditor(HudModule hud, GuiScreen parent, boolean arrangeOnly) {
    this.hud = hud;
    this.parent = parent;
    this.arrangeOnly = arrangeOnly;
  }

  @Override
  public void initGui() {
    dragging = null;
    closeCustomization();
    elementsMenu = false;
    if (!initialized) {
      initialized = true;
      previousRepeat = Keyboard.areRepeatEventsEnabled();
    }
    Keyboard.enableRepeatEvents(true);
    if (preview == null) preview = new HudRenderer(hud);
    buttonList.clear();
    toolbarDragging = false;
    if (arrangeOnly) return;
    ClickGuiModule preferences = GuiTheme.preferences(hud.runtime);
    if (preferences != null) utilities.load(preferences.toolsLayout().get());
    utilities.resizeEditor("hud-editor", width, height);
    toolbar = utilities.box("hud-editor", Math.max(4, width / 2 - TOOLBAR_WIDTH / 2), 7);
    toolbar.recover(width, height, TOOLBAR_WIDTH, toolbar.collapsed ? 14 : 30);
    int x = (int) toolbar.x, y = (int) toolbar.y + 16;
    toggle = new ThemeButton(0, x, y, 59, "");
    buttonList.add(toggle);
    buttonList.add(new ThemeButton(3, x + 63, y, 59, "Elements"));
    placeToolbar();
    toggleLabel();
  }

  private void placeToolbar() {
    int[] offsets = {0, 63};
    for (int i = 0; i < buttonList.size(); i++) {
      GuiButton b = buttonList.get(i);
      b.xPosition = (int) toolbar.x + offsets[i];
      b.yPosition = (int) toolbar.y + 16;
      b.visible = !toolbar.collapsed;
    }
  }

  private void saveToolbar() {
    if (arrangeOnly) return;
    ClickGuiModule preferences = GuiTheme.preferences(hud.runtime);
    if (preferences != null && hud.runtime.isRunning())
      hud.runtime.modules().setSetting("clickgui", preferences.toolsLayout(), utilities.encode());
  }

  @Override
  public void updateScreen() {
    if (!hud.runtime.isRunning() || mc.theWorld == null || mc.thePlayer == null) {
      dragging = null;
      mc.displayGuiScreen(null);
      return;
    }
    preview.update(true);
    if (customization != null) customization.tick();
    toggleLabel();
  }

  private void toggleLabel() {
    if (toggle != null) toggle.displayString = "Hud";
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    if (preview == null || mc.theWorld == null || mc.thePlayer == null) return;
    ClickGuiModule preferences = GuiTheme.preferences(hud.runtime);
    ClickGuiModule.Font choice = GuiTheme.font(hud.runtime);
    boolean nextBold = GuiTheme.bold(preferences);
    boolean nextModern = GuiTheme.modern(hud.runtime);
    float nextPixels = mc.displayWidth / (float) width;
    if (choice != fontSeen
        || nextBold != boldSeen
        || nextModern != modernSeen
        || nextModern && nextPixels != pixelsSeen) {
      dragging = null;
      toolbarDragging = false;
      closeCustomization();
      fontSeen = choice;
      boldSeen = nextBold;
      modernSeen = nextModern;
      pixelsSeen = nextPixels;
    }
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      GuiTheme.configure(canvas, fonts, preferences, mc.displayWidth / (float) width);
      canvas.rect(
          0,
          0,
          width,
          height,
          nextModern
              ? 0x46191817
              : GuiTheme.alpha(preferences == null ? .25 : preferences.background.get(), 0x090b0e));
      if (dragging != null) {
        dragX = mouseX - offsetX;
        dragY = mouseY - offsetY;
      }
      preview.render(width, height, true, dragging, dragX, dragY);
      if (dragging == null && customization == null && !elementsMenu) {
        Element[] order = Element.values();
        for (int i = order.length - 1; i >= 0; i--) {
          HudRenderer.Box box = preview.boxes.get(order[i]);
          if (box.contains(mouseX, mouseY)) {
            canvas.outline(box.x, box.y, box.w, box.h, 0x557d7771);
            break;
          }
        }
      }
      if (arrangeOnly) return;
      if (toolbarDragging) {
        toolbar.x = mouseX - toolbarOffsetX;
        toolbar.y = mouseY - toolbarOffsetY;
        toolbar.recover(width, height, TOOLBAR_WIDTH, toolbar.collapsed ? 14 : 30);
        placeToolbar();
      }
      if (canvas.modern)
        ModernTheme.surface(
            canvas,
            toolbar.x,
            toolbar.y,
            TOOLBAR_WIDTH,
            toolbar.collapsed ? 14 : 30,
            ModernTheme.PANEL);
      GuiTheme.header(
          canvas,
          "Hud editor  " + (toolbar.collapsed ? "+" : "-"),
          toolbar.x,
          toolbar.y,
          TOOLBAR_WIDTH);
      super.drawScreen(mouseX, mouseY, partialTicks);
      if (customization != null) {
        customization.drag(mouseX, mouseY);
        customization.draw(
            toggle.xPosition + 64, toggle.yPosition + 16, width, height, mouseX, mouseY);
      } else if (elementsMenu) drawElements(mouseX, mouseY);
    }
  }

  private void drawElements(int mx, int my) {
    elementsWidth = 148;
    for (Element element : Element.values())
      elementsWidth =
          Math.max(
              elementsWidth,
              (int) Math.ceil(canvas.font.width(element.label)) + (canvas.modern ? 24 : 10));
    elementsWidth = Math.min(width - 12, elementsWidth);
    elementsX = Math.max(6, Math.min(width - elementsWidth - 6, toggle.xPosition + 64));
    int h = (int) GuiTheme.HEADER + Element.values().length * (int) GuiTheme.ROW + 4;
    elementsY = toggle.yPosition < height / 2 ? toggle.yPosition + 16 : toggle.yPosition - h - 4;
    elementsY = Math.max(4, Math.min(height - h - 4, elementsY));
    if (canvas.classic) {
      perihelion.integration.minecraft189.gui.raven.RavenTheme.frame(
          canvas, elementsX, elementsY, elementsWidth, h, true);
      perihelion.integration.minecraft189.gui.raven.RavenTheme.header(
          canvas, "Elements", elementsX, elementsY, elementsWidth);
      for (int i = 0; i < Element.values().length; i++) {
        Element element = Element.values()[i];
        float top = elementsY + GuiTheme.HEADER + i * GuiTheme.ROW;
        boolean shown = hud.elementEnabled(element);
        if (UiCanvas.inside(mx, my, elementsX, top, elementsWidth, GuiTheme.ROW))
          canvas.rect(elementsX + 1, top, elementsWidth - 2, GuiTheme.ROW, 0x5a000000);
        canvas.outline(
            elementsX + elementsWidth - 11, top + 5, 5, 5, shown ? 0xff14ff00 : canvas.muted);
        if (shown) canvas.rect(elementsX + elementsWidth - 10, top + 6, 3, 3, 0xff14ff00);
        canvas.label(
            element.label,
            elementsX + 5,
            canvas.font.centeredY(top, GuiTheme.ROW),
            elementsWidth - 20,
            shown ? 0xff14ff00 : canvas.text);
      }
      return;
    }
    if (canvas.modern)
      ModernTheme.surface(canvas, elementsX, elementsY, elementsWidth, h, ModernTheme.PANEL);
    else canvas.rect(elementsX, elementsY, elementsWidth, h, GuiTheme.overlay(canvas));
    GuiTheme.header(canvas, "Elements", elementsX, elementsY, elementsWidth);
    for (int i = 0; i < Element.values().length; i++) {
      Element element = Element.values()[i];
      float y = elementsY + GuiTheme.HEADER + i * GuiTheme.ROW;
      boolean shown = hud.elementEnabled(element);
      if (shown) canvas.rect(elementsX, y, elementsWidth, GuiTheme.ROW, canvas.selection);
      if (UiCanvas.inside(mx, my, elementsX, y, elementsWidth, GuiTheme.ROW))
        canvas.rect(elementsX, y, elementsWidth, GuiTheme.ROW, canvas.hover);
      if (canvas.modern)
        ModernTheme.checkboxRow(canvas, elementsX + elementsWidth, y, GuiTheme.ROW, shown);
      canvas.label(
          element.label,
          elementsX + 5,
          canvas.font.centeredY(y, GuiTheme.ROW),
          elementsWidth - (canvas.modern ? 24 : 10),
          shown ? canvas.selectedText : canvas.muted);
    }
  }

  @Override
  protected void mouseClicked(int x, int y, int button) throws IOException {
    if (customization != null) {
      if (customization.back(x, y)) {
        closeCustomization();
        elementsMenu = true;
      } else if (customization.contains(x, y)) customization.click(x, y, button);
      else closeCustomization();
      return;
    }
    if (!arrangeOnly && UiCanvas.inside(x, y, toolbar.x, toolbar.y, TOOLBAR_WIDTH, 14)) {
      commit();
      if (button == 1) {
        toolbar.collapsed = !toolbar.collapsed;
        elementsMenu = false;
        placeToolbar();
        saveToolbar();
      } else if (button == 0) {
        toolbarDragging = true;
        toolbarOffsetX = x - (int) toolbar.x;
        toolbarOffsetY = y - (int) toolbar.y;
      }
      return;
    }
    for (GuiButton b : buttonList)
      if (b.visible
          && x >= b.xPosition
          && x < b.xPosition + b.width
          && y >= b.yPosition
          && y < b.yPosition + b.height) {
        super.mouseClicked(x, y, button);
        return;
      }
    if (elementsMenu) {
      if ((button == 0 || button == 1)
          && UiCanvas.inside(
              x,
              y,
              elementsX,
              elementsY + GuiTheme.HEADER,
              elementsWidth,
              Element.values().length * GuiTheme.ROW)) {
        int index = (int) ((y - elementsY - GuiTheme.HEADER) / GuiTheme.ROW);
        Element element = Element.values()[index];
        if (button == 1) openCustomization(element);
        else hud.toggleElement(element);
      } else elementsMenu = false;
      return;
    }
    Element[] elements = Element.values();
    for (int i = elements.length - 1; i >= 0; i--) {
      Element element = elements[i];
      HudRenderer.Box box = preview.boxes.get(element);
      if (!box.contains(x, y)) continue;
      if (button == 1 && !arrangeOnly) openCustomization(element);
      else if (button == 0) {
        dragging = element;
        offsetX = x - box.anchorX;
        offsetY = y - box.anchorY;
        dragX = box.anchorX;
        dragY = box.anchorY;
      }
      return;
    }
  }

  @Override
  protected void mouseReleased(int x, int y, int button) {
    if (customization != null && button == 0) customization.release();
    if (button == 0 && toolbarDragging) {
      toolbar.x = x - toolbarOffsetX;
      toolbar.y = y - toolbarOffsetY;
      toolbar.recover(width, height, TOOLBAR_WIDTH, toolbar.collapsed ? 14 : 30);
      toolbarDragging = false;
      placeToolbar();
      saveToolbar();
    }
    if (button == 0 && dragging != null) {
      dragX = x - offsetX;
      dragY = y - offsetY;
      commit();
    }
    super.mouseReleased(x, y, button);
  }

  private void commit() {
    if (dragging == null || preview == null || !hud.runtime.isRunning()) return;
    if (fontSeen != GuiTheme.font(hud.runtime)
        || boldSeen != GuiTheme.bold(GuiTheme.preferences(hud.runtime))
        || modernSeen != GuiTheme.modern(hud.runtime)
        || modernSeen && pixelsSeen != mc.displayWidth / (float) width) {
      dragging = null;
      return;
    }
    HudRenderer.Box box = preview.boxes.get(dragging);
    hud.move(dragging, dragX, dragY, width, height, box.anchorW, box.anchorH);
    dragging = null;
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    commit();
    if (button.id == 0) {
      hud.runtime.modules().toggle("hud");
      toggleLabel();
    }
    if (button.id == 3) elementsMenu = !elementsMenu;
  }

  @Override
  protected void keyTyped(char character, int code) throws IOException {
    if (code == Keyboard.KEY_ESCAPE) {
      if (customization != null && customization.type(character, code)) return;
      commit();
      mc.displayGuiScreen(parent);
      return;
    }
    if (customization != null) {
      customization.type(character, code);
    }
  }

  private void openCustomization(Element element) {
    commit();
    elementsMenu = false;
    closeCustomization();
    if (!hud.options(element).isEmpty()) customization = new HudCustomization(hud, element, canvas);
  }

  private void closeCustomization() {
    if (customization != null) customization.close();
    customization = null;
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    if (customization != null) customization.wheel(Mouse.getEventDWheel());
  }

  @Override
  public void onGuiClosed() {
    toolbarDragging = false;
    closeCustomization();
    Keyboard.enableRepeatEvents(previousRepeat);
    initialized = false;
    saveToolbar();
    commit();
    if (preview != null) preview.close();
    preview = null;
    fonts.close();
    KeyBinding.unPressAllKeys();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  private final class ThemeButton extends GuiButton {
    ThemeButton(int id, int x, int y, int w, String text) {
      super(id, x, y, w, 14, text);
    }

    @Override
    public void drawButton(Minecraft game, int mx, int my) {
      if (!visible) return;
      boolean active =
          id == 0 && hud.runtime.modules().require("hud").requestedEnabled()
              || id == 3 && (elementsMenu || customization != null);
      GuiTheme.button(
          canvas,
          displayString,
          xPosition,
          yPosition,
          width,
          height,
          active,
          enabled,
          UiCanvas.inside(mx, my, xPosition, yPosition, width, height));
    }
  }
}
