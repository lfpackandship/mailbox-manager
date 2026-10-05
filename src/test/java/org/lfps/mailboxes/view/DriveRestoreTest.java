package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.drive.DriveBackup;
import org.lfps.mailboxes.drive.FakeGoogle;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for restoring a backup from Google Drive, against a {@link FakeGoogle}.
 */
class DriveRestoreTest {

  private final Predicate<String> realConfirm = RestoreView.confirm;

  private FakeGoogle google;

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void setUp() throws IOException {
    TestSandbox.require();
    deleteRecursively(Database.dataDir());
    Database.prepareDataDir();
    Database.initSchema();
    google = FakeGoogle.install();
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      MainMenuView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void tearDown() throws IOException {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    RestoreView.confirm = realConfirm;
    google.close();
    deleteRecursively(Database.dataDir());
  }

  @Test
  void theGoogleDriveButtonOnlyShowsWhenConnected() throws Exception {
    assertFalse(FxTestSupport.call(() -> openRestore().getScene().lookup("#restoreFromDriveButton").isVisible()));
    FxTestSupport.run(() -> List.copyOf(Window.getWindows()).forEach(w -> {
      if (w != mainWindow) {
        w.hide();
      }
    }));

    connect();

    assertTrue(FxTestSupport.call(() -> openRestore().getScene().lookup("#restoreFromDriveButton").isVisible()));
  }

  @Test
  void restoresABackupDownloadedFromGoogleDrive() throws Exception {
    var repository = new MailboxRepository();
    repository.insert(new Mailbox(0, "Ada", "Lovelace", null, "77", null, "", null, null, null, null));
    var exported = Database.exportBackup(Files.createTempDirectory(Database.dataDir(), "export"));
    google.addBackup("mailboxes-2026-09-01.db", Files.readAllBytes(exported));
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM mailboxes");
    }
    connect();
    RestoreView.confirm = message -> {
      assertTrue(message.contains("daily backup from tuesday, september 1, 2026"), message);
      return true;
    };

    var drive = openDriveRestore();
    waitFor(() -> FxTestSupport.call(() -> !list(drive).getItems().isEmpty()));
    assertEquals("Daily backup from Tuesday, September 1, 2026",
        FxTestSupport.call(() -> list(drive).getItems().stream().map(name -> RestoreView.describe(Path.of(name)))
            .findFirst().orElseThrow()));

    FxTestSupport.run(() -> {
      list(drive).getSelectionModel().select(0);
      ((Button) drive.getScene().lookup("#driveRestoreButton")).fire();
    });

    waitFor(() -> boxNumbers().equals(List.of("77")));
    waitFor(() -> !FxTestSupport.call(drive::isShowing));
    assertEquals(1, google.downloads);
  }

  @Test
  void sayWhenThereAreNoBackupsInGoogleDrive() throws Exception {
    connect();

    var drive = openDriveRestore();

    waitFor(() -> status(drive).equals("There are no backups in Google Drive yet."));
  }

  @Test
  void aProblemReachingGoogleIsShown() throws Exception {
    connect();
    google.goOffline();

    var drive = openDriveRestore();

    waitFor(() -> status(drive).contains("internet"));
  }

  /** Connects Google Drive to the fake Google, waiting until it's done. */
  private void connect() throws Exception {
    DriveBackup.connect(google.browser()).get(10, TimeUnit.SECONDS);
  }

  /** Opens Settings and then the Restore Backup window, and returns it. */
  private Stage openRestore() {
    SettingsView.show(mainWindow);
    var settings = (Stage) Window.getWindows().stream()
        .filter(w -> w instanceof Stage && "Settings".equals(((Stage) w).getTitle()))
        .findFirst()
        .orElseThrow();
    ((Button) settings.getScene().lookup("#restoreButton")).fire();
    return (Stage) Window.getWindows().stream()
        .filter(w -> w instanceof Stage && "Restore Backup".equals(((Stage) w).getTitle()))
        .findFirst()
        .orElseThrow();
  }

  /** Opens the Restore from Google Drive window, and returns it. */
  private Stage openDriveRestore() {
    return FxTestSupport.call(() -> {
      ((Button) openRestore().getScene().lookup("#restoreFromDriveButton")).fire();
      return (Stage) Window.getWindows().stream()
          .filter(w -> w instanceof Stage && "Restore from Google Drive".equals(((Stage) w).getTitle()))
          .findFirst()
          .orElseThrow();
    });
  }

  /** Returns the list of backups in Google Drive. */
  @SuppressWarnings("unchecked")
  private static ListView<String> list(Stage window) {
    return (ListView<String>) window.getScene().lookup("#driveBackupList");
  }

  /** Returns the message on the Restore from Google Drive window. */
  private static String status(Stage window) {
    return FxTestSupport.call(() -> ((Label) window.getScene().lookup("#driveRestoreStatusLabel")).getText());
  }

  /** Returns the box numbers in the database. */
  private static List<String> boxNumbers() {
    try {
      return new MailboxRepository().findAll().stream().map(Mailbox::getBoxNumber)
          .collect(java.util.stream.Collectors.toList());
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }

  /** Waits up to ten seconds for something to become true, such as a background upload finishing. */
  private static void waitFor(Supplier<Boolean> condition) {
    var deadline = System.currentTimeMillis() + 10_000;
    while (!condition.get()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Timed out waiting");
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    }
  }

  /** Deletes a folder and everything in it, if it exists. */
  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      for (var path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
        Files.delete(path);
      }
    }
  }

}
