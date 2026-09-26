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
  TEXT_SIZE("text_size", "NORMAL");

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
