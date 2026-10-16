package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.importing.ImportCheck;
import org.lfps.mailboxes.importing.ImportTemplate;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for the Import Boxes screen: saving the template, reading a
 * file, fixing and skipping rows with problems, and importing.
 */
class ImportBoxesViewTest {

  private static final Function<Window, File> REAL_CHOOSE_FILE = ImportBoxesView.chooseFile;

  private static final Dialogs.Confirm REAL_CONFIRM = Dialogs.confirm;

  private final MailboxRepository mailboxes = new MailboxRepository();

  private final List<String> questions = new ArrayList<>();

  @TempDir
  Path folder;

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void showImport() throws SQLException, IOException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    deleteTemplates();
    mailboxes.insert(new Mailbox(0, "Grace", "Hopper", null, "12", null, "", null, null, null, null));
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      ImportBoxesView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void closeAllWindows() throws IOException {
    ImportBoxesView.chooseFile = REAL_CHOOSE_FILE;
    Dialogs.confirm = REAL_CONFIRM;
    FxTestSupport.run(() -> {
      AppWindow.setLeaveCheck(null);
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    deleteTemplates();
  }

  @Test
  void saveTemplatePutsItInDownloadsWithoutReplacingOneThere() {
    FxTestSupport.run(() -> button("saveTemplateButton").fire());
    FxTestSupport.run(() -> button("saveTemplateButton").fire());

    var downloads = SettingsView.downloadsFolder();
    assertTrue(Files.exists(downloads.resolve(ImportTemplate.FILE_NAME)));
    assertTrue(Files.exists(downloads.resolve("Mailbox Manager import template (2).xlsx")));
    assertTrue(status().startsWith("Saved the template to " + downloads.resolve("Mailbox Manager import template (2)"
        + ".xlsx")), status());
  }

  @Test
  void listsTheRowsWithProblemsAndOnlyImportsOnceTheyreFixedOrSkipped() throws IOException, SQLException {
    choose(csv("Box Number,First Name,Phone\n40,Ada,\n12,Alan,\n41,Bob,555-12\n"));

    assertEquals("boxes.csv: 1 box ready to import, and 2 rows with problems to fix or skip first.", status());
    assertEquals(List.of("3", "4"), problemRows());
    assertTrue(FxTestSupport.call(() -> button("importButton").isDisabled()));

    // Box 12 is rented: skip it. Bob's phone is wrong: fix it.
    selectProblem("3");
    FxTestSupport.run(() -> button("skipRowButton").fire());
    selectProblem("4");
    FxTestSupport.run(() -> button("fixRowButton").fire());
    var fixWindow = fixWindow();
    FxTestSupport.run(() -> {
      ((TextField) fixWindow.getScene().lookup("#fixPHONE")).setText("555-123-4567");
      ((Button) fixWindow.getScene().lookup("#fixSaveButton")).fire();
    });

    assertFalse(FxTestSupport.call(fixWindow::isShowing));
    assertEquals(List.of(), problemRows());
    assertEquals("Import 2 boxes", FxTestSupport.call(() -> button("importButton").getText()));
    FxTestSupport.run(() -> button("importButton").fire());

    assertEquals("Added 2 boxes from boxes.csv. They're on Manage Boxes.", status());
    var added = mailboxes.findAll().stream().map(Mailbox::getBoxNumber).collect(Collectors.toList());
    assertEquals(List.of("12", "40", "41"), added);
    assertEquals("555-123-4567", mailboxes.findAll().get(2).getPhone());
    assertTrue(FxTestSupport.call(() -> button("importButton").isDisabled()));
  }

  @Test
  void aFixThatsStillWrongKeepsTheWindowOpenWithTheNewProblem() throws IOException {
    choose(csv("Box Number,Email\n40,nope\n"));
    selectProblem("2");
    FxTestSupport.run(() -> button("fixRowButton").fire());
    var fixWindow = fixWindow();

    FxTestSupport.run(() -> {
      ((TextField) fixWindow.getScene().lookup("#fixEMAIL")).setText("still nope");
      ((Button) fixWindow.getScene().lookup("#fixSaveButton")).fire();
    });

    assertTrue(FxTestSupport.call(fixWindow::isShowing));
    assertEquals("Email: still nope isn't an email address.",
        FxTestSupport.call(() -> ((Label) fixWindow.getScene().lookup("#fixProblemsLabel")).getText()));
  }

  @Test
  void skipAllAsksFirst() throws IOException {
    choose(csv("Box Number,Phone\n12,\n41,1\n42,\n"));
    answer(false);

    FxTestSupport.run(() -> button("skipAllButton").fire());
    assertEquals(List.of("Skip all 2 rows with problems?"), questions);
    assertEquals(2, problemRows().size());

    answer(true);
    FxTestSupport.run(() -> button("skipAllButton").fire());
    assertEquals(List.of(), problemRows());
    assertEquals("Import 1 box", FxTestSupport.call(() -> button("importButton").getText()));
  }

  @Test
  void saysWhenAFileCantBeRead() throws IOException {
    choose(Files.writeString(folder.resolve("boxes.txt"), "Box Number\n40\n"));

    assertTrue(status().startsWith("Couldn't read boxes.txt. Choose an Excel file (.xlsx) or a CSV file"), status());
    assertTrue(FxTestSupport.call(() -> ((Label) mainWindow.getScene().getRoot().lookup("#importStatusLabel"))
        .getStyle()).contains("red"));
  }

  @Test
  void leavingBeforeImportingAsksFirst() throws IOException {
    choose(csv("Box Number\n40\n"));
    answer(false);

    assertFalse(FxTestSupport.call(AppWindow::mayLeave));
    assertEquals(List.of("Leave without importing?"), questions);
  }

  @Test
  void theFileMenuAndManageBoxesOpenIt() {
    FxTestSupport.run(() -> ManageBoxesView.show(mainWindow));
    FxTestSupport.run(() -> button("importBoxesButton").fire());

    assertTrue(FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookup("#saveTemplateButton") != null));
  }

  /** Writes a CSV file named boxes.csv to import, and returns it. */
  private Path csv(String text) throws IOException {
    return Files.writeString(folder.resolve("boxes.csv"), text, StandardCharsets.UTF_8);
  }

  /** Chooses a file with Choose File. */
  private void choose(Path file) {
    ImportBoxesView.chooseFile = owner -> file.toFile();
    FxTestSupport.run(() -> button("chooseImportFileButton").fire());
  }

  /** Makes every question asked get the same answer, noting each question. */
  private void answer(boolean yes) {
    questions.clear();
    Dialogs.confirm = (owner, question, details, yesButton, noButton) -> {
      questions.add(question);
      return yes;
    };
  }

  /** Returns the row numbers of the rows listed with problems. */
  private List<String> problemRows() {
    return FxTestSupport.call(() -> table().getItems().stream()
        .map(result -> String.valueOf(result.getRow().getRowNumber()))
        .collect(Collectors.toList()));
  }

  /** Selects the row with problems that has a row number. */
  private void selectProblem(String rowNumber) {
    FxTestSupport.run(() -> table().getSelectionModel().select(table().getItems().stream()
        .filter(result -> String.valueOf(result.getRow().getRowNumber()).equals(rowNumber))
        .findFirst().orElseThrow()));
  }

  /** Returns the open Fix Row window. */
  private static Stage fixWindow() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#fixSaveButton") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow());
  }

  /** Returns the list of rows with problems. */
  @SuppressWarnings("unchecked")
  private TableView<ImportCheck.Result> table() {
    return (TableView<ImportCheck.Result>) mainWindow.getScene().getRoot().lookup("#importProblemsTable");
  }

  /** Returns the message under the buttons. */
  private String status() {
    return FxTestSupport.call(() -> ((Label) mainWindow.getScene().getRoot().lookup("#importStatusLabel")).getText());
  }

  /** Returns the button with an id on the main window. */
  private Button button(String id) {
    return (Button) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  /** Deletes the templates saved to the test Downloads folder. */
  private static void deleteTemplates() throws IOException {
    var downloads = SettingsView.downloadsFolder();
    if (!Files.isDirectory(downloads)) {
      return;
    }
    try (var files = Files.list(downloads)) {
      for (var file : files.filter(f -> f.getFileName().toString().startsWith("Mailbox Manager import template"))
          .collect(Collectors.toList())) {
        Files.delete(file);
      }
    }
  }

}
