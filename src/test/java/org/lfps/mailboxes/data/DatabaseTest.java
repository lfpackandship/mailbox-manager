package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;

/**
 * Tests for where the database is kept, the daily backups, and upgrading
 * databases made by older versions.
 */
class DatabaseTest {

  private static final Path BACKUP_DIR = Database.dataDir().resolve("backups");

  @BeforeEach
  void freshDataDir() throws IOException {
    TestSandbox.require();
    deleteRecursively(Database.dataDir());
    Files.deleteIfExists(Paths.get("mailboxes.db"));
  }

  @AfterEach
  void removeDataDir() throws IOException {
    // Some tests leave a fake or broken database behind; don't let later
    // test classes open it.
    deleteRecursively(Database.dataDir());
    Files.deleteIfExists(Paths.get("mailboxes.db"));
  }

  @Test
  void dataDirIsUnderUserHome() {
    assertTrue(Database.dataDir().startsWith(Paths.get(System.getProperty("user.home"))));
  }

  @Test
  void movesLegacyDatabaseFromWorkingDirectory() throws IOException {
    var legacy = Paths.get("mailboxes.db");
    Files.writeString(legacy, "legacy");

    Database.prepareDataDir();

    assertFalse(Files.exists(legacy));
    assertEquals("legacy", Files.readString(Database.dataDir().resolve("mailboxes.db")));
  }

  @Test
  void doesNotOverwriteExistingDatabaseWithLegacyOne() throws IOException {
    Files.createDirectories(Database.dataDir());
    Files.writeString(Database.dataDir().resolve("mailboxes.db"), "current");
    Files.writeString(Paths.get("mailboxes.db"), "legacy");

    Database.prepareDataDir();

    assertEquals("current", Files.readString(Database.dataDir().resolve("mailboxes.db")));
    assertTrue(Files.exists(Paths.get("mailboxes.db")));
  }

  @Test
  void writesOneBackupPerDay() throws IOException {
    Database.prepareDataDir();
    Database.initSchema();

    Database.backupDaily();
    Database.backupDaily();

    assertEquals(1, listBackups().size());
    assertTrue(Files.size(BACKUP_DIR.resolve("mailboxes-" + LocalDate.now() + ".db")) > 0);
  }

  @Test
  void keepsNewestThirtyBackupsAndIgnoresOtherFiles() throws IOException {
    Database.prepareDataDir();
    Database.initSchema();
    Files.createDirectories(BACKUP_DIR);
    for (var day = 1; day <= 35; day++) {
      Files.createFile(BACKUP_DIR.resolve(String.format("mailboxes-2020-01-%02d.db", day)));
    }
    Files.createFile(BACKUP_DIR.resolve("notes.txt"));

    Database.backupDaily();

    var backups = listBackups();
    assertEquals(30, backups.size());
    assertTrue(backups.contains("mailboxes-" + LocalDate.now() + ".db"));
    assertFalse(backups.contains("mailboxes-2020-01-06.db"));
    assertTrue(backups.contains("mailboxes-2020-01-07.db"));
    assertTrue(Files.exists(BACKUP_DIR.resolve("notes.txt")));
  }

  @Test
  void keepsNumberOfBackupsFromSettings() throws IOException, SQLException {
    Database.prepareDataDir();
    Database.initSchema();
    new SettingsRepository().put(Setting.BACKUPS_TO_KEEP, "5");
    Files.createDirectories(BACKUP_DIR);
    for (var day = 1; day <= 10; day++) {
      Files.createFile(BACKUP_DIR.resolve(String.format("mailboxes-2020-01-%02d.db", day)));
    }

    Database.backupDaily();

    var backups = listBackups();
    assertEquals(5, backups.size());
    assertTrue(backups.contains("mailboxes-" + LocalDate.now() + ".db"));
    assertTrue(backups.contains("mailboxes-2020-01-07.db"));
    assertFalse(backups.contains("mailboxes-2020-01-06.db"));
  }

  @Test
  void replacesLeftoverFromEarlierFailedBackup() throws IOException {
    Database.prepareDataDir();
    Database.initSchema();
    Files.createDirectories(BACKUP_DIR);
    var partial = BACKUP_DIR.resolve("mailboxes-" + LocalDate.now() + ".db.partial");
    Files.writeString(partial, "half-written");

    Database.backupDaily();

    assertFalse(Files.exists(partial));
    assertTrue(Files.size(BACKUP_DIR.resolve("mailboxes-" + LocalDate.now() + ".db")) > 0);
  }

