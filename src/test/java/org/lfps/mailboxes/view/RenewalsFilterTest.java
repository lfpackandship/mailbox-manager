package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for which boxes Renewals lists as past due and due soon, and how it
 * says when each is due.
 */
class RenewalsFilterTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

  private static final List<Mailbox> MAILBOXES = List.of(
      box("no-date", null),
      box("in-window-end", TODAY.plusDays(14)),
      box("yesterday", TODAY.minusDays(1)),
      box("after-window", TODAY.plusDays(15)),
      box("today", TODAY),
      box("last-month", TODAY.minusDays(30)),
      box("tomorrow", TODAY.plusDays(1)));

  @Test
  void pastDueIncludesOnlyEarlierDatesEarliestFirst() {
    assertEquals(List.of("last-month", "yesterday"),
        boxNumbers(RenewalsView.pastDue(MAILBOXES, TODAY)));
  }

  @Test
  void upcomingIncludesTodayThroughLastDayOfWindowEarliestFirst() {
    assertEquals(List.of("today", "tomorrow", "in-window-end"),
        boxNumbers(RenewalsView.upcoming(MAILBOXES, TODAY, 14)));
  }

  @Test
  void widerWindowPicksUpLaterDates() {
    assertEquals(List.of("today", "tomorrow", "in-window-end", "after-window"),
        boxNumbers(RenewalsView.upcoming(MAILBOXES, TODAY, 15)));
  }

  @Test
  void oneDayWindowCoversTodayAndTomorrow() {
    assertEquals(List.of("today", "tomorrow"),
        boxNumbers(RenewalsView.upcoming(MAILBOXES, TODAY, 1)));
  }

  @Test
  void describesHowFarAwayTheEndDateIs() {
    assertEquals("2 days overdue", RenewalsView.dueStatus(TODAY.minusDays(2), TODAY));
    assertEquals("1 day overdue", RenewalsView.dueStatus(TODAY.minusDays(1), TODAY));
    assertEquals("Due today", RenewalsView.dueStatus(TODAY, TODAY));
    assertEquals("In 1 day", RenewalsView.dueStatus(TODAY.plusDays(1), TODAY));
    assertEquals("In 30 days", RenewalsView.dueStatus(TODAY.plusDays(30), TODAY));
  }

  /** Makes a box with the given end date. */
  private static Mailbox box(String boxNumber, LocalDate endDate) {
    return new Mailbox(0, "First", "Last", null, boxNumber, null, "(555) 000-0000", null,
        null, endDate, null);
  }

  /** Returns the boxes' numbers, in the same order. */
  private static List<String> boxNumbers(List<Mailbox> mailboxes) {
    return mailboxes.stream().map(Mailbox::getBoxNumber).collect(Collectors.toList());
  }

}
