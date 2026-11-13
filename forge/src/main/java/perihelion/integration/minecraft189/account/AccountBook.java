package perihelion.integration.minecraft189.account;

import java.util.*;

/** Snapshot reads and synchronized edits keep the UI independent of serialized account work. */
public final class AccountBook {
  public static final int LIMIT = 5000;
  private final List<Account> accounts = new ArrayList<>();

  public synchronized List<Account> list(String query, boolean pinned, boolean alphabetical) {
    List<Account> result = new ArrayList<>();
    String q = query.toLowerCase(Locale.ROOT).trim();
    for (Account a : accounts)
      if ((!pinned || a.pinned) && (a.name + " " + a.type()).toLowerCase(Locale.ROOT).contains(q))
        result.add(a.copy());
    Comparator<Account> order = Comparator.comparing(a -> !a.pinned);
    if (alphabetical) order = order.thenComparing(a -> a.name.toLowerCase(Locale.ROOT));
    result.sort(order);
    return result;
  }

  public synchronized List<Account> snapshot() {
    List<Account> result = new ArrayList<>();
    for (Account a : accounts) result.add(a.copy());
    return result;
  }

  public synchronized Account get(String id) {
    for (Account a : accounts) if (a.id.equals(id)) return a.copy();
    return null;
  }

  public synchronized Account upsert(Account incoming) {
    for (int i = 0; i < accounts.size(); i++) {
      Account a = accounts.get(i);
      boolean sameIdentity =
          a.uuid.replace("-", "").equalsIgnoreCase(incoming.uuid.replace("-", ""))
              && "offline".equals(a.kind) == "offline".equals(incoming.kind);
      if (a.id.equals(incoming.id) || sameIdentity) {
        Account next = incoming.copy();
        next.id = a.id;
        next.pinned = a.pinned;
        next.unban = a.unban;
        if (next.refresh.isEmpty() && !"offline".equals(next.kind)) next.refresh = a.refresh;
        accounts.set(i, next);
        return next.copy();
      }
    }
    if (accounts.size() >= LIMIT) throw new IllegalStateException("Account limit reached.");
    accounts.add(incoming.copy());
    return incoming.copy();
  }

  public synchronized Account remove(String id) {
    for (Iterator<Account> it = accounts.iterator(); it.hasNext(); ) {
      Account a = it.next();
      if (a.id.equals(id)) {
        it.remove();
        return a.copy();
      }
    }
    return null;
  }

  public synchronized int index(String id) {
    for (int i = 0; i < accounts.size(); i++) if (accounts.get(i).id.equals(id)) return i;
    return -1;
  }

  public synchronized void restore(Account a, int index) {
    if (a == null || get(a.id) != null || accounts.size() >= LIMIT) return;
    for (Account existing : accounts)
      if (existing.uuid.equals(a.uuid) && existing.kind.equals(a.kind)) return;
    accounts.add(Math.max(0, Math.min(index, accounts.size())), a.copy());
  }

  public synchronized void pin(String id) {
    for (Account a : accounts) if (a.id.equals(id)) a.pinned = !a.pinned;
  }

  public synchronized void move(String id, int direction) {
    if (direction != -1 && direction != 1) return;
    int from = index(id);
    if (from < 0) return;
    for (int to = from + direction; to >= 0 && to < accounts.size(); to += direction)
      if (accounts.get(from).pinned == accounts.get(to).pinned) {
        Collections.swap(accounts, from, to);
        return;
      }
  }

  public synchronized void ban(String uuid, long until) {
    for (Account a : accounts)
      if (a.uuid.replace("-", "").equalsIgnoreCase(uuid.replace("-", ""))) a.unban = until;
  }

  public synchronized void load(List<Account> values) {
    accounts.clear();
    if (values.size() > LIMIT) throw new IllegalArgumentException("Account limit exceeded.");
    Set<String> ids = new HashSet<>();
    for (Account a : values) {
      if (a == null
          || a.id == null
          || !ids.add(a.id)
          || a.name == null
          || a.uuid == null
          || a.kind == null
          || a.access == null
          || a.refresh == null
          || a.validation == null) throw new IllegalArgumentException("Invalid account file.");
      if (!a.name.matches("[A-Za-z0-9_]{3,16}")
          || !a.uuid.replace("-", "").matches("[A-Fa-f0-9]{32}"))
        throw new IllegalArgumentException("Invalid account identity.");
      accounts.add(a.copy());
    }
  }
}
