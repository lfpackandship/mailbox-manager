package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.RentalHistoryRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;
import org.lfps.mailboxes.util.Money;

/**
 * A separate window showing everything recorded for one box, including its
 * rental history, opened from Manage Boxes, Renewals, or Payments, with
 * buttons to edit or renew it, or print a renewal reminder for it. Only one is open at a time; viewing another box
 * replaces it.
 */
final class BoxDetailsView {

  /** Shown in place of a detail that wasn't entered. */
  static final String NONE = "—";

  /** The open details window, or {@code null} if none is open. */
  private static Stage window;

  /**
   * Opens the details window for a box on top of the main window, replacing
   * any details window already open.
   *
   * @param owner the main window
   * @param mailbox the box to show
   * @param onEdit called after the window closes when the user clicks Edit
   * @param onRenewed called after the box is renewed from this window, to
   *     refresh the screen behind it
   */
  static void show(Stage owner, Mailbox mailbox, Runnable onEdit, Runnable onRenewed) {
    close();

    var title = new Label("Box " + mailbox.getBoxNumber()
        + (isBlank(mailbox.getBoxName()) ? "" : " – " + mailbox.getBoxName()));
    title.setId("detailTitle");
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    var today = LocalDate.now();
    var fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL);
    var endDate = mailbox.getEndDate() == null ? NONE
        : mailbox.getEndDate().format(fullDate)
            + (mailbox.isClosed() ? "" : " (" + RenewalsView.dueStatus(mailbox.getEndDate(), today).toLowerCase() + ")");

    String history;
    try {
      history = describeHistory(new RentalHistoryRepository().findForMailbox(mailbox.getId()));
    } catch (SQLException e) {
      history = "Couldn't load the rental history: " + e.getMessage();
    }

    var grid = new GridPane();
    grid.setHgap(15);
    grid.setVgap(8);
    var rows = new ArrayList<Label[]>();
    if (mailbox.isClosed()) {
      rows.add(row("Closed", "detailClosed", mailbox.getClosedDate().format(fullDate)));
    }
    rows.addAll(List.of(
        row("Holder", "detailHolder", orNone(mailbox.getFullName())),
        row("Business title", "detailBusinessTitle", orNone(mailbox.getBusinessTitle())),
        row("Also receives mail as", "detailAlternateNames", lines(mailbox.getAlternateBusinessNames())),
        row("Phone", "detailPhone", orNone(PhoneNumberFormatter.format(mailbox.getPhone()))),
        row("Email", "detailEmail", orNone(mailbox.getEmail())),
        row("Rental ends", "detailEndDate", endDate),
        row("Box", "detailForwardingOnly", !mailbox.isForwardingOnly() ? "Rented here"
            : "Forwarding only: no box rented here, mail is forwarded"
                + (mailbox.getForwardingAddresses().isEmpty() ? ". No forwarding address is recorded yet." : "")),
        row("Keys", "detailKeys", orNone(KeyFields.describe(mailbox.getKeyCount(), mailbox.getKeyDepositCents()))),
        row("Forwarding addresses", "detailForwarding", lines(mailbox.getForwardingAddresses())),
        row("Notes", "detailNotes", orNone(mailbox.getNotes())),
        row("Rental history", "detailHistory", history)));
    for (var i = 0; i < rows.size(); i++) {
      grid.addRow(i, rows.get(i));
    }

    var stage = new Stage();

    var editBtn = new Button("Edit");
    editBtn.setId("detailsEditButton");
    editBtn.setOnAction(e -> {
      stage.close();
      onEdit.run();
    });

    var renewBtn = new Button("Renew…");
    renewBtn.setId("detailsRenewButton");
    renewBtn.setDisable(mailbox.isClosed());
    renewBtn.setOnAction(e -> {
      stage.close();
      RenewBoxView.show(owner, mailbox, onRenewed);
    });

