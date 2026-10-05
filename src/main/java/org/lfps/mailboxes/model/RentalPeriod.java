package org.lfps.mailboxes.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * An immutable entry in a box's rental history: a stretch of time the box was
 * rented for, recorded when the box is first rented or renewed, with what was
 * paid for it.
 */
public final class RentalPeriod {

  /**
   * Formats the dates in {@link #describePeriod()}, such as Oct 5, 2026.
   */
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  /**
   * The database id, or {@code 0} if not yet saved.
   */
  private final int id;

  /** The id of the box the rental is for. */
  private final int mailboxId;

  /** The day the rental or renewal was recorded. */
  private final LocalDate recordedOn;

  /** The first day of the rental. */
  private final LocalDate startDate;

  /** The last day of the rental. */
  private final LocalDate endDate;

  /**
   * The amount paid in cents, or {@code null} if not recorded.
   */
  private final Long amountCents;

  /**
   * How it was paid, such as Cash, or {@code null} if not recorded.
   */
  private final String paymentMethod;

  /**
   * A note, such as a check number, or {@code null} if none.
   */
  private final String note;

  /**
   * Creates a rental history entry. Blank text is stored as {@code null}.
   *
   * @param id the database id, or {@code 0} for a not-yet-persisted entry
   * @param mailboxId the id of the box it belongs to
   * @param recordedOn the day it was recorded, usually the day it was paid
   * @param startDate the first day of the period
   * @param endDate the last day of the period, which becomes the box's end date
   * @param amountCents the amount paid, in cents, or {@code null} if not recorded
   * @param paymentMethod how it was paid, such as "Cash", or blank/null
   * @param note a note about the payment, or blank/null
   */
  public RentalPeriod(int id, int mailboxId, LocalDate recordedOn, LocalDate startDate, LocalDate endDate,
      Long amountCents, String paymentMethod, String note) {
    this.id = id;
    this.mailboxId = mailboxId;
    this.recordedOn = recordedOn;
    this.startDate = startDate;
    this.endDate = endDate;
    this.amountCents = amountCents;
    this.paymentMethod = trimToNull(paymentMethod);
    this.note = trimToNull(note);
  }

  /**
   * Returns the database id.
   *
   * @return the database id, or {@code 0} if not yet persisted
   */
  public int getId() {
    return id;
  }

  /**
   * Returns the id of the box this entry belongs to.
   *
   * @return the mailbox id
   */
  public int getMailboxId() {
    return mailboxId;
  }

  /**
   * Returns the day the entry was recorded.
   *
   * @return the day recorded
   */
  public LocalDate getRecordedOn() {
    return recordedOn;
  }

  /**
   * Returns the first day of the period.
   *
   * @return the start date
   */
  public LocalDate getStartDate() {
    return startDate;
  }

  /**
   * Returns the last day of the period.
   *
   * @return the end date
   */
  public LocalDate getEndDate() {
    return endDate;
  }

  /**
   * Returns the amount paid.
   *
   * @return the amount in cents, or {@code null} if none was recorded
   */
  public Long getAmountCents() {
    return amountCents;
  }

  /**
   * Returns how the period was paid for.
   *
   * @return the payment method, or {@code null} if none was recorded
   */
  public String getPaymentMethod() {
    return paymentMethod;
  }

  /**
   * Returns the note about the payment.
   *
   * @return the note, or {@code null} if none
   */
  public String getNote() {
    return note;
  }

  /**
   * Returns the period as a date range, such as "Sep 26, 2026 – Mar 26, 2027".
   *
   * @return the period for display
   */
  public String describePeriod() {
    return startDate.format(DATE) + " – " + endDate.format(DATE);
  }

  /**
   * Trims text, treating blank text as none.
   *
   * @param value the text, or {@code null}
   * @return the trimmed text, or {@code null} if it's blank
   */
  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

}
