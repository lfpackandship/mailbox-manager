package org.lfps.mailboxes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import org.junit.jupiter.api.Test;

/**
 * Tests for a rental history entry's fields and its description.
 */
class RentalPeriodTest {

  @Test
  void blankTextIsStoredAsNoneAndTheRestIsTrimmed() {
    var period = new RentalPeriod(0, 1, LocalDate.now(), LocalDate.now(), LocalDate.now(), null, "  ", " note ");
    assertNull(period.getPaymentMethod());
    assertEquals("note", period.getNote());
  }

  @Test
  void describesThePeriodAsADateRange() {
    var start = LocalDate.of(2026, 9, 26);
    var end = LocalDate.of(2027, 3, 26);
    var medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    assertEquals(start.format(medium) + " – " + end.format(medium),
        new RentalPeriod(0, 1, start, start, end, null, null, null).describePeriod());
  }

}
