package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the log of unexpected errors kept in the data folder.
 */
class ErrorLogTest {

  @BeforeEach
  void freshDataDir() throws IOException {
    TestSandbox.require();
    deleteRecursively(Database.dataDir());
  }

  @AfterEach
  void removeDataDir() throws IOException {
    deleteRecursively(Database.dataDir());
  }

  @Test
  void recordsWhatHappenedAndTheFullDetail() throws IOException {
    ErrorLog.record("Saving a box", new IllegalStateException("Something broke", new IOException("Disk full")));

    var log = Files.readString(ErrorLog.file());
    assertTrue(log.contains("Mailbox Manager"), log);
    assertTrue(log.contains("Saving a box"), log);
    assertTrue(log.contains("java.lang.IllegalStateException: Something broke"), log);
    assertTrue(log.contains("Caused by: java.io.IOException: Disk full"), log);
    assertTrue(log.contains("at org.lfps.mailboxes.data.ErrorLogTest"), log);
  }

  @Test
  void addsEachEntryAfterTheLast() throws IOException {
    ErrorLog.record("First", new RuntimeException("one"));
    ErrorLog.record("Second", new RuntimeException("two"));

    var log = Files.readString(ErrorLog.file());
    assertTrue(log.indexOf("First") < log.indexOf("Second"), log);
  }

  @Test
  void neverGrowsPastAboutOneMegabyte() throws IOException {
    var old = Database.dataDir().resolve(ErrorLog.OLD_FILE_NAME);
    for (var i = 0; i < 2000; i++) {
      ErrorLog.record("Entry " + i, new RuntimeException("x".repeat(500)));
    }

    assertTrue(Files.size(ErrorLog.file()) <= ErrorLog.MAX_BYTES, String.valueOf(Files.size(ErrorLog.file())));
    assertTrue(Files.size(old) <= ErrorLog.MAX_BYTES, String.valueOf(Files.size(old)));
    // The newest entry is kept, and the oldest have gone.
    assertTrue(Files.readString(ErrorLog.file()).contains("Entry 1999"));
    assertFalse(Files.readString(old).contains("Entry 0\n"));
  }

  @Test
  void aLogThatCantBeWrittenIsIgnored() throws IOException {
    // A folder where the log should be, so it can't be written.
    Files.createDirectories(ErrorLog.file());

    ErrorLog.record("Saving a box", new RuntimeException("Something broke"));

    assertTrue(Files.isDirectory(ErrorLog.file()));
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
