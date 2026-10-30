package org.lfps.mailboxes;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.drive.DriveBackup;
import org.lfps.mailboxes.drive.DriveException;
import org.lfps.mailboxes.drive.DriveException.Problem;
import org.lfps.mailboxes.util.Errors;
import org.lfps.mailboxes.view.AppWindow;
import org.lfps.mailboxes.view.MainMenuView;
import org.lfps.mailboxes.view.SettingsView;
import org.lfps.mailboxes.view.WindowPlacement;

/**
 * Entry point for the Mailbox Manager JavaFX application.
 */
public class App extends Application {

  /** How often to check whether the date has changed while the app is open. */
  private static final Duration DAY_CHECK_INTERVAL = Duration.minutes(10);

  /** The main window, so its place can be saved when the app closes. */
  private Stage stage;

  /**
   * The day the last daily backup was made for, so the next day's is made
   * if the app is still open then; {@code null} before the first.
   */
  private LocalDate backedUpFor;

  /** Checks every {@link #DAY_CHECK_INTERVAL} for a new day; {@code null} until the app starts. */
  private Timeline dayCheck;

  /** Makes the app; JavaFX calls this when it starts. */
  public App() {
  }

  /**
   * Erases for good the boxes deleted more than 30 days ago, which until then
   * are kept under Recently deleted on Manage Boxes to be restored. Their
   * payments are kept. If it fails, they're simply erased the next time the
   * app starts, so the user isn't troubled with it.
   */
  private static void eraseOldDeletedBoxes() {
    try {
      new MailboxRepository().eraseDeletedBefore(LocalDate.now().minusDays(30));
    } catch (SQLException e) {
      // Tried again at the next start.
    }
  }

  /**
   * Locates the database, initializes its schema, erases boxes deleted more
   * than 30 days ago, shows the main menu where the window was when the app
   * last closed, and makes today's backup (see {@link #backUpToday()}). While
   * the app stays open, each new day's backup is made when the date changes.
   *
   * @param stage the primary window supplied by the JavaFX runtime
   */
  @Override
  public void start(Stage stage) {
    AppWindow.init(getHostServices());
    Database.prepareDataDir();
    Database.initSchema();
    eraseOldDeletedBoxes();

    stage.setTitle("Mailbox Manager");
    WindowPlacement.restore(stage);
    this.stage = stage;
    MainMenuView.show(stage);

    backUpToday();
    // The shop's computer may be left on with the app open for days, so a
    // new day's backup doesn't wait for the app to be opened again.
    dayCheck = new Timeline(new KeyFrame(DAY_CHECK_INTERVAL, e -> backUpIfNewDay(LocalDate.now())));
    dayCheck.setCycleCount(Animation.INDEFINITE);
    dayCheck.play();
  }

  /**
   * Makes the day's backup if it hasn't been made since the app opened or
   * the date last changed.
   *
   * @param today the current day
   */
  void backUpIfNewDay(LocalDate today) {
    if (!today.equals(backedUpFor)) {
      backUpToday();
    }
  }

  /**
   * Takes today's daily backup if it hasn't been taken, copies it to the
   * second backup folder if one is set, then uploads it to Google Drive in
   * the background if it's connected. A problem doesn't stop the app; it
   * shows a warning instead, and the backup is tried again the next time the
   * app starts.
   */
  private void backUpToday() {
    backedUpFor = LocalDate.now();
    var backupProblems = new ArrayList<String>();
    try {
      Database.backupDaily();
    } catch (RuntimeException e) {
      backupProblems.add("Today's backup could not be made: " + Errors.rootMessage(e));
    }
    try {
      Database.copyToSecondBackupFolder();
    } catch (RuntimeException e) {
      backupProblems.add("Today's backup could not be copied to your second backup folder: " + Errors.rootMessage(e));
    }
    if (!backupProblems.isEmpty()) {
      showBackupWarning(stage, backupProblems);
    }

    DriveBackup.backUpTodayInBackground().whenComplete((account, error) -> {
      if (error != null) {
        Platform.runLater(() -> showDriveWarning(stage, DriveBackup.problem(error)));
      }
    });
  }

  /**
   * Saves where the main window is and how big, so it opens the same way next
   * time, and brings today's backup up to date so it has the day's work.
   * JavaFX calls this when the app closes, however it's closed.
   */
  @Override
  public void stop() {
    if (dayCheck != null) {
      dayCheck.stop();
    }
    if (stage != null) {
      WindowPlacement.save(stage);
    }
    try {
      Database.updateTodaysBackup();
    } catch (RuntimeException e) {
      // Not shown, as the app is closing, and it may be closed several
      // times a day. Today's earlier backup is still there, and the next
      // start warns if backups can't be made.
    }
  }

  /**
   * Warns that today's backup couldn't be uploaded to Google Drive, offering to
   * reconnect if Google signed the app out. Nothing is shown if the user
   * cancelled.
   *
   * @param owner the main window
   * @param problem what went wrong
   */
  private static void showDriveWarning(Stage owner, DriveException problem) {
    if (problem.problem() == Problem.CANCELLED) {
      return;
    }
    var alert = new Alert(AlertType.WARNING);
    alert.initOwner(owner);
    alert.setTitle("Google Drive Backup Failed");
    alert.setHeaderText("Today's backup wasn't copied to Google Drive.");
    var safe = "Your backup is still saved on this computer, and your mailbox data is unchanged.";
    if (problem.problem() == Problem.SIGNED_OUT) {
      var reconnect = new ButtonType("Reconnect", ButtonData.OK_DONE);
      alert.getButtonTypes().setAll(reconnect, new ButtonType("Not Now", ButtonData.CANCEL_CLOSE));
      alert.setContentText(problem.getMessage() + " " + safe + "\n\n"
          + "Click Reconnect to sign in to Google again.");
      AppWindow.applyTextSize(alert);
      alert.showAndWait()
          .filter(reconnect::equals)
          .ifPresent(b -> SettingsView.showAndConnectDrive(owner));
      return;
    }
    alert.setContentText(problem.getMessage() + " " + safe + "\n\n"
        + "The app will try again the next time it starts.");
    AppWindow.applyTextSize(alert);
    alert.show();
  }

  /**
   * Warns that today's backup couldn't be saved on this computer, or copied to
   * the second backup folder.
   *
   * @param owner the main window
   * @param problems what went wrong, one message each
   */
  private static void showBackupWarning(Stage owner, List<String> problems) {
    var alert = new Alert(AlertType.WARNING);
    alert.initOwner(owner);
    alert.setTitle("Backup Failed");
    alert.setHeaderText("There was a problem with today's backup.");
    alert.setContentText(String.join("\n\n", problems) + "\n\n"
        + "Your mailbox data is unchanged and the app will work normally. "
        + "It will try again the next time it starts.\n\n"
        + "Backup folder: " + Database.backupDir());
    AppWindow.applyTextSize(alert);
    alert.show();
  }

  /**
   * Launches the JavaFX application.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    launch();
  }

}
