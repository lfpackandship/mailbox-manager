package org.lfps.mailboxes.drive;

import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.drive.DriveException.Problem;

/**
 * Backs up to Google Drive: connecting and disconnecting the user's Google
 * account, and uploading each daily backup. Uploads run one at a time in the
 * background, so the app never waits for them.
 */
public final class DriveBackup {

  /** Google's servers; replaceable so tests can use a fake. {@code null} if unavailable. */
  static GoogleDrive drive = GoogleDrive.standard();

  /** Guards the saved account, so a disconnect isn't undone by an upload finishing. */
  private static final Object ACCOUNT_LOCK = new Object();

  private static final ExecutorService UPLOADS = Executors.newSingleThreadExecutor(task -> {
    var thread = new Thread(task, "Google Drive backup");
    thread.setDaemon(true);
    return thread;
  });

  private static GoogleDrive.SignIn pendingSignIn;

  /**
   * Returns whether this build of the app can back up to Google Drive. Builds
   * made without a Google client ID can't.
   *
   * @return {@code true} if Google Drive can be connected
   */
  public static boolean isAvailable() {
    return drive != null;
  }

  /**
   * Returns the connected Google account.
   *
   * @return the account, or {@code null} if Google Drive isn't connected or
   *     the saved sign-in can't be read
   */
  public static DriveAccount account() {
    try {
      return DriveAccount.load();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Starts connecting a Google account: opens Google's sign-in page, then
   * waits for the user to sign in and click Allow. Once they do, the account
   * is saved, replacing any connected before. Cancels any connection already
   * in progress.
   *
   * @param openBrowser opens a web address in the user's browser
   * @return completes with the connected account on a background thread, or
   *     with a {@link DriveException} if connecting failed or was cancelled
   * @throws DriveException if connecting can't be started
   */
  public static synchronized CompletableFuture<DriveAccount> connect(Consumer<String> openBrowser) {
    // Each task keeps the client it started with, even if the field changes
    // (as when a test puts the real one back) before the task runs.
    var client = drive;
    if (client == null) {
      throw new DriveException(Problem.OTHER, "This copy of Mailbox Manager can't connect to Google Drive.");
    }
    cancelConnect();
    var signIn = client.startSignIn();
    pendingSignIn = signIn;
    var connected = signIn.result().thenApply(account -> {
      synchronized (ACCOUNT_LOCK) {
        var previous = account();
        account.save();
        if (previous != null && !previous.refreshToken().equals(account.refreshToken())) {
          UPLOADS.execute(() -> client.revoke(previous.refreshToken()));
        }
      }
      return account;
    });
    openBrowser.accept(signIn.url());
    return connected;
  }

  /**
   * Gives up on a connection in progress, if there is one.
   */
  public static synchronized void cancelConnect() {
    if (pendingSignIn != null) {
      pendingSignIn.cancel();
      pendingSignIn = null;
    }
  }

  /**
   * Disconnects Google Drive: forgets the sign-in and tells Google to cancel
   * it. Backups already uploaded stay in the user's Google Drive.
   *
   * @throws RuntimeException if the saved sign-in can't be deleted
   */
  public static void disconnect() {
    var client = drive;
    DriveAccount account;
    synchronized (ACCOUNT_LOCK) {
      account = account();
      DriveAccount.delete();
    }
    if (account != null && client != null) {
      UPLOADS.execute(() -> client.revoke(account.refreshToken()));
    }
  }

  /**
   * Uploads today's daily backup to Google Drive in the background, if
   * Google Drive is connected and today's backup has been made, and deletes
   * old daily backups there as {@link Setting#BACKUPS_TO_KEEP} allows.
   *
   * @return completes on a background thread with the account, including
   *     the time of this upload, or {@code null} if Google Drive isn't
   *     connected; or with a {@link DriveException} if the upload failed
   */
  public static CompletableFuture<DriveAccount> backUpTodayInBackground() {
    return CompletableFuture.supplyAsync(DriveBackup::backUpToday, UPLOADS);
  }

  static DriveAccount backUpToday() {
    var client = drive;
    var account = account();
    if (account == null || client == null) {
      return null;
    }
    var todays = Database.todaysBackup();
    if (!Files.exists(todays)) {
      return account;
    }
    int backupsToKeep;
    try {
      backupsToKeep = new SettingsRepository().getInt(Setting.BACKUPS_TO_KEEP);
    } catch (SQLException e) {
      backupsToKeep = Integer.parseInt(Setting.BACKUPS_TO_KEEP.defaultValue());
    }
    client.upload(account.refreshToken(), todays, backupsToKeep, Database::isDailyBackupName);

    var updated = account.withLastBackup(LocalDateTime.now());
    synchronized (ACCOUNT_LOCK) {
      // Only record the upload if the same account is still connected.
      var current = account();
      if (current != null && current.refreshToken().equals(account.refreshToken())) {
        updated.save();
      }
    }
    return updated;
  }

  /**
   * Returns the {@link DriveException} behind a failed background task.
   *
   * @param error what the task completed with
   * @return the problem, as a {@link DriveException} with a message to show
   */
  public static DriveException problem(Throwable error) {
    var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof DriveException) {
      return (DriveException) cause;
    }
    if (cause instanceof TimeoutException) {
      return new DriveException(Problem.CANCELLED, "Signing in took too long, so it was cancelled.", cause);
    }
    if (cause instanceof CancellationException) {
      return new DriveException(Problem.CANCELLED, "Signing in was cancelled.", cause);
    }
    return new DriveException(Problem.OTHER, String.valueOf(cause.getMessage()), cause);
  }

  private DriveBackup() {
  }

}
