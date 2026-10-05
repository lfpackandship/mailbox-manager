package org.lfps.mailboxes.drive;

/**
 * A problem signing in to or backing up to Google Drive. Its message is
 * written to be shown to the user as it is.
 */
public class DriveException extends RuntimeException {

  /** The version of this class's serialized form. */
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

  /** What kind of problem it is. */
  private final Problem problem;

  /**
   * Makes an exception.
   *
   * @param problem what kind of problem it is
   * @param message what went wrong, suitable for showing to the user
   */
  DriveException(Problem problem, String message) {
    this(problem, message, null);
  }

  /**
   * Makes an exception caused by another.
   *
   * @param problem what kind of problem it is
   * @param message what went wrong, suitable for showing to the user
   * @param cause the exception that caused it
   */
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
