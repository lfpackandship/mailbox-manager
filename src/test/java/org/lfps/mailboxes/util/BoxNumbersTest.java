package org.lfps.mailboxes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for sorting box numbers, matching them, and reading ranges of them.
 */
class BoxNumbersTest {

  @Test
  void sortsNumbersByValueWithLettersAfterTheirNumber() {
    var numbers = new ArrayList<>(List.of("100", "12b", "2", "A1", "12A", "12", "11", "010", "1"));
    numbers.sort(BoxNumbers.ORDER);
    assertEquals(List.of("1", "2", "010", "11", "12", "12A", "12b", "100", "A1"), numbers);
  }

  @Test
  void ordersVeryLongNumbersWithoutOverflowing() {
    var numbers = new ArrayList<>(List.of("99999999999999999999", "100000000000000000000", "5"));
    numbers.sort(BoxNumbers.ORDER);
    assertEquals(List.of("5", "99999999999999999999", "100000000000000000000"), numbers);
  }

  @Test
  void numbersDifferingOnlyInCaseOrLeadingZerosSortTogether() {
    assertEquals(0, BoxNumbers.ORDER.compare("12a", "12A"));
    assertEquals(0, BoxNumbers.ORDER.compare("007", "7"));
    assertEquals(0, BoxNumbers.ORDER.compare(" 5", "5"));
  }

  @Test
  void numbersWithTextOnBothSidesCompareEachPart() {
    var numbers = new ArrayList<>(List.of("B-10", "A-2", "B-9", "A-10"));
    numbers.sort(BoxNumbers.ORDER);
    assertEquals(List.of("A-2", "A-10", "B-9", "B-10"), numbers);
  }

  @Test
  void aNumberWithADashThatIsntARangeIsKept() {
    assertEquals(List.of("A-12", "PO-7"), BoxNumbers.parseList("A-12, PO-7"));
  }

  @Test
  void rangesOfOneBoxAndAtTheLimitAreAllowed() {
    assertEquals(List.of("5"), BoxNumbers.parseList("5-5"));
    assertEquals(BoxNumbers.MAX_RANGE, BoxNumbers.parseList("1-" + BoxNumbers.MAX_RANGE).size());
  }

  @Test
  void keyIgnoresSurroundingSpacesAndCase() {
    assertEquals(BoxNumbers.key(" 12a "), BoxNumbers.key("12A"));
    assertEquals("", BoxNumbers.key(null));
  }

  @Test
  void parsesSingleNumbersAndRanges() {
    assertEquals(List.of("1", "2", "3", "12A", "20"), BoxNumbers.parseList(" 1-3, 12A,, 20 , 2"));
  }

  @Test
  void rangesKeepLeadingZeros() {
    assertEquals(List.of("008", "009", "010"), BoxNumbers.parseList("008 - 010"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "''       | Enter at least one box number, like 12 or 1-200.",
      "10-1     | The range 10-1 goes backwards. Put the smaller number first.",
      "1-10000  | The range 1-10000 is more than 2000 boxes. Check the numbers, or add it in smaller ranges.",
      "1-20x    | \"1-20x\" isn't a range. Ranges look like 1-200.",
  })
  void rejectsBadListsWithAHelpfulMessage(String text, String message) {
    var error = assertThrows(IllegalArgumentException.class, () -> BoxNumbers.parseList(text));
    assertEquals(message, error.getMessage());
  }

}
