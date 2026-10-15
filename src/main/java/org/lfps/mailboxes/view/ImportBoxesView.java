package org.lfps.mailboxes.view;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.importing.ImportCheck;
import org.lfps.mailboxes.importing.ImportColumn;
import org.lfps.mailboxes.importing.ImportReader;
import org.lfps.mailboxes.importing.ImportRow;
import org.lfps.mailboxes.importing.ImportTemplate;
import org.lfps.mailboxes.util.Errors;

/**
 * The Import Boxes screen, opened from File → Import Boxes or Import… on
 * Manage Boxes, for adding many boxes at once from a spreadsheet. Save
 * Template puts the template in Downloads; once it's filled in, Choose File
 * reads it and checks every row. Rows with problems are listed, to fix one
 * at a time with Fix, or skip with Skip or Skip All; once none are left,
 * Import adds the rest, all at once.
 */
final class ImportBoxesView {

  /**
   * Asks the user to pick the filled-in spreadsheet. Replaceable so tests can
   * answer without a real file dialog.
   */
  static Function<Window, File> chooseFile = owner -> {
    var chooser = new FileChooser();
    chooser.setTitle("Choose the Spreadsheet to Import");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Spreadsheets (.xlsx, .csv)", "*.xlsx",
        "*.csv"));
    var downloads = SettingsView.downloadsFolder().toFile();
    if (downloads.isDirectory()) {
      chooser.setInitialDirectory(downloads);
    }
    return chooser.showOpenDialog(owner);
  };

  /** The main window the screen is shown in. */
  private final Stage stage;

  /** Says what happened, such as how many rows were read; red for a problem. */
  private final Label statusLabel = new Label();

  /** The rows still to import, as read or fixed, in row order; skipped rows are taken out. */
  private final List<ImportRow> rows = new ArrayList<>();

  /** The rows with problems, as last checked, listed in the table. */
  private final ObservableList<ImportCheck.Result> problems = FXCollections.observableArrayList();

  /** How many rows are ready to import, as last checked. */
  private int ready;

  /** The name of the file being imported, or {@code null} before one is chosen. */
  private String fileName;

  /** The Import button, which says how many boxes it adds. */
  private final Button importBtn = new Button("Import");

  /**
   * Makes the screen for a window.
   *
   * @param stage the main window
   */
  private ImportBoxesView(Stage stage) {
    this.stage = stage;
  }

  /**
   * Shows the Import Boxes screen in the main window, with no file chosen.
   *
   * @param stage the main window
   */
  static void show(Stage stage) {
    new ImportBoxesView(stage).build();
  }

  /** Builds the screen and shows it. */
  private void build() {
    statusLabel.setId("importStatusLabel");
    statusLabel.setWrapText(true);

    var steps = new Label("Add many boxes at once from a spreadsheet:\n"
        + "1. Click Save Template to put a template in your Downloads folder.\n"
        + "2. Open it in Excel, Google Sheets, or Numbers and fill it in, one box per row. Only the box number is "
        + "required. Save it as an Excel workbook (.xlsx) or a CSV file.\n"
        + "3. Click Choose File and pick it. Rows with problems are listed below to fix or skip, then Import "
        + "adds the boxes.");
    steps.setWrapText(true);

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> {
      if (AppWindow.mayLeave()) {
        MainMenuView.show(stage);
      }
    });

    var templateBtn = new Button("Save Template");
    templateBtn.setId("saveTemplateButton");
    templateBtn.setOnAction(e -> saveTemplate());

    var chooseBtn = new Button("Choose File…");
    chooseBtn.setId("chooseImportFileButton");
    chooseBtn.setOnAction(e -> chooseAndRead());

    var table = new TableView<ImportCheck.Result>(problems);
    table.setId("importProblemsTable");
    table.setPlaceholder(new Label("No rows with problems"));
    table.setStyle("-fx-pref-height: 12em;");
    VBox.setVgrow(table, Priority.ALWAYS);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    var rowCol = new TableColumn<ImportCheck.Result, String>("Row");
    rowCol.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getRow()
        .getRowNumber())));
    rowCol.setStyle("-fx-alignment: CENTER-RIGHT;");
    // Narrow columns for the row, box, and name, so the problems, last,
    // get the rest of the width.
    rowCol.setMaxWidth(70);
    var boxCol = new TableColumn<ImportCheck.Result, String>("Box Number");
    boxCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRow()
        .get(ImportColumn.BOX_NUMBER)));
    boxCol.setMaxWidth(130);
    var nameCol = new TableColumn<ImportCheck.Result, String>("Name");
    nameCol.setCellValueFactory(cell -> new SimpleStringProperty(name(cell.getValue().getRow())));
    nameCol.setMaxWidth(220);
    var problemsCol = new TableColumn<ImportCheck.Result, String>("Problems");
    problemsCol.setCellValueFactory(cell -> new SimpleStringProperty(
        String.join(" ", cell.getValue().getProblems())));
    // Several problems can be too long for the column; pointing at them shows them all.
    problemsCol.setCellFactory(column -> new TableCell<>() {
      @Override
      protected void updateItem(String text, boolean empty) {
        super.updateItem(text, empty);
        setText(empty ? null : text);
        setTooltip(empty || text == null ? null : new Tooltip(text.replace(". ", ".\n")));
      }
    });
    table.getColumns().setAll(List.of(rowCol, boxCol, nameCol, problemsCol));

    var selection = table.getSelectionModel().selectedItemProperty();
    table.setRowFactory(tableView -> {
      var row = new TableRow<ImportCheck.Result>();
      row.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
          fix(row.getItem());
        }
      });
      return row;
    });

    var fixBtn = new Button("Fix…");
    fixBtn.setId("fixRowButton");
    fixBtn.disableProperty().bind(selection.isNull());
    fixBtn.setOnAction(e -> fix(table.getSelectionModel().getSelectedItem()));

    var skipBtn = new Button("Skip");
    skipBtn.setId("skipRowButton");
    skipBtn.disableProperty().bind(selection.isNull());
    skipBtn.setOnAction(e -> {
      rows.remove(table.getSelectionModel().getSelectedItem().getRow());
      recheck();
    });

    var skipAllBtn = new Button("Skip All");
    skipAllBtn.setId("skipAllButton");
    skipAllBtn.disableProperty().bind(Bindings.isEmpty(problems));
    skipAllBtn.setOnAction(e -> skipAll());

    importBtn.setId("importButton");
    importBtn.setDefaultButton(true);
    importBtn.setOnAction(e -> importBoxes());

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    var layout = new VBox(10,
        backBtn,
        steps,
        new HBox(10, templateBtn, chooseBtn),
        statusLabel,
        table,
        new HBox(10, fixBtn, skipBtn, skipAllBtn, spacer, importBtn));
    layout.setPadding(new Insets(20));

    updateImportButton();
    AppWindow.show(stage, layout);
    // Fixing rows is work; leaving before importing asks first.
    AppWindow.setLeaveCheck(() -> rows.isEmpty() || Dialogs.confirm.ask(stage, "Leave without importing?",
        "The boxes read from " + fileName + " haven't been added, and any fixes made here will be lost.",
        "Leave Without Importing", "Stay"));
  }

  /**
   * Saves the template in Downloads, under a new name if one's there
   * already, so a template being filled in is never replaced.
   */
  private void saveTemplate() {
    var downloads = SettingsView.downloadsFolder();
    try {
      Files.createDirectories(downloads);
      var file = freeName(downloads, ImportTemplate.FILE_NAME);
      ImportTemplate.write(file);
      showStatus("Saved the template to " + file + ". Open it in Excel, Google Sheets, or Numbers to fill it in.",
          false);
    } catch (IOException e) {
      showStatus("Couldn't save the template: " + Errors.rootMessage(e), true);
    }
  }

  /**
   * Returns a file name in a folder that isn't taken: the name itself, or
   * with a number added, such as "template (2).xlsx".
   *
   * @param folder the folder
   * @param name the name wanted
   * @return the file to write
   */
  static Path freeName(Path folder, String name) {
    var dot = name.lastIndexOf('.');
    var file = folder.resolve(name);
    for (var n = 2; Files.exists(file); n++) {
      file = folder.resolve(name.substring(0, dot) + " (" + n + ")" + name.substring(dot));
    }
    return file;
  }

  /** Asks for the filled-in spreadsheet, then reads and checks it. */
  private void chooseAndRead() {
    if (!rows.isEmpty() && !Dialogs.confirm.ask(stage, "Choose another file?",
        "The boxes read from " + fileName + " haven't been added, and any fixes made here will be lost.",
        "Choose Another File", "Cancel")) {
      return;
    }
    var file = chooseFile.apply(stage);
    if (file == null) {
      return;
    }
    List<ImportRow> read;
    try {
      read = ImportReader.read(file.toPath());
    } catch (IllegalArgumentException | IOException e) {
      clear();
      showStatus("Couldn't read " + file.getName() + ". " + Errors.rootMessage(e), true);
      return;
    }
    rows.clear();
    rows.addAll(read);
    fileName = file.getName();
    if (rows.isEmpty()) {
      clear();
      showStatus(file.getName() + " has no boxes in it. Fill in a row for each box, under the headings.", true);
      return;
    }
    recheck();
  }

  /** Forgets the file read, as after importing it or failing to read another. */
  private void clear() {
    rows.clear();
    problems.clear();
    ready = 0;
    updateImportButton();
  }

  /**
   * Checks every row again against the boxes recorded now, and updates the
   * list of problems, the message, and the Import button.
   */
  private void recheck() {
    List<ImportCheck.Result> results;
    try {
      results = ImportCheck.againstDatabase().check(rows);
    } catch (SQLException e) {
      showStatus("Couldn't check the boxes against those recorded: " + e.getMessage(), true);
      return;
    }
    problems.setAll(results.stream().filter(result -> !result.isOk()).collect(Collectors.toList()));
    ready = results.size() - problems.size();
    updateImportButton();
    showStatus(fileName + ": " + boxes(ready) + " ready to import"
        + (problems.isEmpty() ? "." : ", and " + rowsWord(problems.size()) + " with problems to fix or skip "
            + "first."), false);
  }

  /**
   * Opens a window for fixing a row: a field for each column, with the
   * problems above them. Saving checks the row again, and closes the window
   * once it has no problems.
   *
   * @param result the row and its problems
   */
  private void fix(ImportCheck.Result result) {
    var window = new Stage();
    var problemsLabel = new Label(String.join("\n", result.getProblems()));
    problemsLabel.setId("fixProblemsLabel");
    problemsLabel.setStyle("-fx-text-fill: red;");
    problemsLabel.setWrapText(true);

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(6);
    var fields = new ArrayList<TextField>();
    var columns = ImportColumn.values();
    var outlined = new FieldProblems();
    for (var i = 0; i < columns.length; i++) {
      var field = new TextField(result.getRow().get(columns[i]));
      field.setId("fix" + columns[i].name());
      field.setStyle("-fx-pref-width: 18em;");
      fields.add(field);
      // Two columns of fields, so the window isn't too tall.
      grid.add(new Label(columns[i].getHeading() + ":"), (i % 2) * 2, i / 2);
      grid.add(field, (i % 2) * 2 + 1, i / 2);
    }

    var saveBtn = new Button("Save");
    saveBtn.setId("fixSaveButton");
    saveBtn.setDefaultButton(true);
    saveBtn.setOnAction(e -> {
      var fixed = result.getRow();
      for (var i = 0; i < columns.length; i++) {
        fixed = fixed.with(columns[i], fields.get(i).getText());
      }
      rows.set(rows.indexOf(result.getRow()), fixed);
      recheck();
      var stillWrong = problemsOf(fixed);
      if (stillWrong.isEmpty()) {
        window.close();
      } else {
        problemsLabel.setText(String.join("\n", stillWrong));
        outline(stillWrong, fields, outlined);
      }
    });
    var cancelBtn = new Button("Cancel");
    cancelBtn.setCancelButton(true);
    cancelBtn.setOnAction(e -> window.close());

    var content = new VBox(12, problemsLabel, grid, new HBox(10, saveBtn, cancelBtn));
    content.setPadding(new Insets(20));
    AppWindow.applyTextSize(content);

    window.initOwner(stage);
    window.initModality(Modality.WINDOW_MODAL);
    window.setTitle("Fix Row " + result.getRow().getRowNumber());
    window.setScene(new Scene(content));
    AppWindow.showWithinScreen(window);
    // Once shown, so the cursor can go to the first field with a problem.
    outline(result.getProblems(), fields, outlined);
  }

  /**
   * Outlines in red the fields a row's problems are about, found by the
   * column each problem starts with, such as "Phone:", and puts the cursor
   * in the first. A forwarding address problem outlines its street, city,
   * state, and ZIP.
   *
   * @param rowProblems the row's problems
   * @param fields the Fix window's fields, one per {@link ImportColumn}, in order
   * @param outlined the fields outlined so far, which are cleared first
   */
  private static void outline(List<String> rowProblems, List<TextField> fields, FieldProblems outlined) {
    outlined.clear();
    for (var problem : rowProblems) {
      var heading = problem.substring(0, Math.max(0, problem.indexOf(':')));
      var column = ImportColumn.withHeading(heading);
      if (column != null) {
        outlined.mark(fields.get(column.ordinal()));
      } else if (heading.equals("Forwarding address")) {
        for (var part : List.of(ImportColumn.STREET, ImportColumn.CITY, ImportColumn.STATE, ImportColumn.ZIP)) {
          outlined.mark(fields.get(part.ordinal()));
        }
      }
    }
    outlined.focusFirst();
  }

  /**
   * Returns the problems the last check found with a row.
   *
   * @param row the row
   * @return its problems, or none if it has none
   */
  private List<String> problemsOf(ImportRow row) {
    return problems.stream().filter(result -> result.getRow() == row).findFirst()
        .map(ImportCheck.Result::getProblems).orElse(List.of());
  }

  /** Skips every row with problems, after asking. */
  private void skipAll() {
    var count = problems.size();
    if (!Dialogs.confirm.ask(stage, "Skip all " + rowsWord(count) + " with problems?",
        "They won't be imported. To add them later, fix them in the spreadsheet and import it again; boxes "
            + "already added are caught then.", "Skip " + (count == 1 ? "It" : "Them"), "Cancel")) {
      return;
    }
    for (var result : List.copyOf(problems)) {
      rows.remove(result.getRow());
    }
    recheck();
  }

  /**
   * Adds the boxes, all at once, once no row has problems. They're checked
   * once more first, in case boxes were added elsewhere in the meantime.
   */
  private void importBoxes() {
    recheck();
    if (!problems.isEmpty() || ready == 0) {
      return;
    }
    try {
      var boxes = ImportCheck.boxes(ImportCheck.againstDatabase().check(rows));
      new MailboxRepository().insertAll(boxes);
      var added = boxes.size();
      clear();
      showStatus("Added " + boxes(added) + " from " + fileName + ". They're on Manage Boxes.", false);
    } catch (SQLException e) {
      showStatus("Couldn't add the boxes, so none were added: " + e.getMessage(), true);
    }
  }

  /** Says on the Import button how many boxes it adds, and allows it only once no row has problems. */
  private void updateImportButton() {
    importBtn.setText(ready == 0 ? "Import" : "Import " + boxes(ready));
    importBtn.setDisable(ready == 0 || !problems.isEmpty());
  }

  /**
   * Shows a message under the buttons.
   *
   * @param message the message
   * @param isProblem whether it's a problem, shown in red
   */
  private void showStatus(String message, boolean isProblem) {
    statusLabel.setStyle(isProblem ? "-fx-text-fill: red;" : "");
    statusLabel.setText(message);
  }

  /**
   * Returns a row's holder's name, or business if it has no name, for the
   * list of rows with problems.
   *
   * @param row the row
   * @return such as "John Doe", or an empty string
   */
  private static String name(ImportRow row) {
    var name = (row.get(ImportColumn.FIRST_NAME) + " " + row.get(ImportColumn.LAST_NAME)).strip();
    return name.isEmpty() ? row.get(ImportColumn.BUSINESS_TITLE) : name;
  }

  /**
   * Counts boxes in words.
   *
   * @param count how many
   * @return such as "1 box" or "37 boxes"
   */
  private static String boxes(int count) {
    return count + (count == 1 ? " box" : " boxes");
  }

  /**
   * Counts rows in words.
   *
   * @param count how many
   * @return such as "1 row" or "3 rows"
   */
  private static String rowsWord(int count) {
    return count + (count == 1 ? " row" : " rows");
  }

}
