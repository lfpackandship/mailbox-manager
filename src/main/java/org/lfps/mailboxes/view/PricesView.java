package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * A window for setting the price of each box size for each rental length, as
 * a grid with a row per size, cheapest first, and a column per rental
 * length. Choosing a
 * rental length on Add New Box or when renewing fills in the price. What
 * each size measures is also entered here, for the printed price sheet.
 */
final class PricesView {

  /** The open prices window, or {@code null} if none is open. */
  private static Stage window;

  /**
   * Opens the prices window, or brings it to the front if it's already open.
   *
   * @param owner the window it belongs to
   */
  static void show(Stage owner) {
    if (window != null) {
      window.toFront();
      window.requestFocus();
      return;
    }

    var prices = new PriceRepository();
    var resultLabel = new Label();
    resultLabel.setId("pricesResultLabel");
    resultLabel.setWrapText(true);

    List<String> sizes = List.of();
    List<Integer> lengths = RentalLengths.parse(Setting.RENTAL_LENGTHS.defaultValue());
    Map<String, Long> saved = Map.of();
    Map<String, String> savedDescriptions = Map.of();
    try {
      try {
        lengths = RentalLengths.parse(new SettingsRepository().get(Setting.RENTAL_LENGTHS));
      } catch (IllegalArgumentException badSetting) {
        // Use the default lengths.
      }
      saved = prices.findAll();
      savedDescriptions = prices.findDescriptions();
      sizes = PriceRepository.cheapestFirst(new BoxInventoryRepository().sizes(), saved);
    } catch (SQLException e) {
      showError(resultLabel, "Failed to load prices: " + e.getMessage());
    }

    var priceGrid = new PriceGrid(sizes, lengths, saved, "price");
    var grid = priceGrid.grid;
    var measuresHeader = new Label("Measures");
    measuresHeader.setStyle("-fx-font-weight: bold;");
    var measuresCol = lengths.size() + 1;
    if (!sizes.isEmpty()) {
      grid.add(measuresHeader, measuresCol, 0);
    }

    // What each size measures, for the price sheet, keyed by size.
    var descriptionFields = new LinkedHashMap<String, TextField>();
    for (var row = 0; row < sizes.size(); row++) {
      var size = sizes.get(row);
      var description = new TextField(savedDescriptions.getOrDefault(size.toLowerCase(), ""));
      description.setId("measures-" + size.toLowerCase());
      description.setPromptText("e.g. 3¾\" x 5\" x 14\"");
      description.setStyle("-fx-pref-width: 11em;");
      descriptionFields.put(size, description);
      grid.add(description, measuresCol, row + 1);
    }

    var explanation = new Label((sizes.isEmpty()
        ? "No sizes are recorded in the box inventory, so there's only a default price for each rental length. "
            + "Record sizes on the Box Inventory screen to price them separately. "
        : "")
        + "The default price is used for boxes with no size, or whose size has no price for that length. "
        + "Leave a price blank if there isn't one. Choosing a rental length on Add New Box or when renewing "
        + "fills in the price, which can still be changed. What each size measures is shown on the "
        + "printed price sheet.");
    explanation.setWrapText(true);
    explanation.setStyle("-fx-max-width: 36em;");

    var stage = new Stage();

    var saveBtn = new Button("Save");
    saveBtn.setId("pricesSaveButton");
    saveBtn.setDefaultButton(true);
    saveBtn.setOnAction(e -> {
      Map<String, Long> toSave;
      try {
        toSave = priceGrid.read();
      } catch (IllegalArgumentException ex) {
        showError(resultLabel, ex.getMessage());
        return;
      }
      var descriptions = new HashMap<String, String>();
      descriptionFields.forEach((size, field) -> descriptions.put(size, field.getText()));
      try {
        prices.save(toSave);
        prices.saveDescriptions(descriptions);
      } catch (SQLException ex) {
        showError(resultLabel, "Failed to save: " + ex.getMessage());
        return;
      }
      priceGrid.show(toSave);
      resultLabel.setStyle("-fx-text-fill: green;");
      resultLabel.setText("Saved");
    });

    var printBtn = new Button("Print Price Sheet…");
    printBtn.setId("printPriceSheetButton");
    printBtn.setOnAction(e -> PriceSheetView.showPriceSheet(owner));

    var closeBtn = new Button("Close");
    closeBtn.setId("pricesCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var title = new Label("Prices");
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    // A price change scheduled for a later date, set on its own window.
    var changeLabel = new Label();
    changeLabel.setId("priceChangeLabel");
    changeLabel.setWrapText(true);
    Runnable showChange = () -> changeLabel.setText(describeChange());
    showChange.run();
    var changeBtn = new Button("Price Change…");
    changeBtn.setId("priceChangeButton");
    changeBtn.setOnAction(e -> PriceChangeView.show(stage, owner, showChange));
    var changeHeader = new Label("Price Change");
    changeHeader.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");
    var changeBox = new VBox(8, changeHeader, changeLabel, changeBtn);

    var content = new VBox(12, title, explanation, grid, new HBox(10, saveBtn, printBtn, closeBtn), resultLabel,
        new Separator(), changeBox);
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    AppWindow.applyTextSize(scrollPane);

    stage.initOwner(owner);
    stage.setTitle("Prices");
    stage.setScene(new Scene(scrollPane));
    stage.setOnHidden(e -> window = null);
    window = stage;
    AppWindow.showWithinScreen(stage);
  }

  /**
   * Returns the price of renting a box for a number of months, based on its
   * size in the box inventory.
   *
   * @param boxNumber the box number
   * @param months the rental length
   * @return the price in cents, or {@code null} if there's no price for it
   *     or the prices can't be read
   */
  static Long priceFor(String boxNumber, int months) {
    try {
      var size = boxNumber == null || boxNumber.isBlank() ? null : new BoxInventoryRepository().sizeOf(boxNumber);
      return new PriceRepository().priceFor(size, months);
    } catch (SQLException e) {
      return null;
    }
  }

  /**
   * Says whether a price change is scheduled, and when it starts.
   *
   * @return the description
   */
  private static String describeChange() {
    try {
      var change = new PriceRepository().findChange();
      return change == null
          ? "To raise prices from a later date, click Price Change…. You can also print notices for box holders."
          : "New prices start on " + change.startsOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
              + ". Until then, adding or renewing a box uses the prices above. To see or change the new prices, "
              + "or print notices, click Price Change….";
    } catch (SQLException e) {
      return "Couldn't check for a price change: " + e.getMessage();
    }
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
  private PricesView() {
  }

}
