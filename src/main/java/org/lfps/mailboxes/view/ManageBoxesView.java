package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;

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
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Lists all mailboxes in a searchable table with actions to edit or delete a
 * selected entry.
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
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    var firstNameCol = new TableColumn<Mailbox, String>("First Name");
    firstNameCol.setCellValueFactory(new PropertyValueFactory<>("firstName"));

    var lastNameCol = new TableColumn<Mailbox, String>("Last Name");
    lastNameCol.setCellValueFactory(new PropertyValueFactory<>("lastName"));

    var businessTitleCol = new TableColumn<Mailbox, String>("Business Title");
    businessTitleCol.setCellValueFactory(new PropertyValueFactory<>("businessTitle"));

    var alternateBusinessNamesCol = new TableColumn<Mailbox, String>("Alternate Business Names");
    alternateBusinessNamesCol.setCellValueFactory(cellData -> new SimpleStringProperty(
        String.join(", ", cellData.getValue().getAlternateBusinessNames())));

    var boxNumberCol = new TableColumn<Mailbox, String>("Box Number");
    boxNumberCol.setCellValueFactory(new PropertyValueFactory<>("boxNumber"));

    var boxNameCol = new TableColumn<Mailbox, String>("Box Name");
    boxNameCol.setCellValueFactory(new PropertyValueFactory<>("boxName"));

    var phoneCol = new TableColumn<Mailbox, String>("Phone");
    phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));

    var emailCol = new TableColumn<Mailbox, String>("Email");
    emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));

    var endDateCol = new TableColumn<Mailbox, LocalDate>("End Date");
    endDateCol.setCellValueFactory(new PropertyValueFactory<>("endDate"));

    var columns = table.getColumns();
    columns.add(firstNameCol);
    columns.add(lastNameCol);
    columns.add(businessTitleCol);
    columns.add(alternateBusinessNamesCol);
    columns.add(boxNumberCol);
    columns.add(boxNameCol);
    columns.add(phoneCol);
    columns.add(emailCol);
    columns.add(endDateCol);

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
    searchField.setPromptText("Search by name, business, box, phone, email, or forwarding address");
    searchField.textProperty().addListener((obs, oldQuery, query) ->
        mailboxes.setPredicate(m -> matches(m, query)));
    searchField.setText(initialQuery);

    var editBtn = new Button("Edit");
    editBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    editBtn.setOnAction(e -> {
      var selected = table.getSelectionModel().getSelectedItem();
      if (selected != null) {
        EditBoxView.show(stage, selected, () -> show(stage, searchField.getText()));
      }
    });

    var deleteBtn = new Button("Delete");
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
      confirm.showAndWait()
          .filter(response -> response == ButtonType.YES)
          .ifPresent(response -> {
            try {
              repository.delete(selected.getId());
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
        new HBox(10, editBtn, deleteBtn),
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
