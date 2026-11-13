package org.lfps.mailboxes.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Keeps the app's files private to the person signed in to the computer. The
 * data folder holds every box holder's name, phone number, and address, and
 * the Google Drive sign-in, so other accounts on a shared computer shouldn't
 * be able to read them. On Linux, new files can otherwise be read by anyone
 * with an account. Windows and Mac already keep the folders the app uses
 * private, so there's nothing to do on Windows, and on a Mac it does no harm.
 */
public final class OwnerOnly {

  /**
   * Lets only the owner read or change a file or folder (and, for a folder,
   * open it). It's done where the file system supports Unix permissions, and
   * skipped elsewhere, such as on Windows. If the permissions can't be
   * changed, as on some network or USB drives, the file is left as it was,
   * since the app should still work there.
   *
   * @param path the file or folder; nothing is done if it doesn't exist
   */
  public static void restrict(Path path) {
    var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
    if (posix == null || !Files.exists(path)) {
      return;
    }
    try {
      posix.setPermissions(PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
    } catch (IOException | UnsupportedOperationException e) {
      // Kept as it was; see above.
    }
  }

  /** Not used: permissions are set with static methods. */
  private OwnerOnly() {
  }

}
