package org.lfps.mailboxes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

  @ParameterizedTest
  @CsvSource({
      "60, 6000",
      "60.5, 6050",
      "60.05, 6005",
      "'$1,200.00', 120000",
      "' $ 45 ', 4500",
      ".99, 99",
      "0, 0",
  })
  void parsesDollarsAndCents(String text, long cents) {
    assertEquals(cents, Money.parse(text));
  }

  @Test
  void blankMeansNoAmount() {
    assertNull(Money.parse("  "));
    assertNull(Money.parse(null));
  }

  @ParameterizedTest
  @ValueSource(strings = { "abc", "-5", "1.234", "12.3.4", "$" })
  void rejectsThingsThatArentAmounts(String text) {
    assertThrows(IllegalArgumentException.class, () -> Money.parse(text));
  }

  @Test
  void rejectsHugeAmounts() {
    assertThrows(IllegalArgumentException.class, () -> Money.parse("100000.01"));
  }

  @Test
  void acceptsTheLargestAmount() {
    assertEquals(Money.MAX_CENTS, Money.parse("100000"));
  }

  @Test
  void formatsAsDollars() {
    assertEquals("$1,200.00", Money.format(120000));
    assertEquals("$0.99", Money.format(99));
  }

}
