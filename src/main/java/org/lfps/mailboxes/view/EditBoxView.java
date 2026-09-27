package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.BoxNumbers;
import org.lfps.mailboxes.util.Validators;

/**
 * Form for editing an existing mailbox's details and saving changes.
 */
public class EditBoxView {

  /**
   * Builds and displays a pre-filled edit form for the given mailbox.
   *
   * @param stage the window to render the form into
   * @param mailbox the mailbox to edit
   * @param onDone navigates back to the calling screen after saving or cancelling
   */
  public static void show(Stage stage, Mailbox mailbox, Runnable onDone) {
    var firstNameField = new TextField(mailbox.getFirstName());
    var lastNameField = new TextField(mailbox.getLastName());
    var businessTitleField = new TextField(orEmpty(mailbox.getBusinessTitle()));
    var boxNumberField = new TextField(mailbox.getBoxNumber());
    boxNumberField.setId("boxNumberField");
    var boxNameField = new TextField(orEmpty(mailbox.getBoxName()));
    var phoneField = new TextField(PhoneNumberFormatter.format(mailbox.getPhone()));
    phoneField.setTextFormatter(PhoneNumberFormatter.create());
    var emailField = new TextField(orEmpty(mailbox.getEmail()));

    var businessNamesEditor = new BusinessNamesEditor();
    businessNamesEditor.setNames(mailbox.getAlternateBusinessNames());

    var forwardingEditor = new ForwardingAddressesEditor();
    forwardingEditor.setAddresses(mailbox.getForwardingAddresses());

    var endDateField = new DatePicker(mailbox.getEndDate());
    endDateField.setStyle("-fx-pref-width: 12em;");

    var rentalLengthButtons = RentalLengthButtons.create(months -> extendEndDate(endDateField, months));

    var notesField = notesField();
    notesField.setText(orEmpty(mailbox.getNotes()));

    for (var field : new TextField[] { firstNameField, lastNameField, businessTitleField,
        boxNumberField, boxNameField, phoneField, emailField }) {
      field.setStyle("-fx-pref-width: 12em;");
    }

    var saveBtn = new Button("Save");
    var resultLabel = new Label();
    resultLabel.setId("resultLabel");
    var repository = new MailboxRepository();

    saveBtn.setOnAction(e -> {
      var errors = new StringBuilder();

      if (firstNameField.getText().isBlank()) {
        errors.append("First name is required.\n");
      }
      if (lastNameField.getText().isBlank()) {
        errors.append("Last name is required.\n");
      }
      if (boxNumberField.getText().isBlank()) {
        errors.append("Box number is required.\n");
      } else if (!mailbox.isClosed()
          && !BoxNumbers.key(boxNumberField.getText()).equals(BoxNumbers.key(mailbox.getBoxNumber()))) {
        // A closed box doesn't hold its number, so only check a changed
        // number on an open box.
        errors.append(BoxNumberChecks.problem(boxNumberField.getText(), mailbox.getId()));
      }

      var phone = phoneField.getText();
      if (phone.isBlank()) {
        errors.append("Phone number is required.\n");
      } else if (!Validators.isValidPhone(phone)) {
        errors.append("Phone number is not valid.\n");
      }

      var email = emailField.getText();
      if (!email.isBlank() && !Validators.isValidEmail(email)) {
        errors.append("Email address is not valid.\n");
      }

      if (errors.length() > 0) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText(errors.toString().trim());
        return;
      }

      var updated = new Mailbox(mailbox.getId(), firstNameField.getText(), lastNameField.getText(),
          businessTitleField.getText(), boxNumberField.getText(), boxNameField.getText(), phone, email,
          businessNamesEditor.getNames(), endDateField.getValue(), forwardingEditor.getAddresses(),
          notesField.getText(), mailbox.getClosedDate());

      try {
        repository.update(updated);
        onDone.run();
      } catch (SQLException ex) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText("Failed to save: " + ex.getMessage());
      }
    });

    var cancelBtn = new Button("Cancel");
    cancelBtn.setOnAction(e -> onDone.run());

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);

    grid.addRow(0, new Label("First Name:"), firstNameField, new Label("Last Name:"), lastNameField);
    grid.addRow(1, new Label("Business Title:"), businessTitleField, new Label("Box Number:"), boxNumberField);
    grid.addRow(2, new Label("Box Name:"), boxNameField, new Label("Phone Number:"), phoneField);
    grid.addRow(3, new Label("Email:"), emailField, new Label("End Date:"), endDateField);
    grid.add(rentalLengthButtons, 0, 4, 4, 1);
    grid.add(new Label("Alternate Business Names:"), 0, 5, 4, 1);
    grid.add(businessNamesEditor, 0, 6, 4, 1);
    grid.add(new Label("Forwarding Addresses:"), 0, 7, 4, 1);
    grid.add(forwardingEditor, 0, 8, 4, 1);
    grid.add(new Label("Notes:"), 0, 9, 4, 1);
    grid.add(notesField, 0, 10, 4, 1);

    var layout = new VBox(8, cancelBtn, grid, saveBtn, resultLabel);
    layout.setPadding(new Insets(15));

    AppWindow.show(stage, layout);
  }

  /**
   * Builds the notes field shared by the Add and Edit Box forms.
   */
  static TextArea notesField() {
    var notesField = new TextArea();
    notesField.setId("notesField");
    notesField.setPromptText("e.g. paid in cash, ID on file, picks up for spouse (optional)");
    notesField.setPrefRowCount(3);
    notesField.setWrapText(true);
    return notesField;
  }

  // Optional fields can be missing (null) in data from older versions; an
  // empty field keeps the form's checks from failing on them.
  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  private static void extendEndDate(DatePicker endDateField, int months) {
    var current = endDateField.getValue();
    endDateField.setValue(current == null
        ? LocalDate.now().plusMonths(months)
        : current.plusMonths(months));
  }

  private EditBoxView() {
  }

}
