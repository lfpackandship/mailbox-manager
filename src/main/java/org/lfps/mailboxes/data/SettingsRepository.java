package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads and writes {@link Setting} values in the {@code settings} table,
 * falling back to each setting's default when it has never been saved.
 */
public class SettingsRepository {

  private static final String SELECT_SQL = "SELECT value FROM settings WHERE key = ?";

  private static final String UPSERT_SQL = "INSERT INTO settings (key, value) VALUES (?, ?) "
      + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";

  /**
   * Returns the saved value of a setting.
   *
   * @param setting the setting to read
   * @return the saved value, or the setting's default if none was saved
   * @throws SQLException if the query fails
   */
  public String get(Setting setting) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {
      stmt.setString(1, setting.key());
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getString("value") : setting.defaultValue();
      }
    }
  }

  /**
   * Returns the saved value of a numeric setting.
   *
   * @param setting the setting to read
   * @return the saved value, or the setting's default if none was saved or
   *     the saved value is not a whole number
   * @throws SQLException if the query fails
   */
  public int getInt(Setting setting) throws SQLException {
    try {
      return Integer.parseInt(get(setting).trim());
    } catch (NumberFormatException e) {
      return Integer.parseInt(setting.defaultValue());
    }
  }

  /**
   * Saves a setting, replacing any previous value.
   *
   * @param setting the setting to save
   * @param value the new value
   * @throws SQLException if the write fails
   */
  public void put(Setting setting, String value) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
      stmt.setString(1, setting.key());
      stmt.setString(2, value);
      stmt.executeUpdate();
    }
  }

}
