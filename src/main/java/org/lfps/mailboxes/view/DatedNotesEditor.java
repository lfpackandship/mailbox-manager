package org.lfps.mailboxes.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.lfps.mailboxes.model.DatedNote;

/**
 * The Dated Notes section of Edit Box, for keeping a history of things that
 * happened with a box, such as "Called about late payment", each with the
 * day it happened. A note is dated today unless an earlier day is chosen.
 * Notes are listed newest first; Edit puts one back in the fields to change
 * it, and Remove deletes it. Nothing is saved until Edit Box's Save is
 * clicked. Add New Box doesn't have the section.
 */
final class DatedNotesEditor extends VBox {

  /** The notes so far, newest first. */
  private final ListView<DatedNote> noteList = new ListView<>();

  /** The day of the note being added. */
  private final DatePicker dateField = new DatePicker(LocalDate.now());

  /** What happened. */
  private final TextField textField = new TextField();

  /** Says what's missing from a note that can't be added, in red. */
  private final Label errorLabel = new Label();

  /**
   * Builds the editor: a day and a note with an Add Note button, the list of
   * notes, and Edit and Remove buttons for the one selected.
   */
  DatedNotesEditor() {
    super(5);

    dateField.setId("datedNoteDateField");
    dateField.setStyle("-fx-pref-width: 10em;");
    textField.setId("datedNoteTextField");
    textField.setPromptText("e.g. Called about late payment");
    HBox.setHgrow(textField, Priority.ALWAYS);

    errorLabel.setId("datedNoteErrorLabel");
    errorLabel.setStyle("-fx-text-fill: red;");
    // Takes no room until there's something to say.
    errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
    errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());

    var addBtn = new Button("Add Note");
    addBtn.setId("addDatedNoteButton");
    addBtn.setOnAction(e -> addTyped());
    // Enter in the note adds it, rather than saving the whole form.
    textField.setOnAction(e -> addTyped());

    var selected = noteList.getSelectionModel().selectedItemProperty();
    var editBtn = new Button("Edit");
    editBtn.setId("editDatedNoteButton");
    editBtn.disableProperty().bind(selected.isNull());
    editBtn.setOnAction(e -> {
      var note = noteList.getSelectionModel().getSelectedItem();
      noteList.getItems().remove(note);
      dateField.setValue(note.getDate());
      textField.setText(note.getText());
      textField.requestFocus();
    });

    var removeBtn = new Button("Remove");
    removeBtn.setId("removeDatedNoteButton");
    removeBtn.disableProperty().bind(selected.isNull());
    removeBtn.setOnAction(e -> noteList.getItems().remove(noteList.getSelectionModel().getSelectedItem()));

    noteList.setId("datedNoteList");
    noteList.setPlaceholder(new Label("No dated notes"));
    noteList.setStyle("-fx-pref-height: 6.5em;");

    var hint = new Label("Keep a history of what happens with the box. Choose an earlier day for something "
        + "that happened before today. To change a note, select it and click Edit.");
    hint.setWrapText(true);

    getChildren().addAll(hint, new HBox(5, dateField, textField, addBtn), errorLabel, noteList,
        new HBox(5, editBtn, removeBtn));
  }

  /**
   * Adds a note that's been typed but not added with Add Note, so it isn't
   * lost when the form is saved. Called just before saving.
   *
   * @return {@code null} if it was added or nothing was typed, or a message
   *     saying what's missing from it, also shown under the fields
   */
  String addTypedBeforeSaving() {
    if (textField.getText().isBlank() || addTyped()) {
      return null;
    }
    return "Finish the dated note or clear it. " + errorLabel.getText();
  }

  /**
   * Adds the note typed in the fields to the list and clears the note, or
   * says what's missing. The day goes back to today for the next note.
   *
   * @return {@code true} if it was added
   */
  private boolean addTyped() {
    DatedNote note;
    try {
      note = new DatedNote(dateField.getValue(), textField.getText());
    } catch (IllegalArgumentException e) {
      errorLabel.setText(e.getMessage());
      return false;
    }
    noteList.getItems().add(note);
    noteList.getItems().sort(DatedNote.NEWEST_FIRST);
    textField.clear();
    dateField.setValue(LocalDate.now());
    errorLabel.setText("");
    return true;
  }

  /**
   * Returns the notes added so far, live, so a section heading can show how
   * many there are.
   *
   * @return the notes
   */
  ObservableList<?> entries() {
    return noteList.getItems();
  }

  /**
   * Has a form notice when a note is typed, added, changed, or removed, so
   * leaving it can ask before throwing that away.
   *
   * @param changes the form's changes
   */
  void watchFor(UnsavedChanges changes) {
    changes.watch(textField.textProperty()).watchList(noteList.getItems());
  }

  /**
   * Returns a snapshot of the notes entered.
   *
   * @return the notes, newest first
   */
  List<DatedNote> getNotes() {
    return new ArrayList<>(noteList.getItems());
  }

  /**
   * Fills in the box's notes, to edit them.
   *
   * @param notes the notes
   */
  void setNotes(List<DatedNote> notes) {
    noteList.getItems().setAll(notes);
    noteList.getItems().sort(DatedNote.NEWEST_FIRST);
  }

}
