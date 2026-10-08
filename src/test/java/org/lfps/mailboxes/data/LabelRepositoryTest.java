package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.PrintedLabel;

/**
 * Tests for handing out forwarding label numbers and keeping the record of
 * labels printed.
 */
class LabelRepositoryTest {

  private static final LocalDateTime OCT_7_NOON = LocalDateTime.of(2026, 10, 7, 12, 0, 0);

  private final LabelRepository labels = new LabelRepository();

  @BeforeEach
  void emptyDatabase() throws SQLException, IOException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM labels");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    Files.deleteIfExists(Database.dataDir().resolve(LabelRepository.LAST_NUMBER_FILE));
  }

  @Test
  void aNumberIsTheDayAndThatDaysCount() {
    var day = LocalDate.of(2026, 10, 7);
    assertEquals("261007-01", LabelRepository.nextNumber(day, List.of(), null));
    assertEquals("261007-04", LabelRepository.nextNumber(day, List.of("261007-03", "261007-01"), null));
    assertEquals("261007-06", LabelRepository.nextNumber(day, List.of("261007-03"), "261007-05"));
    assertEquals("261007-04", LabelRepository.nextNumber(day, List.of("261007-03"), "261006-09"));
    assertEquals("261007-100", LabelRepository.nextNumber(day, List.of("261007-99"), null));
  }

  @Test
  void recordsLabelsAndListsThemNewestFirst() throws SQLException {
    var id = box("12");
    labels.record(id, List.of("Ada Lovelace", "1 Elm St", "Town, IL 60000"), OCT_7_NOON);
    labels.record(id, List.of("Ada Lovelace", "2 Oak St", "Town, IL 60000"), OCT_7_NOON.plusMinutes(5));
    labels.record(id, List.of("Ada Lovelace", "3 Ash St", "Town, IL 60000"), OCT_7_NOON.plusDays(1));

    var all = labels.findAll();

    assertEquals(List.of("261008-01", "261007-02", "261007-01"),
        all.stream().map(PrintedLabel::getNumber).collect(Collectors.toList()));
    assertEquals("Ada Lovelace\n2 Oak St\nTown, IL 60000", all.get(1).getAddress());
    assertEquals(OCT_7_NOON.plusMinutes(5), all.get(1).getPrintedAt());
    assertEquals(id, all.get(1).getMailboxId());
  }

  @Test
  void theNextNumberShownIsTheOneHandedOut() throws SQLException {
    var id = box("12");
    labels.record(id, List.of("Ada"), OCT_7_NOON);

    assertEquals("261007-02", labels.peekNextNumber(OCT_7_NOON.toLocalDate()));
    assertEquals("261007-02", labels.record(id, List.of("Ada"), OCT_7_NOON).getNumber());
  }

  @Test
  void numbersDontRepeatAfterRestoringAnOlderBackup() throws SQLException {
    var id = box("12");
    labels.record(id, List.of("Ada"), OCT_7_NOON);
    labels.record(id, List.of("Ada"), OCT_7_NOON);
    // As if a backup from before those two labels were restored.
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM labels");
    }

    assertEquals("261007-03", labels.record(id, List.of("Ada"), OCT_7_NOON).getNumber());
  }

  @Test
  void aDeletedRecordsNumberIsntHandedOutAgain() throws SQLException {
    var id = box("12");
    var cancelled = labels.record(id, List.of("Ada"), OCT_7_NOON);

    labels.delete(cancelled.getNumber());

    assertEquals(List.of(), labels.findAll());
    assertEquals("261007-02", labels.record(id, List.of("Ada"), OCT_7_NOON).getNumber());
  }

  @Test
  void deletingABoxDeletesItsLabels() throws SQLException {
    var gone = box("12");
    var kept = box("13");
    labels.record(gone, List.of("Ada"), OCT_7_NOON);
    labels.record(kept, List.of("Grace"), OCT_7_NOON);

    new MailboxRepository().delete(gone);

    assertEquals(List.of(kept), labels.findAll().stream().map(PrintedLabel::getMailboxId)
        .collect(Collectors.toList()));
  }

  /** Adds a box with the given number and returns its id. */
  private static int box(String boxNumber) throws SQLException {
    return new MailboxRepository().insert(new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "", null,
        null, null, null));
  }

}
