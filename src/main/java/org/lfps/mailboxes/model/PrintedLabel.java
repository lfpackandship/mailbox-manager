package org.lfps.mailboxes.model;

import java.time.LocalDateTime;

/**
 * A forwarding label that was printed, kept for the shop's records: its
 * number, which is printed small on the label, the box it was for, when it
 * was printed, and who and where it was addressed to at the time.
 */
public final class PrintedLabel {

  /** The label's number, such as "261007-03": the day it was printed, then that day's count. */
  private final String number;

  /** The id of the box it was printed for. */
  private final int mailboxId;

  /** When it was printed, to the second, on this computer's clock. */
  private final LocalDateTime printedAt;

  /**
   * Who and where it was addressed to, one line each, as printed, so the
   * record stays right if the box's forwarding address changes later.
   */
  private final String address;

  /**
   * Makes a record of a printed label.
   *
   * @param number the label's number
   * @param mailboxId the id of the box it was printed for
   * @param printedAt when it was printed
   * @param address who and where it was addressed to, one line each
   */
  public PrintedLabel(String number, int mailboxId, LocalDateTime printedAt, String address) {
    this.number = number;
    this.mailboxId = mailboxId;
    this.printedAt = printedAt;
    this.address = address;
  }

  /**
   * Returns the label's number.
   *
   * @return the number, such as "261007-03"
   */
  public String getNumber() {
    return number;
  }

  /**
   * Returns the id of the box the label was printed for.
   *
   * @return the box's id
   */
  public int getMailboxId() {
    return mailboxId;
  }

  /**
   * Returns when the label was printed.
   *
   * @return the date and time
   */
  public LocalDateTime getPrintedAt() {
    return printedAt;
  }

  /**
   * Returns who and where the label was addressed to.
   *
   * @return the lines as printed, separated by line breaks
   */
  public String getAddress() {
    return address;
  }

}
