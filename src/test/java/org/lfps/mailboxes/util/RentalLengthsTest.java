package org.lfps.mailboxes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for reading, formatting, and labelling the rental lengths set in
 * Settings.
 */
class RentalLengthsTest {

  @Test
  void parsesCommaOrSpaceSeparatedLengthsSortedWithoutDuplicates() {
    assertEquals(List.of(1, 3, 6, 12), RentalLengths.parse("1,3,6,12"));
    assertEquals(List.of(1, 3, 6, 12), RentalLengths.parse(" 12, 6  3 1, 6 "));
    assertEquals(List.of(120), RentalLengths.parse("120"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "''                  | Enter at least one rental length.",
      "' , '               | Enter at least one rental length.",
      "1, 3, six           | Rental lengths must be whole numbers of months, like 1, 3, 6, 12.",
      "1.5                 | Rental lengths must be whole numbers of months, like 1, 3, 6, 12.",
      "0, 3                | Rental lengths must be from 1 to 120 months.",
      "121                 | Rental lengths must be from 1 to 120 months.",
      "1, 2, 3, 4, 5, 6, 7 | Enter at most 6 rental lengths.",
  })
  void rejectsInvalidListsWithAHelpfulMessage(String text, String message) {
    var error = assertThrows(IllegalArgumentException.class, () -> RentalLengths.parse(text));
    assertEquals(message, error.getMessage());
  }

  @Test
  void rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> RentalLengths.parse(null));
  }

  @Test
  void formatsForDisplay() {
    assertEquals("1, 3, 6, 12", RentalLengths.format(List.of(1, 3, 6, 12)));
  }

  @ParameterizedTest
  @ValueSource(ints = { 2, 6, 24 })
  void labelsPluralMonths(int months) {
    assertEquals(months + " Months", RentalLengths.label(months));
  }

  @Test
  void labelsOneMonth() {
    assertEquals("1 Month", RentalLengths.label(1));
  }

}
