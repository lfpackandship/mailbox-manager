package org.lfps.mailboxes.importing;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.BoxNumbers;
import org.lfps.mailboxes.util.Money;
import org.lfps.mailboxes.util.Validators;

/**
 * Checks the rows read from a spreadsheet for File → Import Boxes, the way
 * Add New Box checks its form: the box number is filled in, not rented to
 * someone else, in the box inventory if that's set up, and not on an earlier
 * row too, and every other value is one the app can use. A row that passes
 * becomes the box to add; one that doesn't lists what's wrong, for fixing or
 * skipping on the Import Boxes window.
 */
public final class ImportCheck {

  /** The ways a date can be written, tried in order. */
  private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
      DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
      DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
      DateTimeFormatter.ofPattern("M-d-yyyy", Locale.US),
      DateTimeFormatter.ISO_LOCAL_DATE,
      DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
      DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US));

  /** What a row came to once checked. */
  public static final class Result {

    /** The row as read, or as fixed. */
    private final ImportRow row;

    /** The box to add, or {@code null} if the row has problems. */
    private final Mailbox mailbox;

    /** What's wrong with the row, each a sentence; empty if nothing is. */
    private final List<String> problems;

    /**
     * Makes a result.
     *
     * @param row the row
     * @param mailbox the box to add, or {@code null} if there are problems
     * @param problems what's wrong; empty if nothing is
     */
    Result(ImportRow row, Mailbox mailbox, List<String> problems) {
      this.row = row;
      this.mailbox = mailbox;
      this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    /**
     * Returns the row.
     *
     * @return the row as read, or as fixed
     */
    public ImportRow getRow() {
      return row;
    }

    /**
     * Returns the box the row adds.
     *
     * @return the box, or {@code null} if the row has problems
     */
    public Mailbox getMailbox() {
      return mailbox;
    }

    /**
     * Returns what's wrong with the row.
     *
     * @return each problem as a sentence, such as "Phone: 555-12 isn't a
     *     10-digit phone number."; empty if nothing is wrong
     */
    public List<String> getProblems() {
      return problems;
    }

    /**
     * Returns whether the row can be imported as it is.
     *
     * @return {@code true} if it has no problems
     */
    public boolean isOk() {
      return problems.isEmpty();
    }

  }

  /** The open boxes' numbers that are taken, by {@link BoxNumbers#key}. */
  private final Set<String> taken;

  /** The box inventory's numbers, by {@link BoxNumbers#key}; empty if it isn't set up. */
  private final Set<String> inventory;

  /**
   * Makes a check against what's already recorded.
   *
   * @param taken the box numbers rented to open boxes, by
   *     {@link BoxNumbers#key}
   * @param inventory the box inventory's numbers, by {@link BoxNumbers#key},
   *     or none if it isn't set up
   */
  ImportCheck(Set<String> taken, Set<String> inventory) {
    this.taken = taken;
    this.inventory = inventory;
  }

  /**
   * Makes a check against the boxes and inventory in the database now.
   *
   * @return the check
   * @throws SQLException if they can't be read
   */
  public static ImportCheck againstDatabase() throws SQLException {
    // Forwarding-only boxes don't hold their numbers.
    var taken = new MailboxRepository().findOpen().stream()
        .filter(mailbox -> !mailbox.isForwardingOnly())
        .map(mailbox -> BoxNumbers.key(mailbox.getBoxNumber()))
        .collect(Collectors.toSet());
    var inventory = new BoxInventoryRepository().findAll().stream()
        .map(box -> BoxNumbers.key(box.getBoxNumber()))
        .collect(Collectors.toSet());
    return new ImportCheck(taken, inventory);
  }

  /**
   * Checks every row. A box number on more than one row is a problem on each
   * row after the first, so fixing or skipping rows and checking again
   * settles it.
   *
   * @param rows the rows, in order
   * @return a result for each row, in the same order
   */
  public List<Result> check(List<ImportRow> rows) {
    var results = new ArrayList<Result>();
    // Where each box number was first seen, for saying where else it is.
    var firstSeen = new HashMap<String, Integer>();
    for (var row : rows) {
      var problems = new ArrayList<String>();
      var mailbox = toMailbox(row, problems);
      var number = row.get(ImportColumn.BOX_NUMBER);
      if (!number.isEmpty() && mailbox != null && !mailbox.isForwardingOnly()) {
        var key = BoxNumbers.key(number);
        var earlier = firstSeen.putIfAbsent(key, row.getRowNumber());
        if (earlier != null) {
          problems.add("Box Number: box " + number + " is on row " + earlier + " too.");
        }
      }
      results.add(new Result(row, problems.isEmpty() ? mailbox : null, problems));
    }
    return results;
  }

  /**
   * Turns a row into the box it adds, noting each problem with it.
   *
   * @param row the row
   * @param problems where to add the problems found
   * @return the box, or {@code null} if a value couldn't be read at all;
   *     a box is returned even with some problems, so its number can still
   *     be checked against other rows
   */
  private Mailbox toMailbox(ImportRow row, List<String> problems) {
    var forwardingOnly = yesNo(row, problems);
    var boxNumber = row.get(ImportColumn.BOX_NUMBER);
    if (boxNumber.isEmpty()) {
      problems.add("Box Number: it's required.");
    } else if (!forwardingOnly) {
      var key = BoxNumbers.key(boxNumber);
      if (taken.contains(key)) {
        problems.add("Box Number: box " + boxNumber + " is already rented to someone else.");
      } else if (!inventory.isEmpty() && !inventory.contains(key)) {
        problems.add("Box Number: box " + boxNumber + " isn't in the box inventory.");
      }
    }

    var phone = row.get(ImportColumn.PHONE);
    if (!phone.isEmpty() && !Validators.isValidPhone(phone)) {
      problems.add("Phone: " + phone + " isn't a 10-digit phone number.");
    }
    var email = row.get(ImportColumn.EMAIL);
    if (!email.isEmpty() && !Validators.isValidEmail(email)) {
      problems.add("Email: " + email + " isn't an email address.");
    }

    var endDate = date(row.get(ImportColumn.END_DATE), problems);
    var keys = keys(row.get(ImportColumn.KEYS), problems);
    Long deposit = null;
    try {
      deposit = Money.parse(row.get(ImportColumn.KEY_DEPOSIT));
    } catch (IllegalArgumentException e) {
      problems.add("Key Deposit: " + e.getMessage());
    }

    var names = Arrays.stream(row.get(ImportColumn.ALSO_RECEIVES_MAIL_AS).split(";"))
        .map(String::strip)
        .filter(name -> !name.isEmpty())
        .collect(Collectors.toList());
    var addresses = address(row, problems);

    return new Mailbox(0, row.get(ImportColumn.FIRST_NAME), row.get(ImportColumn.LAST_NAME),
        row.get(ImportColumn.BUSINESS_TITLE), boxNumber, row.get(ImportColumn.BOX_NAME), phone, email, names,
        endDate, addresses, row.get(ImportColumn.NOTES), null, keys, deposit, forwardingOnly);
  }

  /**
   * Reads the Forwarding Only column: Yes, Y, True, X, or 1 for yes; blank,
   * No, N, False, or 0 for no.
   *
   * @param row the row
   * @param problems where to add a problem
   * @return whether the box is forwarding only; no if it can't be read
   */
  private static boolean yesNo(ImportRow row, List<String> problems) {
    var value = row.get(ImportColumn.FORWARDING_ONLY).toLowerCase(Locale.US);
    if (Set.of("yes", "y", "true", "x", "1").contains(value)) {
      return true;
    }
    if (!Set.of("", "no", "n", "false", "0").contains(value)) {
      problems.add("Forwarding Only: put Yes or No, not " + row.get(ImportColumn.FORWARDING_ONLY) + ".");
    }
    return false;
  }

  /**
   * Reads a date written any of the usual ways, such as 12/31/2026,
   * 2026-12-31, or Dec 31, 2026.
   *
   * @param text the date, or an empty string
   * @param problems where to add a problem
   * @return the date, or {@code null} if it's blank or can't be read
   */
  static LocalDate date(String text, List<String> problems) {
    if (text.isEmpty()) {
      return null;
    }
    for (var format : DATE_FORMATS) {
      try {
        return LocalDate.parse(text, format);
      } catch (DateTimeParseException tryNext) {
        // Not written this way.
      }
    }
    problems.add("End Date: " + text + " isn't a date like 12/31/2026.");
    return null;
  }

  /**
   * Reads how many keys were given, a whole number from 0 to 99.
   *
   * @param text the number, or an empty string
   * @param problems where to add a problem
   * @return the number, or {@code null} if it's blank or can't be read
   */
  private static Integer keys(String text, List<String> problems) {
    if (text.isEmpty()) {
      return null;
    }
    try {
      var number = new BigDecimal(text);
      if (number.signum() >= 0 && number.compareTo(BigDecimal.valueOf(99)) <= 0
          && number.stripTrailingZeros().scale() <= 0) {
        return number.intValueExact();
      }
    } catch (NumberFormatException | ArithmeticException notANumber) {
      // Said below.
    }
    problems.add("Keys: " + text + " isn't a whole number of keys.");
    return null;
  }

  /**
   * Reads the forwarding address, if one is filled in. A street, city,
   * two-letter state, and ZIP code are all needed once any part is.
   *
   * @param row the row
   * @param problems where to add a problem
   * @return the address on its own in a list, or none
   */
  private static List<ForwardingAddress> address(ImportRow row, List<String> problems) {
    var street = row.get(ImportColumn.STREET);
    var unit = row.get(ImportColumn.UNIT);
    var city = row.get(ImportColumn.CITY);
    var state = row.get(ImportColumn.STATE).toUpperCase(Locale.US);
    var zip = row.get(ImportColumn.ZIP);
    // A spreadsheet that took a ZIP code as a number drops its leading zero.
    if (zip.matches("\\d{4}")) {
      zip = "0" + zip;
    }
    if (street.isEmpty() && unit.isEmpty() && city.isEmpty() && state.isEmpty() && zip.isEmpty()) {
      return List.of();
    }
    var missing = new ArrayList<String>();
    if (street.isEmpty()) {
      missing.add("a street");
    }
    if (city.isEmpty()) {
      missing.add("a city");
    }
    if (!Validators.isValidState(state)) {
      missing.add("a two-letter state");
    }
    if (!Validators.isValidZip(zip)) {
      missing.add("a ZIP code (12345 or 12345-6789)");
    }
    if (!missing.isEmpty()) {
      problems.add("Forwarding address: it needs " + String.join(", ", missing) + ".");
      return List.of();
    }
    return List.of(new ForwardingAddress(street, unit, city, state, zip, null));
  }

  /**
   * Returns the boxes from the rows that pass, ready to add.
   *
   * @param results the checked rows
   * @return the boxes to add, in row order
   */
  public static List<Mailbox> boxes(List<Result> results) {
    return results.stream().filter(Result::isOk).map(Result::getMailbox).collect(Collectors.toList());
  }

}
