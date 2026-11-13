package org.lfps.mailboxes.util;

import java.util.regex.Pattern;

/**
 * Helpers for turning errors into messages to show the user. Each message is
 * a plain sentence saying what didn't work, written where it's shown, followed
 * by the error's own explanation: in brackets from {@link #detail(Throwable)}
 * when it's technical, for whoever helps if it keeps happening, such as
 * "Couldn't save. Please try again. (database is locked)"; or as a sentence
 * from {@link #reason(Throwable)} when the app wrote it for the user, such as
 * "Couldn't read boxes.txt. Choose an Excel file (.xlsx) or a CSV file (.csv)."
 */
public final class Errors {

  /**
   * How the database driver words its errors, such as "[SQLITE_BUSY] The
   * database file is locked (database is locked)": a code, a general
   * description, and in brackets the specific problem, which is the part
   * worth showing.
   */
  private static final Pattern SQLITE_MESSAGE = Pattern.compile("^\\[SQLITE_\\w+\\][^(]*\\((.+)\\)$", Pattern.DOTALL);

  /**
   * Returns an error's own explanation, to add after a sentence saying what
   * didn't work. It's the innermost cause's, which usually says what actually
   * went wrong (such as "No space left on device") rather than what was being
   * attempted, with the database driver's code and general description left
   * off.
   *
   * @param error the error to describe
   * @return the explanation in brackets after a space, such as
   *     {@code " (database is locked)"}, or an empty string if the error
   *     doesn't give one
   */
  public static String detail(Throwable error) {
    var message = explanation(error);
    return message == null ? "" : " (" + message + ")";
  }

  /**
   * Returns an error's own explanation as a sentence to add after another,
   * for errors whose message is already written for the user, such as one
   * saying to choose an Excel file, which would read as a footnote in
   * brackets. Found the same way as {@link #detail(Throwable)}.
   *
   * @param error the error to describe
   * @return the explanation after a space, such as
   *     {@code " Choose an Excel file (.xlsx) or a CSV file (.csv)."}, or an
   *     empty string if the error doesn't give one
   */
  public static String reason(Throwable error) {
    var message = explanation(error);
    return message == null ? "" : " " + message;
  }

  /**
   * Finds an error's own explanation; see {@link #detail(Throwable)}.
   *
   * @param error the error to describe
   * @return the explanation, or {@code null} if the error doesn't give one
   */
  private static String explanation(Throwable error) {
    String message = null;
    for (var cause = error; cause != null; cause = cause.getCause()) {
      var own = cause.getMessage();
      // An error made from another without a message of its own takes the
      // other's class name as its message, such as
      // "java.lang.NullPointerException", which means nothing to the user.
      var copied = cause.getCause() != null && cause.getCause().toString().equals(own);
      if (own != null && !own.isBlank() && !copied) {
        message = own.trim();
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    if (message == null) {
      return null;
    }
    var sqlite = SQLITE_MESSAGE.matcher(message);
    return sqlite.matches() ? sqlite.group(1).trim() : message;
  }

  /** Not used: errors are described with static methods. */
  private Errors() {
  }

}
