package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;

import javafx.geometry.Insets;
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
 * Form for entering a new mailbox holder and saving it to the database.
 */
public class AddBoxView {

  /**
   * Builds and displays the Add New Box form on the given stage.
   *
   * @param stage the window to render the form into
   */
  public static void show(Stage stage) {
    var businessTitleField = new TextField();
    businessTitleField.setPromptText("Acme Inc (optional)");

    var firstNameField = new TextField();
    firstNameField.setPromptText("John");

    var lastNameField = new TextField();
    lastNameField.setPromptText("Doe");

    var boxNumber = new TextField();
    boxNumber.setPromptText("310");

    var boxNameField = new TextField();
    boxNameField.setPromptText("optional");

    var businessNamesEditor = new BusinessNamesEditor();

    var phoneField = new TextField();
    phoneField.setPromptText("(555) 123-4567");
    phoneField.setTextFormatter(PhoneNumberFormatter.create());

    var emailField = new TextField();
    emailField.setPromptText("optional");

    var endDateField = new DatePicker();
    endDateField.setPrefWidth(160);

    var oneMonthBtn = new Button("1 Month");
    oneMonthBtn.setOnAction(e -> endDateField.setValue(LocalDate.now().plusMonths(1)));

    var threeMonthsBtn = new Button("3 Months");
    threeMonthsBtn.setOnAction(e -> endDateField.setValue(LocalDate.now().plusMonths(3)));

    var sixMonthsBtn = new Button("6 Months");
    sixMonthsBtn.setOnAction(e -> endDateField.setValue(LocalDate.now().plusMonths(6)));

    var twelveMonthsBtn = new Button("12 Months");
    twelveMonthsBtn.setOnAction(e -> endDateField.setValue(LocalDate.now().plusMonths(12)));

    for (var field : new TextField[] { firstNameField, lastNameField, businessTitleField,
        boxNumber, boxNameField, phoneField, emailField }) {
      field.setPrefWidth(160);
    }

    var submitBtn = new Button("Submit");
    var resultLabel = new Label();
    var repository = new MailboxRepository();

    submitBtn.setOnAction(e -> {
      var errors = new StringBuilder();

      if (firstNameField.getText().isBlank()) {
        errors.append("First name is required.\n");
      }
      if (lastNameField.getText().isBlank()) {
        errors.append("Last name is required.\n");
      }
      if (boxNumber.getText().isBlank()) {
        errors.append("Box number is required.\n");
      } else {
        try {
          if (repository.isBoxNumberTaken(boxNumber.getText(), 0)) {
            errors.append("Box " + boxNumber.getText().trim() + " is already assigned to someone else.\n");
          }
        } catch (SQLException ex) {
          errors.append("Could not check box number: " + ex.getMessage() + "\n");
        }
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

      var mailbox = new Mailbox(0, firstNameField.getText(), lastNameField.getText(),
          businessTitleField.getText(), boxNumber.getText(), boxNameField.getText(), phone, email,
          businessNamesEditor.getNames(), endDateField.getValue());

      try {
        repository.insert(mailbox);
        resultLabel.setStyle("-fx-text-fill: green;");
        resultLabel.setText("Saved");
      } catch (SQLException ex) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText("Failed to save: " + ex.getMessage());
      }
    });

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);

    grid.addRow(0, new Label("First Name:"), firstNameField, new Label("Last Name:"), lastNameField);
    grid.addRow(1, new Label("Business Title:"), businessTitleField, new Label("Box Number:"), boxNumber);
    grid.addRow(2, new Label("Box Name:"), boxNameField, new Label("Phone Number:"), phoneField);
    grid.addRow(3, new Label("Email:"), emailField, new Label("End Date:"), endDateField);
    grid.add(new HBox(8, oneMonthBtn, threeMonthsBtn, sixMonthsBtn, twelveMonthsBtn), 0, 4, 4, 1);
    grid.add(new Label("Alternate Business Names:"), 0, 5, 4, 1);
    grid.add(businessNamesEditor, 0, 6, 4, 1);

    var layout = new VBox(8, backBtn, grid, submitBtn, resultLabel);
    layout.setPadding(new Insets(15));

    AppWindow.show(stage, layout);
  }

  private AddBoxView() {
  }

}
