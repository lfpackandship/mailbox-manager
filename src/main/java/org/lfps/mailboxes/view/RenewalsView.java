package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
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
    pastDueTable.setItems(FXCollections.observableArrayList(pastDue));

    var upcomingTable = buildTable(today);
    upcomingTable.setItems(FXCollections.observableArrayList(upcoming));

    var pastDueEditBtn = new Button("Edit");
    pastDueEditBtn.disableProperty().bind(pastDueTable.getSelectionModel().selectedItemProperty().isNull());
    pastDueEditBtn.setOnAction(e -> {
      var selected = pastDueTable.getSelectionModel().getSelectedItem();
      if (selected != null) {
        EditBoxView.show(stage, selected, () -> show(stage));
      }
    });

    var upcomingEditBtn = new Button("Edit");
    upcomingEditBtn.disableProperty().bind(upcomingTable.getSelectionModel().selectedItemProperty().isNull());
    upcomingEditBtn.setOnAction(e -> {
      var selected = upcomingTable.getSelectionModel().getSelectedItem();
      if (selected != null) {
        EditBoxView.show(stage, selected, () -> show(stage));
      }
    });

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
        new HBox(10, pastDueEditBtn),
        upcomingHeader,
        upcomingTable,
        new HBox(10, upcomingEditBtn),
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

  private static TableView<Mailbox> buildTable(LocalDate today) {
    var table = new TableView<Mailbox>();
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.setStyle("-fx-pref-height: 14em;");

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
    statusCol.setCellValueFactory(cellData -> {
      var endDate = cellData.getValue().getEndDate();
      var days = ChronoUnit.DAYS.between(today, endDate);
      String text;
      if (days < 0) {
        text = -days + " day" + (days == -1 ? "" : "s") + " overdue";
      } else if (days == 0) {
        text = "Due today";
      } else {
        text = "In " + days + " day" + (days == 1 ? "" : "s");
      }
      return new SimpleStringProperty(text);
    });

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
