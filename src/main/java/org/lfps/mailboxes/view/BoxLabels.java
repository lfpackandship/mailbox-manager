package org.lfps.mailboxes.view;

import java.time.LocalDate;
import java.util.ArrayList;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import org.lfps.mailboxes.model.Mailbox;

/**
 * Describes a box and its holder in titles, tooltips, and questions, leaving
 * out the holder when the box has no name or business title.
 */
final class BoxLabels {

  /** Not used: labels are made with static methods. */
  private BoxLabels() {
  }

  /**
   * Returns the box number and its holder.
   *
   * @param mailbox the box
   * @return such as "Box 12 – Ada Lovelace", or "Box 12" if it has no holder
   *     name
   */
  static String boxAndHolder(Mailbox mailbox) {
    var holder = mailbox.getHolderName();
    return "Box " + mailbox.getBoxNumber() + (holder.isEmpty() ? "" : " – " + holder);
  }

  /**
   * Returns the words to follow a box number in a question about the box.
   *
   * @param mailbox the box
   * @return such as " for Ada Lovelace", or an empty string if it has no
   *     holder name
   */
  static String forHolder(Mailbox mailbox) {
    var holder = mailbox.getHolderName();
    return holder.isEmpty() ? "" : " for " + holder;
  }

  /**
   * Makes a table's box number column say which boxes are forwarding only
   * and whose mail is being held today, such as "12 (forwarding)" or "12
   * (holding)"; see {@link #boxNumber}. Sorting still goes by the number.
   *
   * @param column the box number column
   */
  static void markBoxNumbers(TableColumn<Mailbox, String> column) {
    column.setCellFactory(c -> new TableCell<>() {
      @Override
      protected void updateItem(String number, boolean empty) {
        super.updateItem(number, empty);
        var mailbox = getTableRow() == null ? null : getTableRow().getItem();
        setText(empty || number == null ? null : mailbox == null ? number : boxNumber(mailbox, LocalDate.now()));
      }
    });
  }

  /**
   * Returns a box's number as lists show it: marked "(forwarding)" if it's
   * forwarding only, so it isn't mistaken for the box rented under the same
   * number, and "(holding)" if its mail is being held, such as "12
   * (forwarding, holding)".
   *
   * @param mailbox the box
   * @param today today, to tell whether a hold is on
   * @return the box number, marked
   */
  static String boxNumber(Mailbox mailbox, LocalDate today) {
    var marks = new ArrayList<String>();
    if (mailbox.isForwardingOnly()) {
      marks.add("forwarding");
    }
    if (mailbox.isHoldingMailOn(today)) {
      marks.add("holding");
    }
    return mailbox.getBoxNumber() + (marks.isEmpty() ? "" : " (" + String.join(", ", marks) + ")");
  }

  /**
   * Returns the holder's name, or a stand-in if the box has none.
   *
   * @param mailbox the box
   * @param fallback what to use if the box has no holder name
   * @return the holder's name or the fallback
   */
  static String holderOr(Mailbox mailbox, String fallback) {
    var holder = mailbox.getHolderName();
    return holder.isEmpty() ? fallback : holder;
  }

}
