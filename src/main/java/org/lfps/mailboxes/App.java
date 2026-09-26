package org.lfps.mailboxes;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Screen;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.util.Errors;
import org.lfps.mailboxes.view.AppWindow;
import org.lfps.mailboxes.view.MainMenuView;

/**
 * Entry point for the Mailbox Manager JavaFX application.
 */
public class App extends Application {

  /**
   * Locates the database, initializes its schema, takes the daily backup and
   * copies it to the second backup folder if one is set, and shows the main
   * menu. A backup problem doesn't stop the app from opening; it shows a
   * warning instead, and the backup is retried on the next launch.
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

    // Make the window bigger to match larger text, but no bigger than the screen.
    var screen = Screen.getPrimary().getVisualBounds();
    var scale = AppWindow.textScale();
    stage.setTitle("Mailbox Manager");
    stage.setWidth(Math.min(700 * scale, screen.getWidth()));
    stage.setHeight(Math.min(600 * scale, screen.getHeight()));
    MainMenuView.show(stage);

    if (!backupProblems.isEmpty()) {
      showBackupWarning(stage, backupProblems);
    }
  }

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
