package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.lfps.mailboxes.data.LabelRepository;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.BoxNumbers;
import org.lfps.mailboxes.util.Money;

import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Lists mailboxes in a searchable table showing the key details of each, with
 * actions to view the full entry (also by double-clicking a row or pressing
 * Enter), edit, renew, close, reopen, or delete it. Open boxes are shown by
 * default; forwarding-only or closed ones can be shown instead, or all.
 */
public class ManageBoxesView {

  /** Which boxes the table shows. */
  enum Show {
    /** Boxes that are open, rented or forwarding only. */
    OPEN("Open boxes"),
    /** Open boxes that are forwarding only. */
    FORWARDING("Forwarding only"),
    /** Boxes that have been closed. */
    CLOSED("Closed boxes"),
    /** Every box, open or closed. */
    ALL("All boxes");

    /** What the choice is called in the menu. */
    private final String label;

    /**
     * Makes a choice.
     *
     * @param label what the choice is called in the menu
     */
    Show(String label) {
      this.label = label;
    }

    /**
     * Returns whether a box belongs in the table when this is chosen.
     *
     * @param mailbox the box
     * @return {@code true} if it's shown
     */
    boolean includes(Mailbox mailbox) {
      if (this == FORWARDING) {
        return !mailbox.isClosed() && mailbox.isForwardingOnly();
      }
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

  /**
   * Builds and displays the list of boxes with a search already typed and a
   * choice of boxes already made, as when coming back to the screen.
   *
   * @param stage the window to render the list into
   * @param initialQuery the search, or an empty string
   * @param initialShow which boxes to show
   */
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
    BoxLabels.markForwarding(boxNumberCol);

    var nameCol = new TableColumn<Mailbox, String>("Name");
    nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFullName()));

    // Hidden, but saved in the spreadsheet so names can be sorted by last name there.
    var firstNameCol = new TableColumn<Mailbox, String>("First Name");
    firstNameCol.setCellValueFactory(new PropertyValueFactory<>("firstName"));
    firstNameCol.setVisible(false);

    var lastNameCol = new TableColumn<Mailbox, String>("Last Name");
    lastNameCol.setCellValueFactory(new PropertyValueFactory<>("lastName"));
    lastNameCol.setVisible(false);

    var businessTitleCol = new TableColumn<Mailbox, String>("Business Title");
    businessTitleCol.setCellValueFactory(new PropertyValueFactory<>("businessTitle"));

    var phoneCol = new TableColumn<Mailbox, String>("Phone");
    phoneCol.setCellValueFactory(cell -> new SimpleStringProperty(
        PhoneNumberFormatter.format(cell.getValue().getPhone())));

    table.getColumns().setAll(List.of(boxNumberCol, nameCol, firstNameCol, lastNameCol, businessTitleCol,
        phoneCol));
    // Hidden, but saved in the spreadsheet so it has everything recorded.
    table.getColumns().addAll(hiddenColumns());

