package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.DepositOutcome;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for saving, finding, and deleting mailboxes with their names and
 * addresses.
 */
class MailboxRepositoryTest {

  private final MailboxRepository repository = new MailboxRepository();

  @BeforeEach
  void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM mailboxes");
    }
  }

  @Test
  void insertedMailboxRoundTrips() throws SQLException {
    repository.insert(mailbox("210", List.of("Alt One", "Alt Two"), LocalDate.of(2027, 1, 31)));

    var all = repository.findAll();
    assertEquals(1, all.size());
    var saved = all.get(0);
    assertTrue(saved.getId() > 0);
    assertEquals("Ada", saved.getFirstName());
    assertEquals("Lovelace", saved.getLastName());
    assertEquals("Engines Ltd", saved.getBusinessTitle());
    assertEquals("210", saved.getBoxNumber());
    assertEquals("Box", saved.getBoxName());
    assertEquals("(555) 123-4567", saved.getPhone());
    assertEquals("ada@example.com", saved.getEmail());
    assertEquals(List.of("Alt One", "Alt Two"), saved.getAlternateBusinessNames());
    assertEquals(LocalDate.of(2027, 1, 31), saved.getEndDate());
  }

  @Test
  void missingEndDateRoundTripsAsNull() throws SQLException {
    repository.insert(mailbox("210", List.of(), null));
    assertNull(repository.findAll().get(0).getEndDate());
  }

  @Test
  void blankAlternateNamesAreNotSaved() throws SQLException {
    repository.insert(mailbox("210", Arrays.asList("Kept", "", "  ", null), null));
    assertEquals(List.of("Kept"), repository.findAll().get(0).getAlternateBusinessNames());
  }

  @Test
  void updateChangesFieldsAndReplacesAlternateNames() throws SQLException {
    repository.insert(mailbox("210", List.of("Old Name"), null));
    var id = repository.findAll().get(0).getId();

    repository.update(new Mailbox(id, "Grace", "Hopper", null, "211", null,
        "(555) 999-0000", null, List.of("New A", "New B"), LocalDate.of(2026, 12, 1), null));

    var all = repository.findAll();
    assertEquals(1, all.size());
    var updated = all.get(0);
    assertEquals(id, updated.getId());
    assertEquals("Grace", updated.getFirstName());
    assertEquals("211", updated.getBoxNumber());
    assertEquals(List.of("New A", "New B"), updated.getAlternateBusinessNames());
    assertEquals(LocalDate.of(2026, 12, 1), updated.getEndDate());
  }

  @Test
  void deleteRemovesMailboxAndItsAlternateNames() throws SQLException {
    repository.insert(mailbox("210", List.of("Gone"), null));
    repository.insert(mailbox("211", List.of("Stays"), null));
    var toDelete = repository.findAll().get(0);

    repository.delete(toDelete.getId());

    var all = repository.findAll();
    assertEquals(1, all.size());
    assertEquals(List.of("Stays"), all.get(0).getAlternateBusinessNames());
    try (var conn = Database.connect(); var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT COUNT(*) FROM business_names")) {
      rs.next();
      assertEquals(1, rs.getInt(1));
    }
  }

  @Test
  void forwardingAddressesRoundTripInOrder() throws SQLException {
    var summer = new ForwardingAddress("1 Lake Rd", null, "Duluth", "MN", "55802", "summer");
    var winter = new ForwardingAddress("88 Palm Way", "Unit 3B", "Naples", "FL", "34102-1234", null);
    repository.insert(withForwarding("210", List.of(summer, winter)));

    assertEquals(List.of(summer, winter), repository.findAll().get(0).getForwardingAddresses());
  }

  @Test
  void updateReplacesForwardingAddresses() throws SQLException {
    repository.insert(withForwarding("210",
        List.of(new ForwardingAddress("1 Old St", null, "Oldtown", "OH", "44101", null))));
    var saved = repository.findAll().get(0);
    var replacement = new ForwardingAddress("2 New Ave", "Apt 7", "Newport", "RI", "02840", "work");

    repository.update(new Mailbox(saved.getId(), saved.getFirstName(), saved.getLastName(), null,
        saved.getBoxNumber(), null, saved.getPhone(), null, List.of(), null, List.of(replacement)));

    assertEquals(List.of(replacement), repository.findAll().get(0).getForwardingAddresses());
  }

  @Test
  void deleteRemovesForwardingAddresses() throws SQLException {
    repository.insert(withForwarding("210",
        List.of(new ForwardingAddress("1 Gone St", null, "Anytown", "TX", "75001", null))));
    repository.insert(withForwarding("211",
        List.of(new ForwardingAddress("2 Kept St", null, "Anytown", "TX", "75001", null))));

    repository.delete(repository.findAll().get(0).getId());

    try (var conn = Database.connect(); var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT street FROM forwarding_addresses")) {
      assertTrue(rs.next());
      assertEquals("2 Kept St", rs.getString(1));
      assertFalse(rs.next());
    }
  }

  @Test
  void mailboxWithoutForwardingHasAnEmptyList() throws SQLException {
    repository.insert(mailbox("210", List.of(), null));
    assertEquals(List.of(), repository.findAll().get(0).getForwardingAddresses());
  }

  @Test
  void boxNumberTakenIgnoresWhitespaceAndCase() throws SQLException {
    repository.insert(mailbox("12A", List.of(), null));
    assertTrue(repository.isBoxNumberTaken("12A", 0));
    assertTrue(repository.isBoxNumberTaken("  12a ", 0));
    assertFalse(repository.isBoxNumberTaken("12B", 0));
  }

  @Test
  void boxNumberIsNotTakenByTheMailboxBeingEdited() throws SQLException {
    repository.insert(mailbox("210", List.of(), null));
    repository.insert(mailbox("211", List.of(), null));
    var first = repository.findAll().get(0);

    assertFalse(repository.isBoxNumberTaken("210", first.getId()));
    assertTrue(repository.isBoxNumberTaken("211", first.getId()));
  }

  @Test
  void notesAndClosedDateRoundTrip() throws SQLException {
    repository.insert(new Mailbox(0, "Ada", "Lovelace", null, "210", null, "(555) 123-4567", null,
        List.of(), null, null, "  ID on file\nPaid cash  ", LocalDate.of(2026, 3, 1)));

    var saved = repository.findAll().get(0);
    assertEquals("ID on file\nPaid cash", saved.getNotes());
    assertEquals(LocalDate.of(2026, 3, 1), saved.getClosedDate());
    assertTrue(saved.isClosed());
  }

  @Test
  void keysAndDepositRoundTripAndCanBeCleared() throws SQLException {
    var id = repository.insert(new Mailbox(0, "Ada", "Lovelace", null, "12", null, "", null, null, null, null,
        null, null, 2, 2000L));
    var saved = repository.findAll().get(0);
    assertEquals(2, saved.getKeyCount());
    assertEquals(2000L, saved.getKeyDepositCents());

    repository.update(new Mailbox(id, "Ada", "Lovelace", null, "12", null, "", null, null, null, null,
        null, null, 0, null));
    var updated = repository.findAll().get(0);
    assertEquals(0, updated.getKeyCount());
    assertNull(updated.getKeyDepositCents());
    // Renewing and closing keep them.
    assertEquals(0, updated.withEndDate(LocalDate.now()).withClosedDate(null).getKeyCount());
  }

  @Test
  void blankNotesAreStoredAsNone() throws SQLException {
    repository.insert(new Mailbox(0, "Ada", "Lovelace", null, "210", null, "(555) 123-4567", null,
        List.of(), null, null, "   ", null));
    assertNull(repository.findAll().get(0).getNotes());
  }

  @Test
  void closingAndReopeningKeepsTheRecord() throws SQLException {
    var id = repository.insert(mailbox("210", List.of("Kept"), null));

    repository.setClosedDate(id, LocalDate.of(2026, 9, 1));
    assertEquals(List.of(), repository.findOpen());
    var closed = repository.findAll().get(0);
    assertEquals(LocalDate.of(2026, 9, 1), closed.getClosedDate());
    assertEquals(List.of("Kept"), closed.getAlternateBusinessNames());

    repository.setClosedDate(id, null);
    assertFalse(repository.findOpen().get(0).isClosed());
  }

  @Test
  void closingRecordsWhatHappenedToTheKeyDepositAndReopeningForgetsIt() throws SQLException {
    var id = repository.insert(mailbox("210", List.of(), null));

    repository.setClosedDate(id, LocalDate.of(2026, 9, 1), DepositOutcome.KEPT);
    assertEquals(DepositOutcome.KEPT, repository.findAll().get(0).getKeyDepositOutcome());

    repository.setClosedDate(id, null, DepositOutcome.KEPT);
    assertNull(repository.findAll().get(0).getKeyDepositOutcome());
  }

  @Test
  void updateSavesTheKeyDepositOutcome() throws SQLException {
    var id = repository.insert(mailbox("210", List.of(), null));
    repository.setClosedDate(id, LocalDate.of(2026, 9, 1));
    var closed = repository.findAll().get(0);

    repository.update(new Mailbox(id, "Ada", "Lovelace", null, "210", null, "", null, null, null, null, null,
        closed.getClosedDate(), 1, 1000L, false, DepositOutcome.RETURNED));

    assertEquals(DepositOutcome.RETURNED, repository.findAll().get(0).getKeyDepositOutcome());
  }

  @Test
  void aClosedBoxDoesNotHoldItsNumber() throws SQLException {
    var id = repository.insert(mailbox("210", List.of(), null));
    repository.setClosedDate(id, LocalDate.of(2026, 9, 1));

    assertFalse(repository.isBoxNumberTaken("210", 0));
  }

  @Test
  void updateSavesNotesAndClosedDate() throws SQLException {
    var id = repository.insert(mailbox("210", List.of(), null));
    var saved = repository.findAll().get(0);

    repository.update(new Mailbox(id, saved.getFirstName(), saved.getLastName(), null, "210", null,
        saved.getPhone(), null, List.of(), null, null, "New note", LocalDate.of(2026, 1, 5)));

    var updated = repository.findAll().get(0);
    assertEquals("New note", updated.getNotes());
    assertEquals(LocalDate.of(2026, 1, 5), updated.getClosedDate());
  }

  @Test
  void findOpenIsInBoxNumberOrderToo() throws SQLException {
    for (var boxNumber : List.of("20", "3", "100")) {
      repository.insert(mailbox(boxNumber, List.of(), null));
    }
    var closed = repository.insert(mailbox("4", List.of(), null));
    repository.setClosedDate(closed, LocalDate.of(2026, 1, 1));

    assertEquals(List.of("3", "20", "100"), repository.findOpen().stream()
        .map(Mailbox::getBoxNumber).collect(Collectors.toList()));
  }

  @Test
  void insertReturnsTheNewId() throws SQLException {
    var first = repository.insert(mailbox("1", List.of(), null));
    var second = repository.insert(mailbox("2", List.of(), null));

    assertTrue(second > first);
    assertEquals(first, repository.findAll().get(0).getId());
  }

  @Test
  void findAllSortsBoxNumbersByValue() throws SQLException {
    for (var boxNumber : List.of("100", "2", "12A", "10", "12", "1")) {
      repository.insert(mailbox(boxNumber, List.of(), null));
    }

    assertEquals(List.of("1", "2", "10", "12", "12A", "100"), repository.findAll().stream()
        .map(Mailbox::getBoxNumber).collect(Collectors.toList()));
  }

  /** Makes a box with the given forwarding addresses. */
  private static Mailbox withForwarding(String boxNumber, List<ForwardingAddress> addresses) {
    return new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "(555) 123-4567", null,
        List.of(), null, addresses);
  }

  /** Makes a box with every field filled in, and the given alternate business names and end date. */
  private static Mailbox mailbox(String boxNumber, List<String> alternateNames, LocalDate endDate) {
    return new Mailbox(0, "Ada", "Lovelace", "Engines Ltd", boxNumber, "Box",
        "(555) 123-4567", "ada@example.com", alternateNames, endDate, null);
  }

}
