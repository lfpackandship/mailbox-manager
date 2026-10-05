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
 * A grid of price fields, with a row for each box size and the default
 * price, and a column for each rental length. Used on the Prices window for
 * today's prices and on the Price Change window for the new ones.
 */
final class PriceGrid {

  /** The grid, to add to a window. Columns after the prices are free for other fields. */
  final GridPane grid = new GridPane();

  /** Each price field, keyed like the prices in {@link PriceRepository}. */
  private final Map<String, TextField> fields = new LinkedHashMap<>();

  /**
   * Builds the grid.
   *
   * @param sizes the sizes, one row each, followed by a row for the default price
   * @param lengths the rental lengths, one column each
   * @param prices the prices to fill in, keyed by {@link PriceRepository#key(String, int)}
   * @param idPrefix starts each field's id, which is followed by the size and length
   */
  PriceGrid(List<String> sizes, List<Integer> lengths, Map<String, Long> prices, String idPrefix) {
    grid.setHgap(10);
    grid.setVgap(8);
    for (var col = 0; col < lengths.size(); col++) {
      var header = new Label(RentalLengths.label(lengths.get(col)));
      header.setStyle("-fx-font-weight: bold;");
      grid.add(header, col + 1, 0);
    }

    var rows = new ArrayList<String>(sizes);
    rows.add(PriceRepository.DEFAULT_SIZE);
    for (var row = 0; row < rows.size(); row++) {
      var size = rows.get(row);
      var label = new Label(size.isEmpty() ? "Default:" : size + ":");
      if (size.isEmpty()) {
        label.setStyle("-fx-font-style: italic;");
      }
      grid.add(label, 0, row + 1);
      for (var col = 0; col < lengths.size(); col++) {
        var key = PriceRepository.key(size, lengths.get(col));
        var field = new TextField();
        field.setId(idPrefix + "-" + (size.isEmpty() ? "default" : size.toLowerCase()) + "-" + lengths.get(col));
        field.setStyle("-fx-pref-width: 7em;");
        fields.put(key, field);
        grid.add(field, col + 1, row + 1);
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
