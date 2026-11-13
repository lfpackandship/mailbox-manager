package org.lfps.mailboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.lfps.mailboxes.view.StartupProblemView;

/**
 * Tests for starting the app: it opens normally after the daily backup, and
 * still opens, with a warning, when a backup can't be made or copied; and
 * when the data can't be opened, offers to restore a backup or quit. Also
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

  private final App.StartupProblem realStartupProblem = App.startupProblem;

  private final Runnable realQuit = App.quit;

  /** Whether a test left the database damaged, so it's removed afterwards for the next test. */
  private boolean damaged;

  @AfterEach
  void cleanUp() throws IOException {
    FxTestSupport.run(() -> {
      started.forEach(App::stop);
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    App.startupProblem = realStartupProblem;
    App.quit = realQuit;
    if (damaged) {
      Files.deleteIfExists(Database.dataDir().resolve("mailboxes.db"));
    }
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
  void damagedDataCanBeReplacedWithABackupAsTheAppStarts() throws IOException, SQLException {
    var backup = backupWithBox("101");
    damageTheData();
    var asked = new ArrayList<RuntimeException>();
    App.startupProblem = (problem, restoreProblem) -> {
      asked.add(restoreProblem);
      return backup;
    };

    var mainWindow = startApp();

    assertTrue(FxTestSupport.call(mainWindow::isShowing));
    assertEquals(java.util.Collections.singletonList(null), asked);
    assertEquals(List.of("101"), boxNumbersIn(Database.dataDir().resolve("mailboxes.db")));
    assertTrue(windowTitles().contains("Backup Restored"), windowTitles().toString());
    assertTrue(Files.readString(org.lfps.mailboxes.data.ErrorLog.file()).contains("Opening the data as the app started"));
  }

  @Test
  void choosingQuitWhenTheDataCantBeOpenedClosesTheApp() throws IOException {
    damageTheData();
    // Left by an earlier test, if any; checked below that none is made.
    Files.deleteIfExists(Database.todaysBackup());
    var quit = new java.util.concurrent.atomic.AtomicBoolean();
    App.startupProblem = (problem, restoreProblem) -> null;
    App.quit = () -> quit.set(true);

    var mainWindow = startApp();

    assertTrue(quit.get());
    assertFalse(FxTestSupport.call(mainWindow::isShowing));
    // Nothing is backed up from data that couldn't be opened, when starting or closing.
    FxTestSupport.run(() -> started.forEach(App::stop));
    assertFalse(Files.exists(Database.todaysBackup()));
  }

  @Test
  void aBackupThatCantBeRestoredAsksAgainAndSaysWhy() throws IOException, SQLException {
    var good = backupWithBox("101");
    damageTheData();
    var notABackup = Files.writeString(Database.dataDir().resolve("elsewhere-notes.db"), "notes");
    var asked = new ArrayList<RuntimeException>();
    App.startupProblem = (problem, restoreProblem) -> {
      asked.add(restoreProblem);
      return asked.size() == 1 ? notABackup : good;
    };

    var mainWindow = startApp();

    assertTrue(FxTestSupport.call(mainWindow::isShowing));
    assertEquals(2, asked.size());
    assertEquals("elsewhere-notes.db isn't a Mailbox Manager backup.", asked.get(1).getMessage());
    var message = StartupProblemView.message(new RuntimeException("file is not a database"), asked.get(1));
    assertTrue(message.startsWith("elsewhere-notes.db isn't a Mailbox Manager backup.\n\n"), message);
    assertTrue(message.contains("(file is not a database)"), message);
    assertTrue(message.endsWith("Your data folder is " + Database.dataDir()), message);
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
    assertTrue(warning.contains("could not be copied to your second backup folder. The second backup folder "
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

  /** Saves a backup holding one box with the given number, and returns it. */
  private static Path backupWithBox(String boxNumber) throws SQLException {
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM mailboxes");
    }
    new MailboxRepository().insert(new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "", null, null, null,
        null));
    return Database.exportBackup(Database.dataDir().resolve("elsewhere"));
  }

  /** Overwrites the database with something that isn't one, as a damaged file would be. */
  private void damageTheData() throws IOException {
    Database.prepareDataDir();
    Files.writeString(Database.dataDir().resolve("mailboxes.db"), "not a database any more");
    damaged = true;
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
