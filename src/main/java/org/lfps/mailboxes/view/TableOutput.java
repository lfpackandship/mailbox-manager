package org.lfps.mailboxes.view;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Prints the lists on Manage Boxes, Renewals, and Payments, or saves them as
 * a spreadsheet (a CSV file, which Excel and Google Sheets open). Both use
 * the rows as they're shown: searched, filtered, and sorted. Printing shows
 * the columns on screen; the spreadsheet also has any hidden columns, for
 * details left off the screen to keep it easy to read.
 */
final class TableOutput {

  /** Asks where to save a file. */
  interface ChooseFile {
    /**
     * Asks where to save.
     *
     * @param owner the window the dialog belongs to
     * @param suggestedName the file name to start with
     * @return the file chosen, or {@code null} if cancelled
     */
    File choose(Window owner, String suggestedName);
  }

  /** Asks where to save a spreadsheet. Replaceable so tests can answer without a real dialog. */
  static ChooseFile chooseFile = (owner, suggestedName) -> {
    var chooser = new FileChooser();
    chooser.setTitle("Save as Spreadsheet");
    chooser.setInitialFileName(suggestedName);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Spreadsheet (CSV)", "*.csv"));
    return chooser.showSaveDialog(owner);
  };

  /** How many lines of a list fit on a printed page. */
  static final int LINES_PER_PAGE = 40;

  /** Where a column's own formatting is kept, for values the table formats itself. */
  private static final String FORMAT = "TableOutput.format";

  /** A list to print or save, with a heading if it's one of several. */
  static final class Section {

    final String heading;
    final TableView<?> table;

    Section(String heading, TableView<?> table) {
      this.heading = heading;
      this.table = table;
    }

  }

  /**
   * Sets how a column's values are written when printed or saved, for a
   * column whose cells format them, such as amounts kept in cents.
   *
   * @param column the column
   * @param format turns a value into its text
   */
  static <T> void formatWith(TableColumn<?, T> column, Function<T, String> format) {
    column.getProperties().put(FORMAT, format);
  }

