package org.lfps.mailboxes.data;

/**
 * A user-adjustable setting, identified by the key it is stored under in the
 * {@code settings} table and the value used until the user changes it.
 * Add new settings here.
 */
public enum Setting {

  /** How many days ahead the Renewals screen looks for upcoming end dates. */
  RENEWAL_WINDOW_DAYS("renewal_window_days", "30"),

  /** How many daily backups to keep before the oldest are deleted. */
  BACKUPS_TO_KEEP("backups_to_keep", "30"),

  /**
   * A folder, such as a USB drive or a synced cloud folder, that each daily
   * backup is also copied to. Empty means none.
   */
  SECOND_BACKUP_FOLDER("second_backup_folder", ""),

  /**
   * The rental lengths offered as quick-set buttons on the Add and Edit Box
   * forms, in months, separated by commas.
   */
  RENTAL_LENGTHS("rental_lengths", "1,3,6,12"),

  /** The first day of the week on the Calendar screen: SUNDAY or MONDAY. */
  WEEK_START("week_start", "SUNDAY"),

  /** How large text is throughout the app: NORMAL, LARGE, or EXTRA_LARGE. */
  TEXT_SIZE("text_size", "NORMAL"),

  /** The refundable deposit taken for each key, such as "$10.00", or empty for none. */
  KEY_DEPOSIT("key_deposit", "$10.00"),

  /** The shop's name, at the top of the price sheet and renewal reminders. */
  SHOP_NAME("shop_name", "Lake Forest Pack and Ship"),

  /** The shop's address and phone number, under its name, one item per line. */
  SHOP_DETAILS("shop_details", "736 N. Western Ave\nLake Forest, IL 60045\n(847) 615-0222"),

  /**
   * The text above the prices on the price sheet. Lines starting with "-"
   * are shown as bullet points.
   */
  PRICE_SHEET_INTRO("price_sheet_intro", "Mailbox Service Includes:\n"
      + "- 24-Hour Access to Mailbox\n"
      + "- Telephone Mail Check\n"
      + "- Parcel and Overnight Mail Receiving\n"
      + "- Mail Forwarding *\n"
      + "\n"
      + "* requires funding for postage and envelopes"),

  /** The text below the prices on the price sheet. */
  PRICE_SHEET_NOTE("price_sheet_note", "Plus $10.00 Refundable Key Deposit Required / Key"),

  /** The message on a renewal reminder, under the box's end date. */
  REMINDER_MESSAGE("reminder_message",
      "To keep your mailbox, please renew at the counter. Current prices are below, with your box's size circled.");

  private final String key;
  private final String defaultValue;

  Setting(String key, String defaultValue) {
    this.key = key;
    this.defaultValue = defaultValue;
  }

  /**
   * Returns the key this setting is stored under.
   *
   * @return the storage key
   */
  public String key() {
    return key;
  }

  /**
   * Returns the value used when the setting has never been saved.
   *
   * @return the default value
   */
  public String defaultValue() {
    return defaultValue;
  }

}
