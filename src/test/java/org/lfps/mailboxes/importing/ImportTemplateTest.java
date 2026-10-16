package org.lfps.mailboxes.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the Import Boxes template: its sheets, headings, example row,
 * and the colors that mark the required column.
 */
class ImportTemplateTest {

  @TempDir
  Path folder;

  @Test
  void hasTheInstructionsHeadingsAndExampleOnTheBoxesSheet() throws IOException {
    var file = folder.resolve(ImportTemplate.FILE_NAME);
    ImportTemplate.write(file);

    var rows = rows(file, "Boxes");
    assertEquals(ImportTemplate.INSTRUCTIONS, rows.get(0).get(0));
    assertEquals(Arrays.stream(ImportColumn.values()).map(ImportColumn::getHeading).collect(Collectors.toList()),
        rows.get(ImportTemplate.HEADING_ROW));
    assertEquals("310", rows.get(ImportTemplate.EXAMPLE_ROW).get(0));
    assertEquals("Doe", rows.get(ImportTemplate.EXAMPLE_ROW).get(ImportColumn.LAST_NAME.ordinal()));
  }

  @Test
  void theKeySaysWhatEachColumnIsForAndWhichAreRequired() throws IOException {
    var file = folder.resolve(ImportTemplate.FILE_NAME);
    ImportTemplate.write(file);

    var rows = rows(file, "Key");
    assertEquals(List.of("Required", "Orange columns must be filled in for every box."), rows.get(0));
    // The blank row between the colors and the columns is left out.
    assertEquals(List.of("Column", "What goes in it"), rows.get(2));
    assertEquals("Box Number", rows.get(3).get(0));
    assertTrue(rows.get(3).get(1).startsWith("Required. The box number"), rows.get(3).get(1));
    assertEquals(ImportColumn.values().length + 3, rows.size());
  }

  @Test
  void colorsTheRequiredAndOptionalHeadings() throws IOException {
    var file = folder.resolve(ImportTemplate.FILE_NAME);
    ImportTemplate.write(file);

    try (var zip = new ZipFile(file.toFile())) {
      var styles = new String(zip.getInputStream(zip.getEntry("xl/styles.xml")).readAllBytes(),
          StandardCharsets.UTF_8);
      assertTrue(styles.contains(ImportTemplate.REQUIRED_COLOR), styles);
      assertTrue(styles.contains(ImportTemplate.OPTIONAL_COLOR), styles);
    }
  }

  /**
   * Reads every row of a sheet of a saved workbook, each as its cells' text,
   * leaving out blank rows and blank cells at the end of a row.
   */
  private static List<List<String>> rows(Path file, String sheetName) throws IOException {
    var rows = new ArrayList<List<String>>();
    try (var in = Files.newInputStream(file); var workbook = new ReadableWorkbook(in)) {
      var sheet = workbook.findSheet(sheetName).orElseThrow();
      try (var stream = sheet.openStream()) {
        stream.filter(row -> row.getCellCount() > 0 && !cells(row).isEmpty()).forEach(row -> rows.add(cells(row)));
      }
    }
    return rows;
  }

  /** Returns a row's cells' text, leaving out blank cells at the end. */
  private static List<String> cells(Row row) {
    var cells = new ArrayList<String>();
    for (var i = 0; i < row.getCellCount(); i++) {
      cells.add(row.getCellText(i));
    }
    while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
      cells.remove(cells.size() - 1);
    }
    return cells;
  }

}
