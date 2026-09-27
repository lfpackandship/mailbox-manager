package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;

class RentalHistoryRepositoryTest {

  private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);

  private final MailboxRepository mailboxes = new MailboxRepository();

  private final RentalHistoryRepository history = new RentalHistoryRepository();

  @BeforeEach
  void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
  }

  @Test
  void addingABoxWithARentalStartsItsHistory() throws SQLException {
    var id = mailboxes.insert(box("210", JAN_1.plusMonths(6)),
        new RentalPeriod(0, 0, JAN_1, JAN_1, JAN_1.plusMonths(6), 6000L, "Cash", null));

    var periods = history.findForMailbox(id);
    assertEquals(1, periods.size());
    var first = periods.get(0);
    assertEquals(id, first.getMailboxId());
    assertEquals(JAN_1, first.getRecordedOn());
    assertEquals(JAN_1, first.getStartDate());
    assertEquals(LocalDate.of(2026, 7, 1), first.getEndDate());
    assertEquals(6000L, first.getAmountCents());
    assertEquals("Cash", first.getPaymentMethod());
    assertNull(first.getNote());
  }

  @Test
  void renewingRecordsThePeriodAndMovesTheEndDate() throws SQLException {
    var id = mailboxes.insert(box("210", JAN_1));
    var newEnd = JAN_1.plusMonths(12);

    history.renew(id, new RentalPeriod(0, 0, JAN_1, JAN_1, newEnd, null, " ", "  check #1042 "));

    assertEquals(newEnd, mailboxes.findAll().get(0).getEndDate());
    var renewal = history.findForMailbox(id).get(0);
    assertNull(renewal.getAmountCents());
    assertNull(renewal.getPaymentMethod());
    assertEquals("check #1042", renewal.getNote());
  }

  @Test
  void historyIsOldestFirst() throws SQLException {
    var id = mailboxes.insert(box("210", JAN_1));
    history.renew(id, period(JAN_1.plusMonths(3), JAN_1.plusMonths(3), JAN_1.plusMonths(6)));
    history.renew(id, period(JAN_1, JAN_1, JAN_1.plusMonths(3)));

    assertEquals(List.of(JAN_1, JAN_1.plusMonths(3)), history.findForMailbox(id).stream()
        .map(RentalPeriod::getStartDate).collect(Collectors.toList()));
  }

  @Test
  void findsEntriesRecordedBetweenTwoDaysForAllBoxesNewestFirst() throws SQLException {
    var first = mailboxes.insert(box("210", JAN_1));
    var second = mailboxes.insert(box("211", JAN_1));
    history.renew(first, period(LocalDate.of(2025, 12, 31), JAN_1, JAN_1.plusMonths(1)));
    history.renew(first, period(LocalDate.of(2026, 1, 1), JAN_1, JAN_1.plusMonths(1)));
    history.renew(second, period(LocalDate.of(2026, 1, 31), JAN_1, JAN_1.plusMonths(1)));
    history.renew(second, period(LocalDate.of(2026, 2, 1), JAN_1, JAN_1.plusMonths(1)));

    var january = history.findRecordedBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1)), january.stream()
        .map(RentalPeriod::getRecordedOn).collect(Collectors.toList()));
  }

  @Test
  void deletingAnEntryLeavesTheEndDateAlone() throws SQLException {
    var id = mailboxes.insert(box("210", JAN_1));
    history.renew(id, period(JAN_1, JAN_1, JAN_1.plusMonths(1)));

    history.delete(history.findForMailbox(id).get(0).getId());

    assertEquals(List.of(), history.findForMailbox(id));
    assertEquals(JAN_1.plusMonths(1), mailboxes.findAll().get(0).getEndDate());
  }

  @Test
  void deletingABoxDeletesItsHistory() throws SQLException {
    var gone = mailboxes.insert(box("210", JAN_1));
    var kept = mailboxes.insert(box("211", JAN_1));
    history.renew(gone, period(JAN_1, JAN_1, JAN_1.plusMonths(1)));
    history.renew(kept, period(JAN_1, JAN_1, JAN_1.plusMonths(1)));

    mailboxes.delete(gone);

    assertEquals(List.of(), history.findForMailbox(gone));
    assertEquals(1, history.findForMailbox(kept).size());
  }

  private static RentalPeriod period(LocalDate recordedOn, LocalDate start, LocalDate end) {
    return new RentalPeriod(0, 0, recordedOn, start, end, 1000L, "Card", null);
  }

  private static Mailbox box(String boxNumber, LocalDate endDate) {
    return new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "(555) 123-4567", null,
        List.of(), endDate, null);
  }

}
