package org.lfps.mailboxes.view;

import java.util.List;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Questions asked in a dialog that waits for an answer. Each is replaceable
 * so tests can answer without a real dialog.
 */
final class Dialogs {

  /** Asks a yes or no question, with buttons named for what they do. */
  interface Confirm {
    /**
     * Asks the question.
     *
     * @param owner the window the dialog belongs to
     * @param question the question, such as "Close box 12?"
     * @param details what happens if the answer is yes
     * @param yes the button that goes ahead, such as "Close Box"
     * @param no the button that doesn't, such as "Keep It Open"
     * @return {@code true} if the answer was yes
     */
    boolean ask(Window owner, String question, String details, String yes, String no);
  }

  /** Asks a yes or no question that also has a choice to make if yes. */
  interface ConfirmWithChoice {
    /**
     * Asks the question.
     *
     * @param owner the window the dialog belongs to
     * @param question the question, such as "Close box 12?"
     * @param details what happens if the answer is yes
     * @param choiceQuestion what the choices answer, such as "The $20.00
     *     key deposit was:"
     * @param choices the choices, one of which is picked
     * @param initial the index of the choice picked to start with
     * @param yes the button that goes ahead, such as "Close Box"
     * @param no the button that doesn't, such as "Keep It Open"
     * @return the index of the choice picked if the answer was yes, or -1 if
     *     it was no
     */
    int ask(Window owner, String question, String details, String choiceQuestion, List<String> choices,
        int initial, String yes, String no);
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

  /**
   * Asks a yes or no question in a confirmation dialog. Its buttons say what
   * they do, which is harder to misread than Yes and No, and pressing Enter
   * picks the one that changes nothing.
   */
  static Confirm confirm = (owner, question, details, yes, no) -> {
    var yesButton = new ButtonType(yes, ButtonData.OK_DONE);
    var noButton = new ButtonType(no, ButtonData.CANCEL_CLOSE);
    var alert = new Alert(AlertType.CONFIRMATION, details, yesButton, noButton);
    alert.initOwner(owner);
    alert.setHeaderText(question);
    ((Button) alert.getDialogPane().lookupButton(yesButton)).setDefaultButton(false);
    ((Button) alert.getDialogPane().lookupButton(noButton)).setDefaultButton(true);
    AppWindow.applyTextSize(alert);
    return alert.showAndWait().filter(yesButton::equals).isPresent();
  };

  /**
   * Asks a yes or no question in a confirmation dialog, like
   * {@link #confirm}, with the choices as radio buttons under the details.
   */
  static ConfirmWithChoice confirmWithChoice = (owner, question, details, choiceQuestion, choices, initial,
      yes, no) -> {
    var yesButton = new ButtonType(yes, ButtonData.OK_DONE);
    var noButton = new ButtonType(no, ButtonData.CANCEL_CLOSE);
    var alert = new Alert(AlertType.CONFIRMATION, null, yesButton, noButton);
    alert.initOwner(owner);
    alert.setHeaderText(question);

    var detailsLabel = new Label(details);
    detailsLabel.setWrapText(true);
    var group = new ToggleGroup();
    var content = new VBox(8, detailsLabel, new Label(choiceQuestion));
    for (var i = 0; i < choices.size(); i++) {
      var radio = new RadioButton(choices.get(i));
      radio.setToggleGroup(group);
      radio.setUserData(i);
      radio.setSelected(i == initial);
      content.getChildren().add(radio);
    }
    alert.getDialogPane().setContent(content);

    ((Button) alert.getDialogPane().lookupButton(yesButton)).setDefaultButton(false);
    ((Button) alert.getDialogPane().lookupButton(noButton)).setDefaultButton(true);
    AppWindow.applyTextSize(alert);
    if (alert.showAndWait().filter(yesButton::equals).isEmpty()) {
      return -1;
    }
    return group.getSelectedToggle() == null ? initial : (Integer) group.getSelectedToggle().getUserData();
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
