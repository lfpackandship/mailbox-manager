package org.lfps.mailboxes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for how a forwarding address is tidied up, compared, and shown.
 */
class ForwardingAddressTest {

  @Test
  void formatsOnOneLineWithTheNoteInParentheses() {
    assertEquals("88 Palm Way, Unit 3B, Naples, FL 34102 (winter)",
        new ForwardingAddress("88 Palm Way", "Unit 3B", "Naples", "FL", "34102", "winter").toString());
  }

  @Test
  void leavesOutAMissingUnitAndNote() {
    assertEquals("1 Lake Rd, Duluth, MN 55802",
        new ForwardingAddress("1 Lake Rd", null, "Duluth", "MN", "55802", null).toString());
  }

  @Test
  void trimsFieldsUpperCasesTheStateAndTreatsBlanksAsMissing() {
    var address = new ForwardingAddress("  1 Lake Rd ", "  ", " Duluth", " mn ", " 55802 ", "");

    assertEquals("1 Lake Rd", address.getStreet());
    assertNull(address.getUnit());
    assertEquals("Duluth", address.getCity());
    assertEquals("MN", address.getState());
    assertEquals("55802", address.getZip());
    assertNull(address.getNote());
  }

  @Test
  void addressesWithTheSameFieldsAreEqual() {
    var a = new ForwardingAddress("1 Lake Rd", null, "Duluth", "MN", "55802", "summer");
    assertEquals(a, new ForwardingAddress("1 Lake Rd", "", "Duluth", "mn", "55802", "summer"));
    assertEquals(a.hashCode(), new ForwardingAddress("1 Lake Rd", "", "Duluth", "mn", "55802", "summer").hashCode());
    assertNotEquals(a, new ForwardingAddress("1 Lake Rd", null, "Duluth", "MN", "55802", "winter"));
  }

}
