package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * A window for scheduling a price change: the day new prices start and what
 * they'll be. Until that day, adding or renewing a box uses today's prices,
 * so box holders can renew early at them; on that day the new prices replace
 * today's. From here, notices of the change can be printed for box holders.
 */
final class PriceChangeView {

  /**
   * Opens the price change window over the Prices window.
   *
   * @param pricesWindow the Prices window
   * @param mainWindow the main window, which the notices window belongs to
   * @param onChanged run when the change is saved or cancelled
   */
  static void show(Stage pricesWindow, Stage mainWindow, Runnable onChanged) {
    var stage = new Stage();
    var prices = new PriceRepository();
    var settings = new SettingsRepository();

    var resultLabel = new Label();
    resultLabel.setId("priceChangeResultLabel");
    resultLabel.setWrapText(true);

    List<String> sizes = List.of();
    List<Integer> lengths = RentalLengths.parse(Setting.RENTAL_LENGTHS.defaultValue());
    Map<String, Long> current = Map.of();
    PriceRepository.PriceChange change = null;
    var message = "";
    try {
      try {
        lengths = RentalLengths.parse(settings.get(Setting.RENTAL_LENGTHS));
      } catch (IllegalArgumentException badSetting) {
        // Use the default lengths.
      }
      current = prices.findAll();
      change = prices.findChange();
      sizes = PriceRepository.cheapestFirst(new BoxInventoryRepository().sizes(), current);
      message = settings.get(Setting.PRICE_CHANGE_MESSAGE);
    } catch (SQLException e) {
      showError(resultLabel, "Failed to load prices: " + e.getMessage());
    }
    var today = current;

    var explanation = new Label("Choose the day the new prices start, and enter the new prices. They start "
        + "out as today's prices, so change just the ones that are going up. Until that day, adding or "
        + "renewing a box still uses today's prices, so box holders can renew early to keep them. On that "
        + "day, the new prices replace today's on their own.");
    explanation.setWrapText(true);
    explanation.setStyle("-fx-max-width: 36em;");

    var dateField = new DatePicker(change == null ? null : change.startsOn);
    dateField.setId("priceChangeDate");
    dateField.setStyle("-fx-pref-width: 12em;");

    var grid = new PriceGrid(sizes, lengths, change == null ? today : change.applyTo(today), "newPrice");

    var messageField = new TextArea(message);
    messageField.setId("priceChangeMessageField");
    messageField.setPrefRowCount(3);
    messageField.setWrapText(true);
    messageField.setStyle("-fx-pref-width: 30em;");

    var printBtn = new Button("Print Notices…");
    printBtn.setId("printNoticesButton");
    var cancelChangeBtn = new Button("Cancel Price Change");
    cancelChangeBtn.setId("cancelPriceChangeButton");
    cancelChangeBtn.setDisable(change == null);

    var saveBtn = new Button("Save");
    saveBtn.setId("priceChangeSaveButton");
    saveBtn.setDefaultButton(true);
    // Saves what's entered, returning whether it worked.
    BooleanSupplier save = () -> {
      var startsOn = dateField.getValue();
      if (startsOn == null || !startsOn.isAfter(LocalDate.now())) {
        dateField.requestFocus();
        showError(resultLabel, "Choose a day after today for the new prices to start. To change prices "
            + "starting today, use the Prices window instead.");
        return false;
      }
      Map<String, Long> entered;
      try {
        entered = grid.read();
      } catch (IllegalArgumentException ex) {
        showError(resultLabel, ex.getMessage());
        return false;
      }
      // Only the prices that differ from today's make up the change.
      var changed = new HashMap<String, Long>();
      for (var entry : entered.entrySet()) {
        if (!Objects.equals(entry.getValue(), today.get(entry.getKey()))) {
          changed.put(entry.getKey(), entry.getValue());
        }
      }
      if (changed.isEmpty()) {
        showError(resultLabel, "The new prices are the same as today's. Change the ones that are going up.");
        return false;
      }
      try {
        prices.scheduleChange(startsOn, changed);
        settings.put(Setting.PRICE_CHANGE_MESSAGE, messageField.getText().strip());
      } catch (SQLException ex) {
        showError(resultLabel, "Failed to save: " + ex.getMessage());
        return false;
      }
      grid.show(entered);
      cancelChangeBtn.setDisable(false);
      onChanged.run();
      resultLabel.setStyle("-fx-text-fill: green;");
      resultLabel.setText("Saved. New prices start on "
          + startsOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + ".");
      return true;
    };
    saveBtn.setOnAction(e -> save.getAsBoolean());

    // Printing saves first, so the notices match what's on screen.
    printBtn.setOnAction(e -> {
      if (!save.getAsBoolean()) {
        return;
      }
      try {
        var boxes = rentedBoxes();
        if (boxes.isEmpty()) {
          AppWindow.inform(stage, AlertType.INFORMATION, "No notices to print",
              "No boxes are rented at the moment, so there's no one to give a notice to.");
          return;
        }
        PriceSheetView.showNotices(mainWindow, boxes, prices.findChange());
      } catch (SQLException ex) {
        showError(resultLabel, "Couldn't load the boxes: " + ex.getMessage());
      }
    });

    cancelChangeBtn.setOnAction(e -> {
      if (!Dialogs.confirm.ask(stage, "Cancel the price change?",
          "The new prices won't start, and today's prices stay as they are. If notices were given out, "
              + "let box holders know.")) {
        return;
      }
      try {
        prices.cancelChange();
      } catch (SQLException ex) {
        showError(resultLabel, "Failed to cancel: " + ex.getMessage());
        return;
      }
      dateField.setValue(null);
      grid.show(today);
      cancelChangeBtn.setDisable(true);
      onChanged.run();
      resultLabel.setStyle("-fx-text-fill: green;");
      resultLabel.setText("The price change is cancelled.");
    });

    var closeBtn = new Button("Close");
    closeBtn.setId("priceChangeCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var title = new Label("Price Change");
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    var noticeExplanation = new Label("Print Notices… saves, then prints a notice for each rented box with the "
        + "day the new prices start, today's prices, and the new ones. The message below is printed on each.");
    noticeExplanation.setWrapText(true);
    noticeExplanation.setStyle("-fx-max-width: 36em;");

    var content = new VBox(12, title, explanation,
        new HBox(10, new Label("New prices start on:"), dateField),
        grid.grid,
        noticeExplanation,
        new Label("Message on the notices:"), messageField,
        new HBox(10, saveBtn, printBtn, cancelChangeBtn, closeBtn), resultLabel);
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    AppWindow.applyTextSize(scrollPane);

    stage.initOwner(pricesWindow);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle("Price Change");
    stage.setScene(new Scene(scrollPane));
    AppWindow.showWithinScreen(stage);
  }

  /**
   * Returns the open boxes rented here, in box number order: forwarding-only
   * boxes don't pay box prices.
   *
   * @return the boxes
   * @throws SQLException if they can't be read
   */
  private static List<Mailbox> rentedBoxes() throws SQLException {
    return new MailboxRepository().findOpen().stream()
        .filter(box -> !box.isForwardingOnly())
        .collect(Collectors.toList());
  }

  /**
   * Shows a problem in red.
   *
   * @param label where to show it
   * @param message the problem
   */
  private static void showError(Label label, String message) {
    label.setStyle("-fx-text-fill: red;");
    label.setText(message);
  }

  /** Not used: the window is built with static methods. */
  private PriceChangeView() {
  }

}
