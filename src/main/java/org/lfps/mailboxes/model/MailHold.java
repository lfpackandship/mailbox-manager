package org.lfps.mailboxes.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * An immutable hold on a box holder's mail, such as while they're on
 * vacation: the shop keeps their mail from one day through another instead
 * of putting it in the box or forwarding it. It's set in the Mail Hold
 * section of Add New Box or Edit Box and ends by itself after its last day.
 */
public final class MailHold {

  /** Formats a day in a description, such as "Oct 20". */
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

  /** Formats a day in another year than today's, such as "Jan 3, 2027". */
  private static final DateTimeFormatter DAY_AND_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

  /** The first day mail is held. */
  private final LocalDate from;

  /** The last day mail is held. */
  private final LocalDate until;

  /**
   * What to do with the held mail, such as "forward it all on the 20th", or
   * {@code null} if nothing was noted.
   */
  private final String note;

  /**
   * Creates a hold. A blank note is stored as {@code null}.
   *
   * @param from the first day mail is held
   * @param until the last day mail is held, the same as or after
   *     {@code from}
   * @param note what to do with the held mail, or blank/null if nothing
   * @throws IllegalArgumentException if a day is missing or {@code until}
   *     is before {@code from}
   */
  public MailHold(LocalDate from, LocalDate until, String note) {
    if (from == null || until == null) {
      throw new IllegalArgumentException("A mail hold needs both a From and an Until date.");
    }
    if (until.isBefore(from)) {
      throw new IllegalArgumentException("A mail hold's Until date can't be before its From date.");
    }
    this.from = from;
    this.until = until;
    this.note = note == null || note.isBlank() ? null : note.strip();
  }

  /**
   * Returns the first day mail is held.
   *
   * @return the day
   */
  public LocalDate getFrom() {
    return from;
  }

  /**
   * Returns the last day mail is held.
   *
   * @return the day
   */
  public LocalDate getUntil() {
    return until;
  }

  /**
   * Returns what to do with the held mail.
   *
   * @return the note, or {@code null} if nothing was noted
   */
  public String getNote() {
    return note;
  }

  /**
   * Returns whether mail is being held on a day.
   *
   * @param day the day, normally today
   * @return {@code true} if the day is from {@link #getFrom()} through
   *     {@link #getUntil()}
   */
  public boolean isOn(LocalDate day) {
    return !day.isBefore(from) && !day.isAfter(until);
  }

  /**
   * Returns whether the hold has ended by a day, after which it's treated as
   * if there were none.
   *
   * @param day the day, normally today
   * @return {@code true} if the day is after {@link #getUntil()}
   */
  public boolean isOverBy(LocalDate day) {
    return day.isAfter(until);
  }

  /**
   * Describes the hold for a box's details, such as "Holding mail Oct 20 –
   * Nov 3: forward it all on the 20th", or "Will hold mail …" for one that
   * hasn't started.
   *
   * @param today today, which decides the wording and whether years are shown
   * @return the description, or an empty string if the hold is over
   */
  public String describe(LocalDate today) {
    if (isOverBy(today)) {
      return "";
    }
    return (from.isAfter(today) ? "Will hold mail " : "Holding mail ") + describeDays(today)
        + (note == null ? "" : ": " + note);
  }

  /**
   * Describes the days held, such as "Oct 20 – Nov 3", with the year on a
   * day in another year than today's.
   *
   * @param today today
   * @return the days
   */
  public String describeDays(LocalDate today) {
    return day(from, today) + (from.equals(until) ? "" : " – " + day(until, today));
  }

  /**
   * Formats a day, with its year only if it isn't this year.
   *
   * @param day the day
   * @param today today
   * @return such as "Oct 20" or "Jan 3, 2027"
   */
  private static String day(LocalDate day, LocalDate today) {
    return day.format(day.getYear() == today.getYear() ? DAY : DAY_AND_YEAR);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof MailHold)) {
      return false;
    }
    var hold = (MailHold) other;
    return from.equals(hold.from) && until.equals(hold.until) && Objects.equals(note, hold.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, until, note);
  }

}
