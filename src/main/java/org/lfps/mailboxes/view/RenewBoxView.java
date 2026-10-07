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
import org.lfps.mailboxes.util.Money;

/**
 * A window for renewing a box: choose how long it's renewed for, and record
 * what was paid. Choosing a rental length fills in its price for the box's
 * size (see {@link PricesView}). The renewal is added to the box's rental
 * history and moves its end date.
 *
 * <p>The same window, opened with {@link #edit}, fixes an entry already in
 * the rental history, from "Edit Entry…" on Payments.
 */
final class RenewBoxView {

  /** Formats dates in full, such as Monday, October 5, 2026. */
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
    open(owner, mailbox, null, onRenewed);
  }

  /**
   * Opens the window to change an entry in a box's rental history, filled in
   * with what was recorded. Saving changes the entry, and the box's end date
   * too if the entry is what set it (see
   * {@link RentalHistoryRepository#update}).
   *
   * @param owner the main window, which can't be used until this is closed
   * @param mailbox the box the entry belongs to
   * @param entry the entry to change
   * @param onSaved called after the change is saved
   */
  static void edit(Stage owner, Mailbox mailbox, RentalPeriod entry, Runnable onSaved) {
    open(owner, mailbox, entry, onSaved);
  }

  /**
   * Builds and opens the window, for a renewal or to change an entry.
   *
   * @param owner the main window, which can't be used until this is closed
   * @param mailbox the box being renewed, or that the entry belongs to
   * @param entry the entry to change, or {@code null} to renew the box
   * @param onSaved called after the renewal or change is saved
   */
  private static void open(Stage owner, Mailbox mailbox, RentalPeriod entry, Runnable onSaved) {
    var today = LocalDate.now();
    var current = mailbox.getEndDate();
    var editing = entry != null;

    var title = new Label((editing ? "Edit Payment for " : "Renew ") + BoxLabels.boxAndHolder(mailbox));
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    String explanation;
    if (editing) {
      explanation = "If the box's rental ends on this entry's end date, changing the end date here "
          + "changes when the box's rental ends too.";
    } else if (current == null) {
      explanation = "This box has no end date set.";
    } else {
      explanation = "The rental ends " + current.format(DATE) + " ("
          + RenewalsView.dueStatus(current, today).toLowerCase() + ").";
    }
    var currentLabel = new Label(explanation);
    currentLabel.setId("renewCurrentLabel");
    currentLabel.setWrapText(true);

    // The day the payment was taken, which decides where it's counted on
    // Payments. A renewal is always recorded today.
    var recordedField = new DatePicker(editing ? entry.getRecordedOn() : today);
    recordedField.setId("renewRecordedField");
    recordedField.setStyle("-fx-pref-width: 12em;");

    // Renewing continues from the current end date, so no days are lost or
    // given away; the start can be changed, e.g. for a box that lapsed long ago.
    LocalDate firstDay;
    if (editing) {
      firstDay = entry.getStartDate();
    } else {
      firstDay = current == null ? today : current;
    }
    var startField = new DatePicker(firstDay);
    startField.setId("renewStartField");
    startField.setStyle("-fx-pref-width: 12em;");

    var endField = new DatePicker(editing ? entry.getEndDate() : null);
    endField.setId("renewEndField");
    endField.setStyle("-fx-pref-width: 12em;");

    var payment = new PaymentFields();
    if (editing && entry.getAmountCents() != null) {
      payment.amountField.setText(Money.format(entry.getAmountCents()));
    }
    if (editing && entry.getPaymentMethod() != null) {
      payment.methodField.getEditor().setText(entry.getPaymentMethod());
    }

    var lengthButtons = RentalLengthButtons.create(months -> {
      var start = startField.getValue() == null ? today : startField.getValue();
      endField.setValue(start.plusMonths(months));
      // Box sizes' prices don't apply to forwarding.
      if (!mailbox.isForwardingOnly()) {
        payment.suggest(PricesView.priceFor(mailbox.getBoxNumber(), months));
      }
    });

    var noteField = new TextField();
    noteField.setId("renewNoteField");
    noteField.setPromptText("e.g. check #1042 (optional)");
    noteField.setStyle("-fx-pref-width: 12em;");
    if (editing && entry.getNote() != null) {
      noteField.setText(entry.getNote());
    }

    var errorLabel = new Label();
    errorLabel.setId("renewErrorLabel");
    errorLabel.setStyle("-fx-text-fill: red;");
    errorLabel.setWrapText(true);

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    var row = 0;
    if (editing) {
      grid.addRow(row++, new Label("Paid on:"), recordedField);
    }
    grid.addRow(row++, new Label(editing ? "Start date:" : "Renew from:"), startField);
    grid.add(lengthButtons, 0, row++, 2, 1);
    grid.addRow(row++, new Label(editing ? "End date:" : "New end date:"), endField);
    grid.addRow(row++, new Label("Amount paid:"), payment.amountField);
    grid.addRow(row++, new Label("Paid by:"), payment.methodField);
    grid.addRow(row, new Label("Note:"), noteField);

    var stage = new Stage();

    var renewBtn = new Button(editing ? "Save" : "Renew");
    renewBtn.setId("renewSaveButton");
    renewBtn.setDefaultButton(true);
    renewBtn.setOnAction(e -> {
      var errors = new StringBuilder();
      var recordedOn = recordedField.getValue();
      if (recordedOn == null) {
        errors.append("Choose the day it was paid.\n");
      }
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
        var period = new RentalPeriod(editing ? entry.getId() : 0, mailbox.getId(), recordedOn, start, end,
            amount, payment.method(), noteField.getText());
        if (editing) {
          new RentalHistoryRepository().update(period);
        } else {
          new RentalHistoryRepository().renew(mailbox.getId(), period);
        }
        stage.close();
        onSaved.run();
      } catch (SQLException ex) {
        errorLabel.setText((editing ? "Failed to save the changes: " : "Failed to save the renewal: ")
            + ex.getMessage());
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
    stage.setTitle((editing ? "Edit Payment, Box " : "Renew Box ") + mailbox.getBoxNumber());
    stage.setScene(new Scene(scrollPane));
    AppWindow.showWithinScreen(stage);
  }

  /** Not used: the window is built with static methods. */
  private RenewBoxView() {
  }

}
