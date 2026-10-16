package org.lfps.mailboxes.importing;

import java.util.EnumMap;
import java.util.Map;

/**
 * An immutable row of a spreadsheet being imported, as read: the text in
 * each column, before it's checked. Fixing a row on the Import Boxes window
 * makes a changed copy with {@link #with}.
 */
public final class ImportRow {

  /** The row's number as the spreadsheet shows it, counting from 1. */
  private final int rowNumber;

  /** The text in each column, without surrounding spaces; never {@code null}. */
  private final Map<ImportColumn, String> values;

  /**
   * Makes a row.
   *
   * @param rowNumber the row's number as the spreadsheet shows it
   * @param values the text in each column; missing columns are empty
   */
  public ImportRow(int rowNumber, Map<ImportColumn, String> values) {
    this.rowNumber = rowNumber;
    this.values = new EnumMap<>(ImportColumn.class);
    for (var column : ImportColumn.values()) {
      var value = values.get(column);
      this.values.put(column, value == null ? "" : value.strip());
    }
  }

  /**
   * Returns the row's number as the spreadsheet shows it, so problems can say
   * which row they're on.
   *
   * @return the row number, counting from 1
   */
  public int getRowNumber() {
    return rowNumber;
  }

  /**
   * Returns the text in a column.
   *
   * @param column the column
   * @return the text, without surrounding spaces, or an empty string
   */
  public String get(ImportColumn column) {
    return values.get(column);
  }

  /**
   * Returns a copy of the row with different text in a column, as when a
   * problem is fixed.
   *
   * @param column the column
   * @param value the new text, or {@code null} for none
   * @return the changed copy
   */
  public ImportRow with(ImportColumn column, String value) {
    var changed = new EnumMap<>(values);
    changed.put(column, value);
    return new ImportRow(rowNumber, changed);
  }

  /**
   * Returns whether every column is empty, as for a blank row at the end of
   * a spreadsheet, which is skipped.
   *
   * @return {@code true} if nothing is filled in
   */
  public boolean isBlank() {
    return values.values().stream().allMatch(String::isEmpty);
  }

  /**
   * Returns whether the row is the template's example row, left in, which is
   * skipped. Only the columns the spreadsheet has are compared.
   *
   * @param present the columns the spreadsheet has
   * @return {@code true} if each of them holds the template's example
   */
  boolean isExample(Iterable<ImportColumn> present) {
    for (var column : present) {
      if (!get(column).equals(column.getExample())) {
        return false;
      }
    }
    return true;
  }

}