    var allMailboxes = FXCollections.<Mailbox>observableArrayList();
    // Each box's forwarding label numbers, so a search for one finds its box.
    Map<Integer, List<String>> labelNumbers = new HashMap<>();
    try {
      allMailboxes.setAll(repository.findAll());
      for (var label : new LabelRepository().findAll()) {
        labelNumbers.computeIfAbsent(label.getMailboxId(), id -> new ArrayList<>()).add(label.getNumber());
      }
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
    searchField.setPromptText("Search by name, business, box, phone, email, forwarding address, notes, "
        + "or label number");
    HBox.setHgrow(searchField, Priority.ALWAYS);

    var showChoice = new ChoiceBox<Show>();
    showChoice.setId("showChoice");
    showChoice.getItems().setAll(Show.values());
    showChoice.setValue(initialShow);

    Runnable filter = () -> mailboxes.setPredicate(
        m -> showChoice.getValue().includes(m)
            && matches(m, searchField.getText(), labelNumbers.getOrDefault(m.getId(), List.of())));
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
          if (!selected.isForwardingOnly()
              && repository.isBoxNumberTaken(selected.getBoxNumber(), selected.getId())) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("Box " + selected.getBoxNumber().trim() + " has been given to someone else, so "
                + BoxLabels.holderOr(selected, "its holder") + " can't be reopened in it. Edit the box number first.");
            return;
          }
          repository.setClosedDate(selected.getId(), null);
        } else {
          var question = "Close box " + selected.getBoxNumber() + BoxLabels.forHolder(selected) + "?";
          var details = keysReminder(selected)
              + "The box becomes free to rent to someone else. Everything recorded for it is kept, and you can "
              + "find it again by showing closed boxes.";
          var deposit = selected.getKeyDepositCents();
          if (deposit != null && deposit > 0) {
            // Optional: it can be left as not recorded, and set later on Edit Box.
            var choice = Dialogs.confirmWithChoice.ask(stage, question, details,
                "The " + Money.format(deposit) + " key deposit was:", KeyFields.OUTCOME_CHOICES,
                KeyFields.NOT_RECORDED, "Close Box", "Keep It Open");
            if (choice < 0) {
              return;
            }
            repository.setClosedDate(selected.getId(), LocalDate.now(), KeyFields.outcomeAt(choice));
          } else if (Dialogs.confirm.ask(stage, question, details, "Close Box", "Keep It Open")) {
            repository.setClosedDate(selected.getId(), LocalDate.now());
          } else {
            return;
          }
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
              + "If the holder has given up the box, close it instead to keep the record.",
          "Delete Forever", "Cancel")) {
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

    var printBtn = new Button("Print…");
    printBtn.setId("printListButton");
    printBtn.setOnAction(e -> TableOutput.print(listTitle(showChoice.getValue(), searchField.getText()),
        List.of(new TableOutput.Section(null, table)), statusLabel));

    var spreadsheetBtn = new Button("Save as Spreadsheet…");
    spreadsheetBtn.setId("spreadsheetButton");
    spreadsheetBtn.setOnAction(e -> TableOutput.run(statusLabel, () -> TableOutput.saveSpreadsheet(stage,
        "boxes-" + LocalDate.now() + ".csv", List.of(table))));

    // Delete sits apart at the far right, so it isn't clicked in place of
    // Close Box, which keeps the box's record.
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    var layout = new VBox(10,
        new HBox(10, backBtn, printBtn, spreadsheetBtn),
        new HBox(10, searchField, showChoice),
        table,
        new HBox(10, viewBtn, editBtn, renewBtn, closeBtn, spacer, deleteBtn),
        statusLabel);
    layout.setPadding(new Insets(20));

    AppWindow.show(stage, layout);
  }

  /**
   * Returns the title of the printed list, such as "Open boxes matching
   * “smith”".
   *
   * @param show which boxes are shown
   * @param query the search, or {@code null}
   * @return the title
   */
  static String listTitle(Show show, String query) {
    return show + (query == null || query.isBlank() ? "" : " matching “" + query.trim() + "”");
  }

  /**
   * Makes the columns that start hidden, which can be shown from the table's
   * menu.
   *
   * @return the columns
   */
  private static List<TableColumn<Mailbox, ?>> hiddenColumns() {
    var boxNameCol = new TableColumn<Mailbox, String>("Box Name");
    boxNameCol.setCellValueFactory(new PropertyValueFactory<>("boxName"));

    var emailCol = new TableColumn<Mailbox, String>("Email");
    emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));

    var endDateCol = new TableColumn<Mailbox, LocalDate>("End Date");
    endDateCol.setCellValueFactory(new PropertyValueFactory<>("endDate"));
    TableOutput.showDates(endDateCol);

    var alternateNamesCol = new TableColumn<Mailbox, String>("Also Receives Mail As");
    alternateNamesCol.setCellValueFactory(cell -> new SimpleStringProperty(
        String.join("; ", cell.getValue().getAlternateBusinessNames())));

    var forwardingCol = new TableColumn<Mailbox, String>("Forwarding Addresses");
    forwardingCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getForwardingAddresses()
        .stream().map(String::valueOf).collect(Collectors.joining("; "))));

    var keysCol = new TableColumn<Mailbox, Integer>("Keys");
    keysCol.setCellValueFactory(new PropertyValueFactory<>("keyCount"));

    var depositCol = new TableColumn<Mailbox, Long>("Key Deposit");
    depositCol.setCellValueFactory(new PropertyValueFactory<>("keyDepositCents"));
    TableOutput.formatWith(depositCol, Money::format);

    var outcomeCol = new TableColumn<Mailbox, String>("Key Deposit Was");
    outcomeCol.setCellValueFactory(cell -> new SimpleStringProperty(
        cell.getValue().getKeyDepositOutcome() == null ? "" : cell.getValue().getKeyDepositOutcome().getLabel()));

    var notesCol = new TableColumn<Mailbox, String>("Notes");
    notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

    var closedCol = new TableColumn<Mailbox, LocalDate>("Closed");
    closedCol.setCellValueFactory(new PropertyValueFactory<>("closedDate"));
    TableOutput.showDates(closedCol);

    var forwardingOnlyCol = new TableColumn<Mailbox, String>("Forwarding Only");
    forwardingOnlyCol.setCellValueFactory(cell -> new SimpleStringProperty(
        cell.getValue().isForwardingOnly() ? "Yes" : ""));

    var columns = List.<TableColumn<Mailbox, ?>>of(boxNameCol, emailCol, endDateCol, forwardingOnlyCol,
        alternateNamesCol, forwardingCol, keysCol, depositCol, outcomeCol, notesCol, closedCol);
    columns.forEach(column -> column.setVisible(false));
    return columns;
  }

  /**
   * Reminds whoever closes a box to collect its keys and give back the key
   * deposit, such as "Collect the 2 keys and give back the $20.00 key deposit.
   * ", or returns an empty string if neither is recorded.
   *
   * @param mailbox the box being closed
   * @return the reminder
   */
  static String keysReminder(Mailbox mailbox) {
    var count = mailbox.getKeyCount();
    var deposit = mailbox.getKeyDepositCents();
    var keys = count == null || count == 0 ? "" : (count == 1 ? "the key" : "the " + count + " keys");
    var refund = deposit == null || deposit == 0 ? "" : "give back the " + Money.format(deposit) + " key deposit";
    if (keys.isEmpty() && refund.isEmpty()) {
      return "";
    }
    if (keys.isEmpty()) {
      return "Remember to " + refund + ". ";
    }
    return "Remember to collect " + keys + (refund.isEmpty() ? "" : " and " + refund) + ". ";
  }

  /**
   * Checks whether a mailbox matches every word of a search query. Each word
   * may appear, case-insensitively, in any text field, alternate business name,
   * forwarding address (including its note), or the notes; a word made of
   * digits also matches the phone number ignoring its formatting.
   *
   * @param mailbox the box
   * @param query the search
   * @return {@code true} if the box matches
   */
  static boolean matches(Mailbox mailbox, String query) {
    return matches(mailbox, query, List.of());
  }

  /**
   * Checks whether a mailbox matches every word of a search query, like
   * {@link #matches(Mailbox, String)}, also looking in the numbers of the
   * forwarding labels printed for it.
   *
   * @param mailbox the box
   * @param query the search
   * @param labelNumbers the numbers of its printed forwarding labels
   * @return {@code true} if the box matches
   */
  static boolean matches(Mailbox mailbox, String query, List<String> labelNumbers) {
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
    if (mailbox.isForwardingOnly()) {
      haystack.append("forwarding only\n");
    }
    labelNumbers.forEach(number -> haystack.append(number).append('\n'));
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

  /** Not used: the screen is built with static methods. */
  private ManageBoxesView() {
  }

}
