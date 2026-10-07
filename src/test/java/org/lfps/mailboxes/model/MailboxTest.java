package org.lfps.mailboxes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for a mailbox's constructors, copies with changes, and names.
 */
class MailboxTest {

  private static final ForwardingAddress NAPLES =
      new ForwardingAddress("88 Palm Way", null, "Naples", "FL", "34102", "winter");

  private final Mailbox box = new Mailbox(7, "Ada", "Lovelace", "Engines Ltd", "12A", "Corner", "(555) 123-4567",
      "ada@example.com", List.of("AL Consulting"), LocalDate.of(2026, 6, 1), List.of(NAPLES), "ID on file", null);

  @Test
  void theOlderConstructorMakesAnOpenBoxWithNoNotes() {
    var old = new Mailbox(1, "Ada", "Lovelace", null, "1", null, "5551234567", null, null, null, null);
    assertNull(old.getNotes());
    assertFalse(old.isClosed());
    assertEquals(List.of(), old.getAlternateBusinessNames());
    assertEquals(List.of(), old.getForwardingAddresses());
  }

  @Test
  void withEndDateChangesOnlyTheEndDate() {
    var renewed = box.withEndDate(LocalDate.of(2027, 6, 1));
    assertEquals(LocalDate.of(2027, 6, 1), renewed.getEndDate());
    assertSameExceptEndAndClosed(box, renewed);
    assertEquals(LocalDate.of(2026, 6, 1), box.getEndDate());
  }

  @Test
  void withClosedDateClosesAndReopens() {
    var closed = box.withClosedDate(LocalDate.of(2026, 9, 1));
    assertTrue(closed.isClosed());
    assertEquals(LocalDate.of(2026, 9, 1), closed.getClosedDate());
    assertEquals(box.getEndDate(), closed.getEndDate());
    assertSameExceptEndAndClosed(box, closed);

    assertFalse(closed.withClosedDate(null).isClosed());
  }

  @Test
  void reopeningForgetsWhatHappenedToTheKeyDeposit() {
    var closed = new Mailbox(1, "Ada", "Lovelace", null, "12", null, "", null, null, null, null, null,
        LocalDate.of(2026, 9, 1), 1, 1000L, false, DepositOutcome.KEPT);

    assertEquals(DepositOutcome.KEPT, closed.withEndDate(LocalDate.of(2027, 1, 1)).getKeyDepositOutcome());
    assertNull(closed.withClosedDate(null).getKeyDepositOutcome());
  }

  @Test
  void fullNameJoinsFirstAndLast() {
    assertEquals("Ada Lovelace", box.getFullName());
  }

  @Test
  void fullNameLeavesOutABlankName() {
    assertEquals("Lovelace", named(" ", "Lovelace", null).getFullName());
    assertEquals("Ada", named("Ada ", "", null).getFullName());
    assertEquals("", named("", "", null).getFullName());
  }

  @Test
  void holderNameFallsBackToTheBusinessTitle() {
    assertEquals("Ada Lovelace", box.getHolderName());
    assertEquals("Engines Ltd", named("", "", " Engines Ltd ").getHolderName());
    assertEquals("", named("", "", null).getHolderName());
  }

  @Test
  void listsCantBeChanged() {
    assertThrows(UnsupportedOperationException.class,
        () -> box.getAlternateBusinessNames().add("More"));
  }

  /** Makes a box with the given names. */
  private static Mailbox named(String firstName, String lastName, String businessTitle) {
    return new Mailbox(1, firstName, lastName, businessTitle, "1", null, "5551234567", null, null, null, null);
  }

  /** Checks two boxes are the same apart from their end and closing dates. */
  private static void assertSameExceptEndAndClosed(Mailbox expected, Mailbox actual) {
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getFullName(), actual.getFullName());
    assertEquals(expected.getBusinessTitle(), actual.getBusinessTitle());
    assertEquals(expected.getBoxNumber(), actual.getBoxNumber());
    assertEquals(expected.getBoxName(), actual.getBoxName());
    assertEquals(expected.getPhone(), actual.getPhone());
    assertEquals(expected.getEmail(), actual.getEmail());
    assertEquals(expected.getAlternateBusinessNames(), actual.getAlternateBusinessNames());
    assertEquals(expected.getForwardingAddresses(), actual.getForwardingAddresses());
    assertEquals(expected.getNotes(), actual.getNotes());
  }

}
