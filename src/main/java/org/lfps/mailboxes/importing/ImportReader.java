package org.lfps.mailboxes.importing;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;

/**
 * Reads the boxes from a spreadsheet for File → Import Boxes: an Excel file
 * (.xlsx), as Excel, Google Sheets, and Numbers save, or a CSV file. It finds
 * the headings row by its Box Number heading, so the template's instructions
 * above it, or a spreadsheet with no instructions, both work, and reads each
 * row below it. Columns are matched by heading in any order, and others are
 * ignored. Blank rows and the template's example row are skipped. Nothing is
 * checked here; see {@link ImportCheck}.
 */
public final class ImportReader {

  /** What's said about a file named .xlsx that isn't a workbook this app can read. */
  private static final String NOT_A_WORKBOOK = "It isn't an Excel file this app can read. Save it again as an "
      + "Excel workbook (.xlsx).";

  /**
   * Reads the rows of a spreadsheet.
   *
   * @param file the file, ending in .xlsx or .csv
   * @return the rows below the headings, in order, without blank rows or
   *     the template's example
   * @throws IllegalArgumentException if it isn't an .xlsx or .csv file, or
   *     has no Box Number heading, with a message for the user
   * @throws IOException if it can't be read, such as a damaged file
   */
  public static List<ImportRow> read(Path file) throws IOException {
    var name = file.getFileName().toString().toLowerCase();
    List<List<String>> cells;
    if (name.endsWith(".xlsx")) {
      cells = readXlsx(file);
    } else if (name.endsWith(".csv")) {
      cells = parseCsv(Files.readString(file, StandardCharsets.UTF_8));
    } else {
      throw new IllegalArgumentException("Choose an Excel file (.xlsx) or a CSV file (.csv). In Numbers or "
          + "Google Sheets, export or download the spreadsheet as Excel first.");
    }
    return rows(cells);
  }

  /**
   * Turns a spreadsheet's cells into rows: finds the headings, then reads
   * each row below them.
   *
   * @param cells the spreadsheet's rows of cells, from the top; a row may be
   *     shorter than others
   * @return the rows, without blank rows or the template's example
   * @throws IllegalArgumentException if no row has a Box Number heading
   */
  static List<ImportRow> rows(List<List<String>> cells) {
    var headingRow = -1;
    for (var r = 0; r < cells.size() && headingRow < 0; r++) {
      for (var cell : cells.get(r)) {
        if (ImportColumn.withHeading(cell) == ImportColumn.BOX_NUMBER) {
          headingRow = r;
          break;
        }
      }
    }
    if (headingRow < 0) {
      throw new IllegalArgumentException("This spreadsheet doesn't have a Box Number column. Start from the "
          + "template that Save Template… makes, and keep its headings.");
    }

    // Which column of the spreadsheet each of ours is in; the first if one appears twice.
    var positions = new LinkedHashMap<ImportColumn, Integer>();
    var headings = cells.get(headingRow);
    for (var c = 0; c < headings.size(); c++) {
      var column = ImportColumn.withHeading(headings.get(c));
      if (column != null) {
        positions.putIfAbsent(column, c);
      }
    }

    var rows = new ArrayList<ImportRow>();
    for (var r = headingRow + 1; r < cells.size(); r++) {
      var line = cells.get(r);
      Map<ImportColumn, String> values = new EnumMap<>(ImportColumn.class);
      positions.forEach((column, c) -> values.put(column, c < line.size() ? line.get(c) : ""));
      var row = new ImportRow(r + 1, values);
      if (!row.isBlank() && !row.isExample(positions.keySet())) {
        rows.add(row);
      }
    }
    return rows;
  }

  /**
   * Reads the first sheet of an Excel file as text. A number is read as
   * written, such as 8475551234 rather than 8.475551234E9; a cell formatted
   * as a date, as when a date is typed into Excel, is read as one, such as
   * 2026-12-31.
   *
   * @param file the file
   * @return each row's cells, from the top; empty rows are kept, so row
   *     numbers match the spreadsheet's
   * @throws IOException if it can't be read
   */
  private static List<List<String>> readXlsx(Path file) throws IOException {
    requireWorkbook(file);
    var rows = new ArrayList<List<String>>();
    // Cell formats are read too, to tell dates from other numbers.
    try (InputStream in = Files.newInputStream(file);
        var workbook = new ReadableWorkbook(in, new ReadingOptions(true, false))) {
      try (var stream = workbook.getFirstSheet().openStream()) {
        stream.forEach(row -> {
          // Rows with nothing in them aren't in the file, so fill in the gap.
          while (rows.size() < row.getRowNum() - 1) {
            rows.add(List.of());
          }
          var cells = new ArrayList<String>();
          for (var c = 0; c < row.getCellCount(); c++) {
            cells.add(text(row.getCell(c)));
          }
          rows.add(cells);
        });
      }
    } catch (RuntimeException e) {
      // FastExcel reports a damaged workbook it can't make sense of this way.
      throw new IOException(NOT_A_WORKBOOK, e);
    }
    return rows;
  }

