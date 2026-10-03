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
import org.lfps.mailboxes.model.RentalPeriod;
import org.lfps.mailboxes.util.Validators;

/**
 * Form for entering a new mailbox holder and saving it to the database. If
 * an end date is set, the rental is also recorded in the box's rental
 * history, with the amount paid if one is entered. Choosing a rental length
 * fills in its price for the box's size (see {@link PricesView}), and
 * entering the number of keys fills in the key deposit.
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

    var chooseBoxBtn = new Button("Choose…");
    chooseBoxBtn.setId("chooseBoxButton");
    chooseBoxBtn.setOnAction(e -> BoxInventoryView.chooseEmptyBox(stage, boxNumber::setText));

    var boxNameField = new TextField();
    boxNameField.setPromptText("optional");

    var businessNamesEditor = new BusinessNamesEditor();

    var forwardingEditor = new ForwardingAddressesEditor();

    var phoneField = new TextField();
    phoneField.setPromptText("(555) 123-4567");
    phoneField.setTextFormatter(PhoneNumberFormatter.create());

    var emailField = new TextField();
    emailField.setPromptText("optional");

    var endDateField = new DatePicker();
    endDateField.setStyle("-fx-pref-width: 12em;");

    var payment = new PaymentFields();

    var keys = new KeyFields(null, null);

    // The rental length chosen with the buttons, or 0 if none has been, so
    // its price can be filled in once the box number is known.
    var chosenMonths = new int[1];
    Runnable suggestPrice = () -> {
      if (chosenMonths[0] > 0) {
        payment.suggest(PricesView.priceFor(boxNumber.getText(), chosenMonths[0]));
      }
    };
    boxNumber.textProperty().addListener((obs, oldNumber, newNumber) -> suggestPrice.run());

    var rentalLengthButtons = RentalLengthButtons.create(months -> {
      endDateField.setValue(LocalDate.now().plusMonths(months));
      chosenMonths[0] = months;
      suggestPrice.run();
    });

    var notesField = EditBoxView.notesField();

    for (var field : new TextField[] { firstNameField, lastNameField, businessTitleField,
        boxNameField, phoneField, emailField }) {
      field.setStyle("-fx-pref-width: 12em;");
    }
    // Narrower, to leave room for the Choose button in the same column.
    boxNumber.setStyle("-fx-pref-width: 6em;");

    var submitBtn = new Button("Submit");
    var resultLabel = new Label();
    var repository = new MailboxRepository();

    submitBtn.setOnAction(e -> {
      var errors = new StringBuilder();

      if (boxNumber.getText().isBlank()) {
        errors.append("Box number is required.\n");
      } else {
        errors.append(BoxNumberChecks.problem(boxNumber.getText(), 0));
      }

      var phone = phoneField.getText();
      if (!phone.isBlank() && !Validators.isValidPhone(phone)) {
        errors.append("Phone number is not valid.\n");
      }

      var email = emailField.getText();
      if (!email.isBlank() && !Validators.isValidEmail(email)) {
        errors.append("Email address is not valid.\n");
      }

      // The rental runs from today to the end date; record it in the
      // history along with any payment.
      var today = LocalDate.now();
      var endDate = endDateField.getValue();
      var hasRental = endDate != null && endDate.isAfter(today);
      Long amount = null;
      try {
        amount = payment.amountCents();
      } catch (IllegalArgumentException ex) {
        errors.append(ex.getMessage()).append('\n');
      }
      if ((amount != null || !payment.method().isEmpty()) && !hasRental) {
        errors.append("Set an end date after today to record a payment.\n");
      }

      Integer keyCount = null;
      Long keyDeposit = null;
      try {
        keyCount = keys.count();
        keyDeposit = keys.depositCents();
      } catch (IllegalArgumentException ex) {
        errors.append(ex.getMessage()).append('\n');
      }

      if (errors.length() > 0) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText(errors.toString().trim());
        return;
      }

      var mailbox = new Mailbox(0, firstNameField.getText(), lastNameField.getText(),
          businessTitleField.getText(), boxNumber.getText(), boxNameField.getText(), phone, email,
          businessNamesEditor.getNames(), endDate, forwardingEditor.getAddresses(), notesField.getText(), null,
          keyCount, keyDeposit);
      var rental = hasRental ? new RentalPeriod(0, 0, today, today, endDate, amount, payment.method(), null) : null;

      try {
        repository.insert(mailbox, rental);
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
    grid.addRow(1, new Label("Business Title:"), businessTitleField, new Label("Box Number:"),
        new HBox(5, boxNumber, chooseBoxBtn));
    grid.addRow(2, new Label("Box Name:"), boxNameField, new Label("Phone Number:"), phoneField);
    grid.addRow(3, new Label("Email:"), emailField, new Label("End Date:"), endDateField);
    grid.add(rentalLengthButtons, 0, 4, 4, 1);
    grid.addRow(5, new Label("Amount Paid:"), payment.amountField, new Label("Paid By:"), payment.methodField);
    grid.addRow(6, new Label("Keys:"), keys.countField, new Label("Key Deposit:"), keys.depositField);
    grid.add(new Label("Alternate Business Names:"), 0, 7, 4, 1);
    grid.add(businessNamesEditor, 0, 8, 4, 1);
    grid.add(new Label("Forwarding Addresses:"), 0, 9, 4, 1);
    grid.add(forwardingEditor, 0, 10, 4, 1);
    grid.add(new Label("Notes:"), 0, 11, 4, 1);
    grid.add(notesField, 0, 12, 4, 1);

    var layout = new VBox(8, backBtn, grid, submitBtn, resultLabel);
    layout.setPadding(new Insets(15));

    AppWindow.show(stage, layout);
  }

  private AddBoxView() {
  }

}
