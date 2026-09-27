package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.RentalPeriod;

class BoxDetailsHistoryTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 1);

  private static final LocalDate END = LocalDate.of(2026, 7, 1);

  private final String period = new RentalPeriod(0, 1, START, START, END, null, null, null).describePeriod();

  @Test
  void noHistoryShowsADash() {
    assertEquals(BoxDetailsView.NONE, BoxDetailsView.describeHistory(List.of()));
  }

  @Test
  void showsWhatWasRecordedForEachEntry() {
    var history = BoxDetailsView.describeHistory(List.of(
        entry(null, null, null),
        entry(6000L, null, null),
        entry(null, "Cash", null),
        entry(6000L, "Venmo", "paid late")));

    assertEquals(String.join("\n",
        period,
        period + ": $60.00",
        period + ": Cash",
        period + ": $60.00, Venmo (paid late)"), history);
  }

  private static RentalPeriod entry(Long cents, String method, String note) {
    return new RentalPeriod(0, 1, START, START, END, cents, method, note);
  }

}