  /**
   * Checks that a file is an Excel workbook (.xlsx) before reading it, by
   * how it starts, so a file that only has the name gets a clear message.
   *
   * @param file the file
   * @throws IOException if it isn't one, with a message for the user, or
   *     can't be read
   */
  private static void requireWorkbook(Path file) throws IOException {
    var start = new byte[8];
    try (InputStream in = Files.newInputStream(file)) {
      var read = in.readNBytes(start, 0, start.length);
      if (read == start.length && ReadableWorkbook.isOOXMLZipHeader(start)) {
        return;
      }
      if (read == start.length && ReadableWorkbook.isOLE2Header(start)) {
        throw new IOException("It's an Excel file in the old format (.xls). Open it in Excel and save it as an "
            + "Excel workbook (.xlsx) first.");
      }
    }
    throw new IOException(NOT_A_WORKBOOK);
  }

  /**
   * Returns a cell's value as text.
   *
   * @param cell the cell, or {@code null} for an empty one
   * @return the text, a number as written, or a date as yyyy-MM-dd
   */
  private static String text(Cell cell) {
    if (cell == null || cell.getType() == CellType.EMPTY) {
      return "";
    }
    if (cell.getType() == CellType.NUMBER) {
      if (isDate(cell)) {
        return cell.asDate().toLocalDate().toString();
      }
      return new BigDecimal(cell.getRawValue()).stripTrailingZeros().toPlainString();
    }
    return cell.getText();
  }

  /**
   * Returns whether a number cell holds a date: Excel stores a date as a
   * number of days, formatted to show as a date.
   *
   * @param cell the cell
   * @return {@code true} if its format shows a date
   */
  private static boolean isDate(Cell cell) {
    var id = cell.getDataFormatId();
    // Excel's built-in date formats, which may come without their text.
    if (id != null && ((id >= 14 && id <= 22) || (id >= 45 && id <= 47))) {
      return true;
    }
    var format = cell.getDataFormatString();
    return format != null && isDateFormat(format);
  }

  /**
   * Returns whether an Excel number format shows a date, such as
   * "m/d/yyyy" or "yyyy-mm-dd", as Excel uses when a date is typed in.
   *
   * @param format the format
   * @return {@code true} for a date format
   */
  static boolean isDateFormat(String format) {
    // Leave out quoted text and colors, such as "Total:" or [Red], which can
    // have the letters too.
    var bare = format.replaceAll("\"[^\"]*\"", "").replaceAll("\\[[^]]*]", "").toLowerCase();
    return bare.contains("d") || bare.contains("y");
  }

  /**
   * Splits CSV text into rows of cells, as Excel and Google Sheets write it:
   * cells separated by commas, any of them in double quotes, which can hold
   * commas, line breaks, and doubled quotes.
   *
   * @param csv the text, which may start with the mark some programs put first
   * @return each row's cells, from the top
   */
  static List<List<String>> parseCsv(String csv) {
    var rows = new ArrayList<List<String>>();
    var row = new ArrayList<String>();
    var cell = new StringBuilder();
    var quoted = false;
    var text = csv.startsWith("﻿") ? csv.substring(1) : csv;
    for (var i = 0; i < text.length(); i++) {
      var ch = text.charAt(i);
      if (quoted) {
        if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else if (ch == '"') {
          quoted = false;
        } else {
          cell.append(ch);
        }
      } else if (ch == '"') {
        quoted = true;
      } else if (ch == ',') {
        row.add(cell.toString());
        cell.setLength(0);
      } else if (ch == '\n' || ch == '\r') {
        row.add(cell.toString());
        cell.setLength(0);
        rows.add(row);
        row = new ArrayList<>();
        // A Windows line break is \r\n; count it once.
        if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
      } else {
        cell.append(ch);
      }
    }
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }

  /** Not used: spreadsheets are read with static methods. */
  private ImportReader() {
  }

}
