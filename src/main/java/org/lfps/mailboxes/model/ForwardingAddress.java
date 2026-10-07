package org.lfps.mailboxes.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;

/**
 * An immutable address that a box holder's mail can be forwarded to, with an
 * optional note such as "summer" or "office", and the day it was added. The
 * day isn't part of the address: two addresses are equal if everything else
 * is.
 */
public final class ForwardingAddress {

  /** The street address. */
  private final String street;

  /**
   * The apartment or suite, or {@code null} if none.
   */
  private final String unit;

  /** The city. */
  private final String city;

  /** The two-letter state code. */
  private final String state;

  /** The ZIP code. */
  private final String zip;

  /**
   * A note on when to use the address, such as "summer", or {@code null} if
   * none.
   */
  private final String note;

  /**
   * The day the address was added to the box, or {@code null} if it isn't
   * known, as for addresses added before version 1.10 or not yet saved.
   */
  private final LocalDate addedOn;

  /**
   * Creates a forwarding address with no day added, as typed on Add New Box
   * or Edit Box; the day is set when it's saved.
   *
   * @param street the street address, such as "123 Main St"
   * @param unit the apartment or suite, or blank/null if none
   * @param city the city
   * @param state the two-letter state code
   * @param zip the ZIP code
   * @param note a note about when or why to use this address, or blank/null
   */
  public ForwardingAddress(String street, String unit, String city, String state, String zip, String note) {
    this(street, unit, city, state, zip, note, null);
  }

  /**
   * Creates a forwarding address. Surrounding whitespace is trimmed, the
   * state is upper-cased, and blank optional parts are stored as {@code null}.
   *
   * @param street the street address, such as "123 Main St"
   * @param unit the apartment or suite, or blank/null if none
   * @param city the city
   * @param state the two-letter state code
   * @param zip the ZIP code
   * @param note a note about when or why to use this address, or blank/null
   * @param addedOn the day it was added to the box, or {@code null} if not known
   */
  public ForwardingAddress(String street, String unit, String city, String state, String zip, String note,
      LocalDate addedOn) {
    this.street = trimToNull(street);
    this.unit = trimToNull(unit);
    this.city = trimToNull(city);
    this.state = state == null ? null : trimToNull(state.toUpperCase(java.util.Locale.ROOT));
    this.zip = trimToNull(zip);
    this.note = trimToNull(note);
    this.addedOn = addedOn;
  }

  /**
   * Returns the street address.
   *
   * @return the street address
   */
  public String getStreet() {
    return street;
  }

  /**
   * Returns the apartment or suite.
   *
   * @return the apartment or suite, or {@code null} if none
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the city.
   *
   * @return the city
   */
  public String getCity() {
    return city;
  }

  /**
   * Returns the two-letter state code.
   *
   * @return the state code, upper-cased
   */
  public String getState() {
    return state;
  }

  /**
   * Returns the ZIP code.
   *
   * @return the ZIP code
   */
  public String getZip() {
    return zip;
  }

  /**
   * Returns the note about when or why to use this address.
   *
   * @return the note, or {@code null} if none
   */
  public String getNote() {
    return note;
  }

  /**
   * Returns the day the address was added to the box.
   *
   * @return the day, or {@code null} if it isn't known
   */
  public LocalDate getAddedOn() {
    return addedOn;
  }

  /**
   * Returns the same address with a different day added.
   *
   * @param day the day it was added, or {@code null} if not known
   * @return the copy
   */
  public ForwardingAddress withAddedOn(LocalDate day) {
    return new ForwardingAddress(street, unit, city, state, zip, note, day);
  }

  /**
   * Returns the address on one line, followed by the note in parentheses if
   * there is one, such as "123 Main St, Apt 4, Springfield, IL 62701 (summer)".
   *
   * @return the address for display
   */
  @Override
  public String toString() {
    var parts = new ArrayList<String>();
    parts.add(street);
    if (unit != null) {
      parts.add(unit);
    }
    parts.add(city);
    parts.add(state + " " + zip);
    var text = String.join(", ", parts);
    return note == null ? text : text + " (" + note + ")";
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ForwardingAddress)) {
      return false;
    }
    var that = (ForwardingAddress) other;
    return Objects.equals(street, that.street) && Objects.equals(unit, that.unit)
        && Objects.equals(city, that.city) && Objects.equals(state, that.state)
        && Objects.equals(zip, that.zip) && Objects.equals(note, that.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(street, unit, city, state, zip, note);
  }

  /**
   * Trims text, treating blank text as none.
   *
   * @param value the text, or {@code null}
   * @return the trimmed text, or {@code null} if it's blank
   */
  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

}
