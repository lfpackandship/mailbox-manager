package org.lfps.mailboxes.model;

/**
 * An immutable entry in the box inventory: a physical box that can be rented,
 * whether or not anyone has it now.
 */
public final class InventoryBox {

  /** The box number. */
  private final String boxNumber;

  /**
   * The box's size, such as Small, or {@code null} if not recorded.
   */
  private final String size;

  /**
   * Whether the box is out of service, such as with a broken lock, so it
   * isn't offered as empty. A rented box can be out of service too.
   */
  private final boolean outOfService;

  /**
   * Why the box is out of service, such as "broken lock", or {@code null}
   * if it isn't or no reason was given.
   */
  private final String outOfServiceReason;

  /**
   * Creates an inventory entry for a box in service. A blank size is stored
   * as {@code null}.
   *
   * @param boxNumber the box number, such as "12" or "12A"
   * @param size the size, such as "Large", or blank/null if not recorded
   */
  public InventoryBox(String boxNumber, String size) {
    this(boxNumber, size, false, null);
  }

  /**
   * Creates an inventory entry. A blank size or reason is stored as
   * {@code null}.
   *
   * @param boxNumber the box number, such as "12" or "12A"
   * @param size the size, such as "Large", or blank/null if not recorded
   * @param outOfService whether the box is out of service
   * @param outOfServiceReason why, such as "broken lock", or blank/null if
   *     no reason was given; ignored for a box in service
   */
  public InventoryBox(String boxNumber, String size, boolean outOfService, String outOfServiceReason) {
    this.boxNumber = boxNumber;
    this.size = size == null || size.isBlank() ? null : size.trim();
    this.outOfService = outOfService;
    this.outOfServiceReason = !outOfService || outOfServiceReason == null || outOfServiceReason.isBlank()
        ? null : outOfServiceReason.trim();
  }

  /**
   * Returns the box number.
   *
   * @return the box number
   */
  public String getBoxNumber() {
    return boxNumber;
  }

  /**
   * Returns the box's size.
   *
   * @return the size, or {@code null} if not recorded
   */
  public String getSize() {
    return size;
  }

  /**
   * Returns whether the box is out of service, such as with a broken lock.
   *
   * @return {@code true} if it's out of service
   */
  public boolean isOutOfService() {
    return outOfService;
  }

  /**
   * Returns why the box is out of service.
   *
   * @return the reason, such as "broken lock", or {@code null} if it's in
   *     service or no reason was given
   */
  public String getOutOfServiceReason() {
    return outOfServiceReason;
  }

  /**
   * Describes the box's state for the Box Inventory screen and warnings,
   * such as "out of service: broken lock".
   *
   * @return "out of service", with the reason if there is one, or an empty
   *     string if the box is in service
   */
  public String describeOutOfService() {
    if (!outOfService) {
      return "";
    }
    return "out of service" + (outOfServiceReason == null ? "" : ": " + outOfServiceReason);
  }

}
