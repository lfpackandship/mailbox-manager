package org.lfps.mailboxes.importing;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/**
 * Writes the Import Boxes template: an Excel file (.xlsx), which Excel,
 * Google Sheets, and Numbers all open, with a Boxes sheet to fill in, one
 * box per row, and a Key sheet saying what goes in each column. On the
 * Boxes sheet a line of instructions comes first, then the column headings,
 * with the required ones in orange and the rest in grey, then an example row,
 * which importing skips if it's left in.
 */
public final class ImportTemplate {

  /** The name the template is saved under, in Downloads. */
  public static final String FILE_NAME = "Mailbox Manager import template.xlsx";

  /** The instructions above the headings on the Boxes sheet. */
  static final String INSTRUCTIONS = "Fill in one box per row, under the example. Orange columns must be "
      + "filled in; grey ones can be left blank. The example row is skipped when importing. The Key tab says "
      + "what goes in each column.";

  /** The row the headings are on, counting from 0. */
  static final int HEADING_ROW = 1;

  /** The row the example is on, counting from 0. */
  static final int EXAMPLE_ROW = 2;

  /** The fill behind a required column's heading: orange. */
  static final String REQUIRED_COLOR = "FFC000";

  /** The fill behind an optional column's heading: light grey. */
  static final String OPTIONAL_COLOR = "D9D9D9";

  /** How many rows below the example are set up as text, so Excel keeps ZIP codes' leading zeros. */
  private static final int TEXT_ROWS = 2000;

  /**
   * Writes the template to a file, replacing it if it's there.
   *
   * @param file where to save it, normally {@link #FILE_NAME} in Downloads
   * @throws IOException if it can't be written
   */
  public static void write(Path file) throws IOException {
    // FastExcel wants a version like "1.0"; it's only what the file says made it.
    // Closing the workbook finishes the file.
    try (OutputStream out = Files.newOutputStream(file); var workbook = new Workbook(out, "Mailbox Manager", "1.0")) {
      writeBoxes(workbook.newWorksheet("Boxes"));
      writeKey(workbook.newWorksheet("Key"));
    }
  }

  /**
   * Fills in the Boxes sheet: instructions, colored headings, the example
   * row, and text formatting for the columns where Excel would otherwise
   * change what's typed.
   *
   * @param sheet the sheet
   */
  private static void writeBoxes(Worksheet sheet) {
    var columns = ImportColumn.values();
    sheet.value(0, 0, INSTRUCTIONS);
    sheet.style(0, 0).bold().set();
    for (var c = 0; c < columns.length; c++) {
      var column = columns[c];
      sheet.value(HEADING_ROW, c, column.getHeading());
      sheet.style(HEADING_ROW, c).bold().fillColor(column.isRequired() ? REQUIRED_COLOR : OPTIONAL_COLOR)
          .borderStyle("thin").set();
      sheet.value(EXAMPLE_ROW, c, column.getExample());
      sheet.style(EXAMPLE_ROW, c).italic().fontColor("808080").format("@").set();
      // Wide enough for the heading and the example, in characters.
      sheet.width(c, Math.max(12, Math.max(column.getHeading().length() + 4, column.getExample().length() + 3)));
      // Typed as text, so 02840 stays 02840 and 12/31 stays as typed;
      // dates and numbers are left for the spreadsheet to recognize.
      if (column != ImportColumn.END_DATE && column != ImportColumn.KEYS && column != ImportColumn.KEY_DEPOSIT) {
        sheet.range(EXAMPLE_ROW + 1, c, EXAMPLE_ROW + TEXT_ROWS, c).style().format("@").set();
      }
    }
    // The headings stay in view while scrolling down the boxes.
    sheet.freezePane(0, HEADING_ROW + 1);
  }

  /**
   * Fills in the Key sheet: what the colors mean, then each column's
   * heading, whether it's required, and what goes in it.
   *
   * @param sheet the sheet
   */
  private static void writeKey(Worksheet sheet) {
    sheet.value(0, 0, "Required");
    sheet.style(0, 0).bold().fillColor(REQUIRED_COLOR).set();
    sheet.value(0, 1, "Orange columns must be filled in for every box.");
    sheet.value(1, 0, "Optional");
    sheet.style(1, 0).bold().fillColor(OPTIONAL_COLOR).set();
    sheet.value(1, 1, "Grey columns can be left blank.");

    var row = 3;
    sheet.value(row, 0, "Column");
    sheet.value(row, 1, "What goes in it");
    sheet.style(row, 0).bold().set();
    sheet.style(row, 1).bold().set();
    for (var column : ImportColumn.values()) {
      row++;
      sheet.value(row, 0, column.getHeading());
      sheet.style(row, 0).fillColor(column.isRequired() ? REQUIRED_COLOR : OPTIONAL_COLOR).set();
      sheet.value(row, 1, (column.isRequired() ? "Required. " : "") + column.getDescription());
      sheet.style(row, 1).wrapText(true).set();
    }
    sheet.width(0, 24);
    sheet.width(1, 90);
  }

  /** Not used: the template is written with static methods. */
  private ImportTemplate() {
  }

}
