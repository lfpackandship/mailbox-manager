package org.lfps.mailboxes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/**
 * Tests for turning errors into the explanation shown in brackets after a
 * message.
 */
class ErrorsTest {

  @Test
  void showsOnlyTheSpecificProblemFromADatabaseError() {
    assertEquals(" (database is locked)",
        Errors.detail(new SQLException("[SQLITE_BUSY] The database file is locked (database is locked)")));
    assertEquals(" (no such column: nope)",
        Errors.detail(new SQLException("[SQLITE_ERROR] SQL error or missing database (no such column: nope)")));
  }

  @Test
  void showsTheInnermostExplanation() {
    var error = new RuntimeException("Failed to back up database to /data/backups/mailboxes.db",
        new IOException("No space left on device"));

    assertEquals(" (No space left on device)", Errors.detail(error));
  }

  @Test
  void skipsCausesWithoutAnExplanation() {
    var error = new RuntimeException("Failed to save", new IllegalStateException(new NullPointerException()));

    assertEquals(" (Failed to save)", Errors.detail(error));
  }

  @Test
  void givesNothingWhenThereIsNoExplanation() {
    assertEquals("", Errors.detail(new NullPointerException()));
    assertEquals("", Errors.detail(new RuntimeException("  ")));
  }

  @Test
  void givesAnExplanationWrittenForTheUserAsASentence() {
    var error = new RuntimeException("The second backup folder E:\\Backups can't be found.");

    assertEquals(" The second backup folder E:\\Backups can't be found.", Errors.reason(error));
    assertEquals("", Errors.reason(new NullPointerException()));
  }

  @Test
  void keepsOtherMessagesAsTheyAre() {
    assertEquals(" (The entry is no longer in the rental history.)",
        Errors.detail(new SQLException("The entry is no longer in the rental history.")));
  }

}
