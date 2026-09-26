package org.lfps.mailboxes.view;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class BusinessNamesEditor extends VBox {

  private final ListView<String> namesList = new ListView<>();

  public BusinessNamesEditor() {
    super(5);

    var nameField = new TextField();
    nameField.setPromptText("Alternate business name");

    var addBtn = new Button("Add");
    addBtn.setOnAction(e -> {
      var name = nameField.getText().trim();
      if (!name.isEmpty()) {
        namesList.getItems().add(name);
        nameField.clear();
      }
    });

    var removeBtn = new Button("Remove Selected");
    removeBtn.setOnAction(e -> {
      var selected = namesList.getSelectionModel().getSelectedItem();
      if (selected != null) {
        namesList.getItems().remove(selected);
      }
    });

    namesList.setPrefHeight(100);

    getChildren().addAll(new HBox(5, nameField, addBtn), namesList, removeBtn);
  }

  public List<String> getNames() {
    return new ArrayList<>(namesList.getItems());
  }

  public void setNames(List<String> names) {
    namesList.getItems().setAll(names);
  }

}
