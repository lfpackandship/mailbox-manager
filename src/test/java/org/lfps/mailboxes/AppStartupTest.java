package org.lfps.mailboxes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.view.FxTestSupport;

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

  @AfterEach
  void cleanUp() throws IOException {
    FxTestSupport.run(() -> {
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

  private static Stage startApp() {
    return FxTestSupport.call(() -> {
      var stage = new Stage();
      new App().start(stage);
      return stage;
    });
  }

  private static List<String> windowTitles() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage)
        .map(w -> String.valueOf(((Stage) w).getTitle()))
        .collect(Collectors.toList()));
  }

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
