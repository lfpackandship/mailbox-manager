package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for reading the whole-number settings, such as how many backups to
 * keep.
 */
class SettingsParseTest {

  @ParameterizedTest
  @CsvSource({ "1, 1", "30, 30", "365, 365", "' 14 ', 14", "007, 7" })
  void acceptsWholeNumbersInRange(String text, int expected) {
    assertEquals(expected, SettingsView.parseInRange(text, 1, 365));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "   ", "0", "366", "-5", "abc", "14 days", "1.5", "99999999999" })
  void rejectsBlankNonNumericAndOutOfRange(String text) {
    assertNull(SettingsView.parseInRange(text, 1, 365));
  }

  @Test
  void rejectsNull() {
    assertNull(SettingsView.parseInRange(null, 1, 365));
  }

}