  @Test
  void failedBackupLeavesNothingThatLooksLikeTodaysBackup() throws IOException {
    Database.prepareDataDir();
    Files.writeString(Database.dataDir().resolve("mailboxes.db"), "this is not a SQLite database");

    assertThrows(RuntimeException.class, Database::backupDaily);

    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      assertEquals(java.util.List.of(), files.collect(Collectors.toList()));
    }
  }

  @Test
  void keepsTodaysBackupEvenIfSettingIsZero() throws IOException, SQLException {
    Database.prepareDataDir();
    Database.initSchema();
    new SettingsRepository().put(Setting.BACKUPS_TO_KEEP, "0");
    Files.createDirectories(BACKUP_DIR);
    Files.createFile(BACKUP_DIR.resolve("mailboxes-2020-01-01.db"));

    Database.backupDaily();

    assertEquals(java.util.List.of("mailboxes-" + LocalDate.now() + ".db"), listBackups());
  }

  @Test
  void upgradesDatabaseCreatedBeforeSettingsExisted() throws SQLException {
    Database.prepareDataDir();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE mailboxes (id INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "first_name TEXT NOT NULL, last_name TEXT NOT NULL, business_title TEXT, "
          + "box_number TEXT NOT NULL, box_name TEXT, phone TEXT NOT NULL, email TEXT, "
          + "end_date TEXT)");
    }

    Database.initSchema();
    var settings = new SettingsRepository();
    settings.put(Setting.RENEWAL_WINDOW_DAYS, "45");

    assertEquals(45, settings.getInt(Setting.RENEWAL_WINDOW_DAYS));
  }

  @Test
  void upgradesDatabaseCreatedBeforeNotesClosingAndRentalHistory() throws SQLException {
    Database.prepareDataDir();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE mailboxes (id INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "first_name TEXT NOT NULL, last_name TEXT NOT NULL, business_title TEXT, "
          + "box_number TEXT NOT NULL, box_name TEXT, phone TEXT NOT NULL, email TEXT, "
          + "end_date TEXT)");
      stmt.execute("INSERT INTO mailboxes (first_name, last_name, box_number, phone, end_date) "
          + "VALUES ('Ada', 'Lovelace', '7', '(555) 123-4567', '2026-12-01')");
    }

    Database.initSchema();

    var mailboxes = new MailboxRepository();
    var old = mailboxes.findAll().get(0);
    assertNull(old.getNotes());
    assertFalse(old.isClosed());
    mailboxes.setClosedDate(old.getId(), LocalDate.of(2026, 9, 1));
    new RentalHistoryRepository().renew(old.getId(), new RentalPeriod(0, 0,
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 1), 12000L, "Cash", null));
    new BoxInventoryRepository().add(java.util.List.of("7"), null);

    assertTrue(mailboxes.findAll().get(0).isClosed());
    assertEquals(LocalDate.of(2027, 12, 1), mailboxes.findAll().get(0).getEndDate());
  }

  @Test
  void upgradesDatabaseCreatedBeforeKeysAndSizeMeasurements() throws SQLException {
    Database.prepareDataDir();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE mailboxes (id INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "first_name TEXT NOT NULL, last_name TEXT NOT NULL, business_title TEXT, "
          + "box_number TEXT NOT NULL, box_name TEXT, phone TEXT NOT NULL, email TEXT, "
          + "end_date TEXT, notes TEXT, closed_date TEXT)");
      stmt.execute("INSERT INTO mailboxes (first_name, last_name, box_number, phone) "
          + "VALUES ('Ada', 'Lovelace', '7', '')");
    }

    Database.initSchema();

    var mailboxes = new MailboxRepository();
    var old = mailboxes.findAll().get(0);
    assertNull(old.getKeyCount());
    assertNull(old.getKeyDepositCents());
    mailboxes.update(new Mailbox(old.getId(), "Ada", "Lovelace", null, "7", null, "", null, null, null, null,
        null, null, 2, 2000L));
    assertEquals(2, mailboxes.findAll().get(0).getKeyCount());
    new PriceRepository().saveDescriptions(java.util.Map.of("Small", "3 x 5 x 14"));
    assertEquals("3 x 5 x 14", new PriceRepository().findDescriptions().get("small"));
  }

  private static java.util.List<String> listBackups() throws IOException {
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      return files.map(p -> p.getFileName().toString())
          .filter(name -> name.endsWith(".db"))
          .collect(Collectors.toList());
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
