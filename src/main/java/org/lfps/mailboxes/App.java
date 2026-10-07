package org.lfps.mailboxes;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Database;
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

  /** The main window, so its place can be saved when the app closes. */
  private Stage stage;

  /** Makes the app; JavaFX calls this when it starts. */
  public App() {
  }

  /**
   * Locates the database, initializes its schema, takes the daily backup and
   * copies it to the second backup folder if one is set, and shows the main
   * menu where the window was when the app last closed, then uploads the
   * backup to Google Drive in the background if it's connected. A backup
   * problem doesn't stop the app from opening; it shows a warning instead,
   * and the backup is retried on the next launch.
   *
   * @param stage the primary window supplied by the JavaFX runtime
   */
  @Override
  public void start(Stage stage) {
    AppWindow.init(getHostServices());
    Database.prepareDataDir();
    Database.initSchema();

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

    stage.setTitle("Mailbox Manager");
    WindowPlacement.restore(stage);
    this.stage = stage;
    MainMenuView.show(stage);

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
   * time. JavaFX calls this when the app closes, however it's closed.
   */
  @Override
  public void stop() {
    if (stage != null) {
      WindowPlacement.save(stage);
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
