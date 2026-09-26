package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for the preferences and backup tools in the Settings window, and
 * the screens those preferences change.
 */
class SettingsFeaturesTest {

  private static final Function<Window, File> REAL_CHOOSE_FOLDER = SettingsView.chooseFolder;
  private static final Function<Window, File> REAL_CHOOSE_FILE = RestoreView.chooseFile;
  private static final Predicate<String> REAL_CONFIRM = RestoreView.confirm;

  private final SettingsRepository settings = new SettingsRepository();

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

  private Path elsewhere;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void openMainWindow() throws SQLException, IOException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM mailboxes");
    }
    deleteRecursively(Database.backupDir());
    elsewhere = Database.dataDir().resolve("elsewhere");
    deleteRecursively(elsewhere);
    Files.createDirectories(elsewhere);

    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      MainMenuView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void closeAllWindows() throws IOException {
    SettingsView.chooseFolder = REAL_CHOOSE_FOLDER;
    RestoreView.chooseFile = REAL_CHOOSE_FILE;
    RestoreView.confirm = REAL_CONFIRM;
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    deleteRecursively(elsewhere);
  }

  // Preferences

  @Test
  void savesTextSizeWeekStartAndRentalLengths() throws SQLException {
    var window = openSettings();

    FxTestSupport.run(() -> {
      this.<TextSize>choice(window, "textSizeChoice").setValue(TextSize.LARGE);
      this.<DayOfWeek>choice(window, "weekStartChoice").setValue(DayOfWeek.MONDAY);
      field(window, "rentalLengthsField").setText(" 12,1  6 ");
      button(window, "saveButton").fire();
    });

    assertEquals("Saved", resultText(window));
    assertEquals("LARGE", settings.get(Setting.TEXT_SIZE));
    assertEquals("MONDAY", settings.get(Setting.WEEK_START));
    assertEquals("1, 6, 12", settings.get(Setting.RENTAL_LENGTHS));
    assertEquals("1, 6, 12", FxTestSupport.call(() -> field(window, "rentalLengthsField").getText()));
  }

  @Test
  void largerTextAppliesToOpenWindowsRightAway() {
    var window = openSettings();

    FxTestSupport.run(() -> {
      this.<TextSize>choice(window, "textSizeChoice").setValue(TextSize.EXTRA_LARGE);
      button(window, "saveButton").fire();
    });

    var expected = TextSize.EXTRA_LARGE.style();
    assertEquals(expected, FxTestSupport.call(() -> mainWindow.getScene().getRoot().getStyle()));
    assertEquals(expected, FxTestSupport.call(() -> window.getScene().getRoot().getStyle()));
    assertTrue(expected.contains(String.format(Locale.ROOT, "%.1fpx", Font.getDefault().getSize() * 1.5)), expected);
  }

  @Test
  void rejectsInvalidRentalLengthsAndSavesNothing() throws SQLException {
    var window = openSettings();

    FxTestSupport.run(() -> {
      field(window, "rentalLengthsField").setText("1, 3, six");
      field(window, "renewalWindowField").setText("45");
      button(window, "saveButton").fire();
    });

    assertEquals("Rental lengths must be whole numbers of months, like 1, 3, 6, 12.", resultText(window));
    assertEquals("1,3,6,12", settings.get(Setting.RENTAL_LENGTHS));
    assertEquals(30, settings.getInt(Setting.RENEWAL_WINDOW_DAYS));
  }

  @Test
  void addBoxOffersTheConfiguredRentalLengths() throws SQLException {
    settings.put(Setting.RENTAL_LENGTHS, "2, 24");

    FxTestSupport.run(() -> AddBoxView.show(mainWindow));

    assertEquals(List.of("2 Months", "24 Months"), rentalButtonLabels());
    FxTestSupport.run(() -> buttonLabeled("24 Months").fire());
    assertEquals(LocalDate.now().plusMonths(24),
        FxTestSupport.call(() -> ((DatePicker) mainWindow.getScene().lookup(".date-picker")).getValue()));
  }

  @Test
  void calendarStartsTheWeekOnTheConfiguredDay() throws SQLException {
    for (var firstDay : List.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)) {
      settings.put(Setting.WEEK_START, firstDay.name());

      FxTestSupport.run(() -> CalendarView.show(mainWindow));

      assertEquals(firstDay.getDisplayName(TextStyle.SHORT, Locale.getDefault()), firstCalendarHeader());
    }
  }

  // Backups

  @Test
  void backUpNowSavesACopyToTheChosenFolder() throws IOException {
    SettingsView.chooseFolder = owner -> elsewhere.toFile();
    var window = openSettings();

    FxTestSupport.run(() -> button(window, "backUpNowButton").fire());

    var saved = fileNames(elsewhere);
    assertEquals(1, saved.size(), saved.toString());
    assertTrue(saved.get(0).startsWith("mailboxes-backup-"), saved.toString());
    assertEquals("Saved a backup to " + elsewhere.resolve(saved.get(0)), resultText(window));
  }

  @Test
  void backUpNowDoesNothingIfNoFolderIsChosen() throws IOException {
    SettingsView.chooseFolder = owner -> null;
    var window = openSettings();

    FxTestSupport.run(() -> button(window, "backUpNowButton").fire());

    assertTrue(fileNames(elsewhere).isEmpty());
    assertEquals("", resultText(window));
  }

  @Test
  void choosingASecondBackupFolderCopiesTodaysBackupOnSave() throws SQLException, IOException {
    Database.backupDaily();
    SettingsView.chooseFolder = owner -> elsewhere.toFile();
    var window = openSettings();

    FxTestSupport.run(() -> button(window, "chooseSecondFolderButton").fire());
    assertEquals(elsewhere.toString(), FxTestSupport.call(() -> field(window, "secondBackupFolderField").getText()));
    FxTestSupport.run(() -> button(window, "saveButton").fire());

    assertEquals("Saved", resultText(window));
    assertEquals(elsewhere.toString(), settings.get(Setting.SECOND_BACKUP_FOLDER));
    assertEquals(List.of("mailboxes-" + LocalDate.now() + ".db"), fileNames(elsewhere));
  }

  @Test
  void savingAMissingSecondFolderWarnsButKeepsTheSetting() throws SQLException {
    Database.backupDaily();
    var unplugged = elsewhere.resolve("usb-drive");
    var window = openSettings();

    FxTestSupport.run(() -> {
      field(window, "secondBackupFolderField").setText(unplugged.toString());
      button(window, "saveButton").fire();
    });

    var message = resultText(window);
    assertTrue(message.startsWith("Saved, but today's backup couldn't be copied: The second backup folder "
        + unplugged + " can't be found."), message);
    assertEquals(unplugged.toString(), settings.get(Setting.SECOND_BACKUP_FOLDER));
  }

  @Test
  void dontCopyTurnsOffTheSecondBackupFolder() throws SQLException {
    settings.put(Setting.SECOND_BACKUP_FOLDER, elsewhere.toString());
    var window = openSettings();

    FxTestSupport.run(() -> {
      button(window, "clearSecondFolderButton").fire();
      button(window, "saveButton").fire();
    });

    assertEquals("", settings.get(Setting.SECOND_BACKUP_FOLDER));
  }

  @Test
  void restoringABackupReplacesTheDataAndClosesSettings() throws SQLException {
    mailboxes.insert(box("101"));
    Database.backupDaily();
    mailboxes.insert(box("102"));
    var questions = new java.util.ArrayList<String>();
    RestoreView.confirm = question -> questions.add(question);
    var restoreWindow = openRestore();

    FxTestSupport.run(() -> {
      var list = backupList(restoreWindow);
      assertEquals(1, list.getItems().size());
      list.getSelectionModel().select(0);
      button(restoreWindow, "restoreSelectedButton").fire();
    });

    assertEquals(List.of("101"), boxNumbers());
    assertEquals(1, questions.size());
    assertTrue(questions.get(0).startsWith("Replace all current data with the daily backup from "), questions.get(0));
    assertNull(windowTitled("Settings"));
    assertNull(windowTitled("Restore Backup"));
    assertNotNull(windowTitled("Backup Restored"));
    assertTrue(FxTestSupport.call(mainWindow::isShowing));
  }

  @Test
  void declinedRestoreChangesNothing() throws SQLException {
    mailboxes.insert(box("101"));
    Database.backupDaily();
    mailboxes.insert(box("102"));
    RestoreView.confirm = question -> false;
    var restoreWindow = openRestore();

    FxTestSupport.run(() -> {
      backupList(restoreWindow).getSelectionModel().select(0);
      button(restoreWindow, "restoreSelectedButton").fire();
    });

    assertEquals(List.of("101", "102"), boxNumbers());
    assertTrue(FxTestSupport.call(restoreWindow::isShowing));
  }

  @Test
  void restoringAFileThatIsntABackupShowsWhy() throws SQLException, IOException {
    mailboxes.insert(box("101"));
    var notes = Files.writeString(elsewhere.resolve("notes.db"), "shopping list");
    RestoreView.chooseFile = owner -> notes.toFile();
    RestoreView.confirm = question -> true;
    var restoreWindow = openRestore();

    FxTestSupport.run(() -> button(restoreWindow, "restoreFromFileButton").fire());

    assertEquals("notes.db isn't a Mailbox Manager backup.",
        FxTestSupport.call(() -> ((Label) restoreWindow.getScene().lookup("#restoreErrorLabel")).getText()));
    assertEquals(List.of("101"), boxNumbers());
  }

  @Test
  void restoreButtonIsDisabledUntilABackupIsSelected() {
    Database.backupDaily();
    var restoreWindow = openRestore();

    assertTrue(FxTestSupport.call(() -> button(restoreWindow, "restoreSelectedButton").isDisabled()));
    FxTestSupport.run(() -> backupList(restoreWindow).getSelectionModel().select(0));
    assertFalse(FxTestSupport.call(() -> button(restoreWindow, "restoreSelectedButton").isDisabled()));
  }

  // Helpers

  private Stage openSettings() {
    FxTestSupport.run(() -> SettingsView.show(mainWindow));
    return windowTitled("Settings");
  }

  private Stage openRestore() {
    var settingsWindow = openSettings();
    FxTestSupport.run(() -> button(settingsWindow, "restoreButton").fire());
    return windowTitled("Restore Backup");
  }

  private static Stage windowTitled(String title) {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && title.equals(((Stage) w).getTitle()))
        .map(w -> (Stage) w)
        .findFirst()
        .orElse(null));
  }

  private String resultText(Stage window) {
    return FxTestSupport.call(() -> ((Label) window.getScene().lookup("#resultLabel")).getText());
  }

  private List<String> rentalButtonLabels() {
    return FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> ((Button) node).getText())
        .filter(text -> text.endsWith("Month") || text.endsWith("Months"))
        .collect(Collectors.toList()));
  }

  private Button buttonLabeled(String text) {
    return mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> text.equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

  private String firstCalendarHeader() {
    return FxTestSupport.call(() -> {
      var grid = (GridPane) mainWindow.getScene().getRoot().lookupAll("*").stream()
          .filter(node -> node instanceof GridPane)
          .findFirst()
          .orElseThrow();
      for (Node child : grid.getChildren()) {
        if (Integer.valueOf(0).equals(GridPane.getRowIndex(child))
            && Integer.valueOf(0).equals(GridPane.getColumnIndex(child))) {
          return ((Label) child).getText();
        }
      }
      throw new AssertionError("No header in the first column");
    });
  }

  private List<String> boxNumbers() throws SQLException {
    return mailboxes.findAll().stream().map(Mailbox::getBoxNumber).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private <T> ChoiceBox<T> choice(Stage window, String id) {
    return (ChoiceBox<T>) window.getScene().lookup("#" + id);
  }

  @SuppressWarnings("unchecked")
  private static ListView<Path> backupList(Stage window) {
    return (ListView<Path>) window.getScene().lookup("#backupList");
  }

  private static TextField field(Stage window, String id) {
    return (TextField) window.getScene().lookup("#" + id);
  }

  private static Button button(Stage window, String id) {
    return (Button) window.getScene().lookup("#" + id);
  }

  private static Mailbox box(String boxNumber) {
    return new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "(555) 123-4567", null, null, null);
  }

  private static List<String> fileNames(Path folder) throws IOException {
    try (Stream<Path> files = Files.list(folder)) {
      return files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
    }
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      for (var path : paths.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList())) {
        Files.delete(path);
      }
    }
  }

}
