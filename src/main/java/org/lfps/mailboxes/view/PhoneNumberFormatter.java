package org.lfps.mailboxes.view;

import java.util.function.UnaryOperator;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;

/**
 * Formats phone number input as the user types, rendering typed digits in
 * standard US phone format so only digits need to be entered.
 */
public final class PhoneNumberFormatter {

  /**
   * Creates a {@link TextFormatter} that reformats a text field's content
   * as a US phone number on every edit, ignoring non-digit input.
   *
   * @return a new text formatter for a phone number field
   */
  public static TextFormatter<String> create() {
    UnaryOperator<Change> filter = change -> {
      if (change.isContentChange()) {
        var newText = change.getControlNewText();
        var caretPosition = Math.min(change.getCaretPosition(), newText.length());
        var digitsBeforeCaret = countDigits(newText.substring(0, caretPosition));
        // The leading 1 that format drops is no longer there to count.
        if (digitsBeforeCaret > 0 && hasCountryCode(digitsOf(newText))) {
          digitsBeforeCaret--;
        }

        var formatted = format(newText);
        change.setText(formatted);
        change.setRange(0, change.getControlText().length());

        var newCaretPosition = caretIndexAfterDigits(formatted,
            Math.min(digitsBeforeCaret, countDigits(formatted)));
        change.setCaretPosition(newCaretPosition);
        change.setAnchor(newCaretPosition);
      }
      return change;
    };

    return new TextFormatter<>(filter);
  }

  /**
   * Counts the digit characters in a string.
   *
   * @param text the text to scan
   * @return the number of digit characters found
   */
  private static int countDigits(String text) {
    var count = 0;
    for (var i = 0; i < text.length(); i++) {
      if (Character.isDigit(text.charAt(i))) {
        count++;
      }
    }
    return count;
  }

  /**
   * Finds the index in a formatted string that comes right after its
   * {@code digitCount}th digit, so the caret can be restored to the same
   * digit it was next to before reformatting.
   *
   * @param formatted the formatted text to search
   * @param digitCount how many digits should precede the returned index
   * @return the index after the {@code digitCount}th digit, or the string's
   *     length if it has fewer digits than that
   */
  private static int caretIndexAfterDigits(String formatted, int digitCount) {
    if (digitCount <= 0) {
      return 0;
    }
    var seen = 0;
    for (var i = 0; i < formatted.length(); i++) {
      if (Character.isDigit(formatted.charAt(i))) {
        seen++;
        if (seen == digitCount) {
          return i + 1;
        }
      }
    }
    return formatted.length();
  }

  /**
   * Returns just the digits of some text.
   *
   * @param text the text, or {@code null}
   * @return its digits, or an empty string if it has none
   */
  private static String digitsOf(String text) {
    return text == null ? "" : text.replaceAll("[^0-9]", "");
  }

  /**
   * Returns whether digits are a 10-digit number with the US country code 1
   * in front, such as 18476150222, which {@link #format} drops.
   *
   * @param digits the digits only
   * @return {@code true} for 11 digits starting with 1
   */
  private static boolean hasCountryCode(String digits) {
    return digits.length() == 11 && digits.startsWith("1");
  }

  /**
   * Formats a string of digits (ignoring any other characters) as a US
   * phone number. A leading 1, the country code, is dropped from an 11-digit
   * number, as in 1-847-615-0222; otherwise only the first 10 digits are
   * kept.
   *
   * @param input the raw input to format
   * @return the formatted phone number, or a partial prefix if fewer than
   *     10 digits were given
   */
  public static String format(String input) {
    var digits = digitsOf(input);
    if (hasCountryCode(digits)) {
      digits = digits.substring(1);
    } else if (digits.length() > 10) {
      digits = digits.substring(0, 10);
    }

    if (digits.isEmpty()) {
      return "";
    }
    if (digits.length() <= 3) {
      return "(" + digits;
    }
    if (digits.length() <= 6) {
      return "(" + digits.substring(0, 3) + ") " + digits.substring(3);
    }
    return "(" + digits.substring(0, 3) + ") " + digits.substring(3, 6) + "-" + digits.substring(6);
  }

  /** Not used: phone numbers are formatted with static methods. */
  private PhoneNumberFormatter() {
  }

}
