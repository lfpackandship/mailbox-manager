package org.lfps.mailboxes;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.view.MainMenuView;

/**
 * Entry point for the Mailbox Manager JavaFX application.
 */
public class App extends Application {

  /**
   * Locates the database, initializes its schema, takes the daily backup,
   * and shows the main menu. A failed backup doesn't stop the app from
   * opening; it shows a warning instead and is retried on the next launch.
   *
   * @param stage the primary window supplied by the JavaFX runtime
   */
  @Override
  public void start(Stage stage) {
    Database.prepareDataDir();
    Database.initSchema();

    RuntimeException backupError = null;
    try {
      Database.backupDaily();
    } catch (RuntimeException e) {
      backupError = e;
    }

    stage.setTitle("Mailbox Manager");
    stage.setWidth(700);
    stage.setHeight(600);
    MainMenuView.show(stage);

    if (backupError != null) {
      showBackupWarning(stage, backupError);
    }
  }

  private static void showBackupWarning(Stage owner, RuntimeException error) {
    var reason = error.getCause() != null && error.getCause().getMessage() != null
        ? error.getCause().getMessage()
        : error.getMessage();

    var alert = new Alert(AlertType.WARNING);
    alert.initOwner(owner);
    alert.setTitle("Backup Failed");
    alert.setHeaderText("Today's backup could not be made.");
    alert.setContentText("Your mailbox data is unchanged and the app will work normally. "
        + "It will try the backup again the next time it starts.\n\n"
        + "Backup folder: " + Database.dataDir().resolve("backups") + "\n"
        + "Reason: " + reason);
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
