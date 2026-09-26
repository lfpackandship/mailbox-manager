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

  private final ListView<ForwardingAddress> addressList = new ListView<>();

  /**
   * Builds the editor: fields for one address with an Add button, a list of
   * the addresses entered so far, and a Remove Selected button.
   */
  public ForwardingAddressesEditor() {
    super(5);

    var streetField = field("streetField", "123 Main St", 14);
    var unitField = field("unitField", "Apt 4 (optional)", 9);
    var cityField = field("cityField", "Springfield", 10);
    var stateField = field("stateField", "IL", 3);
    var zipField = field("zipField", "62701", 6);
    var noteField = field("noteField", "e.g. summer (optional)", 14);

    var errorLabel = new Label();
    errorLabel.setId("forwardingErrorLabel");
    errorLabel.setStyle("-fx-text-fill: red;");

    var addBtn = new Button("Add Address");
    addBtn.setId("addForwardingButton");
    addBtn.setOnAction(e -> {
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
        return;
      }

      addressList.getItems().add(new ForwardingAddress(streetField.getText(), unitField.getText(),
          cityField.getText(), stateField.getText(), zipField.getText(), noteField.getText()));
      for (var field : List.of(streetField, unitField, cityField, stateField, zipField, noteField)) {
        field.clear();
      }
      errorLabel.setText("");
    });

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
   */
  static String joinWithAnd(List<String> items) {
    if (items.size() <= 2) {
      return String.join(" and ", items);
    }
    return String.join(", ", items.subList(0, items.size() - 1)) + ", and " + items.get(items.size() - 1);
  }

  private static TextField field(String id, String prompt, int widthEm) {
    var field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    field.setStyle("-fx-pref-width: " + widthEm + "em;");
    return field;
  }

}
