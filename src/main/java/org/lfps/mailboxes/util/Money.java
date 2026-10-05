package org.lfps.mailboxes.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Reads and formats amounts of money, which are stored as whole cents so
 * totals add up exactly.
 */
public final class Money {

  /** The largest amount accepted, to catch typos. */
  public static final long MAX_CENTS = 100_000_00L;

  /**
   * Parses an amount as typed, such as {@code 60}, {@code 60.5},
   * {@code $1,200.00}.
   *
   * @param text the amount as typed
   * @return the amount in cents, or {@code null} if the text is blank
   * @throws IllegalArgumentException if the text isn't an amount of dollars
   *     and cents, with a message suitable for showing to the user
   */
  public static Long parse(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    var cleaned = text.trim().replaceFirst("^\\$\\s*", "").replace(",", "");
    if (!cleaned.matches("\\d+(\\.\\d{0,2})?|\\.\\d{1,2}")) {
      throw new IllegalArgumentException("Enter the amount paid in dollars and cents, like 60 or 60.00.");
    }
    var cents = new BigDecimal(cleaned).movePointRight(2).longValueExact();
    if (cents > MAX_CENTS) {
      throw new IllegalArgumentException("The amount paid is too large. Check it and try again.");
    }
    return cents;
  }

  /**
   * Formats an amount for display, such as {@code $1,200.00}.
   *
   * @param cents the amount in cents
   * @return the formatted amount
   */
  public static String format(long cents) {
    return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2));
  }

  /** Not used: amounts are handled with static methods. */
  private Money() {
  }

}
