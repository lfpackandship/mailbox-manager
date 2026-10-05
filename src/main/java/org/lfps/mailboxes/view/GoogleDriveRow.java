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

  /** Formats the time of a backup, such as 9:02 AM. */
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

  /** Formats the day of a backup more than a day ago, such as Oct 3. */
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");

  /** The Settings window, which questions are asked over. */
  private final Window owner;

  /** Says whether Google Drive is connected, and to which account. */
  private final Label status = new Label();

  /** Says when the last backup was uploaded, or what went wrong. */
  private final Label detail = new Label();

  /** Starts signing in to Google Drive. */
  private final Button connectBtn = new Button("Connect Google Drive");

  /** Stops signing in. */
  private final Button cancelBtn = new Button("Cancel");

  /** Stops backing up to Google Drive. */
  private final Button disconnectBtn = new Button("Disconnect");

  /** The sign-in in progress, so an earlier one that was replaced is ignored when it ends. */
  private CompletableFuture<DriveAccount> connecting;

  /**
   * Makes the row, showing whether Google Drive is connected.
   *
   * @param owner the Settings window
   */
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
      status.setText("Not available in this copy of the app.");
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

  /** Uploads today's backup in the background, showing how it went. */
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

  /** Asks whether to stop backing up to Google Drive, and does if the user agrees. */
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

  /**
   * Shows which account backups go to and when the last one was uploaded, or
   * that Google Drive isn't connected.
   *
   * @param account the connected account, or {@code null} if none
   */
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

  /**
   * Shows only the buttons that apply.
   *
   * @param connect whether to show Connect Google Drive
   * @param cancel whether to show Cancel
   * @param disconnect whether to show Disconnect
   */
  private void showButtons(boolean connect, boolean cancel, boolean disconnect) {
    show(connectBtn, connect);
    show(cancelBtn, cancel);
    show(disconnectBtn, disconnect);
  }

  /**
   * Shows or hides a button, closing up the space when it's hidden.
   *
   * @param button the button
   * @param shown whether to show it
   */
  private static void show(Button button, boolean shown) {
    button.setVisible(shown);
    button.setManaged(shown);
  }

  /**
   * Shows the line under the status, hiding it when there's nothing to say.
   *
   * @param text what to show, or an empty string to hide the line
   * @param problem whether it's a problem, shown in red
   */
  private void showDetail(String text, boolean problem) {
    detail.setStyle(problem ? "-fx-text-fill: red;" : "");
    detail.setText(text);
    detail.setVisible(!text.isEmpty());
    detail.setManaged(!text.isEmpty());
  }

  /**
   * Describes when a backup was uploaded, such as "today at 9:02 AM".
   *
   * @param time when the backup was uploaded
   * @return the description
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
