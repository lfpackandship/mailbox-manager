package org.lfps.mailboxes.view;

import java.sql.SQLException;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
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
    firstNameField.setId("firstNameField");
    lastNameField.setId("lastNameField");
    var businessTitleField = new TextField(orEmpty(mailbox.getBusinessTitle()));
    var boxNumberField = new TextField(mailbox.getBoxNumber());
    boxNumberField.setId("boxNumberField");
    var boxNameField = new TextField(orEmpty(mailbox.getBoxName()));
    var phoneField = new TextField(PhoneNumberFormatter.format(mailbox.getPhone()));
    phoneField.setTextFormatter(PhoneNumberFormatter.create());
    phoneField.setId("phoneField");
    var emailField = new TextField(orEmpty(mailbox.getEmail()));

    var businessNamesEditor = new BusinessNamesEditor();
    businessNamesEditor.setNames(mailbox.getAlternateBusinessNames());

    var forwardingEditor = new ForwardingAddressesEditor();
    forwardingEditor.setAddresses(mailbox.getForwardingAddresses());

    var namesSection = section("Alternate Business Names", businessNamesEditor, businessNamesEditor.entries());
    var forwardingSection = section("Forwarding Addresses", forwardingEditor, forwardingEditor.entries());

    var endDateField = new DatePicker(mailbox.getEndDate());
    endDateField.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");

    // Renewing is done with Renew…, which records the payment as well. The end
    // date here is only for correcting a mistake.
    var renewHint = new Label("To renew, use Renew… on Manage Boxes, Renewals, or the box's details, so the "
        + "payment is recorded too. Change the end date here only to correct a mistake.");
    renewHint.setId("renewHint");
    renewHint.setWrapText(true);
    renewHint.setStyle("-fx-font-size: 0.9em; -fx-text-fill: #555555;");

    var keys = new KeyFields(mailbox.getKeyCount(), mailbox.getKeyDepositCents());
    var forwardingOnlyBox = forwardingOnlyBox(mailbox.isForwardingOnly(), keys.countField, keys.depositField);

    var notesField = notesField();
    notesField.setText(orEmpty(mailbox.getNotes()));

    hints(firstNameField, lastNameField, businessTitleField, boxNumberField, boxNameField, phoneField, emailField);
    for (var field : new TextField[] { firstNameField, lastNameField, businessTitleField,
        boxNumberField, boxNameField, phoneField, emailField }) {
      field.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");
    }

    // Watched once filled in, so only what's changed after that counts.
    var changes = new UnsavedChanges().watch(firstNameField.textProperty(), lastNameField.textProperty(),
        businessTitleField.textProperty(), boxNumberField.textProperty(), boxNameField.textProperty(),
        phoneField.textProperty(), emailField.textProperty(), endDateField.valueProperty(),
        keys.countField.textProperty(), keys.depositField.textProperty(),
        forwardingOnlyBox.selectedProperty(), notesField.textProperty());
    businessNamesEditor.watchFor(changes);
    forwardingEditor.watchFor(changes);

    var saveBtn = new Button("Save");
    var resultLabel = new Label();
    resultLabel.setId("resultLabel");
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
      if (boxNumberField.getText().isBlank()) {
        errors.append("Box number is required.\n");
        problems.mark(boxNumberField);
      } else if (!mailbox.isClosed() && !forwardingOnly
          && (mailbox.isForwardingOnly()
              || !BoxNumbers.key(boxNumberField.getText()).equals(BoxNumbers.key(mailbox.getBoxNumber())))) {
        // Closed and forwarding-only boxes don't hold their numbers, so only
        // check an open box whose number changed or that now holds it.
        var problem = BoxNumberChecks.problem(boxNumberField.getText(), mailbox.getId());
        if (!problem.isEmpty()) {
          errors.append(problem);
          problems.mark(boxNumberField);
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

      var updated = new Mailbox(mailbox.getId(), firstNameField.getText(), lastNameField.getText(),
          businessTitleField.getText(), boxNumberField.getText(), boxNameField.getText(), phone, email,
          businessNamesEditor.getNames(), endDateField.getValue(), forwardingEditor.getAddresses(),
          notesField.getText(), mailbox.getClosedDate(), keyCount, keyDeposit, forwardingOnly);

      try {
        repository.update(updated);
        onDone.run();
      } catch (SQLException ex) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText("Failed to save: " + ex.getMessage());
      }
    });

    var cancelBtn = new Button("Cancel");
    cancelBtn.setOnAction(e -> {
      if (AppWindow.mayLeave()) {
        onDone.run();
      }
    });

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);

    grid.addRow(0, new Label("First Name:"), firstNameField, new Label("Last Name:"), lastNameField);
    grid.addRow(1, new Label("Business Title:"), businessTitleField, new Label("Box Number:"), boxNumberField);
    grid.add(forwardingOnlyBox, 0, 2, 4, 1);
    grid.addRow(3, new Label("Box Name:"), boxNameField, new Label("Phone Number:"), phoneField);
    grid.addRow(4, new Label("Email:"), emailField, new Label("End Date:"), endDateField);
    grid.add(renewHint, 0, 5, 4, 1);
    grid.addRow(6, new Label("Keys:"), keys.countField, new Label("Key Deposit:"), keys.depositField);
    grid.add(namesSection, 0, 7, 4, 1);
    grid.add(forwardingSection, 0, 8, 4, 1);
    grid.add(new Label("Notes:"), 0, 9, 4, 1);
    grid.add(notesField, 0, 10, 4, 1);

    var layout = new VBox(8, grid);
    layout.setPadding(new Insets(15));

    AppWindow.show(stage, buttonBar(cancelBtn, saveBtn, resultLabel), layout);
    AppWindow.setLeaveCheck(() -> changes.confirmLeave(stage));
  }

  /**
   * Builds the forwarding-only check box shared by the Add and Edit Box forms,
   * which turns off the parts of the form that only apply to a box rented here.
   *
   * @param checked whether it starts ticked
   * @param rentedOnly the controls that only apply to a box rented here
   * @return the checkbox
   */
  static CheckBox forwardingOnlyBox(boolean checked, Node... rentedOnly) {
    var box = new CheckBox("Forwarding only: they don't rent a box here, we forward their mail. "
        + "The box number can be one someone else rents now.");
    box.setId("forwardingOnlyBox");
    box.setWrapText(true);
    box.selectedProperty().addListener((obs, was, now) -> {
      for (var node : rentedOnly) {
        node.setDisable(now);
      }
    });
    box.setSelected(checked);
    for (var node : rentedOnly) {
      node.setDisable(checked);
    }
    return box;
  }

  /**
   * Builds the bar shared by the Add and Edit Box forms that stays in view
   * above the form as it scrolls: the button that leaves the form, the Save
   * button, and under them what happened when saving, which is hidden while
   * there's nothing to say.
   *
   * @param leave the button that leaves the form
   * @param save the button that saves it
   * @param result where saving says what happened
   * @return the bar
   */
  static VBox buttonBar(Button leave, Button save, Label result) {
    result.setWrapText(true);
    result.managedProperty().bind(result.textProperty().isNotEmpty());
    result.visibleProperty().bind(result.textProperty().isNotEmpty());
    var bar = new VBox(8, new HBox(10, leave, save), result);
    bar.setPadding(new Insets(10, 15, 10, 15));
    bar.setStyle("-fx-border-color: transparent transparent lightgray transparent;");
    return bar;
  }

  /**
   * Puts a list's editor, such as the forwarding addresses, in a section that
   * can be folded up to save room, headed with how many entries it has, such
   * as "Forwarding Addresses (2)". Shared by the Add and Edit Box forms.
   *
   * @param name the section's name
   * @param editor the editor
   * @param entries the editor's entries, which the heading counts
   * @return the section, open if there are entries already and folded if not
   */
  static TitledPane section(String name, Node editor, ObservableList<?> entries) {
    var body = new VBox(editor);
    body.setPadding(new Insets(8));
    var section = new TitledPane("", body);
    section.setAnimated(false);
    section.textProperty().bind(Bindings.createStringBinding(
        () -> entries.isEmpty() ? name : name + " (" + entries.size() + ")", entries));
    section.setExpanded(!entries.isEmpty());
    return section;
  }

  /**
   * Gives the Add and Edit Box forms' fields the same hints, shown while a
   * field is empty, which say which fields can be left blank. Only the box
   * number is required. The box name, a nickname few boxes have, also
   * explains itself when pointed at.
   *
   * @param firstName the first name field
   * @param lastName the last name field
   * @param businessTitle the business title field
   * @param boxNumber the box number field
   * @param boxName the box name field
   * @param phone the phone number field
   * @param email the email field
   */
  static void hints(TextField firstName, TextField lastName, TextField businessTitle, TextField boxNumber,
      TextField boxName, TextField phone, TextField email) {
    firstName.setPromptText("John (optional)");
    lastName.setPromptText("Doe (optional)");
    businessTitle.setPromptText("Acme Inc (optional)");
    boxNumber.setPromptText("310 (required)");
    boxName.setPromptText("nickname (optional)");
    boxName.setTooltip(new Tooltip("A nickname for the box, if it helps to tell it apart. It's shown in the "
        + "box's details and found by searching on Manage Boxes. Most boxes don't need one."));
    phone.setPromptText("(555) 123-4567 (optional)");
    email.setPromptText("optional");
  }

  /**
   * Builds the notes field shared by the Add and Edit Box forms.
   *
   * @return the field
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
  /**
   * Returns text, or an empty string in place of {@code null}.
   *
   * @param value the text, or {@code null}
   * @return the text, never {@code null}
   */
  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  /** Not used: the form is built with static methods. */
  private EditBoxView() {
  }

}
