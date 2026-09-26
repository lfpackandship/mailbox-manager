package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite connection and schema for the mailbox database.
 */
public class Database {

  private static final String URL = "jdbc:sqlite:mailboxes.db";

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

  private Database() {
  }

}
