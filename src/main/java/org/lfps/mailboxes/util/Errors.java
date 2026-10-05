package org.lfps.mailboxes.util;

/**
 * Helpers for turning errors into messages to show the user.
 */
public final class Errors {

  /**
   * Returns the message of the innermost cause of an error, which usually
   * says what actually went wrong (such as "No space left on device") rather
   * than what was being attempted.
   *
   * @param error the error to describe
   * @return the most specific message available
   */
  public static String rootMessage(Throwable error) {
    var cause = error;
    while (cause.getCause() != null && cause.getCause().getMessage() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  /** Not used: errors are described with static methods. */
  private Errors() {
  }

}
