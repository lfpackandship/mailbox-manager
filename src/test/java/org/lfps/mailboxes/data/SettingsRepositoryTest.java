package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for saving and reading settings, and their defaults.
 */
class SettingsRepositoryTest {

  private final SettingsRepository repository = new SettingsRepository();

  @BeforeEach
  void emptySettings() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
    }
  }

  @Test
  void returnsDefaultWhenNeverSaved() throws SQLException {
    assertEquals("30", repository.get(Setting.RENEWAL_WINDOW_DAYS));
    assertEquals(30, repository.getInt(Setting.BACKUPS_TO_KEEP));
  }

  @Test
  void savedValueReplacesPreviousOne() throws SQLException {
    repository.put(Setting.RENEWAL_WINDOW_DAYS, "14");
    repository.put(Setting.RENEWAL_WINDOW_DAYS, "60");

    assertEquals(60, repository.getInt(Setting.RENEWAL_WINDOW_DAYS));
    assertEquals(30, repository.getInt(Setting.BACKUPS_TO_KEEP));
  }

  @Test
  void getIntFallsBackToDefaultForNonNumericValue() throws SQLException {
    repository.put(Setting.BACKUPS_TO_KEEP, "lots");

    assertEquals(30, repository.getInt(Setting.BACKUPS_TO_KEEP));
  }

}