    var reminderBtn = new Button("Print Reminder…");
    reminderBtn.setId("detailsReminderButton");
    reminderBtn.setDisable(mailbox.isClosed() || mailbox.getEndDate() == null);
    reminderBtn.setOnAction(e -> PriceSheetView.showReminders(owner, List.of(mailbox)));

    var closeBtn = new Button("Close");
    closeBtn.setId("detailsCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setDefaultButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var content = new VBox(15, title, grid, new HBox(10, editBtn, renewBtn, reminderBtn, closeBtn));
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    AppWindow.applyTextSize(scrollPane);

    stage.initOwner(owner);
    stage.setTitle(BoxLabels.boxAndHolder(mailbox));
    stage.setScene(new Scene(scrollPane));
    stage.setOnHidden(e -> {
      if (window == stage) {
        window = null;
      }
    });
    window = stage;
    AppWindow.showWithinScreen(stage);
  }

  /**
   * Makes a table of boxes open a box's details when its row is
   * double-clicked, or when Enter is pressed on the selected row.
   *
   * @param table the table of boxes
   * @param view shows the details of the chosen box
   */
  static void openOnDoubleClickOrEnter(TableView<Mailbox> table, Consumer<Mailbox> view) {
    table.setRowFactory(tableView -> {
      var row = new TableRow<Mailbox>() {
        @Override
        protected void updateItem(Mailbox item, boolean empty) {
          super.updateItem(item, empty);
          // Grey out closed boxes so they stand apart from rented ones.
          setStyle(item != null && item.isClosed() ? "-fx-text-background-color: gray;" : "");
        }
      };
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
  }

  /**
   * Closes the details window if one is open, for example when the box it
   * shows has been deleted.
   */
  static void close() {
    if (window != null) {
      window.close();
    }
  }

  /**
   * Makes a row of the details: a bold name and its value.
   *
   * @param name what the row shows, such as "Phone"
   * @param id the value's id
   * @param value the value
   * @return the name and value labels
   */
  private static Label[] row(String name, String id, String value) {
    var nameLabel = new Label(name + ":");
    nameLabel.setStyle("-fx-font-weight: bold;");
    var valueLabel = new Label(value);
    valueLabel.setId(id);
    valueLabel.setWrapText(true);
    return new Label[] { nameLabel, valueLabel };
  }

  /**
   * Describes a box's rental history, one entry per line, such as "Sep 26, 2026
   * – Mar 26, 2027: $60.00, Check (check #1042)".
   *
   * @param periods the history, oldest first
   * @return the description, or "None" if there's no history
   */
  static String describeHistory(List<RentalPeriod> periods) {
    if (periods.isEmpty()) {
      return NONE;
    }
    var lines = new ArrayList<String>();
    for (var period : periods) {
      var paid = new ArrayList<String>();
      if (period.getAmountCents() != null) {
        paid.add(Money.format(period.getAmountCents()));
      }
      if (period.getPaymentMethod() != null) {
        paid.add(period.getPaymentMethod());
      }
      var line = period.describePeriod() + (paid.isEmpty() ? "" : ": " + String.join(", ", paid));
      lines.add(period.getNote() == null ? line : line + " (" + period.getNote() + ")");
    }
    return String.join("\n", lines);
  }

  /**
   * Lists items, one per line.
   *
   * @param items the items
   * @return the list, or "None" if it's empty
   */
  private static String lines(List<?> items) {
    return items.isEmpty() ? NONE : items.stream().map(String::valueOf).collect(Collectors.joining("\n"));
  }

  /**
   * Returns a value, or "None" if it's blank.
   *
   * @param value the value, or {@code null}
   * @return the value or "None"
   */
  private static String orNone(String value) {
    return isBlank(value) ? NONE : value;
  }

  /**
   * Returns whether a value is missing or blank.
   *
   * @param value the value, or {@code null}
   * @return {@code true} if it's {@code null} or only whitespace
   */
  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** Not used: the window is built with static methods. */
  private BoxDetailsView() {
  }

}
