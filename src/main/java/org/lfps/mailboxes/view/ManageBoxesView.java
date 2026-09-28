package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.BoxNumbers;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Lists mailboxes in a searchable table showing the key details of each, with
 * actions to view the full entry (also by double-clicking a row or pressing
 * Enter), edit, renew, close, reopen, or delete it. Open boxes are shown by
 * default; closed ones can be shown instead, or both.
 */
public class ManageBoxesView {

  /** Which boxes the table shows. */
  enum Show {
    OPEN("Open boxes"),
    CLOSED("Closed boxes"),
    ALL("All boxes");

    private final String label;

    Show(String label) {
      this.label = label;
    }

    /**
     * Returns whether a box belongs in the table when this is chosen.
     */
    boolean includes(Mailbox mailbox) {
      return this == ALL || mailbox.isClosed() == (this == CLOSED);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /**
   * Builds and displays the mailbox list on the given stage.
   *
   * @param stage the window to render the list into
   */
  public static void show(Stage stage) {
    show(stage, "", Show.OPEN);
  }

  private static void show(Stage stage, String initialQuery, Show initialShow) {
    var repository = new MailboxRepository();
    var statusLabel = new Label();
    statusLabel.setId("statusLabel");
    statusLabel.setWrapText(true);

    var table = new TableView<Mailbox>();
    table.setId("boxTable");
    // Start small enough to fit the default window, then grow to fill it.
    table.setStyle("-fx-pref-height: 12em;");
    VBox.setVgrow(table, Priority.ALWAYS);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    // Only the details needed to pick out a box; View shows the rest.
    var boxNumberCol = new TableColumn<Mailbox, String>("Box Number");
    boxNumberCol.setCellValueFactory(new PropertyValueFactory<>("boxNumber"));
    boxNumberCol.setComparator(BoxNumbers.ORDER);

    var firstNameCol = new TableColumn<Mailbox, String>("First Name");
    firstNameCol.setCellValueFactory(new PropertyValueFactory<>("firstName"));

    var lastNameCol = new TableColumn<Mailbox, String>("Last Name");
    lastNameCol.setCellValueFactory(new PropertyValueFactory<>("lastName"));

    var businessTitleCol = new TableColumn<Mailbox, String>("Business Title");
    businessTitleCol.setCellValueFactory(new PropertyValueFactory<>("businessTitle"));

    var phoneCol = new TableColumn<Mailbox, String>("Phone");
    phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));

    table.getColumns().setAll(List.of(boxNumberCol, firstNameCol, lastNameCol, businessTitleCol, phoneCol));

    var allMailboxes = FXCollections.<Mailbox>observableArrayList();
    try {
      allMailboxes.setAll(repository.findAll());
    } catch (SQLException e) {
      statusLabel.setStyle("-fx-text-fill: red;");
      statusLabel.setText("Failed to load mailboxes: " + e.getMessage());
    }
    var mailboxes = new FilteredList<>(allMailboxes);
    // A filtered list can't be reordered, so sort a view of it instead;
    // otherwise clicking a column header wouldn't sort.
    var sorted = new SortedList<>(mailboxes);
    sorted.comparatorProperty().bind(table.comparatorProperty());
    table.setItems(sorted);

    var searchField = new TextField();
    searchField.setId("searchField");
    searchField.setPromptText("Search by name, business, box, phone, email, forwarding address, or notes");
    HBox.setHgrow(searchField, Priority.ALWAYS);

    var showChoice = new ChoiceBox<Show>();
    showChoice.setId("showChoice");
    showChoice.getItems().setAll(Show.values());
    showChoice.setValue(initialShow);

    Runnable filter = () -> mailboxes.setPredicate(
        m -> showChoice.getValue().includes(m) && matches(m, searchField.getText()));
    searchField.textProperty().addListener((obs, oldQuery, query) -> filter.run());
    showChoice.valueProperty().addListener((obs, oldShow, newShow) -> filter.run());
    searchField.setText(initialQuery);
    filter.run();

    Runnable refresh = () -> show(stage, searchField.getText(), showChoice.getValue());
    Consumer<Mailbox> edit = mailbox -> EditBoxView.show(stage, mailbox, refresh);
    Consumer<Mailbox> renew = mailbox -> RenewBoxView.show(stage, mailbox, refresh);
    Consumer<Mailbox> view = mailbox -> BoxDetailsView.show(stage, mailbox, () -> edit.accept(mailbox), refresh);
    var selection = table.getSelectionModel().selectedItemProperty();

    BoxDetailsView.openOnDoubleClickOrEnter(table, view);

    var viewBtn = new Button("View");
    viewBtn.setId("viewButton");
    viewBtn.disableProperty().bind(selection.isNull());
    viewBtn.setOnAction(e -> view.accept(table.getSelectionModel().getSelectedItem()));

