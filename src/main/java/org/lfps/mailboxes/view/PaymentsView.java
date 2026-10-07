package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.RentalHistoryRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;
import org.lfps.mailboxes.util.BoxNumbers;
import org.lfps.mailboxes.util.Money;

/**
 * Lists the rentals and renewals recorded between two dates, for every box,
 * with the total paid, for example to see what was taken in this month, and
 * the key deposits being held, which are given back.
 */
public class PaymentsView {

  /** A rental history entry and the box it belongs to. */
  static final class Entry {

    /** The rental history entry. */
    final RentalPeriod period;

    /**
     * The box it belongs to, or {@code null} if the box was deleted.
     */
    final Mailbox mailbox;

    /**
     * Makes an entry.
     *
     * @param period the rental history entry
     * @param mailbox the box it belongs to
     */
    Entry(RentalPeriod period, Mailbox mailbox) {
      this.period = period;
      this.mailbox = mailbox;
    }

  }

  /**
   * Builds and displays this month's payments on the given stage.
   *
   * @param stage the window to render the list into
   */
  public static void show(Stage stage) {
    var today = LocalDate.now();
    show(stage, today.withDayOfMonth(1), today);
  }

  /**
   * Builds and displays the payments recorded between two days.
   *
   * @param stage the window to render the list into
   * @param from the first day, inclusive
   * @param to the last day, inclusive
   */
  private static void show(Stage stage, LocalDate from, LocalDate to) {
    var statusLabel = new Label();
    var entries = new ArrayList<Entry>();
    var depositsHeld = "";
    try {
      var allMailboxes = new MailboxRepository().findAll();
      depositsHeld = depositsHeld(allMailboxes);
      var mailboxes = allMailboxes.stream()
          .collect(Collectors.toMap(Mailbox::getId, Function.identity()));
      for (var period : new RentalHistoryRepository().findRecordedBetween(from, to)) {
        entries.add(new Entry(period, mailboxes.get(period.getMailboxId())));
      }
    } catch (SQLException e) {
      statusLabel.setStyle("-fx-text-fill: red;");
      statusLabel.setText("Failed to load payments: " + e.getMessage());
    }

    var fromField = new DatePicker(from);
    fromField.setId("paymentsFromField");
    fromField.setStyle("-fx-pref-width: 10em;");
    var toField = new DatePicker(to);
    toField.setId("paymentsToField");
    toField.setStyle("-fx-pref-width: 10em;");

    var showBtn = new Button("Show");
    showBtn.setId("paymentsShowButton");
    showBtn.setDefaultButton(true);
    showBtn.setOnAction(e -> {
      if (fromField.getValue() == null || toField.getValue() == null) {
        statusLabel.setStyle("-fx-text-fill: red;");
        statusLabel.setText("Choose both dates.");
      } else if (toField.getValue().isBefore(fromField.getValue())) {
        statusLabel.setStyle("-fx-text-fill: red;");
        statusLabel.setText("The second date must be on or after the first.");
      } else {
        show(stage, fromField.getValue(), toField.getValue());
      }
    });

    var today = LocalDate.now();
    var thisMonthBtn = new Button("This Month");
    thisMonthBtn.setOnAction(e -> show(stage, today.withDayOfMonth(1), today));
    var lastMonthBtn = new Button("Last Month");
    lastMonthBtn.setOnAction(e -> {
      var lastMonth = YearMonth.from(today).minusMonths(1);
      show(stage, lastMonth.atDay(1), lastMonth.atEndOfMonth());
    });
    var thisYearBtn = new Button("This Year");
    thisYearBtn.setOnAction(e -> show(stage, today.withDayOfYear(1), today));

    var fromRow = new HBox(8, new Label("From:"), fromField, new Label("To:"), toField, showBtn);
    fromRow.setAlignment(Pos.CENTER_LEFT);

    var table = new TableView<Entry>();
    table.setId("paymentsTable");
    table.setStyle("-fx-pref-height: 12em;");
    VBox.setVgrow(table, Priority.ALWAYS);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.setPlaceholder(new Label("Nothing recorded in these dates"));

    var dateCol = new TableColumn<Entry, LocalDate>("Date");
    dateCol.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().period.getRecordedOn()));
    TableOutput.showDates(dateCol);

    var boxCol = new TableColumn<Entry, String>("Box");
    boxCol.setCellValueFactory(cell -> new SimpleStringProperty(
        cell.getValue().mailbox == null ? "" : cell.getValue().mailbox.getBoxNumber()));
    boxCol.setComparator(BoxNumbers.ORDER);

    var nameCol = new TableColumn<Entry, String>("Name");
    nameCol.setCellValueFactory(cell -> new SimpleStringProperty(
        cell.getValue().mailbox == null ? "" : cell.getValue().mailbox.getHolderName()));

    var periodCol = new TableColumn<Entry, String>("Period");
    periodCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().period.describePeriod()));

    var amountCol = new TableColumn<Entry, Long>("Amount");
    amountCol.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().period.getAmountCents()));
    amountCol.setCellFactory(column -> new TableCell<>() {
      @Override
      protected void updateItem(Long cents, boolean empty) {
        super.updateItem(cents, empty);
        setText(empty || cents == null ? null : Money.format(cents));
      }
    });
    TableOutput.formatWith(amountCol, Money::format);

    var methodCol = new TableColumn<Entry, String>("Paid By");
    methodCol.setCellValueFactory(cell -> new SimpleStringProperty(
        Objects.toString(cell.getValue().period.getPaymentMethod(), "")));

    var noteCol = new TableColumn<Entry, String>("Note");
    noteCol.setCellValueFactory(cell -> new SimpleStringProperty(
        Objects.toString(cell.getValue().period.getNote(), "")));

    table.getColumns().setAll(List.of(dateCol, boxCol, nameCol, periodCol, amountCol, methodCol, noteCol));
    table.setItems(FXCollections.observableArrayList(entries));

    var totalLabel = new Label(total(entries));
    totalLabel.setId("paymentsTotal");
    totalLabel.setStyle("-fx-font-weight: bold;");

    // Not income: it's given back when the keys are returned.
    var depositsLabel = new Label(depositsHeld);
    depositsLabel.setId("paymentsDepositsHeld");
    depositsLabel.setVisible(!depositsHeld.isEmpty());
    depositsLabel.setManaged(!depositsHeld.isEmpty());

    Runnable refresh = () -> show(stage, from, to);
    var selection = table.getSelectionModel().selectedItemProperty();

    Runnable viewSelected = () -> {
      var entry = table.getSelectionModel().getSelectedItem();
      if (entry != null && entry.mailbox != null) {
        BoxDetailsView.show(stage, entry.mailbox, () -> EditBoxView.show(stage, entry.mailbox, refresh), refresh);
      }
    };
    table.setRowFactory(tableView -> {
      var row = new TableRow<Entry>();
      row.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
          viewSelected.run();
        }
      });
      return row;
    });

    var viewBtn = new Button("View Box");
    viewBtn.setId("paymentsViewButton");
    viewBtn.disableProperty().bind(selection.isNull());
    viewBtn.setOnAction(e -> viewSelected.run());

    // Fixes an entry recorded wrongly. A box that's been deleted has no
    // history left, but an entry without a box can't be edited either way.
    var editBtn = new Button("Edit Entry…");
    editBtn.setId("paymentsEditButton");
    editBtn.disableProperty().bind(Bindings.createBooleanBinding(
        () -> selection.get() == null || selection.get().mailbox == null, selection));
    editBtn.setOnAction(e -> {
      var entry = table.getSelectionModel().getSelectedItem();
      if (entry != null && entry.mailbox != null) {
        RenewBoxView.edit(stage, entry.mailbox, entry.period, refresh);
      }
    });

    var deleteBtn = new Button("Delete Entry");
    deleteBtn.setId("paymentsDeleteButton");
    deleteBtn.disableProperty().bind(selection.isNull());
    deleteBtn.setOnAction(e -> {
      var entry = table.getSelectionModel().getSelectedItem();
      if (entry == null) {
        return;
      }
      if (!Dialogs.confirm.ask(stage, "Delete this entry from the rental history?",
          "Use this for an entry recorded by mistake. The box's rental end date isn't changed; edit the box "
              + "to change it.", "Delete Entry", "Cancel")) {
        return;
      }
      try {
        new RentalHistoryRepository().delete(entry.period.getId());
        refresh.run();
      } catch (SQLException ex) {
        statusLabel.setStyle("-fx-text-fill: red;");
        statusLabel.setText("Failed to delete: " + ex.getMessage());
      }
    });

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    var printBtn = new Button("Print…");
    printBtn.setId("printListButton");
    printBtn.setOnAction(e -> TableOutput.print(
        "Payments, " + from.format(dates) + " – " + to.format(dates) + "\n" + total(entries),
        List.of(new TableOutput.Section(null, table)), statusLabel));

    var spreadsheetBtn = new Button("Save as Spreadsheet…");
    spreadsheetBtn.setId("spreadsheetButton");
    spreadsheetBtn.setOnAction(e -> TableOutput.run(statusLabel, () -> TableOutput.saveSpreadsheet(stage,
        "payments-" + from + "-to-" + to + ".csv", List.of(table))));

    var layout = new VBox(10,
        new HBox(10, backBtn, printBtn, spreadsheetBtn),
        fromRow,
        new HBox(8, thisMonthBtn, lastMonthBtn, thisYearBtn),
        table,
        totalLabel,
        depositsLabel,
        new HBox(10, viewBtn, editBtn, deleteBtn),
        statusLabel);
    layout.setPadding(new Insets(20));

    AppWindow.show(stage, layout);
  }

  /**
   * Describes how many entries there are and the total paid, such as "3
   * entries, $180.00 paid".
   *
   * @param entries the payments
   * @return the total
   */
  static String total(List<Entry> entries) {
    var cents = entries.stream()
        .map(entry -> entry.period.getAmountCents())
        .filter(Objects::nonNull)
        .mapToLong(Long::longValue)
        .sum();
    return entries.size() + (entries.size() == 1 ? " entry, " : " entries, ") + Money.format(cents) + " paid";
  }

  /**
   * Describes the key deposits being held for open boxes, which aren't counted
   * as paid since they're given back, such as "Key deposits held for open
   * boxes: $120.00 (not included above)".
   *
   * @return the description, or an empty string if no deposits are held
   * @param mailboxes every box
   */
  static String depositsHeld(List<Mailbox> mailboxes) {
    var cents = mailboxes.stream()
        .filter(m -> !m.isClosed() && m.getKeyDepositCents() != null)
        .mapToLong(Mailbox::getKeyDepositCents)
        .sum();
    return cents == 0 ? "" : "Key deposits held for open boxes: " + Money.format(cents) + " (not included above)";
  }

  /** Not used: the screen is built with static methods. */
  private PaymentsView() {
  }

}
