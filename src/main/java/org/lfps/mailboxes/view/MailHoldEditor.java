package org.lfps.mailboxes.view;

import java.time.LocalDate;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.lfps.mailboxes.model.MailHold;

/**
 * The Mail Hold section of the Add and Edit Box forms, for holding a box
 * holder's mail from one day through another, such as while they're on
 * vacation, with a note on what to do with it. Leaving both days empty means
 * no hold; the hold clears by itself after its last day.
 */
final class MailHoldEditor extends VBox {

  /** The first day mail is held. */
  private final DatePicker fromField = new DatePicker();

  /** The last day mail is held. */
  private final DatePicker untilField = new DatePicker();

  /** What to do with the held mail, such as "forward it all on the 20th". */
  private final TextField noteField = new TextField();

  /** The folding section the editor sits in, headed with the days held. */
  private final TitledPane section;

  /**
   * Builds the editor in its folding section: From and Until days, a note,
   * and a Clear button that ends the hold. The section starts open if a hold
   * is set.
   *
   * @param hold the hold the box has, or {@code null} for none
   */
  MailHoldEditor(MailHold hold) {
    super(5);

    fromField.setId("holdFromField");
    fromField.setPromptText("first day");
    untilField.setId("holdUntilField");
    untilField.setPromptText("last day");
    noteField.setId("holdNoteField");
    noteField.setPromptText("e.g. forward it all on the 20th (optional)");
    GridPane.setHgrow(noteField, Priority.ALWAYS);

    var clearBtn = new Button("Clear");
    clearBtn.setId("clearHoldButton");
    clearBtn.setOnAction(e -> setHold(null));

    var hint = new Label("Hold the mail from the first day through the last, instead of putting it in the box or "
        + "forwarding it. The hold ends by itself after the last day.");
    hint.setWrapText(true);

    var fields = new GridPane();
    fields.setHgap(10);
    fields.setVgap(5);
    fields.addRow(0, new Label("From:"), fromField, new Label("Until:"), untilField, clearBtn);
    fields.add(new Label("Note:"), 0, 1);
    fields.add(noteField, 1, 1, 4, 1);
    getChildren().addAll(hint, fields);

    var body = new VBox(this);
    body.setPadding(new Insets(8));
    section = new TitledPane("", body);
    section.setId("mailHoldSection");
    section.setAnimated(false);
    // Headed with the days held, such as "Mail Hold (Oct 20 – Nov 3)", so it
    // shows while folded.
    section.textProperty().bind(Bindings.createStringBinding(() -> {
      var from = fromField.getValue();
      var until = untilField.getValue();
      return from == null || until == null || until.isBefore(from) ? "Mail Hold"
          : "Mail Hold (" + new MailHold(from, until, null).describeDays(LocalDate.now()) + ")";
    }, fromField.valueProperty(), untilField.valueProperty()));

    setHold(hold);
    section.setExpanded(hold != null);
  }

  /**
   * Returns the folding section to put on the form.
   *
   * @return the section
   */
  TitledPane section() {
    return section;
  }

  /**
   * Fills in the fields with a hold, or empties them.
   *
   * @param hold the hold, or {@code null} for none
   */
  void setHold(MailHold hold) {
    fromField.setValue(hold == null ? null : hold.getFrom());
    untilField.setValue(hold == null ? null : hold.getUntil());
    noteField.setText(hold == null || hold.getNote() == null ? "" : hold.getNote());
  }

  /**
   * Returns the hold entered.
   *
   * @return the hold, or {@code null} if both days and the note are empty
   * @throws IllegalArgumentException if a day is missing or the last day is
   *     before the first, with a message for the form; the section is
   *     opened to show it
   */
  MailHold getHold() {
    var from = fromField.getValue();
    var until = untilField.getValue();
    if (from == null && until == null && noteField.getText().isBlank()) {
      return null;
    }
    try {
      return new MailHold(from, until, noteField.getText());
    } catch (IllegalArgumentException e) {
      section.setExpanded(true);
      throw e;
    }
  }

  /**
   * Returns the field to outline when the hold has a mistake.
   *
   * @return the From field if it's empty, otherwise the Until field
   */
  DatePicker problemField() {
    return fromField.getValue() == null ? fromField : untilField;
  }

  /**
   * Has a form notice when anything here is changed, so leaving it can ask
   * before throwing that away.
   *
   * @param changes the form's changes
   */
  void watchFor(UnsavedChanges changes) {
    changes.watch(fromField.valueProperty(), untilField.valueProperty(), noteField.textProperty());
  }

}
