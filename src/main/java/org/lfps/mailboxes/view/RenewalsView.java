package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Lists mailboxes whose box rental has already expired and those expiring
 * within the number of days set by {@link Setting#RENEWAL_WINDOW_DAYS}, each
 * sorted with the most urgent entry first.
 */
public class RenewalsView {

  /**
   * Builds and displays the renewals list on the given stage.
   *
   * @param stage the window to render the list into
   */
  public static void show(Stage stage) {
    var statusLabel = new Label();
    var today = LocalDate.now();
    var pastDue = List.<Mailbox>of();
    var upcoming = List.<Mailbox>of();
    var windowDays = Integer.parseInt(Setting.RENEWAL_WINDOW_DAYS.defaultValue());

    try {
      windowDays = new SettingsRepository().getInt(Setting.RENEWAL_WINDOW_DAYS);
      var mailboxes = new MailboxRepository().findAll();
      pastDue = pastDue(mailboxes, today);
      upcoming = upcoming(mailboxes, today, windowDays);
    } catch (SQLException e) {
      statusLabel.setStyle("-fx-text-fill: red;");
      statusLabel.setText("Failed to load mailboxes: " + e.getMessage());
    }

    var pastDueTable = buildTable(today);
    pastDueTable.setId("pastDueTable");
    pastDueTable.setItems(FXCollections.observableArrayList(pastDue));

    var upcomingTable = buildTable(today);
    upcomingTable.setId("upcomingTable");
    upcomingTable.setItems(FXCollections.observableArrayList(upcoming));

    Consumer<Mailbox> edit = mailbox -> EditBoxView.show(stage, mailbox, () -> show(stage));
    Consumer<Mailbox> view = mailbox -> BoxDetailsView.show(stage, mailbox, () -> edit.accept(mailbox));
    BoxDetailsView.openOnDoubleClickOrEnter(pastDueTable, view);
    BoxDetailsView.openOnDoubleClickOrEnter(upcomingTable, view);

    var pastDueHeader = new Label("Past Due");
    pastDueHeader.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");

    var upcomingHeader = new Label("Upcoming (next " + windowDays + " days)");
    upcomingHeader.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var layout = new VBox(10,
        backBtn,
        pastDueHeader,
        pastDueTable,
        tableButtons(pastDueTable, "pastDue", view, edit),
        upcomingHeader,
        upcomingTable,
        tableButtons(upcomingTable, "upcoming", view, edit),
        statusLabel);
    layout.setPadding(new Insets(20));

    AppWindow.show(stage, layout);
  }

  /**
   * Returns the mailboxes whose end date is before today, earliest first.
   */
  static List<Mailbox> pastDue(List<Mailbox> mailboxes, LocalDate today) {
    return mailboxes.stream()
        .filter(m -> m.getEndDate() != null && m.getEndDate().isBefore(today))
        .sorted(Comparator.comparing(Mailbox::getEndDate))
        .collect(Collectors.toList());
  }

  /**
   * Returns the mailboxes whose end date is from today through
   * {@code windowDays} days from now inclusive, earliest first.
   */
  static List<Mailbox> upcoming(List<Mailbox> mailboxes, LocalDate today, int windowDays) {
    var windowEnd = today.plusDays(windowDays);
    return mailboxes.stream()
        .filter(m -> m.getEndDate() != null
            && !m.getEndDate().isBefore(today)
            && !m.getEndDate().isAfter(windowEnd))
        .sorted(Comparator.comparing(Mailbox::getEndDate))
        .collect(Collectors.toList());
  }

  /**
   * Describes how far a rental end date is from today, such as "In 3 days",
   * "Due today", or "2 days overdue".
   */
  static String dueStatus(LocalDate endDate, LocalDate today) {
    var days = ChronoUnit.DAYS.between(today, endDate);
    if (days < 0) {
      return -days + " day" + (days == -1 ? "" : "s") + " overdue";
    } else if (days == 0) {
      return "Due today";
    }
    return "In " + days + " day" + (days == 1 ? "" : "s");
  }

  private static HBox tableButtons(TableView<Mailbox> table, String idPrefix, Consumer<Mailbox> view,
      Consumer<Mailbox> edit) {
    var viewBtn = new Button("View");
    viewBtn.setId(idPrefix + "ViewButton");
    viewBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    viewBtn.setOnAction(e -> view.accept(table.getSelectionModel().getSelectedItem()));

    var editBtn = new Button("Edit");
    editBtn.setId(idPrefix + "EditButton");
    editBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    editBtn.setOnAction(e -> edit.accept(table.getSelectionModel().getSelectedItem()));

    return new HBox(10, viewBtn, editBtn);
  }

  private static TableView<Mailbox> buildTable(LocalDate today) {
    var table = new TableView<Mailbox>();
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    // Start small enough for both tables to fit the default window, then
    // share any extra height between them.
    table.setStyle("-fx-pref-height: 8em;");
    VBox.setVgrow(table, Priority.ALWAYS);

    var nameCol = new TableColumn<Mailbox, String>("Name");
    nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(
        cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()));

    var boxNumberCol = new TableColumn<Mailbox, String>("Box Number");
    boxNumberCol.setCellValueFactory(new PropertyValueFactory<>("boxNumber"));

    var phoneCol = new TableColumn<Mailbox, String>("Phone");
    phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));

    var endDateCol = new TableColumn<Mailbox, LocalDate>("End Date");
    endDateCol.setCellValueFactory(new PropertyValueFactory<>("endDate"));

    var statusCol = new TableColumn<Mailbox, String>("Status");
    statusCol.setCellValueFactory(cellData ->
        new SimpleStringProperty(dueStatus(cellData.getValue().getEndDate(), today)));

    var columns = table.getColumns();
    columns.add(nameCol);
    columns.add(boxNumberCol);
    columns.add(phoneCol);
    columns.add(endDateCol);
    columns.add(statusCol);

    return table;
  }

  private RenewalsView() {
  }

}
