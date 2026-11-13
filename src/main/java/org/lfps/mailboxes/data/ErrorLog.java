package org.lfps.mailboxes.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes down unexpected errors, with their full technical detail, in
 * {@code errors.log} in the data folder, so whoever helps with a problem has
 * something to look at afterwards. The user only ever sees a plain message;
 * this is where the rest goes. The log isn't part of the database, so it
 * isn't in backups.
 *
 * <p>It never grows past about 1 MB: once {@code errors.log} reaches
 * {@value #MAX_BYTES} bytes it's renamed to {@code errors-old.log}, replacing
 * the one before, and a new one is started.
 */
public final class ErrorLog {

  /** The log's file name, in the data folder. */
  public static final String FILE_NAME = "errors.log";

  /** What the log is renamed to once full, in the data folder. */
  static final String OLD_FILE_NAME = "errors-old.log";

  /** How big the log grows, in bytes, before it's renamed and a new one started. */
  static final long MAX_BYTES = 512 * 1024;

  /** Formats the time each entry was written. */
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Adds an entry for an error: when, which versions of the app and Java,
   * what was happening, and the error's full detail. A problem writing the
   * log is ignored, since there's nowhere left to report it and the app
   * should carry on.
   *
   * @param happening what the app was doing, such as "Starting up"
   * @param error the error
   */
  public static synchronized void record(String happening, Throwable error) {
    try {
      var entry = entry(happening, error).getBytes(StandardCharsets.UTF_8);
      Files.createDirectories(Database.dataDir());
      var log = file();
      if (Files.exists(log) && Files.size(log) + entry.length > MAX_BYTES) {
        Files.move(log, Database.dataDir().resolve(OLD_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
      }
      Files.write(log, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException | RuntimeException e) {
      // Nowhere left to report it; see above.
    }
  }

  /**
   * Returns the log file. It may not exist, if nothing has gone wrong yet.
   *
   * @return {@code errors.log} in the data folder
   */
  public static Path file() {
    return Database.dataDir().resolve(FILE_NAME);
  }

  /**
   * Writes out an entry for the log.
   *
   * @param happening what the app was doing
   * @param error the error
   * @return the entry, ending with a blank line to separate it from the next
   */
  private static String entry(String happening, Throwable error) {
    var trace = new StringWriter();
    error.printStackTrace(new PrintWriter(trace));
    var version = ErrorLog.class.getPackage() == null ? null : ErrorLog.class.getPackage().getImplementationVersion();
    return LocalDateTime.now().format(TIME) + "  Mailbox Manager " + (version == null ? "(development build)" : version)
        + ", Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + "\n"
        + happening + "\n"
        + trace + "\n";
  }

  /** Not used: errors are recorded with static methods. */
  private ErrorLog() {
  }

}
