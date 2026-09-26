package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.Mailbox;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Lists all mailboxes in a searchable table showing the key details of each,
 * with actions to view the full entry (also by double-clicking a row or
 * pressing Enter), edit it, or delete it.
 */
public class ManageBoxesView {

  /**
   * Builds and displays the mailbox list on the given stage.
   *
   * @param stage the window to render the list into
   */
  public static void show(Stage stage) {
    show(stage, "");
  }

  private static void show(Stage stage, String initialQuery) {
    var repository = new MailboxRepository();
    var statusLabel = new Label();

    var table = new TableView<Mailbox>();
    table.setId("boxTable");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    // Only the details needed to pick out a box; View shows the rest.
    var boxNumberCol = new TableColumn<Mailbox, String>("Box Number");
    boxNumberCol.setCellValueFactory(new PropertyValueFactory<>("boxNumber"));

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
    table.setItems(mailboxes);

    var searchField = new TextField();
    searchField.setId("searchField");
    searchField.setPromptText("Search by name, business, box, phone, email, or forwarding address");
    searchField.textProperty().addListener((obs, oldQuery, query) ->
        mailboxes.setPredicate(m -> matches(m, query)));
    searchField.setText(initialQuery);

    Consumer<Mailbox> edit = mailbox ->
        EditBoxView.show(stage, mailbox, () -> show(stage, searchField.getText()));
    Consumer<Mailbox> view = mailbox -> BoxDetailsView.show(stage, mailbox, () -> edit.accept(mailbox));

    // Double-click a row, or press Enter on it, to see the full entry.
    table.setRowFactory(tableView -> {
      var row = new TableRow<Mailbox>();
      row.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
          view.accept(row.getItem());
        }
      });
      return row;
    });
    table.setOnKeyPressed(e -> {
      var selected = table.getSelectionModel().getSelectedItem();
      if (e.getCode() == KeyCode.ENTER && selected != null) {
        view.accept(selected);
      }
    });

    var viewBtn = new Button("View");
    viewBtn.setId("viewButton");
    viewBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    viewBtn.setOnAction(e -> view.accept(table.getSelectionModel().getSelectedItem()));

    var editBtn = new Button("Edit");
    editBtn.setId("editButton");
    editBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    editBtn.setOnAction(e -> edit.accept(table.getSelectionModel().getSelectedItem()));

    var deleteBtn = new Button("Delete");
    deleteBtn.setId("deleteButton");
    deleteBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    deleteBtn.setOnAction(e -> {
      var selected = table.getSelectionModel().getSelectedItem();
      if (selected == null) {
        return;
      }

      var confirm = new Alert(AlertType.CONFIRMATION,
          "Delete box " + selected.getBoxNumber() + " for "
              + selected.getFirstName() + " " + selected.getLastName() + "?",
          ButtonType.YES, ButtonType.NO);
      AppWindow.applyTextSize(confirm);
      confirm.showAndWait()
          .filter(response -> response == ButtonType.YES)
          .ifPresent(response -> {
            try {
              repository.delete(selected.getId());
              BoxDetailsView.close();
              show(stage, searchField.getText());
            } catch (SQLException ex) {
              statusLabel.setStyle("-fx-text-fill: red;");
              statusLabel.setText("Failed to delete: " + ex.getMessage());
            }
          });
    });

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var layout = new VBox(10,
        backBtn,
        searchField,
        table,
        new HBox(10, viewBtn, editBtn, deleteBtn),
        statusLabel);
    layout.setPadding(new Insets(20));

    AppWindow.show(stage, layout);
  }

  /**
   * Checks whether a mailbox matches every word of a search query. Each word
   * may appear, case-insensitively, in any text field, alternate business
   * name, or forwarding address (including its note); a word made of digits
   * also matches the phone number ignoring its formatting.
   */
  static boolean matches(Mailbox mailbox, String query) {
    if (query == null || query.isBlank()) {
      return true;
    }

    var haystack = new StringBuilder();
    for (var value : new String[] { mailbox.getFirstName(), mailbox.getLastName(),
        mailbox.getBusinessTitle(), mailbox.getBoxNumber(), mailbox.getBoxName(),
        mailbox.getPhone(), mailbox.getEmail() }) {
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
