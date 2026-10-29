package org.lfps.mailboxes.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.util.Money;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * A grid of price fields, with a column for each box size and the default
 * price, and a row for each rental length, laid out like the printed price
 * sheet. Used on the Prices window for today's prices and on the Price Change
 * window for the new ones.
 */
final class PriceGrid {

  /**
   * The grid, to add to a window. Rows between the size headings and the
   * prices are left free for other fields, such as what each size measures.
   */
  final GridPane grid = new GridPane();

  /** Each price field, keyed like the prices in {@link PriceRepository}. */
  private final Map<String, TextField> fields = new LinkedHashMap<>();

  /**
   * Builds the grid.
   *
   * @param sizes the sizes, one column each, followed by a column for the default price
   * @param lengths the rental lengths, one row each
   * @param prices the prices to fill in, keyed by {@link PriceRepository#key(String, int)}
   * @param idPrefix starts each field's id, which is followed by the size and length
   * @param freeRows how many rows to leave empty under the size headings, for
   *     other fields; 0 puts the prices straight under the headings
   */
  PriceGrid(List<String> sizes, List<Integer> lengths, Map<String, Long> prices, String idPrefix, int freeRows) {
    grid.setHgap(10);
    grid.setVgap(8);
    var columns = new ArrayList<String>(sizes);
    columns.add(PriceRepository.DEFAULT_SIZE);
    for (var col = 0; col < columns.size(); col++) {
      var size = columns.get(col);
      var header = new Label(size.isEmpty() ? "Default" : size);
      header.setStyle(size.isEmpty() ? "-fx-font-weight: bold; -fx-font-style: italic;" : "-fx-font-weight: bold;");
      grid.add(header, col + 1, 0);
    }

    var firstPriceRow = 1 + freeRows;
    for (var row = 0; row < lengths.size(); row++) {
      var length = lengths.get(row);
      grid.add(new Label(RentalLengths.label(length)), 0, firstPriceRow + row);
      for (var col = 0; col < columns.size(); col++) {
        var size = columns.get(col);
        var key = PriceRepository.key(size, length);
        var field = new TextField();
        field.setId(idPrefix + "-" + (size.isEmpty() ? "default" : size.toLowerCase()) + "-" + length);
        field.setStyle("-fx-pref-width: 7em;");
        fields.put(key, field);
        grid.add(field, col + 1, firstPriceRow + row);
      }
    }
    show(prices);
  }

  /**
   * Reads the prices entered.
   *
   * @return the price in cents for each key, or {@code null} where it's blank
   * @throws IllegalArgumentException if one isn't a price, with a message
   *     suitable for showing to the user; the cursor is put in that field
   */
  Map<String, Long> read() {
    var prices = new HashMap<String, Long>();
    for (var entry : fields.entrySet()) {
      try {
        prices.put(entry.getKey(), Money.parse(entry.getValue().getText()));
      } catch (IllegalArgumentException ex) {
        entry.getValue().requestFocus();
        throw new IllegalArgumentException("\"" + entry.getValue().getText().trim() + "\" isn't a price. Enter "
            + "prices in dollars and cents, like 60 or 60.00.", ex);
      }
    }
    return prices;
  }

  /**
   * Fills in the fields, for example to tidy up what was typed after saving.
   *
   * @param prices the prices, keyed by {@link PriceRepository#key(String, int)};
   *     fields with no price are left blank
   */
  void show(Map<String, Long> prices) {
    for (var entry : fields.entrySet()) {
      var price = prices.get(entry.getKey());
      entry.getValue().setText(price == null ? "" : Money.format(price));
    }
  }

}
