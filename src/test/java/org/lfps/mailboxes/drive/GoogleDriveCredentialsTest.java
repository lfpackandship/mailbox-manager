package org.lfps.mailboxes.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.TestSandbox;

/**
 * Tests for finding the Google client ID in the data folder.
 */
class GoogleDriveCredentialsTest {

  @BeforeEach
  void prepare() throws IOException {
    TestSandbox.require();
    Files.createDirectories(Database.dataDir());
  }

  @AfterEach
  void removeFile() throws IOException {
    Files.deleteIfExists(GoogleDrive.credentialsFile());
  }

  @Test
  void usesTheClientIdInTheDataFolder() throws IOException {
    Files.writeString(GoogleDrive.credentialsFile(),
        "client_id=from-data-folder.apps.googleusercontent.com\nclient_secret=GOCSPX-test\n");

    assertEquals("from-data-folder.apps.googleusercontent.com", GoogleDrive.standard().clientId());
  }

  @Test
  void ignoresADataFolderFileWithoutAClientId() throws IOException {
    Files.writeString(GoogleDrive.credentialsFile(), "client_id=\nclient_secret=\n");

    var drive = GoogleDrive.standard();

    // Falls back to the built-in client ID, if this build has one.
    if (drive != null) {
      assertNotEquals("", drive.clientId());
    }
  }

}
