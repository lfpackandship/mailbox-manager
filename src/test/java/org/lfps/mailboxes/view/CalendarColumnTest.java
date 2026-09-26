package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalendarColumnTest {

  @ParameterizedTest
  @CsvSource({
      "SUNDAY, SUNDAY, 0", "MONDAY, SUNDAY, 1", "SATURDAY, SUNDAY, 6",
      "MONDAY, MONDAY, 0", "SATURDAY, MONDAY, 5", "SUNDAY, MONDAY, 6",
  })
  void placesEachDayInTheRightColumn(DayOfWeek day, DayOfWeek firstDay, int column) {
    assertEquals(column, CalendarView.column(day, firstDay));
  }

}
