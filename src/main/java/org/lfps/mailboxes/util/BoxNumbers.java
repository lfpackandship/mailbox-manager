package org.lfps.mailboxes.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sorting, comparing, and parsing box numbers, which are text so that numbers
 * like {@code 12A} are allowed.
 */
public final class BoxNumbers {

  /** The most box numbers one range can add, to catch typos like 1-10000. */
  public static final int MAX_RANGE = 2000;

  /**
   * Orders box numbers the way people expect: numbers by value (2 before 10),
   * ignoring letter case, with letters after the number they follow (12
   * before 12A before 13).
   */
  public static final Comparator<String> ORDER = BoxNumbers::compare;

  private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");

  private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");

  /**
   * Returns the form of a box number used to check whether two are the same:
   * trimmed and lower-cased, so {@code " 12a"} and {@code "12A"} match.
   *
   * @param boxNumber the box number as entered, or {@code null}
   * @return the normalized box number, or an empty string for {@code null}
   */
  public static String key(String boxNumber) {
    return boxNumber == null ? "" : boxNumber.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses a list of box numbers and ranges separated by commas, such as
   * {@code "1-200, 301-310, 12A"}. A range keeps the leading zeros of its
   * first number, so {@code 001-003} gives 001, 002, 003. Duplicates are
   * dropped.
   *
   * @param text the list as typed
   * @return the box numbers, in the order given
   * @throws IllegalArgumentException if the list is empty or a range is
   *     backwards or too long, with a message suitable for showing to the user
   */
  public static List<String> parseList(String text) {
    var numbers = new LinkedHashSet<String>();
    for (var part : (text == null ? "" : text).split(",")) {
      part = part.trim();
      if (part.isEmpty()) {
        continue;
      }
      var range = RANGE.matcher(part);
      if (!range.matches()) {
        if (part.contains("-") && part.matches(".*\\d\\s*-.*")) {
          throw new IllegalArgumentException("\"" + part + "\" isn't a range. Ranges look like 1-200.");
        }
        numbers.add(part);
        continue;
      }
      var first = Long.parseLong(range.group(1));
      var last = Long.parseLong(range.group(2));
      if (last < first) {
        throw new IllegalArgumentException("The range " + part + " goes backwards. Put the smaller number first.");
      }
      if (last - first + 1 > MAX_RANGE) {
        throw new IllegalArgumentException("The range " + part + " is more than " + MAX_RANGE
            + " boxes. Check the numbers, or add it in smaller ranges.");
      }
      var width = range.group(1).startsWith("0") ? range.group(1).length() : 1;
      for (var n = first; n <= last; n++) {
        numbers.add(String.format("%0" + width + "d", n));
      }
    }
    if (numbers.isEmpty()) {
      throw new IllegalArgumentException("Enter at least one box number, like 12 or 1-200.");
    }
    return new ArrayList<>(numbers);
  }

  private static int compare(String a, String b) {
    var left = CHUNK.matcher(key(a));
    var right = CHUNK.matcher(key(b));
    while (true) {
      var leftMore = left.find();
      var rightMore = right.find();
      if (!leftMore || !rightMore) {
        return Boolean.compare(leftMore, rightMore);
      }
      var x = left.group();
      var y = right.group();
      var xNumber = Character.isDigit(x.charAt(0));
      var yNumber = Character.isDigit(y.charAt(0));
      int result;
      if (xNumber && yNumber) {
        var xDigits = x.replaceFirst("^0+(?=.)", "");
        var yDigits = y.replaceFirst("^0+(?=.)", "");
        // Compare by length first so numbers of any size work.
        result = xDigits.length() != yDigits.length()
            ? Integer.compare(xDigits.length(), yDigits.length())
            : xDigits.compareTo(yDigits);
      } else if (xNumber != yNumber) {
        // Numbers before letters, so "12" sorts before "A1".
        result = xNumber ? -1 : 1;
      } else {
        result = x.compareTo(y);
      }
      if (result != 0) {
        return result;
      }
    }
  }

  private BoxNumbers() {
  }

}
