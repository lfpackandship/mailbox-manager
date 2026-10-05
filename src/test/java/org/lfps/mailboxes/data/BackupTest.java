package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for exporting, restoring, listing, and copying backups.
 */
class BackupTest {

  private static final Path BACKUP_DIR = Database.backupDir();

  private final MailboxRepository mailboxes = new MailboxRepository();

  private final SettingsRepository settings = new SettingsRepository();

  private Path elsewhere;

  @BeforeEach
  void freshDataDir() throws IOException {
    TestSandbox.require();
    deleteRecursively(Database.dataDir());
    Database.prepareDataDir();
    Database.initSchema();
    // Stands in for a USB drive or cloud folder; inside the data directory so
    // it's cleaned up with it.
    elsewhere = Files.createDirectories(Database.dataDir().resolve("elsewhere"));
  }

  @AfterEach
  void removeDataDir() throws IOException {
    deleteRecursively(Database.dataDir());
  }

  @Test
  void exportWritesACopyOfTheDataToTheChosenFolder() throws SQLException {
    mailboxes.insert(box("101"));

    var exported = Database.exportBackup(elsewhere);

    assertEquals(elsewhere, exported.getParent());
    assertTrue(exported.getFileName().toString().matches("mailboxes-backup-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.db"),
        exported.toString());
    assertEquals(List.of("101"), boxNumbersIn(exported));
  }

  @Test
  void restoreBringsBackTheBackupAndSavesTheReplacedData() throws SQLException {
    mailboxes.insert(box("101"));
    var backup = Database.exportBackup(elsewhere);
    mailboxes.insert(box("102"));

    var saved = Database.restore(backup);

    assertEquals(List.of("101"), boxNumbers(mailboxes.findAll()));
    assertEquals(BACKUP_DIR, saved.getParent());
    assertTrue(saved.getFileName().toString().startsWith("mailboxes-before-restore-"), saved.toString());
    assertEquals(List.of("101", "102"), boxNumbersIn(saved));
    assertTrue(Database.listBackups().contains(saved));
  }

  @Test
  void restoreRefusesFilesThatArentBackupsAndLeavesDataAlone() throws IOException, SQLException {
    mailboxes.insert(box("101"));
    var notes = Files.writeString(elsewhere.resolve("notes.db"), "not a database");
    var otherDatabase = elsewhere.resolve("other.db");
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + otherDatabase);
        var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE recipes (name TEXT)");
    }

    for (var file : List.of(notes, otherDatabase, elsewhere.resolve("missing.db"))) {
      var error = assertThrows(IllegalArgumentException.class, () -> Database.restore(file));
      assertEquals(file.getFileName() + " isn't a Mailbox Manager backup.", error.getMessage());
    }

    assertEquals(List.of("101"), boxNumbers(mailboxes.findAll()));
    assertTrue(Database.listBackups().isEmpty(), Database.listBackups().toString());
    assertFalse(Files.exists(elsewhere.resolve("missing.db")));
  }

  @Test
  void restoreUpgradesABackupFromAnOlderVersion() throws SQLException {
    // The layout used before box names, end dates, and settings existed.
    var old = elsewhere.resolve("old.db");
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + old);
        var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE mailboxes (id INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "first_name TEXT NOT NULL, last_name TEXT NOT NULL, business_title TEXT, "
          + "box_number TEXT NOT NULL, phone TEXT NOT NULL, email TEXT)");
      stmt.execute("CREATE TABLE business_names (id INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "mailbox_id INTEGER NOT NULL, name TEXT NOT NULL)");
      stmt.execute("INSERT INTO mailboxes (first_name, last_name, box_number, phone) "
          + "VALUES ('Old', 'Timer', '7', '(555) 000-0000')");
    }

    Database.restore(old);

    var restored = mailboxes.findAll();
    assertEquals(List.of("7"), boxNumbers(restored));
    assertEquals(null, restored.get(0).getEndDate());
    assertEquals(30, settings.getInt(Setting.RENEWAL_WINDOW_DAYS));
  }

  @Test
  void listsBackupsNewestFirstAndSkipsOtherFiles() throws IOException {
    Files.createDirectories(BACKUP_DIR);
    var older = Files.createFile(BACKUP_DIR.resolve("mailboxes-2026-01-01.db"));
    var newer = Files.createFile(BACKUP_DIR.resolve("mailboxes-before-restore-2026-02-01-120000.db"));
    Files.setLastModifiedTime(older, FileTime.fromMillis(1_000_000));
    Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000_000));
    Files.createFile(BACKUP_DIR.resolve("mailboxes-2026-03-01.db.partial"));
    Files.createFile(BACKUP_DIR.resolve("notes.txt"));

    assertEquals(List.of(newer, older), Database.listBackups());
  }

  @Test
  void copiesTodaysBackupToTheSecondFolderAndTrimsOldOnes() throws IOException, SQLException {
    settings.put(Setting.SECOND_BACKUP_FOLDER, elsewhere.toString());
    settings.put(Setting.BACKUPS_TO_KEEP, "3");
    for (var day = 1; day <= 5; day++) {
      Files.createFile(elsewhere.resolve(String.format("mailboxes-2020-01-%02d.db", day)));
    }
    Files.createFile(elsewhere.resolve("family-photos.jpg"));

    Database.backupDaily();
    Database.copyToSecondBackupFolder();

    var today = "mailboxes-" + LocalDate.now() + ".db";
    assertEquals(List.of("family-photos.jpg", "mailboxes-2020-01-04.db", "mailboxes-2020-01-05.db", today),
        fileNames(elsewhere));
    assertEquals(Files.size(BACKUP_DIR.resolve(today)), Files.size(elsewhere.resolve(today)));
  }

  @Test
  void secondFolderCopyDoesNothingWhenNoFolderIsSet() throws IOException {
    Database.backupDaily();

    Database.copyToSecondBackupFolder();

    assertTrue(fileNames(elsewhere).isEmpty());
  }

  @Test
  void secondFolderCopyExplainsAMissingFolder() throws SQLException {
    var unplugged = elsewhere.resolve("usb-drive");
    settings.put(Setting.SECOND_BACKUP_FOLDER, unplugged.toString());
    Database.backupDaily();

    var error = assertThrows(RuntimeException.class, Database::copyToSecondBackupFolder);

    assertTrue(error.getMessage().contains(unplugged + " can't be found"), error.getMessage());
  }

  /** Makes a box with the given number and a holder. */
  private static Mailbox box(String boxNumber) {
    return new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "(555) 123-4567", null, null, null, null);
  }

  /** Returns the boxes' numbers, in the same order. */
  private static List<String> boxNumbers(List<Mailbox> list) {
    return list.stream().map(Mailbox::getBoxNumber).collect(Collectors.toList());
  }

  /** Reads the box numbers saved in a database file, such as a backup, in order. */
  private static List<String> boxNumbersIn(Path databaseFile) throws SQLException {
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT box_number FROM mailboxes ORDER BY box_number")) {
      var numbers = new java.util.ArrayList<String>();
      while (rs.next()) {
        numbers.add(rs.getString(1));
      }
      return numbers;
    }
  }

  /** Returns the names of the files in a folder, in alphabetical order. */
  private static List<String> fileNames(Path folder) throws IOException {
    try (Stream<Path> files = Files.list(folder)) {
      return files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
    }
  }

  /** Deletes a folder and everything in it, if it exists. */
  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      for (var path : paths.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList())) {
        Files.delete(path);
      }
    }
  }

}
