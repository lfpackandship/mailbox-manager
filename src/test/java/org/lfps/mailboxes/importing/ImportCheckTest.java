package org.lfps.mailboxes.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.ForwardingAddress;

/**
 * Tests for checking rows to import: turning a good row into a box, and
 * each problem a row can have.
 */
class ImportCheckTest {

  /** Box 12 is rented; the inventory isn't set up. */
  private final ImportCheck check = new ImportCheck(Set.of("12"), Set.of());

  @Test
  void aCompleteRowBecomesABox() {
    var values = new EnumMap<ImportColumn, String>(ImportColumn.class);
    for (var column : ImportColumn.values()) {
      values.put(column, column.getExample());
    }
    values.put(ImportColumn.BOX_NUMBER, "40");

    var result = check.check(List.of(new ImportRow(3, values))).get(0);

    assertTrue(result.isOk(), result.getProblems().toString());
    var box = result.getMailbox();
    assertEquals("40", box.getBoxNumber());
    assertEquals("John Doe", box.getFullName());
    assertEquals("(555) 123-4567", box.getPhone());
    assertEquals(LocalDate.of(2026, 12, 31), box.getEndDate());
    assertEquals(2, box.getKeyCount());
    assertEquals(2000L, box.getKeyDepositCents());
    assertEquals(List.of("Acme Deliveries", "Doe Consulting"), box.getAlternateBusinessNames());
    assertEquals(List.of(new ForwardingAddress("123 Main St", "Apt 4", "Springfield", "IL", "62701", null)),
        box.getForwardingAddresses());
    assertEquals("Picks up for spouse", box.getNotes());
  }

  @Test
  void onlyTheBoxNumberIsRequired() {
    var result = check.check(List.of(row(Map.of(ImportColumn.BOX_NUMBER, "40")))).get(0);

    assertTrue(result.isOk(), result.getProblems().toString());
    assertEquals(List.of(), result.getMailbox().getForwardingAddresses());
  }

  @Test
  void listsEveryProblemInARow() {
    var result = check.check(List.of(row(Map.of(
        ImportColumn.FIRST_NAME, "Ada",
        ImportColumn.PHONE, "555-12",
        ImportColumn.EMAIL, "ada at example",
        ImportColumn.END_DATE, "someday",
        ImportColumn.FORWARDING_ONLY, "maybe",
        ImportColumn.KEYS, "2.5",
        ImportColumn.KEY_DEPOSIT, "lots",
        ImportColumn.CITY, "Springfield")))).get(0);

    assertNull(result.getMailbox());
    var problems = result.getProblems();
    assertEquals(8, problems.size(), problems.toString());
    assertEquals("Forwarding Only: put Yes or No, not maybe.", problems.get(0));
    assertEquals("Box Number: it's required.", problems.get(1));
    assertEquals("Phone: 555-12 isn't a 10-digit phone number.", problems.get(2));
    assertEquals("Email: ada at example isn't an email address.", problems.get(3));
    assertEquals("End Date: someday isn't a date like 12/31/2026.", problems.get(4));
    assertEquals("Keys: 2.5 isn't a whole number of keys.", problems.get(5));
    assertTrue(problems.get(6).startsWith("Key Deposit: "), problems.get(6));
    assertEquals("Forwarding address: it needs a street, a two-letter state, a ZIP code (12345 or 12345-6789).",
        problems.get(7));
  }

  @Test
  void readsDatesWrittenTheUsualWays() {
    for (var text : List.of("12/31/2026", "12/31/26", "12-31-2026", "2026-12-31", "Dec 31, 2026",
        "December 31, 2026")) {
      var problems = new ArrayList<String>();
      assertEquals(LocalDate.of(2026, 12, 31), ImportCheck.date(text, problems), text);
      assertEquals(List.of(), problems, text);
    }
  }

  @Test
  void aRentedNumberIsAProblemUnlessTheBoxIsForwardingOnly() {
    var results = check.check(List.of(
        row(Map.of(ImportColumn.BOX_NUMBER, " 12 ")),
        row(Map.of(ImportColumn.BOX_NUMBER, "12", ImportColumn.FORWARDING_ONLY, "Yes"))));

    assertEquals(List.of("Box Number: box 12 is already rented to someone else."), results.get(0).getProblems());
    assertTrue(results.get(1).isOk(), results.get(1).getProblems().toString());
    assertTrue(results.get(1).getMailbox().isForwardingOnly());
  }

  @Test
  void aNumberNotInTheInventoryIsAProblemOnceItsSetUp() {
    var withInventory = new ImportCheck(Set.of(), Set.of("1", "2"));

    var results = withInventory.check(List.of(row(Map.of(ImportColumn.BOX_NUMBER, "2")),
        row(Map.of(ImportColumn.BOX_NUMBER, "99"))));

    assertTrue(results.get(0).isOk());
    assertEquals(List.of("Box Number: box 99 isn't in the box inventory."), results.get(1).getProblems());
  }

  @Test
  void aNumberOnTwoRowsIsAProblemOnTheSecond() {
    var results = check.check(List.of(
        new ImportRow(3, Map.of(ImportColumn.BOX_NUMBER, "40")),
        new ImportRow(4, Map.of(ImportColumn.BOX_NUMBER, "41")),
        new ImportRow(5, Map.of(ImportColumn.BOX_NUMBER, "40"))));

    assertTrue(results.get(0).isOk());
    assertTrue(results.get(1).isOk());
    assertEquals(List.of("Box Number: box 40 is on row 3 too."), results.get(2).getProblems());
    assertEquals(2, ImportCheck.boxes(results).size());
  }

  @Test
  void putsBackAZipCodesLostLeadingZeroAndCapitalizesTheState() {
    var result = check.check(List.of(row(Map.of(ImportColumn.BOX_NUMBER, "40", ImportColumn.STREET, "1 Elm St",
        ImportColumn.CITY, "Newport", ImportColumn.STATE, "ri", ImportColumn.ZIP, "2840")))).get(0);

    assertTrue(result.isOk(), result.getProblems().toString());
    assertEquals(new ForwardingAddress("1 Elm St", null, "Newport", "RI", "02840", null),
        result.getMailbox().getForwardingAddresses().get(0));
  }

  /** Makes row 3 with the given values and the rest empty. */
  private static ImportRow row(Map<ImportColumn, String> values) {
    return new ImportRow(3, values);
  }

}
