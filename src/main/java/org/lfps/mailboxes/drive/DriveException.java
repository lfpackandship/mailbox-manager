package org.lfps.mailboxes.drive;

/**
 * A problem signing in to or backing up to Google Drive. Its message is
 * written to be shown to the user as it is.
 */
public class DriveException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** What kind of problem it was, which decides what the user can do about it. */
  public enum Problem {
    /** Google couldn't be reached, usually because the internet is down. */
    OFFLINE,
    /** The sign-in has expired or been removed; the user must connect again. */
    SIGNED_OUT,
    /** The user cancelled signing in, or didn't finish in time. */
    CANCELLED,
    /** Anything else, such as an error from Google. */
    OTHER
  }

  private final Problem problem;

  DriveException(Problem problem, String message) {
    this(problem, message, null);
  }

  DriveException(Problem problem, String message, Throwable cause) {
    super(message, cause);
    this.problem = problem;
  }

  /**
   * Returns what kind of problem it was.
   *
   * @return the kind of problem
   */
  public Problem problem() {
    return problem;
  }

}
