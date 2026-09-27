package org.lfps.mailboxes.view;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.BoxNumbers;

/**
 * A separate window listing the boxes whose rental ends on one day, opened by
 * clicking the day on the Calendar, with buttons to see a box's details or
 * renew it. Only one is open at a time; opening another day replaces it.
 */
final class CalendarDayView {

  /** The open day window, or {@code null} if none is open. */
  private static Stage window;

  /**
   * Opens the window for a day on top of the main window, replacing any day
   * window already open.
   *
   * @param owner the main window
   * @param date the day
   * @param mailboxes the boxes whose rental ends that day
   * @param onChanged called after a box is edited or renewed from here, to
   *     refresh the Calendar
   */
  static void show(Stage owner, LocalDate date, List<Mailbox> mailboxes, Runnable onChanged) {
    close();

    var today = LocalDate.now();
    var title = new Label((mailboxes.size() == 1 ? "1 rental ends " : mailboxes.size() + " rentals end ")
        + (date.equals(today) ? "today, " : "on ") + date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
    title.setId("dayTitle");
    title.setWrapText(true);
    title.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");

    var status = new Label(RenewalsView.dueStatus(date, today));
    status.setId("dayStatus");

    var table = new TableView<Mailbox>();
    table.setId("dayTable");
    table.setStyle("-fx-pref-height: 12em; -fx-pref-width: 40em;");
    VBox.setVgrow(table, Priority.ALWAYS);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    var boxNumberCol = new TableColumn<Mailbox, String>("Box Number");
    boxNumberCol.setCellValueFactory(new PropertyValueFactory<>("boxNumber"));
    boxNumberCol.setComparator(BoxNumbers.ORDER);

    var nameCol = new TableColumn<Mailbox, String>("Name");
    nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFullName()));

    var businessCol = new TableColumn<Mailbox, String>("Business Title");
    businessCol.setCellValueFactory(new PropertyValueFactory<>("businessTitle"));

    var phoneCol = new TableColumn<Mailbox, String>("Phone");
    phoneCol.setCellValueFactory(cell -> new SimpleStringProperty(
        PhoneNumberFormatter.format(cell.getValue().getPhone())));

    table.getColumns().setAll(List.of(boxNumberCol, nameCol, businessCol, phoneCol));
    table.setItems(FXCollections.observableArrayList(mailboxes));
    table.getSelectionModel().select(0);

    var stage = new Stage();

    // Details and renewals open over the main window, so this one closes
    // first and the Calendar behind it is refreshed afterwards.
    Consumer<Mailbox> view = mailbox -> {
      stage.close();
      BoxDetailsView.show(owner, mailbox, () -> EditBoxView.show(owner, mailbox, onChanged), onChanged);
    };
    Consumer<Mailbox> renew = mailbox -> {
      stage.close();
      RenewBoxView.show(owner, mailbox, onChanged);
    };
    BoxDetailsView.openOnDoubleClickOrEnter(table, view);

    var viewBtn = new Button("View");
    viewBtn.setId("dayViewButton");
    viewBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    viewBtn.setOnAction(e -> view.accept(table.getSelectionModel().getSelectedItem()));

    var renewBtn = new Button("Renew…");
    renewBtn.setId("dayRenewButton");
    renewBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    renewBtn.setOnAction(e -> renew.accept(table.getSelectionModel().getSelectedItem()));

    var closeBtn = new Button("Close");
    closeBtn.setId("dayCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var content = new VBox(10, title, status, table, new HBox(10, viewBtn, renewBtn, closeBtn));
    content.setPadding(new Insets(20));
    AppWindow.applyTextSize(content);

    stage.initOwner(owner);
    stage.setTitle(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
    stage.setScene(new Scene(content));
    stage.setOnHidden(e -> {
      if (window == stage) {
        window = null;
      }
    });
    window = stage;
    stage.show();
  }

  /**
   * Closes the day window if one is open.
   */
  static void close() {
    if (window != null) {
      window.close();
    }
  }

  private CalendarDayView() {
  }

}
