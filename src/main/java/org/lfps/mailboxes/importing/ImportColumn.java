package org.lfps.mailboxes.importing;

/**
 * A column of the spreadsheet that File → Import Boxes reads, one box per
 * row: its heading, whether it must be filled in, an example for the
 * template, and what goes in it, for the template's key. Both the template
 * ({@link ImportTemplate}) and the importer are built from this list, in
 * this order, so they always agree.
 */
public enum ImportColumn {

  /** The box number; the only column that must be filled in. */
  BOX_NUMBER("Box Number", true, "310",
      "The box number, such as 310 or 12A. It must be in the box inventory, if that's set up, and not "
          + "already rented to someone else."),
  /** The holder's first name. */
  FIRST_NAME("First Name", false, "John", "The holder's first name."),
  /** The holder's last name. */
  LAST_NAME("Last Name", false, "Doe", "The holder's last name."),
  /** The holder's business. */
  BUSINESS_TITLE("Business Title", false, "Acme Inc", "The holder's business, if any."),
  /** A nickname for the box. */
  BOX_NAME("Box Name", false, "", "A nickname for the box, if it helps to tell it apart. Most boxes don't have one."),
  /** The holder's phone number. */
  PHONE("Phone", false, "(555) 123-4567", "A 10-digit phone number, written any way, such as 555-123-4567."),
  /** The holder's email address. */
  EMAIL("Email", false, "john@example.com", "An email address."),
  /** The day the rental ends. */
  END_DATE("End Date", false, "12/31/2026",
      "The day the rental ends, such as 12/31/2026. A spreadsheet's own date format works too."),
  /** Whether the shop only forwards the holder's mail. */
  FORWARDING_ONLY("Forwarding Only", false, "No",
      "Yes if they don't rent a box here and we only forward their mail. Leave it blank or put No otherwise."),
  /** How many keys the holder was given. */
  KEYS("Keys", false, "2", "How many keys they were given, as a whole number."),
  /** The key deposit paid. */
  KEY_DEPOSIT("Key Deposit", false, "$20.00", "The key deposit they paid, such as 20 or $20.00."),
  /** Other business names that get mail at the box. */
  ALSO_RECEIVES_MAIL_AS("Also Receives Mail As", false, "Acme Deliveries; Doe Consulting",
      "Other business names that get mail at the box, separated by semicolons (;)."),
  /** Notes about the box or holder. */
  NOTES("Notes", false, "Picks up for spouse", "Anything else about the box or holder."),
  /** The forwarding address's street. */
  STREET("Street", false, "123 Main St",
      "A forwarding address, if there is one: the street. Fill in Street, City, State, and ZIP together."),
  /** The forwarding address's apartment or suite. */
  UNIT("Unit", false, "Apt 4", "The forwarding address's apartment or suite, if any."),
  /** The forwarding address's city. */
  CITY("City", false, "Springfield", "The forwarding address's city."),
  /** The forwarding address's state. */
  STATE("State", false, "IL", "The forwarding address's state, as two letters such as IL."),
  /** The forwarding address's ZIP code. */
  ZIP("ZIP", false, "62701", "The forwarding address's ZIP code: 5 digits, or ZIP+4 such as 62701-1234.");

  /** The column's heading in the spreadsheet. */
  private final String heading;

  /** Whether every row must have it filled in. */
  private final boolean required;

  /** What the template's example row has in it, or an empty string. */
  private final String example;

  /** What goes in the column, for the template's key. */
  private final String description;

  /**
   * Makes a column.
   *
   * @param heading the heading
   * @param required whether every row must have it filled in
   * @param example the example row's value, or an empty string
   * @param description what goes in it
   */
  ImportColumn(String heading, boolean required, String example, String description) {
    this.heading = heading;
    this.required = required;
    this.example = example;
    this.description = description;
  }

  /**
   * Returns the column's heading, such as "Box Number".
   *
   * @return the heading
   */
  public String getHeading() {
    return heading;
  }

  /**
   * Returns whether every row must have it filled in.
   *
   * @return {@code true} for Box Number
   */
  public boolean isRequired() {
    return required;
  }

  /**
   * Returns what the template's example row has in this column.
   *
   * @return the example, or an empty string
   */
  public String getExample() {
    return example;
  }

  /**
   * Returns what goes in the column, for the template's key.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Finds the column with a heading, ignoring letter case and surrounding
   * spaces, so a heading typed slightly differently still counts.
   *
   * @param heading a heading from a spreadsheet
   * @return the column, or {@code null} if no column has that heading
   */
  public static ImportColumn withHeading(String heading) {
    if (heading == null) {
      return null;
    }
    for (var column : values()) {
      if (column.heading.equalsIgnoreCase(heading.strip())) {
        return column;
      }
    }
    return null;
  }

}
