package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.Mailbox;

class MailboxRepositoryTest {

  private final MailboxRepository repository = new MailboxRepository();

  @BeforeEach
  void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM business_names");
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
        "(555) 999-0000", null, List.of("New A", "New B"), LocalDate.of(2026, 12, 1)));

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

  private static Mailbox mailbox(String boxNumber, List<String> alternateNames, LocalDate endDate) {
    return new Mailbox(0, "Ada", "Lovelace", "Engines Ltd", boxNumber, "Box",
        "(555) 123-4567", "ada@example.com", alternateNames, endDate);
  }

}
