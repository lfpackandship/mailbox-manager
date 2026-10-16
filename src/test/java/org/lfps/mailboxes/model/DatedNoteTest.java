package org.lfps.mailboxes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for a box's dated notes: what a note needs, how it reads, and the
 * order a box keeps them in.
 */
class DatedNoteTest {

  private static final LocalDate SEP_2 = LocalDate.of(2026, 9, 2);

  private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

  @Test
  void needsADayAndSomeText() {
    assertEquals("Choose the day for the dated note.",
        assertThrows(IllegalArgumentException.class, () -> new DatedNote(null, "Called")).getMessage());
    assertEquals("Type the dated note.",
        assertThrows(IllegalArgumentException.class, () -> new DatedNote(OCT_15, "  ")).getMessage());
  }

  @Test
  void readsAsItsDayAndText() {
    assertEquals("Oct 15, 2026 – Called about late payment", new DatedNote(OCT_15, "  Called about late payment ")
        .toString());
  }

  @Test
  void aBoxKeepsItsNotesNewestFirstThroughCopies() {
    var older = new DatedNote(SEP_2, "Picked up spare key");
    var newer = new DatedNote(OCT_15, "Called about late payment");
    var box = new Mailbox(0, "Ada", "Lovelace", null, "12", null, "", null, null, null, null)
        .withDatedNotes(List.of(older, newer));

    assertEquals(List.of(newer, older), box.getDatedNotes());
    assertEquals(List.of(newer, older), box.withEndDate(OCT_15).withClosedDate(OCT_15).getDatedNotes());
    assertEquals(List.of(), box.withDatedNotes(null).getDatedNotes());
  }

}
