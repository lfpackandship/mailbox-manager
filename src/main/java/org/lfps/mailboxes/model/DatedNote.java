package org.lfps.mailboxes.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * An immutable entry in a box's dated notes: something that happened with
 * the box, such as "Called about late payment", and the day it happened.
 * Unlike the box's Notes, which are rewritten as things change, dated notes
 * build up a history. They're added on Edit Box, dated today unless an
 * earlier day is chosen.
 */
public final class DatedNote {

  /** Newest first, as dated notes are listed. */
  public static final Comparator<DatedNote> NEWEST_FIRST = Comparator.comparing(DatedNote::getDate).reversed();

  /** Formats the day in a description, such as "Oct 15, 2026". */
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

  /** The day it happened. */
  private final LocalDate date;

  /** What happened, without surrounding spaces. */
  private final String text;

  /**
   * Creates a dated note.
   *
   * @param date the day it happened
   * @param text what happened
   * @throws IllegalArgumentException if the day is missing or the text is
   *     blank, with a message for the form
   */
  public DatedNote(LocalDate date, String text) {
    if (date == null) {
      throw new IllegalArgumentException("Choose the day for the dated note.");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Type the dated note.");
    }
    this.date = date;
    this.text = text.strip();
  }

  /**
   * Returns the day it happened.
   *
   * @return the day
   */
  public LocalDate getDate() {
    return date;
  }

  /**
   * Returns what happened.
   *
   * @return the note's text
   */
  public String getText() {
    return text;
  }

  /**
   * Describes the note as lists and the box's details show it.
   *
   * @return such as "Oct 15, 2026 – Called about late payment"
   */
  @Override
  public String toString() {
    return date.format(DAY) + " – " + text;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof DatedNote)) {
      return false;
    }
    var note = (DatedNote) other;
    return date.equals(note.date) && text.equals(note.text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(date, text);
  }

}
