package org.lfps.mailboxes.view;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javafx.application.Platform;
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
import org.lfps.mailboxes.drive.DriveBackup;
import org.lfps.mailboxes.drive.GoogleDrive;
import org.lfps.mailboxes.util.Errors;

/**
 * A window, opened from Settings, for replacing the current data with a
 * backup: one from the backups folder, a file chosen elsewhere, such as a
 * backup saved to a USB drive, or one downloaded from Google Drive.
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

    var fromDriveBtn = new Button("Google Drive…");
    fromDriveBtn.setId("restoreFromDriveButton");
    var driveConnected = DriveBackup.isAvailable() && DriveBackup.account() != null;
    fromDriveBtn.setVisible(driveConnected);
    fromDriveBtn.setManaged(driveConnected);
    fromDriveBtn.setOnAction(e -> showDrive(stage, settingsWindow, mainWindow));

    var cancelBtn = new Button("Cancel");
    cancelBtn.setId("restoreCancelButton");
    cancelBtn.setCancelButton(true);
    cancelBtn.setOnAction(e -> stage.close());

    var content = new VBox(10, intro, backupList, new HBox(8, restoreSelectedBtn, fromFileBtn, fromDriveBtn, cancelBtn),
        errorLabel);
    content.setPadding(new Insets(20));
    AppWindow.applyTextSize(content);

    stage.initOwner(settingsWindow);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle("Restore Backup");
    stage.setScene(new Scene(content));
    stage.show();
  }

  /**
   * Opens a window listing the backups in Google Drive, on top of the
   * restore window. Restoring one downloads it first.
   */
  private static void showDrive(Stage restoreWindow, Stage settingsWindow, Stage mainWindow) {
    var stage = new Stage();

    var intro = new Label("These are the backups in the " + GoogleDrive.FOLDER_NAME + " folder in your Google "
        + "Drive. Choose one to download and restore.");
    intro.setWrapText(true);

    var statusLabel = new Label("Looking in Google Drive…");
    statusLabel.setId("driveRestoreStatusLabel");
    statusLabel.setWrapText(true);

    var backupList = new ListView<String>();
    backupList.setId("driveBackupList");
    backupList.setPlaceholder(new Label(""));
    backupList.setStyle("-fx-pref-height: 16em; -fx-pref-width: 30em;");
    backupList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : describe(Paths.get(item)));
      }
    });

    var restoreBtn = new Button("Download and Restore");
    restoreBtn.setId("driveRestoreButton");
    restoreBtn.setDefaultButton(true);
    restoreBtn.disableProperty().bind(backupList.getSelectionModel().selectedItemProperty().isNull());
    restoreBtn.setOnAction(e -> {
      var name = backupList.getSelectionModel().getSelectedItem();
      restoreBtn.disableProperty().unbind();
      restoreBtn.setDisable(true);
      showStatus(statusLabel, "Downloading…", false);
      DriveBackup.downloadInBackground(name).whenComplete((file, error) -> Platform.runLater(() -> {
        restoreBtn.disableProperty().bind(backupList.getSelectionModel().selectedItemProperty().isNull());
        if (error != null) {
          showStatus(statusLabel, DriveBackup.problem(error).getMessage(), true);
          return;
        }
        showStatus(statusLabel, "", false);
        if (restore(file, restoreWindow, settingsWindow, mainWindow, statusLabel)) {
          stage.close();
        }
      }));
    });

    var cancelBtn = new Button("Cancel");
    cancelBtn.setId("driveRestoreCancelButton");
    cancelBtn.setCancelButton(true);
    cancelBtn.setOnAction(e -> stage.close());

    DriveBackup.listBackupsInBackground().whenComplete((names, error) -> Platform.runLater(() -> {
      if (error != null) {
        showStatus(statusLabel, DriveBackup.problem(error).getMessage(), true);
      } else if (names.isEmpty()) {
        showStatus(statusLabel, "There are no backups in Google Drive yet.", false);
      } else {
        backupList.setItems(FXCollections.observableArrayList(names));
        showStatus(statusLabel, "", false);
      }
    }));

    var content = new VBox(10, intro, backupList, new HBox(8, restoreBtn, cancelBtn), statusLabel);
    content.setPadding(new Insets(20));
    AppWindow.applyTextSize(content);

    stage.initOwner(restoreWindow);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle("Restore from Google Drive");
    stage.setScene(new Scene(content));
    stage.show();
  }

  private static void showStatus(Label label, String message, boolean error) {
    label.setStyle(error ? "-fx-text-fill: red;" : "");
    label.setText(message);
  }

  /** Restores a backup if the user confirms, returning whether it was restored. */
  private static boolean restore(Path backup, Stage restoreWindow, Stage settingsWindow, Stage mainWindow,
      Label errorLabel) {
    if (!confirm.test("Replace all current data with the " + describe(backup).toLowerCase() + "?\n\n"
        + "Your current data will be saved as a backup first, so you can undo this.")) {
      return false;
    }
    Path saved;
    try {
      saved = Database.restore(backup);
    } catch (IllegalArgumentException e) {
      showStatus(errorLabel, e.getMessage(), true);
      return false;
    } catch (RuntimeException e) {
      showStatus(errorLabel, "Couldn't restore the backup: " + Errors.rootMessage(e), true);
      return false;
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
    return true;
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
