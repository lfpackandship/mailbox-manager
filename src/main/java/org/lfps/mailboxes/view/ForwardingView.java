package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.LabelRepository;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.PrintedLabel;
import org.lfps.mailboxes.util.BoxNumbers;

/**
 * The Forwarding screen, opened from File → Forwarding: everything about
 * forwarding mail in one place. The Who We Forward For tab lists every
 * forwarding address, newest first, with a search to find one and print a
 * new label for it. The Labels Printed tab lists every forwarding label
 * printed, newest first, searchable by label number, for reprinting or the
 * shop's records. Either list can be printed or saved as a spreadsheet.
 */
public class ForwardingView {

  /** A forwarding address and the box it belongs to: one row of Who We Forward For. */
  static final class AddressRow {

    /** The box. */
    final Mailbox mailbox;

    /** One of its forwarding addresses. */
    final ForwardingAddress address;

    /**
     * Makes a row.
     *
     * @param mailbox the box
     * @param address one of its forwarding addresses
     */
    AddressRow(Mailbox mailbox, ForwardingAddress address) {
      this.mailbox = mailbox;
      this.address = address;
    }

  }

  /** A printed label and the box it was for: one row of Labels Printed. */
  static final class LabelRow {

    /** The label. */
    final PrintedLabel label;

    /** The box it was for, or {@code null} if it can't be found. */
    final Mailbox mailbox;

    /**
     * Makes a row.
     *
     * @param label the label
     * @param mailbox the box it was for, or {@code null} if it can't be found
     */
    LabelRow(PrintedLabel label, Mailbox mailbox) {
      this.label = label;
      this.mailbox = mailbox;
    }

  }

