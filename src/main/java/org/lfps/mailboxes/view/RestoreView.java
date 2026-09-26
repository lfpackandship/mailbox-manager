package org.lfps.mailboxes.view;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.util.Errors;

/**
 * A window, opened from Settings, for replacing the current data with a
 * backup: either one from the backups folder or a file chosen elsewhere, such
 * as a backup saved to a USB drive.
 */
final class RestoreView {

  private static final Pattern DAILY = Pattern.compile("mailboxes-(\\d{4}-\\d{2}-\\d{2})\\.db");

  private static final Pattern BEFORE_RESTORE =
      Pattern.compile("mailboxes-before-restore-(\\d{4}-\\d{2}-\\d{2}-\\d{6})\\.db");

  private static final Pattern EXPORTED = Pattern.compile("mailboxes-backup-(\\d{4}-\\d{2}-\\d{2}-\\d{6})\\.db");

  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

  /**
   * Asks the user to pick a backup file. Replaceable so tests can answer
   * without a real file dialog.
   */
  static Function<Window, File> chooseFile = owner -> {
    var chooser = new FileChooser();
    chooser.setTitle("Choose a Backup");
    chooser.getExtensionFilters().add(new ExtensionFilter("Mailbox Manager backups", "*.db"));
    return chooser.showOpenDialog(owner);
  };

  /**
   * Asks the user to confirm a restore, returning whether they agreed.
   * Replaceable so tests can answer without a real dialog.
   */
  static Predicate<String> confirm = message -> {
    var alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
    alert.setTitle("Restore Backup");
    alert.setHeaderText("Replace your current data?");
    AppWindow.applyTextSize(alert);
    return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
  };

  /**
   * Opens the restore window on top of the settings window. After a
   * successful restore both windows close and the main window returns to the
   * main menu, so no screen shows the replaced data.
   *
   * @param settingsWindow the settings window, which this window blocks while open
   * @param mainWindow the main window
   */
  static void show(Stage settingsWindow, Stage mainWindow) {
    var stage = new Stage();

    var intro = new Label("Choose a backup to restore. Your current data will be saved as a "
        + "backup first, so you can undo this by restoring it.");
    intro.setWrapText(true);

    var errorLabel = new Label();
    errorLabel.setId("restoreErrorLabel");
    errorLabel.setStyle("-fx-text-fill: red;");
    errorLabel.setWrapText(true);

    var backupList = new ListView<Path>();
    backupList.setId("backupList");
    backupList.setPlaceholder(new Label("No backups yet."));
    backupList.setStyle("-fx-pref-height: 16em; -fx-pref-width: 30em;");
    backupList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : describe(item));
      }
    });
    try {
      backupList.setItems(FXCollections.observableArrayList(Database.listBackups()));
    } catch (RuntimeException e) {
      errorLabel.setText("Couldn't list the backups: " + Errors.rootMessage(e));
    }

    var restoreSelectedBtn = new Button("Restore");
    restoreSelectedBtn.setId("restoreSelectedButton");
    restoreSelectedBtn.setDefaultButton(true);
    restoreSelectedBtn.disableProperty().bind(backupList.getSelectionModel().selectedItemProperty().isNull());
    restoreSelectedBtn.setOnAction(e -> restore(backupList.getSelectionModel().getSelectedItem(),
        stage, settingsWindow, mainWindow, errorLabel));

    var fromFileBtn = new Button("Choose File…");
    fromFileBtn.setId("restoreFromFileButton");
    fromFileBtn.setOnAction(e -> {
      var file = chooseFile.apply(stage);
      if (file != null) {
        restore(file.toPath(), stage, settingsWindow, mainWindow, errorLabel);
      }
    });

    var cancelBtn = new Button("Cancel");
    cancelBtn.setId("restoreCancelButton");
    cancelBtn.setCancelButton(true);
    cancelBtn.setOnAction(e -> stage.close());

    var content = new VBox(10, intro, backupList, new HBox(8, restoreSelectedBtn, fromFileBtn, cancelBtn),
        errorLabel);
    content.setPadding(new Insets(20));
    AppWindow.applyTextSize(content);

    stage.initOwner(settingsWindow);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle("Restore Backup");
    stage.setScene(new Scene(content));
    stage.show();
  }

  private static void restore(Path backup, Stage restoreWindow, Stage settingsWindow, Stage mainWindow,
      Label errorLabel) {
    if (!confirm.test("Replace all current data with the " + describe(backup).toLowerCase() + "?\n\n"
        + "Your current data will be saved as a backup first, so you can undo this.")) {
      return;
    }
    Path saved;
    try {
      saved = Database.restore(backup);
    } catch (IllegalArgumentException e) {
      errorLabel.setText(e.getMessage());
      return;
    } catch (RuntimeException e) {
      errorLabel.setText("Couldn't restore the backup: " + Errors.rootMessage(e));
      return;
    }

    restoreWindow.close();
    settingsWindow.close();
    MainMenuView.show(mainWindow);

    var done = new Alert(AlertType.INFORMATION,
        "Restored the " + describe(backup).toLowerCase() + ".\n\nYour previous data was saved as "
            + saved.getFileName() + " in the backups folder.");
    done.initOwner(mainWindow);
    done.setTitle("Backup Restored");
    done.setHeaderText("Backup restored");
    AppWindow.applyTextSize(done);
    done.show();
  }

  /**
   * Describes a backup file by what it is and when it was made, such as
   * "Daily backup from Saturday, September 26, 2026".
   */
  static String describe(Path file) {
    var name = file.getFileName().toString();
    try {
      var daily = DAILY.matcher(name);
      if (daily.matches()) {
        return "Daily backup from "
            + LocalDate.parse(daily.group(1)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL));
      }
      var beforeRestore = BEFORE_RESTORE.matcher(name);
      if (beforeRestore.matches()) {
        return "Data saved before a restore on " + formatTimestamp(beforeRestore.group(1));
      }
      var exported = EXPORTED.matcher(name);
      if (exported.matches()) {
        return "Backup saved on " + formatTimestamp(exported.group(1));
      }
    } catch (DateTimeParseException e) {
      // A name that looks like a backup but has an impossible date.
    }
    return "Backup " + name;
  }

  private static String formatTimestamp(String timestamp) {
    return LocalDateTime.parse(timestamp, TIMESTAMP)
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT));
  }

  private RestoreView() {
  }

}