  /**
   * Asks where to save, then saves the lists as a spreadsheet, one row per
   * row shown, under a row of column headings, including hidden columns. The
   * lists must have the same columns.
   *
   * @param owner the window the dialog belongs to
   * @param suggestedName the file name to start with, such as "boxes.csv"
   * @param tables the lists to save, one after another
   * @return a message saying where it was saved, or {@code null} if cancelled
   * @throws IllegalStateException if it couldn't be saved, with a message
   *     suitable for showing to the user
   */
  static String saveSpreadsheet(Window owner, String suggestedName, List<TableView<?>> tables) {
    var file = chooseFile.choose(owner, suggestedName);
    if (file == null) {
      return null;
    }
    if (!file.getName().toLowerCase().endsWith(".csv")) {
      file = new File(file.getParentFile(), file.getName() + ".csv");
    }
    try {
      // The byte order mark tells Excel the file is UTF-8, so dashes and
      // accented letters come out right.
      Files.writeString(file.toPath(), "﻿" + csv(tables), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Couldn't save " + file.getName() + ": " + e.getMessage());
    }
    return "Saved " + file;
  }

  /**
   * Prints the lists, after asking which printer to use, then says how it
   * went.
   *
   * @param title what's printed at the top of each page, such as "Boxes"
   * @param sections the lists, which must have the same columns
   * @param status where to say how it went
   */
  static void print(String title, List<Section> sections, Label status) {
    var pages = pages(title, sections, LocalDate.now());
    pages.forEach(Printing::layOut);
    Printing.print("Mailbox Manager – " + title.lines().findFirst().orElse(title), pages, status,
        pages.size() == 1 ? "Printed 1 page" : "Printed " + pages.size() + " pages");
  }

  /**
   * Writes the lists in CSV format: a row of column headings, then each row
   * shown, one list after another.
   */
  static String csv(List<TableView<?>> tables) {
    var out = new StringBuilder();
    var columns = tables.get(0).getColumns();
    out.append(csvRow(headings(columns)));
    for (var table : tables) {
      for (var row = 0; row < table.getItems().size(); row++) {
        out.append(csvRow(cells(table, row, false)));
      }
    }
    return out.toString();
  }

  /**
   * Lays out the printed pages of the lists, with the column headings at the
   * top of each page and each list's heading before its rows.
   */
  static List<Region> pages(String title, List<Section> sections, LocalDate today) {
    var columns = sections.get(0).table.getVisibleLeafColumns();
    var headings = headings(columns);

    // Each line is a section heading (one item) or a row of cells.
    var lines = new ArrayList<List<String>>();
    for (var section : sections) {
      if (section.heading != null) {
        lines.add(List.of(section.heading));
      }
      if (section.table.getItems().isEmpty()) {
        lines.add(List.of("None"));
      }
      for (var row = 0; row < section.table.getItems().size(); row++) {
        lines.add(cells(section.table, row, true));
      }
    }

    // Share the width out by how much text each column holds.
    var weights = new double[headings.size()];
    for (var col = 0; col < headings.size(); col++) {
      weights[col] = Math.max(4, headings.get(col).length());
    }
    for (var line : lines) {
      if (line.size() == headings.size()) {
        for (var col = 0; col < line.size(); col++) {
          weights[col] = Math.max(weights[col], Math.min(30, line.get(col).length()));
        }
      }
    }
    var total = 0.0;
    for (var weight : weights) {
      total += weight;
    }

    var pageCount = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    var printed = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG));
    var pages = new ArrayList<Region>();
    for (var pageIndex = 0; pageIndex < pageCount; pageIndex++) {
      var grid = new GridPane();
      grid.setHgap(8);
      grid.setVgap(2);
      for (var weight : weights) {
        var constraints = new ColumnConstraints();
        constraints.setPercentWidth(100 * weight / total);
        constraints.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(constraints);
      }
      for (var col = 0; col < headings.size(); col++) {
        var heading = cellLabel(headings.get(col));
        heading.setStyle("-fx-font-weight: bold; -fx-border-color: black; -fx-border-width: 0 0 1 0;");
        heading.setMaxWidth(Double.MAX_VALUE);
        grid.add(heading, col, 0);
      }
      var gridRow = 1;
      for (var line : lines.subList(pageIndex * LINES_PER_PAGE,
          Math.min(lines.size(), (pageIndex + 1) * LINES_PER_PAGE))) {
        if (line.size() == headings.size()) {
          for (var col = 0; col < line.size(); col++) {
            grid.add(cellLabel(line.get(col)), col, gridRow);
          }
        } else {
          var heading = cellLabel(line.get(0));
          heading.setStyle("-fx-font-weight: bold;");
          heading.setPadding(new Insets(6, 0, 0, 0));
          grid.add(heading, 0, gridRow, headings.size(), 1);
        }
        gridRow++;
      }

      var titleLabel = new Label(title);
      titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
      var footer = new Label("Printed " + printed + (pageCount == 1 ? "" : " · Page " + (pageIndex + 1)
          + " of " + pageCount));
      footer.setStyle("-fx-font-size: 9px; -fx-text-fill: #555555;");

      var page = new VBox(8, titleLabel, grid, footer);
      page.setStyle("-fx-font-size: 10px; -fx-text-fill: black; -fx-background-color: white;");
      page.setPrefWidth(PriceSheet.PAGE_WIDTH);
      page.setMinWidth(PriceSheet.PAGE_WIDTH);
      page.setMaxWidth(PriceSheet.PAGE_WIDTH);
      pages.add(page);
    }
    return pages;
  }

  /**
   * Saves, showing how it went: what was done in green, or what
   * went wrong in red. Nothing is shown if it was cancelled.
   *
   * @param status where to show it
   * @param action saves, returning a message or {@code null} if cancelled
   */
  static void run(Label status, Supplier<String> action) {
    try {
      var message = action.get();
      if (message != null) {
        status.setStyle("-fx-text-fill: green;");
        status.setText(message);
      }
    } catch (IllegalStateException e) {
      status.setStyle("-fx-text-fill: red;");
      status.setText(e.getMessage());
    }
  }

  private static Label cellLabel(String text) {
    var label = new Label(text);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMinWidth(0);
    return label;
  }

  private static List<String> headings(List<? extends TableColumn<?, ?>> columns) {
    var headings = new ArrayList<String>();
    for (var column : columns) {
      headings.add(column.getText());
    }
    return headings;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static List<String> cells(TableView<?> table, int row, boolean forPrinting) {
    var cells = new ArrayList<String>();
    for (TableColumn column : forPrinting ? table.getVisibleLeafColumns() : table.getColumns()) {
      var value = column.getCellData(row);
      var format = (Function<Object, String>) column.getProperties().get(FORMAT);
      String text;
      if (value == null) {
        text = "";
      } else if (format != null) {
        text = format.apply(value);
      } else if (value instanceof LocalDate && forPrinting) {
        text = ((LocalDate) value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
      } else {
        text = Objects.toString(value);
      }
      // Keep each row on one line.
      cells.add(text.replaceAll("\\s*\\R\\s*", forPrinting ? " · " : "; "));
    }
    return cells;
  }

  private static String csvRow(List<String> cells) {
    var row = new StringBuilder();
    for (var i = 0; i < cells.size(); i++) {
      if (i > 0) {
        row.append(',');
      }
      var cell = cells.get(i);
      if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
        row.append('"').append(cell.replace("\"", "\"\"")).append('"');
      } else {
        row.append(cell);
      }
    }
    return row.append("\r\n").toString();
  }

  private TableOutput() {
  }

}
