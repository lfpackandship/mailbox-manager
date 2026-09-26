package org.lfps.mailboxes.view;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.model.Mailbox;

/**
 * A separate window showing everything recorded for one box, opened from
 * Manage Boxes, with a button to edit it. Only one is open at a time; viewing
 * another box replaces it.
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
   */
  static void show(Stage owner, Mailbox mailbox, Runnable onEdit) {
    close();

    var title = new Label("Box " + mailbox.getBoxNumber()
        + (isBlank(mailbox.getBoxName()) ? "" : " – " + mailbox.getBoxName()));
    title.setId("detailTitle");
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    var today = LocalDate.now();
    var endDate = mailbox.getEndDate() == null ? NONE
        : mailbox.getEndDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
            + " (" + RenewalsView.dueStatus(mailbox.getEndDate(), today).toLowerCase() + ")";

    var grid = new GridPane();
    grid.setHgap(15);
    grid.setVgap(8);
    var rows = List.of(
        row("Holder", "detailHolder", mailbox.getFirstName() + " " + mailbox.getLastName()),
        row("Business title", "detailBusinessTitle", orNone(mailbox.getBusinessTitle())),
        row("Also receives mail as", "detailAlternateNames", lines(mailbox.getAlternateBusinessNames())),
        row("Phone", "detailPhone", PhoneNumberFormatter.format(mailbox.getPhone())),
        row("Email", "detailEmail", orNone(mailbox.getEmail())),
        row("Rental ends", "detailEndDate", endDate),
        row("Forwarding addresses", "detailForwarding", lines(mailbox.getForwardingAddresses())));
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

    var closeBtn = new Button("Close");
    closeBtn.setId("detailsCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setDefaultButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var content = new VBox(15, title, grid, new HBox(10, editBtn, closeBtn));
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    AppWindow.applyTextSize(scrollPane);

    stage.initOwner(owner);
    stage.setTitle("Box " + mailbox.getBoxNumber() + " – " + mailbox.getFirstName() + " " + mailbox.getLastName());
    stage.setScene(new Scene(scrollPane));
    stage.setOnHidden(e -> {
      if (window == stage) {
        window = null;
      }
    });
    window = stage;
    stage.show();
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

  private static Label[] row(String name, String id, String value) {
    var nameLabel = new Label(name + ":");
    nameLabel.setStyle("-fx-font-weight: bold;");
    var valueLabel = new Label(value);
    valueLabel.setId(id);
    valueLabel.setWrapText(true);
    return new Label[] { nameLabel, valueLabel };
  }

  private static String lines(List<?> items) {
    return items.isEmpty() ? NONE : items.stream().map(String::valueOf).collect(Collectors.joining("\n"));
  }

  private static String orNone(String value) {
    return isBlank(value) ? NONE : value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private BoxDetailsView() {
  }

}
