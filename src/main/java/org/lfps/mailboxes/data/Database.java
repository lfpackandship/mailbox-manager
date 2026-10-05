package org.lfps.mailboxes.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.sqlite.SQLiteConfig;

/**
 * Manages the SQLite connection, schema, and backups for the mailbox
 * database, which lives in a fixed per-user data directory so it's found no
 * matter how the app is launched.
 */
public class Database {

  /** The database file's name. */
  private static final String FILE_NAME = "mailboxes.db";

  /** The folder the database and backups are kept in. */
  private static final Path DATA_DIR = resolveDataDir();

  /** The database file. */
  private static final Path DB_PATH = DATA_DIR.resolve(FILE_NAME);

  /** The folder daily backups are kept in. */
  private static final Path BACKUP_DIR = DATA_DIR.resolve("backups");

  /** The address the database driver opens the database by. */
  private static final String URL = "jdbc:sqlite:" + DB_PATH;

  /** What daily backups are named, such as mailboxes-2026-10-05.db. */
  private static final String DAILY_BACKUP_NAME = "mailboxes-\\d{4}-\\d{2}-\\d{2}\\.db";

  /**
   * Formats the date and time in the names of backups saved with Back Up Now,
   * and of the backup saved before a restore.
   */
  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

  /**
   * Returns the directory holding the database and its backups.
   *
   * @return the per-user data directory
   */
  public static Path dataDir() {
    return DATA_DIR;
  }

  /**
   * Opens a new connection to the local SQLite database file.
   *
   * @return an open connection; callers are responsible for closing it
   * @throws SQLException if the connection cannot be established
   */
  public static Connection connect() throws SQLException {
    return DriverManager.getConnection(URL);
  }

