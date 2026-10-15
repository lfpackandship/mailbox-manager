package org.lfps.mailboxes.view;

import java.sql.SQLException;

import javafx.stage.Window;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.InventoryBox;

/**
 * Checks a box number entered on the Add and Edit Box forms: that it's free
 * and in the box inventory, and whether it's out of service.
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

  /**
   * If a box is marked out of service on Box Inventory, such as with a
   * broken lock, asks whether to use it anyway. It can still be used, since
   * the shop may have fixed it without updating the inventory.
   *
   * @param owner the window the question belongs to
   * @param boxNumber the box number entered; must not be blank
   * @return {@code true} to go ahead: the box is in service, isn't in the
   *     inventory, can't be checked, or the user chose to use it anyway
   */
  static boolean confirmIfOutOfService(Window owner, String boxNumber) {
    InventoryBox box;
    try {
      box = new BoxInventoryRepository().find(boxNumber);
    } catch (SQLException e) {
      // Only a warning, so it isn't worth stopping the save over.
      return true;
    }
    if (box == null || !box.isOutOfService()) {
      return true;
    }
    return Dialogs.confirm.ask(owner, "Box " + box.getBoxNumber() + " is " + box.describeOutOfService() + ".",
        "Use it anyway? If it's been fixed, put it back in service on the Box Inventory screen.",
        "Use It Anyway", "Go Back");
  }

  /** Not used: box numbers are checked with static methods. */
  private BoxNumberChecks() {
  }

}
