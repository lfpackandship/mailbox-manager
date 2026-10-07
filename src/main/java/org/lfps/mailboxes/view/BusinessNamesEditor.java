package org.lfps.mailboxes.view;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A reusable control for adding and removing a list of alternate business
 * names, shared by {@link AddBoxView} and {@link EditBoxView}.
 */
public class BusinessNamesEditor extends VBox {

  /** The names added so far. */
  private final ListView<String> namesList = new ListView<>();

  /** Where a name is typed before it's added. */
  private final TextField nameField = new TextField();

  /**
   * Builds the editor: a text field with an Add button, a list of the
   * names entered so far, and a Remove Selected button.
   */
  public BusinessNamesEditor() {
    super(5);

    nameField.setId("businessNameField");
    nameField.setPromptText("Acme Inc (optional)");

    var addBtn = new Button("Add");
    addBtn.setOnAction(e -> addTyped());

    var removeBtn = new Button("Remove Selected");
    removeBtn.setOnAction(e -> {
      var selected = namesList.getSelectionModel().getSelectedItem();
      if (selected != null) {
        namesList.getItems().remove(selected);
      }
    });

    namesList.setStyle("-fx-pref-height: 5.5em;");

    getChildren().addAll(new HBox(5, nameField, addBtn), namesList, removeBtn);
  }

  /**
   * Adds the name typed in the field to the list and clears the field. Also
   * called just before saving, so a name typed but not added with Add isn't
   * lost.
   */
  public void addTyped() {
    var name = nameField.getText().trim();
    if (!name.isEmpty()) {
      namesList.getItems().add(name);
      nameField.clear();
    }
  }

  /**
   * Has a form notice when anything is typed here, or an entry is added or
   * removed, so leaving it can ask before throwing that away.
   *
   * @param changes the form's changes
   */
  void watchFor(UnsavedChanges changes) {
    changes.watch(nameField.textProperty()).watchList(namesList.getItems());
  }

  /**
   * Returns a snapshot of the names currently entered.
   *
   * @return the names entered so far
   */
  public List<String> getNames() {
    return new ArrayList<>(namesList.getItems());
  }

  /**
   * Replaces the current list of names, e.g. to pre-fill an edit form.
   *
   * @param names the names to display
   */
  public void setNames(List<String> names) {
    namesList.getItems().setAll(names);
  }

}
