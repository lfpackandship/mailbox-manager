package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.Mailbox;
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
   */
  public static void show(Stage stage, Mailbox mailbox) {
    var firstNameField = new TextField(mailbox.getFirstName());
    var lastNameField = new TextField(mailbox.getLastName());
    var businessTitleField = new TextField(mailbox.getBusinessTitle());
    var boxNumberField = new TextField(mailbox.getBoxNumber());
    var boxNameField = new TextField(mailbox.getBoxName() == null ? "" : mailbox.getBoxName());
    var phoneField = new TextField(PhoneNumberFormatter.format(mailbox.getPhone()));
    phoneField.setTextFormatter(PhoneNumberFormatter.create());
    var emailField = new TextField(mailbox.getEmail());

    var businessNamesEditor = new BusinessNamesEditor();
    businessNamesEditor.setNames(mailbox.getAlternateBusinessNames());

    var endDateField = new DatePicker(mailbox.getEndDate());
    endDateField.setPrefWidth(160);

    var oneMonthBtn = new Button("1 Month");
    oneMonthBtn.setOnAction(e -> extendEndDate(endDateField, 1));

    var threeMonthsBtn = new Button("3 Months");
    threeMonthsBtn.setOnAction(e -> extendEndDate(endDateField, 3));

    var sixMonthsBtn = new Button("6 Months");
    sixMonthsBtn.setOnAction(e -> extendEndDate(endDateField, 6));

    var twelveMonthsBtn = new Button("12 Months");
    twelveMonthsBtn.setOnAction(e -> extendEndDate(endDateField, 12));

    for (var field : new TextField[] { firstNameField, lastNameField, businessTitleField,
        boxNumberField, boxNameField, phoneField, emailField }) {
      field.setPrefWidth(160);
    }

    var saveBtn = new Button("Save");
    var resultLabel = new Label();
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
          businessNamesEditor.getNames(), endDateField.getValue());

      try {
        repository.update(updated);
        ManageBoxesView.show(stage);
      } catch (SQLException ex) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText("Failed to save: " + ex.getMessage());
      }
    });

    var cancelBtn = new Button("Cancel");
    cancelBtn.setOnAction(e -> ManageBoxesView.show(stage));

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);

    grid.addRow(0, new Label("First Name:"), firstNameField, new Label("Last Name:"), lastNameField);
    grid.addRow(1, new Label("Business Title:"), businessTitleField, new Label("Box Number:"), boxNumberField);
    grid.addRow(2, new Label("Box Name:"), boxNameField, new Label("Phone Number:"), phoneField);
    grid.addRow(3, new Label("Email:"), emailField, new Label("End Date:"), endDateField);
    grid.add(new HBox(8, oneMonthBtn, threeMonthsBtn, sixMonthsBtn, twelveMonthsBtn), 0, 4, 4, 1);
    grid.add(new Label("Alternate Business Names:"), 0, 5, 4, 1);
    grid.add(businessNamesEditor, 0, 6, 4, 1);

    var layout = new VBox(8, cancelBtn, grid, saveBtn, resultLabel);
    layout.setPadding(new Insets(15));

    stage.setScene(new Scene(layout));
    stage.show();
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
