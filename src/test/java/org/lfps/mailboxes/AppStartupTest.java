package org.lfps.mailboxes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
import org.lfps.mailboxes.view.FxTestSupport;

/**
 * Tests for starting the app: it opens normally after the daily backup, and
 * still opens, with a warning, when a backup can't be made or copied. Also
 * covers backing up when the app closes and when the date changes while it's
 * open.
 */
class AppStartupTest {

  private static final java.nio.file.Path BACKUP_DIR = Database.dataDir().resolve("backups");

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void checkSandbox() {
    TestSandbox.require();
  }

  /** The apps started by a test, stopped after it so their day checks don't run on. */
  private final List<App> started = new ArrayList<>();

  @AfterEach
  void cleanUp() throws IOException {
    FxTestSupport.run(() -> {
      started.forEach(App::stop);
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    // Undo the failures set up below, if any.
    try {
      new SettingsRepository().put(Setting.SECOND_BACKUP_FOLDER, "");
    } catch (SQLException e) {
      // The database may not exist yet.
    }
    if (Files.isRegularFile(BACKUP_DIR)) {
      Files.delete(BACKUP_DIR);
    }
  }

  @Test
  void opensNormallyWithoutWarningWhenBackupSucceeds() {
    var mainWindow = startApp();

    assertTrue(FxTestSupport.call(mainWindow::isShowing));
    assertTrue(windowTitles().stream().noneMatch("Backup Failed"::equals), windowTitles().toString());
  }

  @Test
  void stillOpensAndWarnsWhenBackupFails() throws IOException {
    // A file where the backups folder should be makes the backup fail.
    Database.prepareDataDir();
    deleteRecursively(BACKUP_DIR);
    Files.writeString(BACKUP_DIR, "not a folder");

    var mainWindow = startApp();

    assertTrue(FxTestSupport.call(mainWindow::isShowing));
    assertTrue(windowTitles().contains("Backup Failed"), windowTitles().toString());
  }

  @Test
  void stillOpensAndWarnsWhenTheSecondBackupFolderIsMissing() throws SQLException {
    Database.prepareDataDir();
    Database.initSchema();
    var unplugged = Database.dataDir().resolve("usb-drive-not-plugged-in");
    new SettingsRepository().put(Setting.SECOND_BACKUP_FOLDER, unplugged.toString());

    var mainWindow = startApp();

    assertTrue(FxTestSupport.call(mainWindow::isShowing));
    assertTrue(windowTitles().contains("Backup Failed"), windowTitles().toString());
    var warning = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && "Backup Failed".equals(((Stage) w).getTitle()))
        .map(w -> ((javafx.scene.control.DialogPane) w.getScene().getRoot()).getContentText())
        .findFirst()
        .orElseThrow());
    assertTrue(warning.contains("could not be copied to your second backup folder: The second backup folder "
        + unplugged + " can't be found."), warning);
  }

  @Test
  void closingBringsTodaysBackupUpToDate() throws SQLException {
    startApp();
    // The database isn't emptied between test classes, so use a number no
    // other test does.
    var boxNumber = "added-before-closing";
    assertFalse(boxNumbersIn(Database.todaysBackup()).contains(boxNumber));
    new MailboxRepository().insert(new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "", null, null, null,
        null));

    FxTestSupport.run(() -> started.get(0).stop());

    assertTrue(boxNumbersIn(Database.todaysBackup()).contains(boxNumber));
  }

  @Test
  void backsUpAgainWhenTheDateChangesWhileOpen() throws IOException {
    startApp();
    var app = started.get(0);
    Files.delete(Database.todaysBackup());

    FxTestSupport.run(() -> app.backUpIfNewDay(LocalDate.now()));
    assertFalse(Files.exists(Database.todaysBackup()), "the same day isn't backed up twice");

    FxTestSupport.run(() -> app.backUpIfNewDay(LocalDate.now().plusDays(1)));
    assertTrue(Files.exists(Database.todaysBackup()));
  }

  /** Starts the app in a new window, as when it's opened, and returns the window. */
  private Stage startApp() {
    return FxTestSupport.call(() -> {
      var stage = new Stage();
      var app = new App();
      app.start(stage);
      started.add(app);
      return stage;
    });
  }

  /** Returns the box numbers in a backup file, in order. */
  private static List<String> boxNumbersIn(java.nio.file.Path databaseFile) throws SQLException {
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT box_number FROM mailboxes ORDER BY box_number")) {
      var numbers = new ArrayList<String>();
      while (rs.next()) {
        numbers.add(rs.getString(1));
      }
      return numbers;
    }
  }

  /** Returns the titles of the open windows, including any warnings. */
  private static List<String> windowTitles() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage)
        .map(w -> String.valueOf(((Stage) w).getTitle()))
        .collect(Collectors.toList()));
  }

  /** Deletes a folder and everything in it, if it exists. */
  private static void deleteRecursively(java.nio.file.Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var paths = Files.walk(path)) {
      for (var p : paths.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList())) {
        Files.delete(p);
      }
    }
  }

}
