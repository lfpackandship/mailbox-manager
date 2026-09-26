package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import org.junit.jupiter.api.Test;

class RestoreDescribeTest {

  private static final LocalDateTime AFTERNOON = LocalDateTime.of(2026, 9, 26, 14, 30, 5);

  private static final String AFTERNOON_TEXT =
      AFTERNOON.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT));

  @Test
  void describesDailyBackupsByDate() {
    assertEquals("Daily backup from "
        + LocalDate.of(2026, 9, 26).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        RestoreView.describe(Path.of("backups", "mailboxes-2026-09-26.db")));
  }

  @Test
  void describesDataSavedBeforeARestore() {
    assertEquals("Data saved before a restore on " + AFTERNOON_TEXT,
        RestoreView.describe(Path.of("mailboxes-before-restore-2026-09-26-143005.db")));
  }

  @Test
  void describesBackupsSavedWithBackUpNow() {
    assertEquals("Backup saved on " + AFTERNOON_TEXT,
        RestoreView.describe(Path.of("usb", "mailboxes-backup-2026-09-26-143005.db")));
  }

  @Test
  void fallsBackToTheFileNameForOtherFiles() {
    assertEquals("Backup old-copy.db", RestoreView.describe(Path.of("old-copy.db")));
    assertEquals("Backup mailboxes-2026-02-31.db", RestoreView.describe(Path.of("mailboxes-2026-02-31.db")));
  }

}
