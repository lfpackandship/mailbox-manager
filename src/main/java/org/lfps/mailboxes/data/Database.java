package org.lfps.mailboxes.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the SQLite connection, schema, and daily backups for the mailbox
 * database, which lives in a fixed per-user data directory so it's found no
 * matter how the app is launched.
 */
public class Database {

  private static final String FILE_NAME = "mailboxes.db";

  private static final int BACKUPS_TO_KEEP = 30;

  private static final Path DATA_DIR = resolveDataDir();

  private static final Path DB_PATH = DATA_DIR.resolve(FILE_NAME);

  private static final Path BACKUP_DIR = DATA_DIR.resolve("backups");

  private static final String URL = "jdbc:sqlite:" + DB_PATH;

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
   * Writes today's backup to the {@code backups} folder if one doesn't exist
   * yet, then deletes all but the newest {@value #BACKUPS_TO_KEEP} backups.
   *
   * @throws RuntimeException if the backup cannot be written
   */
  public static void backupDaily() {
    var target = BACKUP_DIR.resolve("mailboxes-" + LocalDate.now() + ".db");
    try {
      Files.createDirectories(BACKUP_DIR);
      if (!Files.exists(target)) {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
          // VACUUM INTO produces a consistent copy even if the database is in use.
          stmt.execute("VACUUM INTO '" + target.toString().replace("'", "''") + "'");
        }
      }

      try (Stream<Path> backups = Files.list(BACKUP_DIR)) {
        var old = backups
            .filter(p -> p.getFileName().toString().matches("mailboxes-\\d{4}-\\d{2}-\\d{2}\\.db"))
            .sorted(Comparator.reverseOrder())
            .skip(BACKUPS_TO_KEEP)
            .collect(Collectors.toList());
        for (var path : old) {
          Files.delete(path);
        }
      }
    } catch (IOException | SQLException e) {
      throw new RuntimeException("Failed to back up database to " + target, e);
    }
  }

  /**
   * Creates the {@code mailboxes} and {@code business_names} tables if they
   * don't already exist, and migrates older databases that predate the
   * {@code box_name} and {@code end_date} columns.
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
        + "end_date TEXT)";

    var createBusinessNames = "CREATE TABLE IF NOT EXISTS business_names ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "mailbox_id INTEGER NOT NULL, "
        + "name TEXT NOT NULL, "
        + "FOREIGN KEY (mailbox_id) REFERENCES mailboxes(id))";

    try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
      stmt.execute(createMailboxes);
      stmt.execute(createBusinessNames);

      try {
        stmt.execute("ALTER TABLE mailboxes ADD COLUMN box_name TEXT");
      } catch (SQLException alreadyMigrated) {
        // box_name column already exists on a pre-existing database.
      }

      try {
        stmt.execute("ALTER TABLE mailboxes ADD COLUMN end_date TEXT");
      } catch (SQLException alreadyMigrated) {
        // end_date column already exists on a pre-existing database.
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to initialize database schema", e);
    }
  }

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

  private Database() {
  }

}
