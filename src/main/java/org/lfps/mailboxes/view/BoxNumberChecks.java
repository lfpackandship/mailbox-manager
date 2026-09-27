package org.lfps.mailboxes.view;

import java.sql.SQLException;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.MailboxRepository;

/**
 * Checks a box number entered on the Add and Edit Box forms.
 */
final class BoxNumberChecks {

  /**
   * Checks that a box number isn't already rented to someone else and, if
   * the box inventory has been set up, that the box is in it.
   *
   * @param boxNumber the box number entered; must not be blank
   * @param excludeId the id of the box being edited, or {@code 0} when adding
   * @return a message describing the problem, ending in a newline, or an
   *     empty string if there is none
   */
  static String problem(String boxNumber, int excludeId) {
    var number = boxNumber.trim();
    try {
      if (new MailboxRepository().isBoxNumberTaken(number, excludeId)) {
        return "Box " + number + " is already assigned to someone else.\n";
      }
      var inventory = new BoxInventoryRepository();
      if (!inventory.isEmpty() && !inventory.contains(number)) {
        return "Box " + number + " isn't in the box inventory. Check the number, or add the box on the "
            + "Box Inventory screen.\n";
      }
      return "";
    } catch (SQLException e) {
      return "Could not check box number: " + e.getMessage() + "\n";
    }
  }

  private BoxNumberChecks() {
  }

}
