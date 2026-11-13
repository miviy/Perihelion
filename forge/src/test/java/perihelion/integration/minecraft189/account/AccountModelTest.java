package perihelion.integration.minecraft189.account;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.Test;

public class AccountModelTest {
  @Test
  public void offlineIdentityIsStableAndNamesAreStrict() {
    assertEquals(Account.offline("Example").uuid, Account.offline("Example").uuid);
    assertNotEquals(Account.offline("Example").uuid, Account.offline("example").uuid);
    for (String name : Arrays.asList("ab", "this_name_is_much_too_long", "../name", "name token"))
      assertThrows(IllegalArgumentException.class, () -> Account.offline(name));
  }

  @Test
  public void refreshUpsertPreservesPinOrderAndRenewability() {
    AccountBook book = new AccountBook();
    Account first = Account.offline("Example");
    first.kind = "msa";
    first.refresh = "renew";
    first.unban = 2000;
    book.upsert(first);
    book.pin(first.id);
    Account updated = first.copy();
    updated.id = UUID.randomUUID().toString();
    updated.name = "Renamed";
    updated.refresh = "";
    updated.access = "new";
    book.upsert(updated);
    assertEquals(1, book.snapshot().size());
    Account result = book.get(first.id);
    assertTrue(result.pinned);
    assertEquals("renew", result.refresh);
    assertEquals("Renamed", result.name);
    assertEquals(2000, result.unban);
  }

  @Test
  public void offlineAndAuthenticatedIdentitiesStayDistinct() {
    AccountBook book = new AccountBook();
    Account a = Account.offline("Example"), b = a.copy();
    b.id = UUID.randomUUID().toString();
    b.kind = "token";
    book.upsert(a);
    book.upsert(b);
    assertEquals(2, book.snapshot().size());
  }

  @Test
  public void filteringSortingReorderingAndUndoKeepStableIds() {
    AccountBook book = new AccountBook();
    Account z = Account.offline("Zulu"), a = Account.offline("Alpha"), b = Account.offline("Bravo");
    book.upsert(z);
    book.upsert(a);
    book.upsert(b);
    book.pin(b.id);
    assertEquals(b.id, book.list("", false, false).get(0).id);
    assertEquals(a.id, book.list("", false, true).get(1).id);
    book.move(a.id, -1);
    assertEquals(a.id, book.list("", false, false).get(1).id);
    book.move(a.id, 0);
    assertEquals(1, book.list("offline", true, false).size());
    assertEquals(a.id, book.list("ALP", false, false).get(0).id);
    int index = book.index(a.id);
    Account removed = book.remove(a.id);
    book.restore(removed, index);
    assertEquals(a.id, book.snapshot().get(index).id);
    book.restore(removed, index);
    assertEquals(3, book.snapshot().size());
  }

  @Test
  public void snapshotsCannotMutateStoredCredentials() {
    AccountBook book = new AccountBook();
    Account a = Account.offline("Example");
    book.upsert(a);
    a.name = "Changed";
    book.snapshot().get(0).name = "ChangedAgain";
    assertEquals("Example", book.get(a.id).name);
  }

  @Test
  public void hypixelDomainsAndDurationsAreBounded() {
    assertTrue(AccountBanStatus.hypixel("mc.hypixel.net:25565"));
    assertTrue(AccountBanStatus.hypixel("hypixel.io"));
    assertFalse(AccountBanStatus.hypixel("evilhypixel.net"));
    assertFalse(AccountBanStatus.hypixel("hypixel.net.evil.test"));
    assertEquals(
        -1, AccountBanStatus.unban("\u00a7cYou are permanently banned from this server!", 1000));
    assertEquals(
        1000 + 93784000,
        AccountBanStatus.unban(
            "You are temporarily banned for 1d 2h 3m 4s from this server!", 1000));
    assertEquals(Long.MIN_VALUE, AccountBanStatus.unban("Disconnected: rate limited", 1000));
    assertEquals(
        Long.MIN_VALUE,
        AccountBanStatus.unban(
            "You are temporarily banned for 999999999999999d from this server!", 1000));
    Account a = Account.offline("Example");
    a.kind = "msa";
    assertTrue(a.ban(10).contains("not checked"));
    a.unban = 1;
    assertTrue(a.ban(10).contains("recheck"));
  }

  @Test
  public void quantityDiscountsAndLimitsUseDecimalPrices() {
    AccountStores.Product p = new AccountStores.Product();
    p.price = new BigDecimal("0.10");
    p.stock = 20;
    p.limit = 10;
    p.discounts.put(3, new BigDecimal("10"));
    assertEquals(0, new BigDecimal("0.27").compareTo(p.total(3)));
    assertThrows(IllegalArgumentException.class, () -> p.total(0));
    assertThrows(IllegalArgumentException.class, () -> p.total(11));
  }
}
