package org.lfps.mailboxes.view;

import java.util.function.UnaryOperator;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;

/**
 * Formats phone number input as the user types, rendering typed digits as
 * {@code (XXX) XXX-XXXX} so only digits need to be entered.
 */
public final class PhoneNumberFormatter {

  /**
   * Creates a {@link TextFormatter} that reformats a text field's content
   * as {@code (XXX) XXX-XXXX} on every edit, ignoring non-digit input.
   *
   * @return a new text formatter for a phone number field
   */
  public static TextFormatter<String> create() {
    UnaryOperator<Change> filter = change -> {
      if (change.isContentChange()) {
        var newText = change.getControlNewText();
        var caretPosition = Math.min(change.getCaretPosition(), newText.length());
        var digitsBeforeCaret = countDigits(newText.substring(0, caretPosition));

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

  private static int countDigits(String text) {
    var count = 0;
    for (var i = 0; i < text.length(); i++) {
      if (Character.isDigit(text.charAt(i))) {
        count++;
      }
    }
    return count;
  }

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
   * Formats a string of digits (ignoring any other characters) as
   * {@code (XXX) XXX-XXXX}, truncating to at most 10 digits.
   *
   * @param input the raw input to format
   * @return the formatted phone number, or a partial prefix if fewer than
   *     10 digits were given
   */
  public static String format(String input) {
    var digits = input == null ? "" : input.replaceAll("[^0-9]", "");
    if (digits.length() > 10) {
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

  private PhoneNumberFormatter() {
  }

}
