package org.lfps.mailboxes.data;

import java.nio.file.Paths;

/**
 * Guards tests that write to or delete the data directory.
 */
public final class TestSandbox {

  /**
   * Fails unless the data directory is inside {@code target/test-home}, as
   * configured for Maven's test run in {@code pom.xml}. Other runners (such
   * as an IDE's) don't apply that setting and would otherwise operate on the
   * real database and backups.
   *
   * @throws IllegalStateException if the data directory is the real one
   */
  public static void require() {
    var sandbox = Paths.get("target", "test-home").toString();
    if (!Database.dataDir().toString().contains(sandbox)) {
      throw new IllegalStateException("Refusing to run against the real data directory "
          + Database.dataDir() + ". Run tests with ./mvnw test.");
    }
  }

  private TestSandbox() {
  }

}
