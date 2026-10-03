package org.lfps.mailboxes.drive;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Properties;

import org.lfps.mailboxes.data.Database;

/**
 * The Google account backups are uploaded to, as saved after signing in.
 * It's kept in its own file in the data folder rather than in the database,
 * so the sign-in isn't copied into every backup (including those uploaded to
 * Google Drive) and restoring a backup doesn't sign out or into another
 * account.
 */
public final class DriveAccount {

  private static final String FILE_NAME = "google-drive.properties";

  private final String email;
  private final String refreshToken;
  private final LocalDateTime lastBackup;

  DriveAccount(String email, String refreshToken, LocalDateTime lastBackup) {
    this.email = email;
    this.refreshToken = refreshToken;
    this.lastBackup = lastBackup;
  }

  /**
   * Returns the Google account's email address.
   *
   * @return the email address, such as {@code name@gmail.com}
   */
  public String email() {
    return email;
  }

  String refreshToken() {
    return refreshToken;
  }

  /**
   * Returns when a backup was last uploaded.
   *
   * @return the time of the last upload, or {@code null} if there hasn't been one
   */
  public LocalDateTime lastBackup() {
    return lastBackup;
  }

  DriveAccount withLastBackup(LocalDateTime time) {
    return new DriveAccount(email, refreshToken, time);
  }

  /**
   * Returns the saved account.
   *
   * @return the account, or {@code null} if Google Drive isn't connected
   * @throws RuntimeException if the file can't be read
   */
  static DriveAccount load() {
    var file = file();
    if (!Files.exists(file)) {
      return null;
    }
    var props = new Properties();
    try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      props.load(in);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read " + file, e);
    }
    var email = props.getProperty("email", "");
    var refreshToken = props.getProperty("refresh_token", "");
    if (refreshToken.isEmpty()) {
      return null;
    }
    LocalDateTime lastBackup = null;
    try {
      var saved = props.getProperty("last_backup", "");
      lastBackup = saved.isEmpty() ? null : LocalDateTime.parse(saved);
    } catch (DateTimeParseException ignored) {
      // Treated as never backed up.
    }
    return new DriveAccount(email, refreshToken, lastBackup);
  }

  /**
   * Saves the account, replacing any saved before.
   *
   * @throws RuntimeException if the file can't be written
   */
  void save() {
    var props = new Properties();
    props.setProperty("email", email);
    props.setProperty("refresh_token", refreshToken);
    if (lastBackup != null) {
      props.setProperty("last_backup", lastBackup.toString());
    }
    var file = file();
    var partial = file.resolveSibling(FILE_NAME + ".partial");
    try {
      Files.createDirectories(file.getParent());
      try (Writer out = Files.newBufferedWriter(partial, StandardCharsets.UTF_8)) {
        props.store(out, "Mailbox Manager's Google Drive sign-in. Delete this file to disconnect.");
      }
      Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new RuntimeException("Failed to save " + file, e);
    } finally {
      try {
        Files.deleteIfExists(partial);
      } catch (IOException ignored) {
        // Nothing more to do.
      }
    }
  }

  /**
   * Forgets the saved account.
   *
   * @throws RuntimeException if the file can't be deleted
   */
  static void delete() {
    try {
      Files.deleteIfExists(file());
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete " + file(), e);
    }
  }

  private static Path file() {
    return Database.dataDir().resolve(FILE_NAME);
  }

}
