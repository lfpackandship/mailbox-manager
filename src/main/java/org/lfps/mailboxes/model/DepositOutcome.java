package org.lfps.mailboxes.model;

/**
 * What happened to a closed box's key deposit: given back to the holder, or
 * kept by the shop, for example because the keys weren't returned. It's
 * chosen when the box is closed on Manage Boxes, or later on Edit Box, and
 * saved by name in the {@code key_deposit_outcome} column. A box with no
 * outcome recorded has {@code null} instead.
 */
public enum DepositOutcome {

  /** The deposit was given back to the holder. */
  RETURNED("Given back"),

  /** The shop kept the deposit, for example because keys weren't returned. */
  KEPT("Kept");

  /** What the outcome is called on screen, such as "Given back". */
  private final String label;

  /**
   * Makes an outcome.
   *
   * @param label what it's called on screen
   */
  DepositOutcome(String label) {
    this.label = label;
  }

  /**
   * Returns what the outcome is called on screen.
   *
   * @return the label, such as "Given back"
   */
  public String getLabel() {
    return label;
  }

  /**
   * Reads an outcome saved by name.
   *
   * @param name the saved name, such as "KEPT", or {@code null}
   * @return the outcome, or {@code null} if none is saved or the name isn't
   *     one this version knows
   */
  public static DepositOutcome fromName(String name) {
    for (var outcome : values()) {
      if (outcome.name().equals(name)) {
        return outcome;
      }
    }
    return null;
  }

}
