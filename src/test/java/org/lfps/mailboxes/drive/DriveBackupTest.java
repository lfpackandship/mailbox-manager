package org.lfps.mailboxes.drive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.drive.DriveException.Problem;

/**
 * Tests for connecting Google Drive and uploading backups to it, against a
 * {@link FakeGoogle}.
 */
class DriveBackupTest {

  private FakeGoogle google;

  @BeforeEach
  void freshDataDir() throws IOException {
    TestSandbox.require();
    deleteRecursively(Database.dataDir());
    Database.prepareDataDir();
    Database.initSchema();
    google = FakeGoogle.install();
  }

  @AfterEach
  void cleanUp() throws IOException {
    google.close();
    deleteRecursively(Database.dataDir());
  }

  @Test
  void connectingSavesTheAccountTheUserSignedInWith() throws Exception {
    google.email = "post.office@gmail.com";

    var account = await(DriveBackup.connect(google.browser()));

    assertEquals("post.office@gmail.com", account.email());
    assertEquals("post.office@gmail.com", DriveBackup.account().email());
    assertNull(DriveBackup.account().lastBackup());
  }

  @Test
  void asksForOnlyTheDrivePermissionSoGoogleShowsNoCheckboxes() throws Exception {
    await(DriveBackup.connect(google.browser()));

    assertEquals("https://www.googleapis.com/auth/drive.file", google.requestedScope);
  }

  @Test
  void theSignInIsKeptOutOfTheDatabaseSoBackupsDontContainIt() throws Exception {
    await(DriveBackup.connect(google.browser()));

    Database.backupDaily();

    var backup = Files.readAllBytes(Database.todaysBackup());
    assertFalse(new String(backup, StandardCharsets.ISO_8859_1).contains("refresh-"));
    assertTrue(Files.exists(Database.dataDir().resolve("google-drive.properties")));
  }

  @Test
  void clickingCancelOnGooglesPageLeavesDriveDisconnected() {
    google.denySignIn = true;

    var problem = failure(DriveBackup.connect(google.browser()));

    assertEquals(Problem.CANCELLED, problem.problem());
    assertNull(DriveBackup.account());
  }

  @Test
  void notAllowingDriveAccessExplainsWhatToDo() {
    google.grantDrive = false;

    var problem = failure(DriveBackup.connect(google.browser()));

    assertEquals(Problem.OTHER, problem.problem());
    assertTrue(problem.getMessage().contains("permission to save files"), problem.getMessage());
    assertNull(DriveBackup.account());
  }

  @Test
  void cancellingWhileWaitingForTheBrowserLeavesDriveDisconnected() {
    var connecting = DriveBackup.connect(url -> { });

    DriveBackup.cancelConnect();

    assertEquals(Problem.CANCELLED, failure(connecting).problem());
    assertNull(DriveBackup.account());
  }

  @Test
  void uploadsTodaysBackupIntoABackupsFolder() throws Exception {
    await(DriveBackup.connect(google.browser()));
    Database.backupDaily();

    var account = await(DriveBackup.backUpTodayInBackground());

    assertEquals(List.of(Database.todaysBackup().getFileName().toString()), google.backupNames());
    var uploaded = google.files.values().stream().filter(f -> !f.folder).findFirst().get();
    assertArrayEquals(Files.readAllBytes(Database.todaysBackup()), uploaded.content);
    assertNotNull(account.lastBackup());
    assertEquals(LocalDate.now(), DriveBackup.account().lastBackup().toLocalDate());
  }

  @Test
  void doesntUploadTodaysBackupTwice() throws Exception {
    await(DriveBackup.connect(google.browser()));
    Database.backupDaily();

    await(DriveBackup.backUpTodayInBackground());
    await(DriveBackup.backUpTodayInBackground());

    assertEquals(1, google.uploads);
    assertEquals(1, google.files.values().stream().filter(f -> f.folder).count());
  }

  @Test
  void deletesOldDailyBackupsButNotOtherFiles() throws Exception {
    new SettingsRepository().put(Setting.BACKUPS_TO_KEEP, "2");
    google.addBackup("mailboxes-2020-01-01.db");
    google.addBackup("mailboxes-2020-01-02.db");
    google.addBackup("notes.txt");
    await(DriveBackup.connect(google.browser()));
    Database.backupDaily();

    await(DriveBackup.backUpTodayInBackground());

    assertEquals(List.of("mailboxes-2020-01-02.db", Database.todaysBackup().getFileName().toString(), "notes.txt"),
        google.backupNames());
  }

  @Test
  void doesNothingWhenDriveIsntConnected() throws Exception {
    Database.backupDaily();

    assertNull(await(DriveBackup.backUpTodayInBackground()));
    assertEquals(0, google.uploads);
  }

  @Test
  void anExpiredSignInAsksTheUserToSignInAgain() throws Exception {
    await(DriveBackup.connect(google.browser()));
    Database.backupDaily();
    google.revokeAll();

    var problem = failure(DriveBackup.backUpTodayInBackground());

    assertEquals(Problem.SIGNED_OUT, problem.problem());
    assertEquals(0, google.uploads);
  }

  @Test
  void beingOfflineSaysToCheckTheInternet() throws Exception {
    await(DriveBackup.connect(google.browser()));
    Database.backupDaily();
    google.goOffline();

    var problem = failure(DriveBackup.backUpTodayInBackground());

    assertEquals(Problem.OFFLINE, problem.problem());
    assertTrue(problem.getMessage().contains("internet"), problem.getMessage());
  }

  @Test
  void disconnectingForgetsTheSignInAndCancelsItWithGoogle() throws Exception {
    await(DriveBackup.connect(google.browser()));

    DriveBackup.disconnect();
    // Revoking happens in the background, after any upload in progress.
    await(DriveBackup.backUpTodayInBackground());

    assertNull(DriveBackup.account());
    assertEquals(List.of("refresh-2"), google.revoked);
  }

  @Test
  void connectingAgainReplacesTheEarlierAccount() throws Exception {
    await(DriveBackup.connect(google.browser()));
    google.email = "other@gmail.com";

    await(DriveBackup.connect(google.browser()));
    await(DriveBackup.backUpTodayInBackground());

    assertEquals("other@gmail.com", DriveBackup.account().email());
    assertEquals(List.of("refresh-2"), google.revoked);
  }

  private static <T> T await(CompletableFuture<T> future) throws Exception {
    try {
      return future.get(10, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw DriveBackup.problem(e.getCause());
    }
  }

  private static DriveException failure(CompletableFuture<?> future) {
    try {
      future.get(10, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      return DriveBackup.problem(e.getCause());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    throw new AssertionError("Expected it to fail");
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      for (var path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
        Files.delete(path);
      }
    }
  }

}
