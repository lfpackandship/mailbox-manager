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
   * Creates an inventory entry. A blank size is stored as {@code null}.
   *
   * @param boxNumber the box number, such as "12" or "12A"
   * @param size the size, such as "Large", or blank/null if not recorded
   */
  public InventoryBox(String boxNumber, String size) {
    this.boxNumber = boxNumber;
    this.size = size == null || size.isBlank() ? null : size.trim();
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

}
