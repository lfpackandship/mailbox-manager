package org.lfps.mailboxes.util;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Reads and writes the list of rental lengths, in months, offered as
 * quick-set buttons on the Add and Edit Box forms (for example
 * {@code "1, 3, 6, 12"}).
 */
public final class RentalLengths {

  /** The longest rental length allowed, in months. */
  public static final int MAX_MONTHS = 120;

  /** The most rental lengths allowed, so the buttons fit on one row. */
  public static final int MAX_COUNT = 6;

  /**
   * Parses a list of rental lengths separated by commas or spaces, sorting
   * them and dropping duplicates.
   *
   * @param text the lengths as typed, such as {@code "12, 1, 6"}
   * @return the lengths in months, shortest first
   * @throws IllegalArgumentException if the text isn't a list of 1 to
   *     {@value #MAX_COUNT} whole numbers from 1 to {@value #MAX_MONTHS},
   *     with a message suitable for showing to the user
   */
  public static List<Integer> parse(String text) {
    var months = new TreeSet<Integer>();
    for (var part : (text == null ? "" : text).trim().split("[,\\s]+")) {
      if (part.isEmpty()) {
        continue;
      }
      int value;
      try {
        value = Integer.parseInt(part);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Rental lengths must be whole numbers of months, like 1, 3, 6, 12.");
      }
      if (value < 1 || value > MAX_MONTHS) {
        throw new IllegalArgumentException("Rental lengths must be from 1 to " + MAX_MONTHS + " months.");
      }
      months.add(value);
    }
    if (months.isEmpty()) {
      throw new IllegalArgumentException("Enter at least one rental length.");
    }
    if (months.size() > MAX_COUNT) {
      throw new IllegalArgumentException("Enter at most " + MAX_COUNT + " rental lengths.");
    }
    return List.copyOf(months);
  }

  /**
   * Formats rental lengths for display and storage.
   *
   * @param months the lengths in months
   * @return the lengths separated by commas, such as {@code "1, 3, 6, 12"}
   */
  public static String format(List<Integer> months) {
    return months.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  /**
   * Returns the button label for a rental length.
   *
   * @param months the length in months
   * @return {@code "1 Month"} or, for example, {@code "6 Months"}
   */
  public static String label(int months) {
    return months == 1 ? "1 Month" : months + " Months";
  }

  /** Not used: rental lengths are handled with static methods. */
  private RentalLengths() {
  }

}
