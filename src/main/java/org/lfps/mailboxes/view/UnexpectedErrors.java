package org.lfps.mailboxes.view;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Window;

import org.lfps.mailboxes.data.ErrorLog;
import org.lfps.mailboxes.util.Errors;

/**
 * Catches any error the app doesn't handle itself, such as a fault in a
 * button's code, which would otherwise go unseen: the button would simply do
 * nothing, and the user might think what they did had worked. Instead the
 * error is written in the {@link ErrorLog} and a plain message says something
 * went wrong. Set up as the app starts; see {@link #install()}.
 */
public final class UnexpectedErrors {

  /**
   * The message last shown, so an error that repeats, as one in drawing a
   * screen can, shows it only once at a time; {@code null} before the first.
   * Only used on the JavaFX thread.
   */
  private static Alert shown;

  /**
   * Makes this the handler for errors nothing else catches, on every thread,
   * including the JavaFX thread that runs the screens.
   */
  public static void install() {
    Thread.setDefaultUncaughtExceptionHandler(UnexpectedErrors::handle);
  }

  /**
   * Writes an error in the log and tells the user. Nothing that goes wrong
   * here is passed on, since that would only bring it back here.
   *
   * @param thread the thread the error happened on
   * @param error the error
   */
  static void handle(Thread thread, Throwable error) {
    try {
      ErrorLog.record("Something unexpected went wrong (on the \"" + thread.getName() + "\" thread)", error);
      if (Platform.isFxApplicationThread()) {
        show(error);
      } else {
        Platform.runLater(() -> show(error));
      }
    } catch (RuntimeException | Error ignored) {
      // As above: the error is in the log if it could be written.
    }
  }

  /**
   * Shows the message, unless it's already showing. It doesn't wait to be
   * closed, since JavaFX can't wait while it's drawing a screen, which is
   * when some errors happen.
   *
   * @param error the error
   */
  private static void show(Throwable error) {
    if (shown != null && shown.isShowing()) {
      return;
    }
    try {
      var alert = new Alert(AlertType.ERROR, message(error));
      Window.getWindows().stream().filter(Window::isFocused).findFirst().ifPresent(alert::initOwner);
      alert.setTitle("Something Went Wrong");
      alert.setHeaderText("Something went wrong.");
      alert.setResizable(true);
      AppWindow.applyTextSize(alert);
      shown = alert;
      alert.show();
    } catch (RuntimeException e) {
      // Not passed on: from Platform.runLater it would come back to handle,
      // which would try to show it again, and so on. It's already logged.
    }
  }

  /**
   * Writes out the message.
   *
   * @param error the error
   * @return the message, in paragraphs
   */
  static String message(Throwable error) {
    return "Mailbox Manager ran into a problem it didn't expect, so what you just did may not have "
        + "finished. Check whether it worked, and if not, try again. If it keeps happening, close the app "
        + "and open it again." + Errors.detail(error) + "\n\n"
        + "The details were saved in " + ErrorLog.FILE_NAME + " in the data folder, for whoever helps you "
        + "with it.";
  }

  /** Not used: errors are handled with static methods. */
  private UnexpectedErrors() {
  }

}