  /** How the time a label was printed is shown, such as "Oct 7, 2026, 2:15 PM". */
  private static final DateTimeFormatter PRINTED = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,
      FormatStyle.SHORT);

  /**
   * Builds and displays the Forwarding screen on the given stage, on the Who
   * We Forward For tab.
   *
   * @param stage the window to render the screen into
   */
  public static void show(Stage stage) {
    var statusLabel = new Label();
    statusLabel.setId("forwardingStatusLabel");
    statusLabel.setWrapText(true);

    List<Mailbox> mailboxes = List.of();
    try {
      mailboxes = new MailboxRepository().findAll();
    } catch (SQLException e) {
      error(statusLabel, "Failed to load the boxes: " + e.getMessage());
    }
    var boxes = mailboxes;

    var addressTable = addressTable();
    var addresses = new FilteredList<>(FXCollections.observableArrayList(addressRows(boxes)));
    addressTable.setItems(addresses);
    var addressSearch = new TextField();
    addressSearch.setId("forwardingAddressSearch");
    addressSearch.setPromptText("Search by name, business, box number, or address");
    addressSearch.textProperty().addListener((obs, was, query) ->
        addresses.setPredicate(row -> matches(row, query)));

    var labelTable = labelTable();
    var allLabels = FXCollections.<LabelRow>observableArrayList();
    var labels = new FilteredList<>(allLabels);
    labelTable.setItems(labels);
    var labelSearch = new TextField();
    labelSearch.setId("forwardingLabelSearch");
    labelSearch.setPromptText("Search by label number, name, box number, or address");
    labelSearch.textProperty().addListener((obs, was, query) ->
        labels.setPredicate(row -> matches(row, query)));
    // Read again each time the tab is shown, so labels just printed appear.
    Runnable loadLabels = () -> {
      try {
        var byId = boxes.stream().collect(Collectors.toMap(Mailbox::getId, Function.identity()));
        allLabels.setAll(labelRows(new LabelRepository().findAll(), byId));
      } catch (SQLException e) {
        error(statusLabel, "Failed to load the labels printed: " + e.getMessage());
      }
    };

    var addressSelection = addressTable.getSelectionModel().selectedItemProperty();
    var printLabelBtn = new Button("Print Label…");
    printLabelBtn.setId("forwardingPrintLabelButton");
    printLabelBtn.disableProperty().bind(addressSelection.isNull());
    printLabelBtn.setOnAction(e -> {
      var row = addressTable.getSelectionModel().getSelectedItem();
      if (row != null) {
        ForwardingLabelView.show(stage, row.mailbox, row.address);
      }
    });
    var viewAddressBoxBtn = viewBoxButton(stage, "forwardingViewBoxButton",
        () -> addressTable.getSelectionModel().getSelectedItem() == null ? null
            : addressTable.getSelectionModel().getSelectedItem().mailbox);
    viewAddressBoxBtn.disableProperty().bind(addressSelection.isNull());
    onDoubleClick(addressTable, printLabelBtn::fire);

    var labelSelection = labelTable.getSelectionModel().selectedItemProperty();
    var reprintBtn = new Button("Reprint…");
    reprintBtn.setId("forwardingReprintButton");
    reprintBtn.disableProperty().bind(Bindings.createBooleanBinding(
        () -> labelSelection.get() == null || labelSelection.get().mailbox == null, labelSelection));
    reprintBtn.setOnAction(e -> {
      var row = labelTable.getSelectionModel().getSelectedItem();
      if (row != null && row.mailbox != null) {
        ForwardingLabelView.reprint(stage, row.mailbox, row.label);
      }
    });
    var viewLabelBoxBtn = viewBoxButton(stage, "forwardingLabelViewBoxButton",
        () -> labelTable.getSelectionModel().getSelectedItem() == null ? null
            : labelTable.getSelectionModel().getSelectedItem().mailbox);
    viewLabelBoxBtn.disableProperty().bind(reprintBtn.disableProperty());
    onDoubleClick(labelTable, reprintBtn::fire);

    var addressHint = new Label("Choose an address and click Print Label… to print a new forwarding label. "
        + "Newest addresses are first; ones added before this list existed are at the end.");
    addressHint.setWrapText(true);
    var addressesTab = new Tab("Who We Forward For", new VBox(10, addressSearch, addressHint, addressTable,
        new HBox(10, printLabelBtn, viewAddressBoxBtn)));
    addressesTab.setId("forwardingAddressesTab");

    var labelHint = new Label("Every forwarding label printed, newest first. Its number is in small type in the "
        + "label's bottom corner.");
    labelHint.setWrapText(true);
    var labelsTab = new Tab("Labels Printed", new VBox(10, labelSearch, labelHint, labelTable,
        new HBox(10, reprintBtn, viewLabelBoxBtn)));
    labelsTab.setId("forwardingLabelsTab");

    for (var tab : List.of(addressesTab, labelsTab)) {
      ((VBox) tab.getContent()).setPadding(new Insets(10, 0, 0, 0));
      VBox.setVgrow(tab == addressesTab ? addressTable : labelTable, Priority.ALWAYS);
    }
    var tabs = new TabPane(addressesTab, labelsTab);
    tabs.setId("forwardingTabs");
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    VBox.setVgrow(tabs, Priority.ALWAYS);
    tabs.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> {
      if (now == labelsTab) {
        loadLabels.run();
      }
    });

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var printBtn = new Button("Print…");
    printBtn.setId("printListButton");
    printBtn.setOnAction(e -> {
      if (tabs.getSelectionModel().getSelectedItem() == labelsTab) {
        TableOutput.print("Forwarding Labels Printed", List.of(new TableOutput.Section(null, labelTable)),
            statusLabel);
      } else {
        TableOutput.print("Who We Forward For", List.of(new TableOutput.Section(null, addressTable)),
            statusLabel);
      }
    });

    var spreadsheetBtn = new Button("Save as Spreadsheet…");
    spreadsheetBtn.setId("spreadsheetButton");
    spreadsheetBtn.setOnAction(e -> {
      var labelsShown = tabs.getSelectionModel().getSelectedItem() == labelsTab;
      TableOutput.run(statusLabel, () -> TableOutput.saveSpreadsheet(stage,
          (labelsShown ? "forwarding-labels-" : "forwarding-addresses-") + LocalDate.now() + ".csv",
          List.of(labelsShown ? labelTable : addressTable)));
    });

    var title = new Label("Forwarding");
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    var layout = new VBox(10, new HBox(10, backBtn, printBtn, spreadsheetBtn), title, tabs, statusLabel);
    layout.setPadding(new Insets(20));

    AppWindow.show(stage, layout);
  }

  /**
   * Returns a row for every forwarding address of every box, open or closed,
   * newest first; addresses with no day added come last, by box number.
   *
   * @param mailboxes every box
   * @return the rows
   */
  static List<AddressRow> addressRows(List<Mailbox> mailboxes) {
    var rows = new ArrayList<AddressRow>();
    for (var mailbox : mailboxes) {
      for (var address : mailbox.getForwardingAddresses()) {
        rows.add(new AddressRow(mailbox, address));
      }
    }
    rows.sort(Comparator.comparing((AddressRow row) -> row.address.getAddedOn(),
            Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(row -> row.mailbox.getBoxNumber(), BoxNumbers.ORDER));
    return rows;
  }

  /**
   * Returns a row for each printed label, in the order given, with the box it
   * was for.
   *
   * @param labels the labels, newest first
   * @param mailboxes every box, by id
   * @return the rows
   */
  static List<LabelRow> labelRows(List<PrintedLabel> labels, Map<Integer, Mailbox> mailboxes) {
    return labels.stream()
        .map(label -> new LabelRow(label, mailboxes.get(label.getMailboxId())))
        .collect(Collectors.toList());
  }

  /**
   * Checks whether a forwarding address matches every word of a search: in
   * the address itself, its note, or its box's number, holder's name,
   * business, or alternate business names, ignoring case. The box's other
   * addresses don't count, so searching for a town finds just that address.
   *
   * @param row the address and its box
   * @param query the search
   * @return {@code true} if it matches, or the search is blank
   */
  static boolean matches(AddressRow row, String query) {
    var box = row.mailbox;
    var text = String.join("\n", row.address.toString(), box.getBoxNumber(), box.getFullName(),
        box.getBusinessTitle() == null ? "" : box.getBusinessTitle(),
        String.join("\n", box.getAlternateBusinessNames()));
    return containsEveryWord(text, query);
  }

  /**
   * Checks whether text contains every word of a search, ignoring case.
   *
   * @param text the text
   * @param query the search
   * @return {@code true} if it does, or the search is blank
   */
  private static boolean containsEveryWord(String text, String query) {
    if (query == null || query.isBlank()) {
      return true;
    }
    var lower = text.toLowerCase();
    for (var word : query.toLowerCase().trim().split("\\s+")) {
      if (!lower.contains(word)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks whether a printed label matches every word of a search: in its
   * number, its box number, the holder's name, or the name and address as
   * printed, ignoring case.
   *
   * @param row the label
   * @param query the search
   * @return {@code true} if it matches, or the search is blank
   */
  static boolean matches(LabelRow row, String query) {
    var text = row.label.getNumber() + "\n" + row.label.getAddress() + "\n"
        + (row.mailbox == null ? "" : row.mailbox.getBoxNumber() + "\n" + row.mailbox.getFullName());
    return containsEveryWord(text, query);
  }

  /**
   * Builds the Who We Forward For table.
   *
   * @return the table, empty
   */
  private static TableView<AddressRow> addressTable() {
    var table = new TableView<AddressRow>();
    table.setId("forwardingAddressTable");
    table.setStyle("-fx-pref-height: 12em;");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.setPlaceholder(new Label("No forwarding addresses"));

    var addedCol = new TableColumn<AddressRow, LocalDate>("Added");
    addedCol.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().address.getAddedOn()));
    TableOutput.showDates(addedCol);
    addedCol.setSortable(false);

    var boxCol = new TableColumn<AddressRow, String>("Box");
    boxCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().mailbox.getBoxNumber()));
    boxCol.setComparator(BoxNumbers.ORDER);

    var nameCol = new TableColumn<AddressRow, String>("Name");
    nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().mailbox.getFullName()));

    var businessCol = new TableColumn<AddressRow, String>("Business");
    businessCol.setCellValueFactory(cell -> new SimpleStringProperty(
        cell.getValue().mailbox.getBusinessTitle() == null ? "" : cell.getValue().mailbox.getBusinessTitle()));

    var addressCol = new TableColumn<AddressRow, String>("Forwarding Address");
    addressCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().address.toString()));

    addedCol.setPrefWidth(130);
    boxCol.setPrefWidth(70);
    nameCol.setPrefWidth(170);
    businessCol.setPrefWidth(170);
    addressCol.setPrefWidth(460);
    table.getColumns().setAll(List.of(addedCol, boxCol, nameCol, businessCol, addressCol));
    return table;
  }

  /**
   * Builds the Labels Printed table.
   *
   * @return the table, empty
   */
  private static TableView<LabelRow> labelTable() {
    var table = new TableView<LabelRow>();
    table.setId("forwardingLabelTable");
    table.setStyle("-fx-pref-height: 12em;");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.setPlaceholder(new Label("No labels printed yet"));

    var numberCol = new TableColumn<LabelRow, String>("Label Number");
    numberCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().label.getNumber()));

    var printedCol = new TableColumn<LabelRow, LocalDateTime>("Printed");
    printedCol.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().label.getPrintedAt()));
    printedCol.setCellFactory(column -> new TableCell<>() {
      @Override
      protected void updateItem(LocalDateTime when, boolean empty) {
        super.updateItem(when, empty);
        setText(empty || when == null ? null : when.format(PRINTED));
      }
    });
    TableOutput.formatWith(printedCol, when -> when.format(PRINTED));

    var boxCol = new TableColumn<LabelRow, String>("Box");
    boxCol.setCellValueFactory(cell -> new SimpleStringProperty(
        cell.getValue().mailbox == null ? "" : cell.getValue().mailbox.getBoxNumber()));
    boxCol.setComparator(BoxNumbers.ORDER);

    var addressCol = new TableColumn<LabelRow, String>("Addressed To");
    addressCol.setCellValueFactory(cell -> new SimpleStringProperty(
        String.join(", ", cell.getValue().label.getAddress().split("\n"))));

    // The address gets most of the room; the rest are short.
    numberCol.setPrefWidth(110);
    printedCol.setPrefWidth(170);
    boxCol.setPrefWidth(60);
    addressCol.setPrefWidth(520);
    table.getColumns().setAll(List.of(numberCol, printedCol, boxCol, addressCol));
    return table;
  }

  /**
   * Makes a View Box button that opens a box's details.
   *
   * @param stage the main window
   * @param id the button's id
   * @param selected returns the box chosen, or {@code null} if none
   * @return the button
   */
  private static Button viewBoxButton(Stage stage, String id, Supplier<Mailbox> selected) {
    var button = new Button("View Box");
    button.setId(id);
    button.setOnAction(e -> {
      var mailbox = selected.get();
      if (mailbox != null) {
        BoxDetailsView.show(stage, mailbox, () -> EditBoxView.show(stage, mailbox, () -> show(stage)),
            () -> show(stage));
      }
    });
    return button;
  }

  /**
   * Runs an action when a row of a table is double-clicked.
   *
   * @param table the table
   * @param action what to do
   * @param <T> the type of the table's rows
   */
  private static <T> void onDoubleClick(TableView<T> table, Runnable action) {
    table.setRowFactory(tableView -> {
      var row = new TableRow<T>();
      row.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
          action.run();
        }
      });
      return row;
    });
  }

  /**
   * Shows a problem in red.
   *
   * @param status where to show it
   * @param message what went wrong
   */
  private static void error(Label status, String message) {
    status.setStyle("-fx-text-fill: red;");
    status.setText(message);
  }

  /** Not used: the screen is built with static methods. */
  private ForwardingView() {
  }

}
