package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

  private static final String URL = "jdbc:sqlite:mailboxes.db";

  public static Connection connect() throws SQLException {
    return DriverManager.getConnection(URL);
  }

  public static void initSchema() {
    var createMailboxes = "CREATE TABLE IF NOT EXISTS mailboxes ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "first_name TEXT NOT NULL, "
        + "last_name TEXT NOT NULL, "
        + "business_title TEXT, "
        + "box_number TEXT NOT NULL, "
        + "box_name TEXT, "
        + "phone TEXT NOT NULL, "
        + "email TEXT)";

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
    } catch (SQLException e) {
      throw new RuntimeException("Failed to initialize database schema", e);
    }
  }

  private Database() {
  }

}
