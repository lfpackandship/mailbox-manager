package org.lfps.mailboxes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Tests for a hold on a box holder's mail: which days it covers, how it's
 * described, and how it ends by itself.
 */
class MailHoldTest {

  private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

  private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

  private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);

  @Test
  void needsBothDaysInOrder() {
    assertThrows(IllegalArgumentException.class, () -> new MailHold(null, NOV_3, null));
    assertThrows(IllegalArgumentException.class, () -> new MailHold(OCT_20, null, null));
    assertThrows(IllegalArgumentException.class, () -> new MailHold(NOV_3, OCT_20, null));
    assertEquals(OCT_20, new MailHold(OCT_20, OCT_20, null).getUntil());
  }

  @Test
  void coversItsFirstAndLastDaysAndEndsAfterTheLast() {
    var hold = new MailHold(OCT_20, NOV_3, null);

    assertFalse(hold.isOn(OCT_20.minusDays(1)));
    assertTrue(hold.isOn(OCT_20));
    assertTrue(hold.isOn(NOV_3));
    assertFalse(hold.isOn(NOV_3.plusDays(1)));
    assertFalse(hold.isOverBy(NOV_3));
    assertTrue(hold.isOverBy(NOV_3.plusDays(1)));
  }

  @Test
  void describesAHoldStartedOrToComeAndNothingOnceItsOver() {
    var hold = new MailHold(OCT_20, NOV_3, "  forward it all on the 20th ");

    assertEquals("Will hold mail Oct 20 – Nov 3: forward it all on the 20th", hold.describe(OCT_15));
    assertEquals("Holding mail Oct 20 – Nov 3: forward it all on the 20th", hold.describe(OCT_20));
    assertEquals("", hold.describe(NOV_3.plusDays(1)));
  }

  @Test
  void showsTheYearOnlyForAnotherYearAndOneDayOnce() {
    assertEquals("Holding mail Dec 28 – Jan 3, 2027",
        new MailHold(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3), null).describe(LocalDate.of(2026, 12, 30)));
    assertEquals("Will hold mail Oct 20", new MailHold(OCT_20, OCT_20, "").describe(OCT_15));
  }

  @Test
  void aBlankNoteIsNone() {
    assertNull(new MailHold(OCT_20, NOV_3, "   ").getNote());
  }

  @Test
  void aBoxIgnoresAHoldThatsOver() {
    var box = new Mailbox(0, "Ada", "Lovelace", null, "12", null, "", null, null, null, null)
        .withMailHold(new MailHold(OCT_20, NOV_3, null));

    assertEquals(new MailHold(OCT_20, NOV_3, null), box.getMailHold(OCT_15));
    assertFalse(box.isHoldingMailOn(OCT_15));
    assertTrue(box.isHoldingMailOn(OCT_20));
    assertNull(box.getMailHold(NOV_3.plusDays(1)));
    assertEquals(new MailHold(OCT_20, NOV_3, null), box.withEndDate(OCT_15).getMailHold(OCT_15));
  }

}
