package org.lfps.mailboxes.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for checking email addresses, phone numbers, ZIP codes, and state
 * codes.
 */
class ValidatorsTest {

  @ParameterizedTest
  @ValueSource(strings = { "a@b.co", "first.last@example.com", "a+tag@sub-domain.org", "x_y@example.io" })
  void acceptsValidEmails(String email) {
    assertTrue(Validators.isValidEmail(email));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "plain", "@example.com", "a@", "a@example", "a@example.c", "a b@example.com" })
  void rejectsInvalidEmails(String email) {
    assertFalse(Validators.isValidEmail(email));
  }

  @Test
  void rejectsNullEmail() {
    assertFalse(Validators.isValidEmail(null));
  }

  @ParameterizedTest
  @ValueSource(strings = { "5551234567", "(555) 123-4567", "555-123-4567", "15551234567", "+1 (555) 123-4567" })
  void acceptsValidPhones(String phone) {
    assertTrue(Validators.isValidPhone(phone));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "555123456", "25551234567", "555123456789", "(555) 123-456" })
  void rejectsInvalidPhones(String phone) {
    assertFalse(Validators.isValidPhone(phone));
  }

  @Test
  void rejectsNullPhone() {
    assertFalse(Validators.isValidPhone(null));
  }

  @ParameterizedTest
  @ValueSource(strings = { "62701", "62701-1234", " 02134 " })
  void acceptsValidZips(String zip) {
    assertTrue(Validators.isValidZip(zip));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "6270", "627011", "62701-123", "62701 1234", "ABCDE" })
  void rejectsInvalidZips(String zip) {
    assertFalse(Validators.isValidZip(zip));
  }

  @ParameterizedTest
  @ValueSource(strings = { "FL", "ny", " Il " })
  void acceptsTwoLetterStates(String state) {
    assertTrue(Validators.isValidState(state));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "F", "Florida", "F1" })
  void rejectsOtherStates(String state) {
    assertFalse(Validators.isValidState(state));
  }

  @Test
  void rejectsNullZipAndState() {
    assertFalse(Validators.isValidZip(null));
    assertFalse(Validators.isValidState(null));
  }

}
