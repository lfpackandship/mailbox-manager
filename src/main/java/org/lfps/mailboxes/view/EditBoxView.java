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

public class EditBoxView {

  public static void show(Stage stage, Mailbox mailbox) {
    var firstNameField = new TextField(mailbox.getFirstName());
    var lastNameField = new TextField(mailbox.getLastName());
    var businessTitleField = new TextField(mailbox.getBusinessTitle());
    var boxNumberField = new TextField(mailbox.getBoxNumber());
    var boxNameField = new TextField(mailbox.getBoxName() == null ? "" : mailbox.getBoxName());
    var phoneField = new TextField(mailbox.getPhone());
    var emailField = new TextField(mailbox.getEmail());

    var businessNamesEditor = new BusinessNamesEditor();
    businessNamesEditor.setNames(mailbox.getAlternateBusinessNames());

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
          businessNamesEditor.getNames());

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

    var layout = new VBox(10,
        cancelBtn,
        new Label("First Name:"), firstNameField,
        new Label("Last Name:"), lastNameField,
        new Label("Business Title:"), businessTitleField,
        new Label("Alternate Business Names:"), businessNamesEditor,
        new Label("Box Number:"), boxNumberField,
        new Label("Box Name:"), boxNameField,
        new Label("Phone Number:"), phoneField,
        new Label("Email:"), emailField,
        saveBtn,
        resultLabel);
    layout.setPadding(new Insets(20));

    stage.setScene(new Scene(layout, 640, 480));
    stage.show();
  }

  private EditBoxView() {
  }

}
