package org.lfps.mailboxes;

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
    var sql = "CREATE TABLE IF NOT EXISTS mailboxes ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "first_name TEXT NOT NULL, "
        + "last_name TEXT NOT NULL, "
        + "business_title TEXT, "
        + "box_number TEXT NOT NULL, "
        + "phone TEXT NOT NULL, "
        + "email TEXT)";

    try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to initialize database schema", e);
    }
  }

  private Database() {
  }

}
