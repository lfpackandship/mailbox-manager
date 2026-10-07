package org.lfps.mailboxes.view;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.util.Validators;

/**
 * A reusable control for adding and removing a box holder's forwarding
 * addresses, each with an optional note, shared by {@link AddBoxView} and
 * {@link EditBoxView}.
 */
public class ForwardingAddressesEditor extends VBox {

  /** The addresses added so far. */
  private final ListView<ForwardingAddress> addressList = new ListView<>();

  /** Where the street is typed. */
  private final TextField streetField = field("streetField", "123 Main St", 14);

  /** Where the apartment or unit is typed, if there is one. */
  private final TextField unitField = field("unitField", "Apt 4 (optional)", 9);

  /** Where the city is typed. */
  private final TextField cityField = field("cityField", "Springfield", 10);

  /** Where the two-letter state is typed. */
  private final TextField stateField = field("stateField", "IL", 3);

  /** Where the ZIP code is typed. */
  private final TextField zipField = field("zipField", "62701", 6);

  /** Where a note about the address, such as when to use it, is typed. */
  private final TextField noteField = field("noteField", "e.g. summer (optional)", 14);

  /** Says what's missing from an address that can't be added, in red. */
  private final Label errorLabel = new Label();

  /**
   * Builds the editor: fields for one address with an Add button, a list of
   * the addresses entered so far, and a Remove Selected button.
   */
  public ForwardingAddressesEditor() {
    super(5);

    errorLabel.setId("forwardingErrorLabel");
    errorLabel.setStyle("-fx-text-fill: red;");

    var addBtn = new Button("Add Address");
    addBtn.setId("addForwardingButton");
    addBtn.setOnAction(e -> addTyped());

    var removeBtn = new Button("Remove Selected");
    removeBtn.setId("removeForwardingButton");
    removeBtn.disableProperty().bind(addressList.getSelectionModel().selectedItemProperty().isNull());
    removeBtn.setOnAction(e -> addressList.getItems().remove(addressList.getSelectionModel().getSelectedItem()));

    addressList.setId("forwardingList");
    addressList.setPlaceholder(new Label("No forwarding addresses"));
    addressList.setStyle("-fx-pref-height: 5.5em;");

    var fields = new GridPane();
    fields.setHgap(5);
    fields.setVgap(5);
    fields.addRow(0, streetField, unitField, cityField, stateField, zipField);
    fields.add(new HBox(5, noteField, addBtn), 0, 1, 5, 1);

    getChildren().addAll(fields, errorLabel, addressList, removeBtn);
  }

  /**
   * Adds an address that's been typed but not added with Add Address, so it
   * isn't lost when the form is saved. Called just before saving.
   *
   * @return {@code null} if it was added or nothing was typed, or a message
   *     saying what's missing from it, also shown under the fields
   */
  public String addTypedBeforeSaving() {
    var typed = List.of(streetField, unitField, cityField, stateField, zipField, noteField).stream()
        .anyMatch(field -> !field.getText().isBlank());
    if (!typed || addTyped()) {
      return null;
    }
    return "Finish the forwarding address or clear its fields. " + errorLabel.getText();
  }

  /**
   * Adds the address typed in the fields to the list and clears them, or
   * says what's missing.
   *
   * @return {@code true} if it was added
   */
  private boolean addTyped() {
    var errors = new ArrayList<String>();
    if (streetField.getText().isBlank()) {
      errors.add("street");
    }
    if (cityField.getText().isBlank()) {
      errors.add("city");
    }
    if (!Validators.isValidState(stateField.getText())) {
      errors.add("two-letter state");
    }
    if (!Validators.isValidZip(zipField.getText())) {
      errors.add("ZIP code (12345 or 12345-6789)");
    }
    if (!errors.isEmpty()) {
      errorLabel.setText("Enter the " + joinWithAnd(errors) + ".");
      return false;
    }

    addressList.getItems().add(new ForwardingAddress(streetField.getText(), unitField.getText(),
        cityField.getText(), stateField.getText(), zipField.getText(), noteField.getText()));
    for (var field : List.of(streetField, unitField, cityField, stateField, zipField, noteField)) {
      field.clear();
    }
    errorLabel.setText("");
    return true;
  }

  /**
   * Returns a snapshot of the addresses currently entered.
   *
   * @return the addresses entered so far
   */
  public List<ForwardingAddress> getAddresses() {
    return new ArrayList<>(addressList.getItems());
  }

  /**
   * Replaces the current list of addresses, e.g. to pre-fill an edit form.
   *
   * @param addresses the addresses to display
   */
  public void setAddresses(List<ForwardingAddress> addresses) {
    addressList.getItems().setAll(addresses);
  }

  /**
   * Joins items as a list in a sentence: "a", "a and b", or "a, b, and c".
   *
   * @param items the items
   * @return the items joined, such as "a, b, and c"
   */
  static String joinWithAnd(List<String> items) {
    if (items.size() <= 2) {
      return String.join(" and ", items);
    }
    return String.join(", ", items.subList(0, items.size() - 1)) + ", and " + items.get(items.size() - 1);
  }

  /**
   * Makes a field for part of an address.
   *
   * @param id the field's id
   * @param prompt the hint shown while it's empty
   * @param widthEm how wide it is, in ems
   * @return the field
   */
  private static TextField field(String id, String prompt, int widthEm) {
    var field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    field.setStyle("-fx-pref-width: " + widthEm + "em;");
    return field;
  }

}
