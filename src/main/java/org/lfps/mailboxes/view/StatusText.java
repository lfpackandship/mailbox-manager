package org.lfps.mailboxes.view;

import javafx.scene.control.Label;

/**
 * Shows how something went in a screen's status line, the label under its
 * buttons: problems in red and successes in green, so every screen looks the
 * same. Problems are worded as described in
 * {@link org.lfps.mailboxes.util.Errors}.
 */
final class StatusText {

  /**
   * Shows a problem in red.
   *
   * @param label the status line
   * @param message what went wrong
   */
  static void showError(Label label, String message) {
    label.setStyle("-fx-text-fill: red;");
    label.setText(message);
  }

  /**
   * Shows a success in green.
   *
   * @param label the status line
   * @param message what was done, such as "Saved"
   */
  static void showSuccess(Label label, String message) {
    label.setStyle("-fx-text-fill: green;");
    label.setText(message);
  }

  /** Not used: messages are shown with static methods. */
  private StatusText() {
  }

}
