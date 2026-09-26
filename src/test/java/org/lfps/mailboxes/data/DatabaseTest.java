package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseTest {

  private static final Path BACKUP_DIR = Database.dataDir().resolve("backups");

  @BeforeEach
  void freshDataDir() throws IOException {
    TestSandbox.require();
    deleteRecursively(Database.dataDir());
    Files.deleteIfExists(Paths.get("mailboxes.db"));
  }

  @Test
  void dataDirIsUnderUserHome() {
    assertTrue(Database.dataDir().startsWith(Paths.get(System.getProperty("user.home"))));
  }

  @Test
  void movesLegacyDatabaseFromWorkingDirectory() throws IOException {
    var legacy = Paths.get("mailboxes.db");
    Files.writeString(legacy, "legacy");

    Database.prepareDataDir();

    assertFalse(Files.exists(legacy));
    assertEquals("legacy", Files.readString(Database.dataDir().resolve("mailboxes.db")));
  }

  @Test
  void doesNotOverwriteExistingDatabaseWithLegacyOne() throws IOException {
    Files.createDirectories(Database.dataDir());
    Files.writeString(Database.dataDir().resolve("mailboxes.db"), "current");
    Files.writeString(Paths.get("mailboxes.db"), "legacy");

    Database.prepareDataDir();

    assertEquals("current", Files.readString(Database.dataDir().resolve("mailboxes.db")));
    assertTrue(Files.exists(Paths.get("mailboxes.db")));
  }

  @Test
  void writesOneBackupPerDay() throws IOException {
    Database.prepareDataDir();
    Database.initSchema();

    Database.backupDaily();
    Database.backupDaily();

    assertEquals(1, listBackups().size());
    assertTrue(Files.size(BACKUP_DIR.resolve("mailboxes-" + LocalDate.now() + ".db")) > 0);
  }

  @Test
  void keepsNewestThirtyBackupsAndIgnoresOtherFiles() throws IOException {
    Database.prepareDataDir();
    Database.initSchema();
    Files.createDirectories(BACKUP_DIR);
    for (var day = 1; day <= 35; day++) {
      Files.createFile(BACKUP_DIR.resolve(String.format("mailboxes-2020-01-%02d.db", day)));
    }
    Files.createFile(BACKUP_DIR.resolve("notes.txt"));

    Database.backupDaily();

    var backups = listBackups();
    assertEquals(30, backups.size());
    assertTrue(backups.contains("mailboxes-" + LocalDate.now() + ".db"));
    assertFalse(backups.contains("mailboxes-2020-01-06.db"));
    assertTrue(backups.contains("mailboxes-2020-01-07.db"));
    assertTrue(Files.exists(BACKUP_DIR.resolve("notes.txt")));
  }

  private static java.util.List<String> listBackups() throws IOException {
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      return files.map(p -> p.getFileName().toString())
          .filter(name -> name.endsWith(".db"))
          .collect(Collectors.toList());
    }
  }

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
