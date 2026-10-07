package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.drive.DriveBackup;
import org.lfps.mailboxes.drive.FakeGoogle;

/**
 * Tests for connecting and disconnecting Google Drive in the Settings window.
 */
class GoogleDriveSettingsTest {

  private final Consumer<String> realBrowser = GoogleDriveRow.openBrowser;

  private final Dialogs.Confirm realConfirm = Dialogs.confirm;

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
    Database.backupDaily();
    google = FakeGoogle.install();
    GoogleDriveRow.openBrowser = google.browser();
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
    GoogleDriveRow.openBrowser = realBrowser;
    Dialogs.confirm = realConfirm;
    google.close();
    deleteRecursively(Database.dataDir());
  }

  @Test
  void connectingShowsTheAccountAndBacksUpStraightAway() {
    google.email = "post.office@gmail.com";
    var settings = openSettings();
    assertTrue(visible(settings, "connectDriveButton"));
    assertFalse(visible(settings, "disconnectDriveButton"));

    FxTestSupport.run(() -> button(settings, "connectDriveButton").fire());

    waitFor(() -> text(settings, "driveDetailLabel").startsWith("Last backed up today at"));
    assertEquals("Backing up to Google Drive as post.office@gmail.com", text(settings, "driveStatusLabel"));
    assertTrue(visible(settings, "disconnectDriveButton"));
    assertFalse(visible(settings, "connectDriveButton"));
    assertEquals(1, google.uploads);
  }

  @Test
  void cancellingWhileSigningInGoesBackToNotConnected() {
    GoogleDriveRow.openBrowser = url -> { };
    var settings = openSettings();

    FxTestSupport.run(() -> button(settings, "connectDriveButton").fire());
    assertTrue(text(settings, "driveStatusLabel").startsWith("Sign in to Google in the browser"));
    assertTrue(visible(settings, "cancelDriveButton"));

    FxTestSupport.run(() -> button(settings, "cancelDriveButton").fire());

    assertTrue(text(settings, "driveStatusLabel").startsWith("Not connected"));
    assertTrue(visible(settings, "connectDriveButton"));
    assertNull(DriveBackup.account());
  }

  @Test
  void disconnectingAsksFirstThenGoesBackToNotConnected() {
    var settings = openSettings();
    FxTestSupport.run(() -> button(settings, "connectDriveButton").fire());
    waitFor(() -> visible(settings, "disconnectDriveButton"));

    Dialogs.confirm = (owner, question, details, yes, no) -> false;
    FxTestSupport.run(() -> button(settings, "disconnectDriveButton").fire());
    assertTrue(visible(settings, "disconnectDriveButton"));

    Dialogs.confirm = (owner, question, details, yes, no) -> true;
    FxTestSupport.run(() -> button(settings, "disconnectDriveButton").fire());
    assertTrue(visible(settings, "connectDriveButton"));
    assertNull(DriveBackup.account());
  }

  @Test
  void aProblemSigningInIsShown() {
    google.grantDrive = false;
    var settings = openSettings();

    FxTestSupport.run(() -> button(settings, "connectDriveButton").fire());

    waitFor(() -> text(settings, "driveDetailLabel").contains("permission to save files"));
    assertTrue(visible(settings, "connectDriveButton"));
  }

  @Test
  void describesWhenTheLastBackupWas() {
    var nine = LocalTime.of(9, 2);
    assertTrue(GoogleDriveRow.describe(LocalDateTime.of(LocalDate.now(), nine)).startsWith("today at "));
    assertTrue(GoogleDriveRow.describe(LocalDateTime.of(LocalDate.now().minusDays(1), nine)).startsWith("yesterday at "));
    assertTrue(GoogleDriveRow.describe(LocalDateTime.of(2026, 3, 5, 9, 2)).startsWith("on Mar 5 at "));
  }

  /** Opens the Settings window and returns it. */
  private Stage openSettings() {
    return FxTestSupport.call(() -> {
      SettingsView.show(mainWindow);
      return (Stage) Window.getWindows().stream()
          .filter(w -> w instanceof Stage && "Settings".equals(((Stage) w).getTitle()))
          .findFirst()
          .orElseThrow();
    });
  }

  /** Returns the button with the given id on a window. */
  private static Button button(Stage window, String id) {
    return (Button) window.getScene().lookup("#" + id);
  }

  /** Returns whether the part with the given id on a window is shown. */
  private static boolean visible(Stage window, String id) {
    return FxTestSupport.call(() -> window.getScene().lookup("#" + id).isVisible());
  }

  /** Returns the text of the label with the given id on a window. */
  private static String text(Stage window, String id) {
    return FxTestSupport.call(() -> ((Label) window.getScene().lookup("#" + id)).getText());
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
