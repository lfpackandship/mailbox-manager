package org.lfps.mailboxes.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import org.lfps.mailboxes.drive.DriveAccount;
import org.lfps.mailboxes.drive.DriveBackup;
import org.lfps.mailboxes.drive.DriveException.Problem;
import org.lfps.mailboxes.drive.GoogleDrive;

/**
 * The Google Drive part of the Settings window: whether backups are copied to
 * Google Drive and which account they go to, with buttons to connect and
 * disconnect. Connecting and disconnecting take effect straight away rather
 * than on Save.
 */
final class GoogleDriveRow extends VBox {

  /**
   * Opens a web page in the browser. Replaceable so tests can sign in
   * without a real browser.
   */
  static Consumer<String> openBrowser = AppWindow::openWebPage;

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");

  private final Window owner;
  private final Label status = new Label();
  private final Label detail = new Label();
  private final Button connectBtn = new Button("Connect Google Drive");
  private final Button cancelBtn = new Button("Cancel");
  private final Button disconnectBtn = new Button("Disconnect");

  /** The sign-in in progress, so an earlier one that was replaced is ignored when it ends. */
  private CompletableFuture<DriveAccount> connecting;

  GoogleDriveRow(Window owner) {
    super(6);
    this.owner = owner;
    status.setId("driveStatusLabel");
    status.setWrapText(true);
    detail.setId("driveDetailLabel");
    detail.setWrapText(true);
    connectBtn.setId("connectDriveButton");
    cancelBtn.setId("cancelDriveButton");
    disconnectBtn.setId("disconnectDriveButton");

    connectBtn.setOnAction(e -> connect());
    cancelBtn.setOnAction(e -> {
      connecting = null;
      DriveBackup.cancelConnect();
      showAccount(DriveBackup.account());
    });
    disconnectBtn.setOnAction(e -> disconnect());

    getChildren().addAll(status, new HBox(8, connectBtn, cancelBtn, disconnectBtn), detail);

    if (DriveBackup.isAvailable()) {
      showAccount(DriveBackup.account());
    } else {
      status.setText("Not set up in this copy of the app. To turn it on, put the app's "
          + "google-oauth.properties file in " + GoogleDrive.credentialsFile().getParent()
          + " and restart the app.");
      showButtons(false, false, false);
      detail.setVisible(false);
      detail.setManaged(false);
    }
  }

  /**
   * Starts connecting Google Drive, as if Connect Google Drive were clicked.
   */
  void connect() {
    if (!DriveBackup.isAvailable()) {
      return;
    }
    try {
      var attempt = DriveBackup.connect(openBrowser);
      connecting = attempt;
      status.setText("Sign in to Google in the browser window that opened, then click Allow.");
      showDetail("", false);
      showButtons(false, true, false);
      attempt.whenComplete((account, error) -> Platform.runLater(() -> {
        if (attempt != connecting) {
          return;
        }
        connecting = null;
        if (error != null) {
          var problem = DriveBackup.problem(error);
          showAccount(DriveBackup.account());
          showDetail(problem.getMessage(), problem.problem() != Problem.CANCELLED);
          return;
        }
        showAccount(account);
        backUpNow();
      }));
    } catch (RuntimeException ex) {
      showAccount(DriveBackup.account());
      showDetail(DriveBackup.problem(ex).getMessage(), true);
    }
  }

  private void backUpNow() {
    showDetail("Backing up to Google Drive now…", false);
    DriveBackup.backUpTodayInBackground().whenComplete((account, error) -> Platform.runLater(() -> {
      if (error != null) {
        showDetail("Couldn't back up to Google Drive: " + DriveBackup.problem(error).getMessage()
            + " Your backup is still saved on this computer.", true);
      } else if (account != null) {
        showAccount(account);
      }
    }));
  }

  private void disconnect() {
    var yes = Dialogs.confirm.ask(owner, "Stop backing up to Google Drive?",
        "Backups already in your Google Drive will be kept. You can connect again at any time.");
    if (!yes) {
      return;
    }
    try {
      DriveBackup.disconnect();
      showAccount(null);
    } catch (RuntimeException ex) {
      showDetail("Couldn't disconnect: " + ex.getMessage(), true);
    }
  }

  private void showAccount(DriveAccount account) {
    if (account == null) {
      status.setText("Not connected. Connect to keep a copy of each daily backup in your Google Drive.");
      showDetail("", false);
      showButtons(true, false, false);
      return;
    }
    status.setText("Backing up to Google Drive as " + account.email());
    showDetail(account.lastBackup() == null
        ? "Not backed up yet."
        : "Last backed up " + describe(account.lastBackup()) + ".", false);
    showButtons(false, false, true);
  }

  private void showButtons(boolean connect, boolean cancel, boolean disconnect) {
    show(connectBtn, connect);
    show(cancelBtn, cancel);
    show(disconnectBtn, disconnect);
  }

  private static void show(Button button, boolean shown) {
    button.setVisible(shown);
    button.setManaged(shown);
  }

  private void showDetail(String text, boolean problem) {
    detail.setStyle(problem ? "-fx-text-fill: red;" : "");
    detail.setText(text);
    detail.setVisible(!text.isEmpty());
    detail.setManaged(!text.isEmpty());
  }

  /**
   * Describes when a backup was uploaded, such as "today at 9:02 AM".
   */
  static String describe(LocalDateTime time) {
    var day = time.toLocalDate();
    var today = LocalDate.now();
    String when;
    if (day.equals(today)) {
      when = "today";
    } else if (day.equals(today.minusDays(1))) {
      when = "yesterday";
    } else {
      when = "on " + time.format(DAY);
    }
    return when + " at " + time.format(TIME);
  }

}