    var editBtn = new Button("Edit");
    editBtn.setId("editButton");
    editBtn.disableProperty().bind(selection.isNull());
    editBtn.setOnAction(e -> edit.accept(table.getSelectionModel().getSelectedItem()));

    var renewBtn = new Button("Renew…");
    renewBtn.setId("renewButton");
    renewBtn.disableProperty().bind(Bindings.createBooleanBinding(
        () -> selection.get() == null || selection.get().isClosed(), selection));
    renewBtn.setOnAction(e -> renew.accept(table.getSelectionModel().getSelectedItem()));

    // Closes an open box, or reopens a closed one.
    var closeBtn = new Button("Close Box");
    closeBtn.setId("closeButton");
    closeBtn.disableProperty().bind(selection.isNull());
    closeBtn.textProperty().bind(Bindings.createStringBinding(
        () -> selection.get() != null && selection.get().isClosed() ? "Reopen" : "Close Box", selection));
    closeBtn.setOnAction(e -> {
      var selected = table.getSelectionModel().getSelectedItem();
      if (selected == null) {
        return;
      }
      try {
        if (selected.isClosed()) {
          if (repository.isBoxNumberTaken(selected.getBoxNumber(), selected.getId())) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("Box " + selected.getBoxNumber().trim() + " has been given to someone else, so "
                + BoxLabels.holderOr(selected, "its holder") + " can't be reopened in it. Edit the box number first.");
            return;
          }
          repository.setClosedDate(selected.getId(), null);
        } else if (Dialogs.confirm.ask(stage, "Close box " + selected.getBoxNumber() + BoxLabels.forHolder(selected) + "?",
            "The box becomes free to rent to someone else. Everything recorded for it is kept, and you can "
                + "find it again by showing closed boxes.")) {
          repository.setClosedDate(selected.getId(), LocalDate.now());
        } else {
          return;
        }
        BoxDetailsView.close();
        refresh.run();
      } catch (SQLException ex) {
        statusLabel.setStyle("-fx-text-fill: red;");
        statusLabel.setText("Failed to save: " + ex.getMessage());
      }
    });

    var deleteBtn = new Button("Delete");
    deleteBtn.setId("deleteButton");
    deleteBtn.disableProperty().bind(selection.isNull());
    deleteBtn.setOnAction(e -> {
      var selected = table.getSelectionModel().getSelectedItem();
      if (selected == null) {
        return;
      }
      if (!Dialogs.confirm.ask(stage, "Permanently delete box " + selected.getBoxNumber() + BoxLabels.forHolder(selected) + "?",
          "Everything recorded for it, including its rental history, will be erased. This can't be undone. "
              + "If the holder has given up the box, close it instead to keep the record.")) {
        return;
      }
      try {
        repository.delete(selected.getId());
        BoxDetailsView.close();
        refresh.run();
      } catch (SQLException ex) {
        statusLabel.setStyle("-fx-text-fill: red;");
        statusLabel.setText("Failed to delete: " + ex.getMessage());
      }
    });

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var layout = new VBox(10,
        backBtn,
        new HBox(10, searchField, showChoice),
        table,
        new HBox(10, viewBtn, editBtn, renewBtn, closeBtn, deleteBtn),
        statusLabel);
    layout.setPadding(new Insets(20));

    AppWindow.show(stage, layout);
  }

  /**
   * Checks whether a mailbox matches every word of a search query. Each word
   * may appear, case-insensitively, in any text field, alternate business
   * name, forwarding address (including its note), or the notes; a word made
   * of digits also matches the phone number ignoring its formatting.
   */
  static boolean matches(Mailbox mailbox, String query) {
    if (query == null || query.isBlank()) {
      return true;
    }

    var haystack = new StringBuilder();
    for (var value : new String[] { mailbox.getFirstName(), mailbox.getLastName(),
        mailbox.getBusinessTitle(), mailbox.getBoxNumber(), mailbox.getBoxName(),
        mailbox.getPhone(), mailbox.getEmail(), mailbox.getNotes() }) {
      if (value != null) {
        haystack.append(value).append('\n');
      }
    }
    mailbox.getAlternateBusinessNames().forEach(name -> haystack.append(name).append('\n'));
    mailbox.getForwardingAddresses().forEach(address -> haystack.append(address).append('\n'));
    var text = haystack.toString().toLowerCase();
    var phoneDigits = mailbox.getPhone() == null ? "" : mailbox.getPhone().replaceAll("[^0-9]", "");

    for (var word : query.toLowerCase().trim().split("\\s+")) {
      var isNumber = word.matches("[0-9]+");
      if (!text.contains(word) && !(isNumber && phoneDigits.contains(word))) {
        return false;
      }
    }
    return true;
  }

  private ManageBoxesView() {
  }

}
