package org.lfps.mailboxes.view;

import java.sql.SQLException;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
    businessTitleField.setPromptText("Enter your business title (optional)");

    var firstNameField = new TextField();
    firstNameField.setPromptText("John");

    var lastNameField = new TextField();
    lastNameField.setPromptText("Doe");

    var boxNumber = new TextField();
    boxNumber.setPromptText("310");

    var boxNameField = new TextField();
    boxNameField.setPromptText("Enter a box nickname (optional)");

    var businessNamesEditor = new BusinessNamesEditor();

    var phoneField = new TextField();
    phoneField.setPromptText("(555) 123-4567");

    var emailField = new TextField();
    emailField.setPromptText("Enter your email (optional)");

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
          businessNamesEditor.getNames());

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

    var layout = new VBox(10,
        backBtn,
        new Label("First Name:"), firstNameField,
        new Label("Last Name:"), lastNameField,
        new Label("Business Title:"), businessTitleField,
        new Label("Alternate Business Names:"), businessNamesEditor,
        new Label("Box Number:"), boxNumber,
        new Label("Box Name:"), boxNameField,
        new Label("Phone Number:"), phoneField,
        new Label("Email:"), emailField,
        submitBtn,
        resultLabel);
    layout.setPadding(new Insets(20));

    stage.setScene(new Scene(layout, 640, 480));
    stage.show();
  }

  private AddBoxView() {
  }

}
