package org.lfps.mailboxes.view;

import org.lfps.mailboxes.model.Mailbox;

/**
 * Describes a box and its holder in titles, tooltips, and questions, leaving
 * out the holder when the box has no name or business title.
 */
final class BoxLabels {

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