  /**
   * Creates the data directory if needed and, on first run, moves a
   * {@code mailboxes.db} left in the working directory by older versions
   * into it.
   *
   * @throws RuntimeException if the directory cannot be created or the old
   *     database cannot be moved
   */
  public static void prepareDataDir() {
    try {
      Files.createDirectories(DATA_DIR);
      var legacy = Paths.get(FILE_NAME).toAbsolutePath();
      if (!Files.exists(DB_PATH) && Files.exists(legacy)) {
        Files.move(legacy, DB_PATH);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to prepare data directory " + DATA_DIR, e);
    }
  }

  /**
   * Returns the folder that holds the daily backups.
   *
   * @return the {@code backups} folder inside the data directory
   */
  public static Path backupDir() {
    return BACKUP_DIR;
  }

  /**
   * Writes today's backup to the {@code backups} folder if one doesn't exist
   * yet, then deletes all but the newest daily backups, keeping as many as
   * the {@link Setting#BACKUPS_TO_KEEP} setting allows. Other backups there,
   * such as those saved before a restore, are left alone.
   *
   * @throws RuntimeException if the backup cannot be written
   */
  public static void backupDaily() {
    var target = todaysBackup();
    try {
      Files.createDirectories(BACKUP_DIR);
      if (!Files.exists(target)) {
        writeCopy(target);
      }
      deleteOldDailyBackups(BACKUP_DIR);
    } catch (IOException | SQLException e) {
      throw new RuntimeException("Failed to back up database to " + target, e);
    }
  }

  /**
   * Copies today's daily backup into the folder chosen in the
   * {@link Setting#SECOND_BACKUP_FOLDER} setting, if one is set and the copy
   * isn't there yet, then deletes old daily backups there the same way as
   * {@link #backupDaily()}. Does nothing if no folder is set or today's backup
   * hasn't been made.
   *
   * @throws RuntimeException if the folder is missing (for example, a USB
   *     drive that isn't plugged in) or the copy fails
   */
  public static void copyToSecondBackupFolder() {
    Path folder = null;
    try {
      var setting = new SettingsRepository().get(Setting.SECOND_BACKUP_FOLDER).trim();
      if (setting.isEmpty()) {
        return;
      }
      folder = Paths.get(setting);
      if (!Files.isDirectory(folder)) {
        throw new RuntimeException("The second backup folder " + folder
            + " can't be found. If it's on a removable or network drive, check that it's connected.");
      }
      var todays = todaysBackup();
      if (!Files.exists(todays)) {
        return;
      }
      var target = folder.resolve(todays.getFileName());
      if (!Files.exists(target)) {
        var partial = folder.resolve(target.getFileName() + ".partial");
        try {
          Files.copy(todays, partial, StandardCopyOption.REPLACE_EXISTING);
          Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
          Files.deleteIfExists(partial);
        }
      }
      deleteOldDailyBackups(folder);
    } catch (IOException | SQLException e) {
      throw new RuntimeException("Failed to copy today's backup to " + folder, e);
    }
  }

  /**
   * Saves a backup of the current data into the given folder, named with the
   * date and time (for example {@code mailboxes-backup-2026-09-26-143005.db}).
   *
   * @param folder the folder to save into, such as a USB drive
   * @return the backup file written
   * @throws RuntimeException if the backup cannot be written
   */
  public static Path exportBackup(Path folder) {
    var target = folder.resolve("mailboxes-backup-" + LocalDateTime.now().format(TIMESTAMP) + ".db");
    try {
      writeCopy(target);
      return target;
    } catch (IOException | SQLException e) {
      throw new RuntimeException("Failed to save a backup to " + target, e);
    }
  }

  /**
   * Returns the backups in the {@code backups} folder, newest first: the
   * daily backups and those saved before a restore.
   *
   * @return the backup files, or an empty list if there are none
   * @throws RuntimeException if the folder cannot be read
   */
  public static List<Path> listBackups() {
    if (!Files.isDirectory(BACKUP_DIR)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      return files
          .filter(p -> p.getFileName().toString().matches("mailboxes-.+\\.db"))
          .sorted(Comparator.comparing(Database::lastModified).reversed())
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException("Failed to list backups in " + BACKUP_DIR, e);
    }
  }

  /**
   * Replaces the current data with a backup. The current data is first saved
   * to the {@code backups} folder, so the restore can be undone by restoring
   * that file. A backup from an older version is upgraded to the current
   * database layout.
   *
   * @param backup the backup file to restore
   * @return the backup of the data that was replaced
   * @throws IllegalArgumentException if the file isn't a Mailbox Manager
   *     database
   * @throws RuntimeException if the restore fails; the current data is left
   *     in place
   */
  public static Path restore(Path backup) {
    requireMailboxDatabase(backup);
    var saved = BACKUP_DIR.resolve("mailboxes-before-restore-" + LocalDateTime.now().format(TIMESTAMP) + ".db");
    var partial = DATA_DIR.resolve(FILE_NAME + ".partial");
    try {
      Files.createDirectories(BACKUP_DIR);
      writeCopy(saved);
      try {
        Files.copy(backup, partial, StandardCopyOption.REPLACE_EXISTING);
        Files.move(partial, DB_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(partial);
      }
    } catch (IOException | SQLException e) {
      throw new RuntimeException("Failed to restore " + backup, e);
    }
    initSchema();
    return saved;
  }

  /**
   * Creates the {@code mailboxes}, {@code business_names},
   * {@code forwarding_addresses}, {@code rental_periods},
   * {@code box_inventory}, {@code prices}, {@code box_sizes}, and {@code settings} tables if
   * they don't already exist, and migrates older databases that predate the
   * {@code box_name}, {@code end_date}, {@code notes}, {@code closed_date},
   * {@code key_count}, {@code key_deposit_cents}, and {@code forwarding_only}
   * columns.
   *
   * @throws RuntimeException if the schema cannot be initialized
   */
  public static void initSchema() {
    var createMailboxes = "CREATE TABLE IF NOT EXISTS mailboxes ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "first_name TEXT NOT NULL, "
        + "last_name TEXT NOT NULL, "
        + "business_title TEXT, "
        + "box_number TEXT NOT NULL, "
        + "box_name TEXT, "
        + "phone TEXT NOT NULL, "
        + "email TEXT, "
        + "end_date TEXT, "
        + "notes TEXT, "
        + "closed_date TEXT, "
        + "key_count INTEGER, "
        + "key_deposit_cents INTEGER, "
        + "forwarding_only INTEGER NOT NULL DEFAULT 0)";

    var createBusinessNames = "CREATE TABLE IF NOT EXISTS business_names ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "mailbox_id INTEGER NOT NULL, "
        + "name TEXT NOT NULL, "
        + "FOREIGN KEY (mailbox_id) REFERENCES mailboxes(id))";

    var createForwardingAddresses = "CREATE TABLE IF NOT EXISTS forwarding_addresses ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "mailbox_id INTEGER NOT NULL, "
        + "street TEXT NOT NULL, "
        + "unit TEXT, "
        + "city TEXT NOT NULL, "
        + "state TEXT NOT NULL, "
        + "zip TEXT NOT NULL, "
        + "note TEXT, "
        + "FOREIGN KEY (mailbox_id) REFERENCES mailboxes(id))";

    var createRentalPeriods = "CREATE TABLE IF NOT EXISTS rental_periods ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "mailbox_id INTEGER NOT NULL, "
        + "recorded_on TEXT NOT NULL, "
        + "start_date TEXT NOT NULL, "
        + "end_date TEXT NOT NULL, "
        + "amount_cents INTEGER, "
        + "payment_method TEXT, "
        + "note TEXT, "
        + "FOREIGN KEY (mailbox_id) REFERENCES mailboxes(id))";

    var createBoxInventory = "CREATE TABLE IF NOT EXISTS box_inventory ("
        + "box_number TEXT PRIMARY KEY COLLATE NOCASE, "
        + "size TEXT)";

    // The price of renting a box of a size for a number of months. An empty
    // size is the default price, for boxes with no size or no price of their own.
    var createPrices = "CREATE TABLE IF NOT EXISTS prices ("
        + "size TEXT NOT NULL COLLATE NOCASE, "
        + "months INTEGER NOT NULL, "
        + "amount_cents INTEGER NOT NULL, "
        + "PRIMARY KEY (size, months))";

    // A price change that starts on a later date: the new price for each size
    // and length, or NULL for no price. Every row has the same start date, as
    // there's only one change at a time. On that date they replace the prices.
    var createScheduledPrices = "CREATE TABLE IF NOT EXISTS scheduled_prices ("
        + "size TEXT NOT NULL COLLATE NOCASE, "
        + "months INTEGER NOT NULL, "
        + "amount_cents INTEGER, "
        + "starts_on TEXT NOT NULL, "
        + "PRIMARY KEY (size, months))";

    // What each box size measures, such as 3¾" x 5" x 14", for the price sheet.
    var createBoxSizes = "CREATE TABLE IF NOT EXISTS box_sizes ("
        + "size TEXT PRIMARY KEY COLLATE NOCASE, "
        + "description TEXT NOT NULL)";

    var createSettings = "CREATE TABLE IF NOT EXISTS settings ("
        + "key TEXT PRIMARY KEY, "
        + "value TEXT NOT NULL)";

    try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
      stmt.execute(createMailboxes);
      stmt.execute(createBusinessNames);
      stmt.execute(createForwardingAddresses);
      stmt.execute(createRentalPeriods);
      stmt.execute(createBoxInventory);
      stmt.execute(createPrices);
      stmt.execute(createScheduledPrices);
      stmt.execute(createBoxSizes);
      stmt.execute(createSettings);

      for (var column : List.of("box_name TEXT", "end_date TEXT", "notes TEXT", "closed_date TEXT",
          "key_count INTEGER", "key_deposit_cents INTEGER", "forwarding_only INTEGER NOT NULL DEFAULT 0")) {
        try {
          stmt.execute("ALTER TABLE mailboxes ADD COLUMN " + column);
        } catch (SQLException alreadyMigrated) {
          // The column already exists on a pre-existing database.
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to initialize database schema", e);
    }
  }

  /**
   * Returns where today's daily backup is written. It may not exist yet.
   *
   * @return today's daily backup file in the {@code backups} folder
   */
  public static Path todaysBackup() {
    return BACKUP_DIR.resolve("mailboxes-" + LocalDate.now() + ".db");
  }

  /**
   * Returns whether a file name is that of a daily backup, such as
   * {@code mailboxes-2026-09-26.db}. Only these are deleted when old.
   *
   * @param fileName the file name to check
   * @return {@code true} for a daily backup's name
   */
  public static boolean isDailyBackupName(String fileName) {
    return fileName.matches(DAILY_BACKUP_NAME);
  }

  /**
   * Writes a consistent copy of the database to {@code target} under a
   * temporary name, renaming it once complete, so a failure never leaves a
   * partial file that looks like a finished backup.
   *
   * @param target where to write the copy; it must not exist
   * @throws IOException if the copy can't be moved into place
   * @throws SQLException if the copy can't be made
   */
  private static void writeCopy(Path target) throws IOException, SQLException {
    var partial = target.resolveSibling(target.getFileName() + ".partial");
    // VACUUM INTO refuses to overwrite a non-empty file, so clear out any
    // leftover from an earlier failed attempt.
    Files.deleteIfExists(partial);
    try {
      try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
        // VACUUM INTO produces a consistent copy even if the database is in use.
        stmt.execute("VACUUM INTO '" + partial.toString().replace("'", "''") + "'");
      }
      Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(partial);
    }
  }

  /**
   * Deletes all but the newest daily backups in a folder, keeping as many as
   * the {@link Setting#BACKUPS_TO_KEEP} setting allows. Only files named like
   * daily backups are touched.
   *
   * @param folder the folder
   * @throws IOException if the folder can't be listed or a backup can't be
   *     deleted
   * @throws SQLException if the setting can't be read
   */
  private static void deleteOldDailyBackups(Path folder) throws IOException, SQLException {
    // Always keep at least today's backup, even if the setting is out of range.
    var backupsToKeep = Math.max(1, new SettingsRepository().getInt(Setting.BACKUPS_TO_KEEP));
    try (Stream<Path> backups = Files.list(folder)) {
      var old = backups
          .filter(p -> isDailyBackupName(p.getFileName().toString()))
          .sorted(Comparator.reverseOrder())
          .skip(backupsToKeep)
          .collect(Collectors.toList());
      for (var path : old) {
        Files.delete(path);
      }
    }
  }

  /**
   * Checks that a file is a Mailbox Manager database, before restoring it.
   *
   * @param file the file
   * @throws IllegalArgumentException if it isn't, with a message suitable for
   *     showing to the user
   */
  private static void requireMailboxDatabase(Path file) {
    var notABackup = new IllegalArgumentException(
        file.getFileName() + " isn't a Mailbox Manager backup.");
    if (!Files.isRegularFile(file)) {
      throw notABackup;
    }
    var config = new SQLiteConfig();
    config.setReadOnly(true);
    try (Connection conn = config.createConnection("jdbc:sqlite:" + file);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'mailboxes'")) {
      if (!rs.next()) {
        throw notABackup;
      }
    } catch (SQLException e) {
      notABackup.initCause(e);
      throw notABackup;
    }
  }

  /**
   * Returns when a file was last changed, for sorting backups.
   *
   * @param path the file
   * @return when it was last changed, or the earliest possible time if that
   *     can't be read
   */
  private static FileTime lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path);
    } catch (IOException e) {
      return FileTime.fromMillis(0);
    }
  }

  /**
   * Returns the folder the app keeps its data in, which depends on the
   * operating system.
   *
   * @return the folder; it may not exist yet
   */
  private static Path resolveDataDir() {
    var home = Paths.get(System.getProperty("user.home"));
    var os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("mac")) {
      return home.resolve("Library").resolve("Application Support").resolve("MailboxManager");
    }
    if (os.contains("win")) {
      var appData = System.getenv("APPDATA");
      return (appData != null ? Paths.get(appData) : home).resolve("MailboxManager");
    }
    return home.resolve(".mailbox-manager");
  }

  /** Not used: the database is reached with static methods. */
  private Database() {
  }

}
