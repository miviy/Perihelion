package perihelion.integration.minecraft189.account;

import java.util.*;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Virtualized account list with secondary tools kept off the primary sign-in flow. */
public final class AccountScreen extends AccountUi {
  private Input search;
  private String selected = "";
  private boolean pinned, alphabetical, context, more;
  private int scroll;
  private float contextX, contextY;
  private List<Account> rows = Collections.emptyList();
  private Account removed;
  private int removedIndex;

  public AccountScreen(AccountService service, GuiScreen parent) {
    super(service, parent);
    panelHeight = 306;
  }

  @Override
  protected String title() {
    return "Accounts";
  }

  @Override
  protected void setup() {
    search = input(false, 64);
  }

  private Account selection() {
    return service.book.get(selected);
  }

  private void rows() {
    rows = service.book.list(search.text(), pinned, alphabetical);
    scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - 5)));
  }

  @Override
  protected void content(float mx, float my) {
    rows();
    label("Current: " + mc.getSession().getUsername(), 16, 33, 310, canvas.enabled);
    label(rows.size() + " accounts", 344, 33, 92, canvas.muted);
    field(search, "Search name or type", 16, 46, 248);
    button(
        "Pinned",
        272,
        46,
        64,
        20,
        pinned,
        true,
        () -> {
          pinned = !pinned;
          scroll = 0;
        });
    button(
        alphabetical ? "A-Z" : "Saved order",
        344,
        46,
        92,
        20,
        false,
        true,
        () -> {
          alphabetical = !alphabetical;
          scroll = 0;
        });
    surface(16, 76, 420, 140, canvas.modern ? ModernTheme.INPUT : canvas.panel);
    for (int i = scroll; i < Math.min(rows.size(), scroll + 5); i++) {
      Account a = rows.get(i);
      float y = 76 + (i - scroll) * 28;
      boolean current =
          a.uuid.replace("-", "").equalsIgnoreCase(mc.getSession().getPlayerID().replace("-", ""));
      boolean chosen = a.id.equals(selected);
      if (chosen || UiCanvas.inside(mx, my, 16, y, 420, 28))
        surface(16, y, 420, 28, chosen ? canvas.selection : canvas.hover);
      if (current) canvas.rect(16, y + 5, 2, 18, canvas.accent);
      label(
          (a.pinned ? "* " : "") + a.name,
          26,
          canvas.font.centeredY(y, 28),
          264,
          current ? canvas.enabled : canvas.text);
      String type = a.type();
      label(type, 426 - canvas.font.width(type), canvas.font.centeredY(y, 28), 128, canvas.muted);
    }
    if (rows.isEmpty())
      label(
          service.ready() ? "No accounts match." : "Loading accounts...",
          26,
          89,
          390,
          canvas.muted);
    if (rows.size() > 5) {
      float length = 140f * 5 / rows.size();
      canvas.rect(434, 76 + (140 - length) * scroll / (rows.size() - 5), 2, length, canvas.accent);
    }
    Account a = selection();
    label(
        a == null
            ? "Select an account. Right-click a row for actions."
            : "offline".equals(a.kind)
                ? a.ban(System.currentTimeMillis())
                : a.validation + " / " + a.ban(System.currentTimeMillis()),
        16,
        226,
        420,
        canvas.muted);
    boolean disconnected = mc.theWorld == null && mc.getNetHandler() == null;
    float span = (panelWidth - 48) / 3;
    button(
        "Log in",
        16,
        246,
        span,
        20,
        true,
        idle() && a != null && disconnected,
        () -> start(service.login(selection(), true)));
    button(
        "Add account",
        24 + span,
        246,
        span,
        20,
        false,
        idle(),
        () -> mc.displayGuiScreen(new AccountAddScreen(service, this)));
    button(
        "More",
        32 + 2 * span,
        246,
        span,
        20,
        more,
        idle(),
        () -> {
          more = !more;
          context = false;
        });
    if (!disconnected && status.isEmpty() && job == null) status = "Disconnect to switch sessions.";
    if (more) {
      float x = panelWidth - 168, y = removed == null ? 166 : 142;
      surface(x, y, 152, removed == null ? 76 : 100, GuiTheme.overlay(canvas));
      navigation(
          "Profile tools",
          x + 4,
          y + 4,
          144,
          20,
          false,
          idle(),
          () -> {
            more = false;
            mc.displayGuiScreen(new AccountProfileScreen(service, this));
          });
      navigation(
          "Stores",
          x + 4,
          y + 28,
          144,
          20,
          false,
          idle(),
          () -> {
            more = false;
            mc.displayGuiScreen(new AccountStoreScreen(service, this));
          });
      navigation(
          "Save launcher",
          x + 4,
          y + 52,
          144,
          20,
          false,
          idle(),
          () -> {
            more = false;
            start(
                service.submit(
                    "Saving launcher...",
                    j -> service.add(service.launcherAccount()),
                    saved -> selected = saved.id));
          });
      if (removed != null)
        navigation(
            "Undo removal",
            x + 4,
            y + 76,
            144,
            20,
            false,
            idle(),
            () -> {
              more = false;
              undo();
            });
    }
    if (context && a != null) {
      String[] names = {
        "Log in", "Check account", a.pinned ? "Unpin" : "Pin", "Copy name", "Remove"
      };
      surface(contextX, contextY, 132, 128, GuiTheme.overlay(canvas));
      for (int i = 0; i < names.length; i++) {
        final int n = i;
        navigation(
            names[i],
            contextX + 4,
            contextY + 4 + i * 24,
            124,
            20,
            false,
            idle() && (n != 0 || disconnected),
            () -> {
              context = false;
              if (n == 0) start(service.login(selection(), true));
              else if (n == 1) start(service.login(selection(), false));
              else if (n == 2) {
                String id = selected;
                start(service.persist("Saving pin...", () -> service.book.pin(id)));
              } else if (n == 3) setClipboardString(a.name);
              else remove();
            });
      }
    }
  }

  private void remove() {
    Account a = selection();
    if (a == null) return;
    confirm(
        "Remove " + a.name + "? You can undo this removal while this account screen remains open.",
        () -> {
          String id = a.id;
          removedIndex = service.book.index(id);
          removed = a.copy();
          start(
              service.submit(
                  "Removing...",
                  j -> {
                    Account r = service.book.remove(id);
                    service.save();
                    return r;
                  },
                  r -> {
                    removed = r;
                    selected = "";
                  }));
        });
  }

  private void undo() {
    Account restore = removed;
    int index = removedIndex;
    start(
        service.submit(
            "Restoring...",
            j -> {
              service.book.restore(restore, index);
              service.save();
              return restore;
            },
            a -> {
              selected = a.id;
              removed = null;
            }));
  }

  @Override
  protected void contentClick(float x, float y, int button) {
    context = false;
    more = false;
    if (UiCanvas.inside(x, y, 16, 76, 420, 140)) {
      int index = scroll + (int) ((y - 76) / 28);
      if (index < rows.size()) {
        selected = rows.get(index).id;
        if (button == 1) {
          context = true;
          contextX = Math.min(x, panelWidth - 148);
          contextY = Math.min(y, 114);
        }
      }
    }
  }

  @Override
  protected void wheel(int d) {
    scroll += d * 3;
    rows();
  }

  @Override
  protected void back() {
    if (search != null && !search.text().isEmpty()) {
      search.text("");
      return;
    }
    super.back();
  }

  @Override
  protected void shortcut(char c, int key) {
    if (isCtrlKeyDown() && key == Keyboard.KEY_F) {
      search.field.setFocused(true);
      return;
    }
    if (search.field.isFocused()) return;
    if (isCtrlKeyDown() && key == Keyboard.KEY_C) {
      Account a = selection();
      if (a != null) setClipboardString(a.name);
      return;
    }
    if (isCtrlKeyDown() && key == Keyboard.KEY_Z && removed != null && idle()) {
      undo();
      return;
    }
    if (key == Keyboard.KEY_DELETE && idle()) {
      remove();
      return;
    }
    if (key == Keyboard.KEY_RETURN && selection() != null && idle() && mc.theWorld == null) {
      start(service.login(selection(), true));
      return;
    }
    int delta =
        key == Keyboard.KEY_UP
            ? -1
            : key == Keyboard.KEY_DOWN
                ? 1
                : key == Keyboard.KEY_PRIOR ? -5 : key == Keyboard.KEY_NEXT ? 5 : 0;
    if (isCtrlKeyDown()
        && delta != 0
        && !pinned
        && !alphabetical
        && search.text().isEmpty()
        && idle()) {
      String id = selected;
      start(service.persist("Reordering...", () -> service.book.move(id, Integer.signum(delta))));
      return;
    }
    int index = -1;
    for (int i = 0; i < rows.size(); i++) if (rows.get(i).id.equals(selected)) index = i;
    if (key == Keyboard.KEY_HOME) index = 0;
    else if (key == Keyboard.KEY_END) index = rows.size() - 1;
    else if (delta != 0) index = Math.max(0, Math.min(rows.size() - 1, index + delta));
    else return;
    if (index >= 0 && index < rows.size()) {
      selected = rows.get(index).id;
      if (index < scroll) scroll = index;
      if (index >= scroll + 5) scroll = index - 4;
    }
  }
}
