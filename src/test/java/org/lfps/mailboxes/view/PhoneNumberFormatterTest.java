package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.scene.control.TextField;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for formatting phone numbers as they're typed, keeping the cursor in
 * the right place.
 */
class PhoneNumberFormatterTest {

  @BeforeAll
  static void startJavaFx() {
    // Controls can't be created until the JavaFX runtime is running.
    FxTestSupport.start();
  }

  @ParameterizedTest
  @CsvSource(value = {
      "'', ''",
      "5, (5",
      "555, (555",
      "5551, (555) 1",
      "555123, (555) 123",
      "5551234, (555) 123-4",
      "5551234567, (555) 123-4567",
      "555123456789, (555) 123-4567",
      "'(555) 123-4567', (555) 123-4567",
      "abc, ''",
  }, emptyValue = "")
  void formatsDigits(String input, String expected) {
    assertEquals(expected, PhoneNumberFormatter.format(input));
  }

  @Test
  void formatsNullAsEmpty() {
    assertEquals("", PhoneNumberFormatter.format(null));
  }

  @Test
  void formatsWhileTyping() {
    var field = phoneField();
    for (var digit : "5551234567".split("")) {
      field.insertText(field.getCaretPosition(), digit);
    }
    assertEquals("(555) 123-4567", field.getText());
    assertEquals(field.getText().length(), field.getCaretPosition());
  }

  @Test
  void ignoresNonDigits() {
    var field = phoneField();
    field.insertText(0, "555");
    field.insertText(field.getCaretPosition(), "x");
    assertEquals("(555", field.getText());
  }

  @Test
  void keepsCaretNextToSameDigitWhenInsertingInMiddle() {
    var field = phoneField();
    field.setText("5551234567");
    // Insert a 9 after the area code, i.e. after "(555" (index 4).
    field.insertText(4, "9");
    assertEquals("(555) 912-3456", field.getText());
    assertEquals("(555) 9".length(), field.getCaretPosition());
  }

  @Test
  void keepsCaretNextToSameDigitWhenDeleting() {
    var field = phoneField();
    field.setText("5551234567");
    // Delete the "1" at index 6 in "(555) 123-4567".
    field.deleteText(6, 7);
    assertEquals("(555) 234-567", field.getText());
    // The caret lands right after the last digit before the deletion, so a
    // further Backspace deletes a digit rather than a separator.
    assertEquals("(555".length(), field.getCaretPosition());
  }

  /** Makes a text field that formats phone numbers as they're typed. */
  private static TextField phoneField() {
    var field = new TextField();
    field.setTextFormatter(PhoneNumberFormatter.create());
    return field;
  }

}
