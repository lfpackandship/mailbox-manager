package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * The row of quick-set rental length buttons ("1 Month", "3 Months", ...)
 * on the Add and Edit Box forms, built from the
 * {@link Setting#RENTAL_LENGTHS} setting.
 */
final class RentalLengthButtons {

  /**
   * Builds one button per configured rental length.
   *
   * @param onChoose called with the button's length in months when clicked
   * @return the row of buttons
   */
  static HBox create(IntConsumer onChoose) {
    var row = new HBox(8);
    for (var months : configuredLengths()) {
      var button = new Button(RentalLengths.label(months));
      button.setOnAction(e -> onChoose.accept(months));
      row.getChildren().add(button);
    }
    return row;
  }

  private static List<Integer> configuredLengths() {
    try {
      return RentalLengths.parse(new SettingsRepository().get(Setting.RENTAL_LENGTHS));
    } catch (SQLException | IllegalArgumentException e) {
      return RentalLengths.parse(Setting.RENTAL_LENGTHS.defaultValue());
    }
  }

  private RentalLengthButtons() {
  }

}
