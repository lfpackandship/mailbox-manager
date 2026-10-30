package org.lfps.mailboxes.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.dhatim.fastexcel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for reading a spreadsheet to import: CSV and Excel files, finding
 * the headings, matching columns, and skipping blank and example rows.
 */
class ImportReaderTest {

  @TempDir
  Path folder;

  @Test
  void splitsCsvWithQuotesCommasLineBreaksAndTheMarkSomeProgramsPutFirst() {
    var csv = "﻿Box Number,Notes\r\n12,\"Picks up, sometimes\"\r\n13,\"Said \"\"hi\"\"\nthen left\"\n";

    assertEquals(List.of(List.of("Box Number", "Notes"), List.of("12", "Picks up, sometimes"),
        List.of("13", "Said \"hi\"\nthen left")), ImportReader.parseCsv(csv));
  }

  @Test
  void findsTheHeadingsBelowInstructionsAndMatchesColumnsInAnyOrder() {
    var rows = ImportReader.rows(List.of(
        List.of("Fill in one box per row"),
        List.of("last name", " BOX NUMBER ", "Shoe Size"),
        List.of("Lovelace", "12", "9")));

    assertEquals(1, rows.size());
    assertEquals("12", rows.get(0).get(ImportColumn.BOX_NUMBER));
    assertEquals("Lovelace", rows.get(0).get(ImportColumn.LAST_NAME));
    assertEquals("", rows.get(0).get(ImportColumn.PHONE));
    assertEquals(3, rows.get(0).getRowNumber());
  }

  @Test
  void skipsBlankRowsAndTheExampleButKeepsRowNumbers() {
    var rows = ImportReader.rows(List.of(
        List.of("Box Number", "Last Name"),
        List.of("310", "Doe"),
        List.of("", " "),
        List.of(),
        List.of("12", "Lovelace")));

    assertEquals(1, rows.size());
    assertEquals(5, rows.get(0).getRowNumber());
  }

  @Test
  void saysWhenThereIsNoBoxNumberColumn() {
    var e = assertThrows(IllegalArgumentException.class,
        () -> ImportReader.rows(List.of(List.of("Name", "Phone"), List.of("Ada", "5551234567"))));

    assertTrue(e.getMessage().startsWith("This spreadsheet doesn't have a Box Number column."), e.getMessage());
  }

  @Test
  void onlyReadsExcelAndCsvFiles() throws IOException {
    var file = Files.writeString(folder.resolve("boxes.numbers"), "x");

    var e = assertThrows(IllegalArgumentException.class, () -> ImportReader.read(file));

    assertTrue(e.getMessage().startsWith("Choose an Excel file (.xlsx) or a CSV file (.csv)."), e.getMessage());
  }

  @Test
  void readsACsvFile() throws IOException {
    var file = folder.resolve("boxes.csv");
    Files.writeString(file, "Box Number,First Name\n12,Ada\n", StandardCharsets.UTF_8);

    var rows = ImportReader.read(file);

    assertEquals("Ada", rows.get(0).get(ImportColumn.FIRST_NAME));
  }

  @Test
  void readsAccentsInAUtf8CsvFile() throws IOException {
    var file = folder.resolve("boxes.csv");
    Files.writeString(file, "Box Number,First Name,Last Name\n12,José,O’Brien\n", StandardCharsets.UTF_8);

    var rows = ImportReader.read(file);

    assertEquals("José", rows.get(0).get(ImportColumn.FIRST_NAME));
    assertEquals("O’Brien", rows.get(0).get(ImportColumn.LAST_NAME));
  }

  @Test
  void readsACsvFileSavedByExcelOnWindows() throws IOException {
    var file = folder.resolve("boxes.csv");
    Files.writeString(file, "Box Number,First Name,Last Name\r\n12,José,O’Brien\r\n",
        Charset.forName("windows-1252"));

    var rows = ImportReader.read(file);

    assertEquals("José", rows.get(0).get(ImportColumn.FIRST_NAME));
    assertEquals("O’Brien", rows.get(0).get(ImportColumn.LAST_NAME));
  }

  @Test
  void readsAnExcelFileWithDatesAndNumbersAsTyped() throws IOException {
    var file = folder.resolve("boxes.xlsx");
    try (var out = Files.newOutputStream(file); var workbook = new Workbook(out, "Test", "1.0")) {
      var sheet = workbook.newWorksheet("Boxes");
      sheet.value(0, 0, "Box Number");
      sheet.value(0, 1, "End Date");
      sheet.value(0, 2, "Phone");
      sheet.value(0, 3, "Keys");
      sheet.value(0, 4, "ZIP");
      // As Excel stores them when typed: a number shown as a date, and plain numbers.
      sheet.value(1, 0, 12);
      sheet.value(1, 1, LocalDate.of(2026, 12, 31));
      sheet.style(1, 1).format("m/d/yyyy").set();
      sheet.value(1, 2, 8475551234L);
      sheet.value(1, 3, 2.0);
      sheet.value(1, 4, "02840");
    }

    var row = ImportReader.read(file).get(0);

    assertEquals("12", row.get(ImportColumn.BOX_NUMBER));
    assertEquals("2026-12-31", row.get(ImportColumn.END_DATE));
    assertEquals("8475551234", row.get(ImportColumn.PHONE));
    assertEquals("2", row.get(ImportColumn.KEYS));
    assertEquals("02840", row.get(ImportColumn.ZIP));
  }

  @Test
  void theTemplateAsSavedHasNoBoxesToImport() throws IOException {
    var file = folder.resolve(ImportTemplate.FILE_NAME);
    ImportTemplate.write(file);

    assertEquals(List.of(), ImportReader.read(file));
  }

  @Test
  void aFileThatIsntReallyExcelIsReportedAsSuch() throws IOException {
    var file = Files.writeString(folder.resolve("old.xlsx"), "not a workbook");

    var e = assertThrows(IOException.class, () -> ImportReader.read(file));

    assertTrue(e.getMessage().startsWith("It isn't an Excel file"), e.getMessage());
  }

  @Test
  void tellsDateFormatsFromOthers() {
    assertTrue(ImportReader.isDateFormat("m/d/yyyy"));
    assertTrue(ImportReader.isDateFormat("[$-409]d-mmm-yy;@"));
    assertFalse(ImportReader.isDateFormat("0.00"));
    assertFalse(ImportReader.isDateFormat("\"Paid\" $#,##0.00"));
  }

}
