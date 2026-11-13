package org.lfps.mailboxes.view;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.util.Errors;

/**
 * The message shown when the app can't open its data as it starts, as when
 * the disk is full or the database file is damaged, in place of the main
 * window. It says where the data folder is, and offers to restore a backup
 * so the shop can carry on without help. See {@code App.start}.
 *
 * <p>It doesn't use the Text size setting, since that's kept in the data
 * that couldn't be opened.
 */
public final class StartupProblemView {

  /**
   * Shows the message, and asks for a backup to restore until one is chosen
   * or the user quits. Waits for the answer.
   *
   * @param problem why the data couldn't be opened
   * @param restoreProblem why the backup chosen last time couldn't be
   *     restored, or {@code null} if this is the first time asking
   * @return the backup to restore, or {@code null} if the user chose to quit
   */
  public static Path chooseBackup(RuntimeException problem, RuntimeException restoreProblem) {
    var restore = new ButtonType("Restore a Backup…", ButtonData.OK_DONE);
    var quit = new ButtonType("Quit", ButtonData.CANCEL_CLOSE);
    var alert = new Alert(AlertType.ERROR, message(problem, restoreProblem), restore, quit);
    alert.setTitle("Mailbox Manager Can't Open");
    alert.setHeaderText("Mailbox Manager couldn't open your data.");
    alert.setResizable(true);
    while (alert.showAndWait().filter(restore::equals).isPresent()) {
      var chooser = new FileChooser();
      chooser.setTitle("Choose a Backup to Restore");
      chooser.getExtensionFilters().add(new ExtensionFilter("Mailbox Manager backups", "*.db"));
      if (Files.isDirectory(Database.backupDir())) {
        chooser.setInitialDirectory(Database.backupDir().toFile());
      }
      var chosen = chooser.showOpenDialog(alert.getOwner());
      if (chosen != null) {
        return chosen.toPath();
      }
      // Cancelled choosing a file: ask again.
    }
    return null;
  }

  /**
   * Writes out the message.
   *
   * @param problem why the data couldn't be opened
   * @param restoreProblem why the last backup chosen couldn't be restored,
   *     or {@code null}
   * @return the message, in paragraphs
   */
  public static String message(RuntimeException problem, RuntimeException restoreProblem) {
    var message = new StringBuilder();
    if (restoreProblem instanceof IllegalArgumentException) {
      // Already says what's wrong with the file, such as that it's damaged.
      message.append(restoreProblem.getMessage()).append("\n\n");
    } else if (restoreProblem != null) {
      message.append("That backup couldn't be restored.").append(Errors.detail(restoreProblem)).append("\n\n");
    }
    message.append("This can happen when the disk is full, or when the data file is damaged.")
        .append(Errors.detail(problem)).append("\n\n")
        .append("If the disk is full, make some space, then open the app again. If the file is damaged, "
            + "restore a backup, such as the newest one in the backups folder. The damaged file is kept "
            + "there too.\n\n")
        .append("Your data folder is ").append(Database.dataDir());
    return message.toString();
  }

  /** Not used: the message is shown with static methods. */
  private StartupProblemView() {
  }

}
