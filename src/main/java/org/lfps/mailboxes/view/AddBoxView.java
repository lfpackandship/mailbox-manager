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
    show(stage, "");
  }

  /**
   * Builds and displays an empty Add New Box form, with a message at the top,
   * as after saving a box.
   *
   * @param stage the window to render the form into
   * @param saved says which box was just saved, or an empty string
   */
  private static void show(Stage stage, String saved) {
    var businessTitleField = new TextField();

    var firstNameField = new TextField();

    var lastNameField = new TextField();

    var boxNumber = new TextField();

    var chooseBoxBtn = new Button("Choose…");
    chooseBoxBtn.setId("chooseBoxButton");
    chooseBoxBtn.setOnAction(e -> BoxInventoryView.chooseEmptyBox(stage, boxNumber::setText));

    var boxNameField = new TextField();

    var businessNamesEditor = new BusinessNamesEditor();

    var forwardingEditor = new ForwardingAddressesEditor();

    // Folded up to start with, as most boxes have neither.
    var namesSection = EditBoxView.section("Alternate Business Names", businessNamesEditor,
        businessNamesEditor.entries());
    var forwardingSection = EditBoxView.section("Forwarding Addresses", forwardingEditor,
        forwardingEditor.entries());

    var phoneField = new TextField();
    phoneField.setTextFormatter(PhoneNumberFormatter.create());

    var emailField = new TextField();

    var endDateField = new DatePicker();
    endDateField.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");

    var payment = new PaymentFields();

    var keys = new KeyFields(null, null);
    var forwardingOnlyBox = EditBoxView.forwardingOnlyBox(false, keys.countField, keys.depositField,
        chooseBoxBtn);

    // The rental length chosen with the buttons, or 0 if none has been, so
    // its price can be filled in once the box number is known.
    var chosenMonths = new int[1];
    // Box sizes' prices don't apply to forwarding.
    Runnable suggestPrice = () -> {
      if (chosenMonths[0] > 0 && !forwardingOnlyBox.isSelected()) {
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

    EditBoxView.hints(firstNameField, lastNameField, businessTitleField, boxNumber, boxNameField, phoneField,
        emailField);
    for (var field : new TextField[] { firstNameField, lastNameField, businessTitleField,
        boxNameField, phoneField, emailField }) {
      field.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");
    }
    // Narrower, to leave room for the Choose button in the same column.
    boxNumber.setStyle("-fx-pref-width: 8em; -fx-max-width: 8em;");

    // Watched from here on, so the prices and deposits filled in as the form
    // opens don't count as changes.
    var changes = new UnsavedChanges().watch(firstNameField.textProperty(), lastNameField.textProperty(),
        businessTitleField.textProperty(), boxNumber.textProperty(), boxNameField.textProperty(),
        phoneField.textProperty(), emailField.textProperty(), endDateField.valueProperty(),
        payment.amountField.textProperty(), payment.methodField.getEditor().textProperty(),
        keys.countField.textProperty(), keys.depositField.textProperty(),
        forwardingOnlyBox.selectedProperty(), notesField.textProperty());
    businessNamesEditor.watchFor(changes);
    forwardingEditor.watchFor(changes);

    var saveBtn = new Button("Save");
    var resultLabel = new Label(saved);
    resultLabel.setId("resultLabel");
    resultLabel.setStyle("-fx-text-fill: green;");
    var repository = new MailboxRepository();

    var problems = new FieldProblems();
    saveBtn.setOnAction(e -> {
      var errors = new StringBuilder();
      problems.clear();

      // A name or address typed but not added with its Add button is saved
      // too, rather than quietly left out.
      businessNamesEditor.addTyped();
      var forwardingProblem = forwardingEditor.addTypedBeforeSaving();
      if (forwardingProblem != null) {
        errors.append(forwardingProblem).append('\n');
        forwardingSection.setExpanded(true);
      }

      var forwardingOnly = forwardingOnlyBox.isSelected();
      if (boxNumber.getText().isBlank()) {
        errors.append("Box number is required.\n");
        problems.mark(boxNumber);
      } else if (!forwardingOnly) {
        var problem = BoxNumberChecks.problem(boxNumber.getText(), 0);
        if (!problem.isEmpty()) {
          errors.append(problem);
          problems.mark(boxNumber);
        }
      }

      var phone = phoneField.getText();
      if (!phone.isBlank() && !Validators.isValidPhone(phone)) {
        errors.append("Phone number is not valid.\n");
        problems.mark(phoneField);
      }

      var email = emailField.getText();
      if (!email.isBlank() && !Validators.isValidEmail(email)) {
        errors.append("Email address is not valid.\n");
        problems.mark(emailField);
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
        problems.mark(payment.amountField);
      }
      if ((amount != null || !payment.method().isEmpty()) && !hasRental) {
        errors.append("Set an end date after today to record a payment.\n");
        problems.mark(endDateField);
      }

      Integer keyCount = null;
      Long keyDeposit = null;
      try {
        keyCount = keys.count();
      } catch (IllegalArgumentException ex) {
        errors.append(ex.getMessage()).append('\n');
        problems.mark(keys.countField);
      }
      try {
        keyDeposit = keys.depositCents();
      } catch (IllegalArgumentException ex) {
        errors.append(ex.getMessage()).append('\n');
        problems.mark(keys.depositField);
      }

      if (errors.length() > 0) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText(errors.toString().trim());
        problems.focusFirst();
        return;
      }

      var mailbox = new Mailbox(0, firstNameField.getText(), lastNameField.getText(),
          businessTitleField.getText(), boxNumber.getText(), boxNameField.getText(), phone, email,
          businessNamesEditor.getNames(), endDate, forwardingEditor.getAddresses(), notesField.getText(), null,
          keyCount, keyDeposit, forwardingOnly);
      var rental = hasRental ? new RentalPeriod(0, 0, today, today, endDate, amount, payment.method(), null) : null;

      try {
        repository.insert(mailbox, rental);
        // A fresh form, so clicking Save again can't add the box twice.
        show(stage, "Saved " + BoxLabels.boxAndHolder(mailbox) + ". The form is empty again, ready for the "
            + "next box.");
      } catch (SQLException ex) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText("Failed to save: " + ex.getMessage());
      }
    });

    var cancelBtn = new Button("Cancel");
    cancelBtn.setOnAction(e -> {
      if (AppWindow.mayLeave()) {
        MainMenuView.show(stage);
      }
    });

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);

    grid.addRow(0, new Label("First Name:"), firstNameField, new Label("Last Name:"), lastNameField);
    grid.addRow(1, new Label("Business Title:"), businessTitleField, new Label("Box Number:"),
        new HBox(5, boxNumber, chooseBoxBtn));
    grid.add(forwardingOnlyBox, 0, 2, 4, 1);
    grid.addRow(3, new Label("Box Name:"), boxNameField, new Label("Phone Number:"), phoneField);
    grid.addRow(4, new Label("Email:"), emailField, new Label("End Date:"), endDateField);
    grid.add(rentalLengthButtons, 0, 5, 4, 1);
    grid.addRow(6, new Label("Amount Paid:"), payment.amountField, new Label("Paid By:"), payment.methodField);
    grid.addRow(7, new Label("Keys:"), keys.countField, new Label("Key Deposit:"), keys.depositField);
    grid.add(namesSection, 0, 8, 4, 1);
    grid.add(forwardingSection, 0, 9, 4, 1);
    grid.add(new Label("Notes:"), 0, 10, 4, 1);
    grid.add(notesField, 0, 11, 4, 1);

    var layout = new VBox(8, grid);
    layout.setPadding(new Insets(15));

    AppWindow.show(stage, EditBoxView.buttonBar(cancelBtn, saveBtn, resultLabel), layout);
    AppWindow.setLeaveCheck(() -> changes.confirmLeave(stage));
  }

  /** Not used: the screen is built with static methods. */
  private AddBoxView() {
  }

}
