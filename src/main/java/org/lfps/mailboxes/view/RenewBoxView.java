package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.RentalHistoryRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;

/**
 * A window for renewing a box: choose how long it's renewed for, and record
 * what was paid. Choosing a rental length fills in its price for the box's
 * size (see {@link PricesView}). The renewal is added to the box's rental
 * history and moves its end date.
 */
final class RenewBoxView {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL);

  /**
   * Opens the renewal window on top of the main window. The main window
   * can't be used until it's closed.
   *
   * @param owner the main window
   * @param mailbox the box to renew
   * @param onRenewed called after the renewal is saved
   */
  static void show(Stage owner, Mailbox mailbox, Runnable onRenewed) {
    var today = LocalDate.now();
    var current = mailbox.getEndDate();

    var title = new Label("Renew Box " + mailbox.getBoxNumber() + " – " + mailbox.getFullName());
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    var currentLabel = new Label(current == null
        ? "This box has no end date set."
        : "The rental ends " + current.format(DATE) + " ("
            + RenewalsView.dueStatus(current, today).toLowerCase() + ").");
    currentLabel.setId("renewCurrentLabel");
    currentLabel.setWrapText(true);

    // Renewing continues from the current end date, so no days are lost or
    // given away; the start can be changed, e.g. for a box that lapsed long ago.
    var startField = new DatePicker(current == null ? today : current);
    startField.setId("renewStartField");
    startField.setStyle("-fx-pref-width: 12em;");

    var endField = new DatePicker();
    endField.setId("renewEndField");
    endField.setStyle("-fx-pref-width: 12em;");

    var payment = new PaymentFields();

    var lengthButtons = RentalLengthButtons.create(months -> {
      var start = startField.getValue() == null ? today : startField.getValue();
      endField.setValue(start.plusMonths(months));
      payment.suggest(PricesView.priceFor(mailbox.getBoxNumber(), months));
    });

    var noteField = new TextField();
    noteField.setId("renewNoteField");
    noteField.setPromptText("e.g. check #1042 (optional)");
    noteField.setStyle("-fx-pref-width: 12em;");

    var errorLabel = new Label();
    errorLabel.setId("renewErrorLabel");
    errorLabel.setStyle("-fx-text-fill: red;");
    errorLabel.setWrapText(true);

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.addRow(0, new Label("Renew from:"), startField);
    grid.add(lengthButtons, 0, 1, 2, 1);
    grid.addRow(2, new Label("New end date:"), endField);
    grid.addRow(3, new Label("Amount paid:"), payment.amountField);
    grid.addRow(4, new Label("Paid by:"), payment.methodField);
    grid.addRow(5, new Label("Note:"), noteField);

    var stage = new Stage();

    var renewBtn = new Button("Renew");
    renewBtn.setId("renewSaveButton");
    renewBtn.setDefaultButton(true);
    renewBtn.setOnAction(e -> {
      var errors = new StringBuilder();
      var start = startField.getValue();
      var end = endField.getValue();
      if (start == null) {
        errors.append("Choose the date to renew from.\n");
      }
      if (end == null) {
        errors.append("Choose a rental length or the new end date.\n");
      } else if (start != null && !end.isAfter(start)) {
        errors.append("The new end date must be after the date it's renewed from.\n");
      }
      Long amount = null;
      try {
        amount = payment.amountCents();
      } catch (IllegalArgumentException ex) {
        errors.append(ex.getMessage()).append('\n');
      }
      if (errors.length() > 0) {
        errorLabel.setText(errors.toString().trim());
        return;
      }

      try {
        new RentalHistoryRepository().renew(mailbox.getId(),
            new RentalPeriod(0, mailbox.getId(), today, start, end, amount, payment.method(), noteField.getText()));
        stage.close();
        onRenewed.run();
      } catch (SQLException ex) {
        errorLabel.setText("Failed to save the renewal: " + ex.getMessage());
      }
    });

    var cancelBtn = new Button("Cancel");
    cancelBtn.setId("renewCancelButton");
    cancelBtn.setCancelButton(true);
    cancelBtn.setOnAction(e -> stage.close());

    var content = new VBox(12, title, currentLabel, grid, errorLabel, new HBox(10, renewBtn, cancelBtn));
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    AppWindow.applyTextSize(scrollPane);

    stage.initOwner(owner);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle("Renew Box " + mailbox.getBoxNumber());
    stage.setScene(new Scene(scrollPane));
    stage.show();
  }

  private RenewBoxView() {
  }

}
