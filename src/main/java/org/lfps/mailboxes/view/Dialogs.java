package org.lfps.mailboxes.view;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

/**
 * Questions asked in a dialog that waits for an answer. Each is replaceable
 * so tests can answer without a real dialog.
 */
final class Dialogs {

  /** Asks a yes or no question. */
  interface Confirm {
    /**
     * Asks the question.
     *
     * @param owner the window the dialog belongs to
     * @param question the question, such as "Close box 12?"
     * @param details what happens if the answer is yes
     * @return {@code true} if the answer was yes
     */
    boolean ask(Window owner, String question, String details);
  }

  /** Asks for a line of text. */
  interface AskText {
    /**
     * Asks for the text.
     *
     * @param owner the window the dialog belongs to
     * @param title the dialog's title
     * @param question what to enter
     * @param initial the text the field starts with
     * @return the text entered, or empty if cancelled
     */
    Optional<String> ask(Window owner, String title, String question, String initial);
  }

  /** Asks a yes or no question in a confirmation dialog. */
  static Confirm confirm = (owner, question, details) -> {
    var alert = new Alert(AlertType.CONFIRMATION, details, ButtonType.YES, ButtonType.NO);
    alert.initOwner(owner);
    alert.setHeaderText(question);
    AppWindow.applyTextSize(alert);
    return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
  };

  /** Asks for a line of text in a text input dialog. */
  static AskText askText = (owner, title, question, initial) -> {
    var dialog = new TextInputDialog(initial);
    dialog.initOwner(owner);
    dialog.setTitle(title);
    dialog.setHeaderText(question);
    AppWindow.applyTextSize(dialog);
    return dialog.showAndWait();
  };

  /** Not used: questions are asked with static methods. */
  private Dialogs() {
  }

}
