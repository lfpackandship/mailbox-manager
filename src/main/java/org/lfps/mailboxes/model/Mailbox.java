package org.lfps.mailboxes.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable record of a rented mailbox: who holds it, how to reach them,
 * and the box it belongs to.
 */
public final class Mailbox {

  private final int id;
  private final String firstName;
  private final String lastName;
  private final String businessTitle;
  private final String boxNumber;
  private final String boxName;
  private final String phone;
  private final String email;
  private final List<String> alternateBusinessNames;

  /**
   * Creates a mailbox record.
   *
   * @param id the database id, or {@code 0} for a not-yet-persisted mailbox
   * @param firstName the holder's first name
   * @param lastName the holder's last name
   * @param businessTitle the holder's primary business title, or blank/null if none
   * @param boxNumber the physical box number
   * @param boxName an optional nickname/label for the box itself
   * @param phone the holder's phone number
   * @param email the holder's email address, or blank/null if none
   * @param alternateBusinessNames additional business names (DBAs) that also
   *     receive mail at this box; {@code null} is treated as empty
   */
  public Mailbox(int id, String firstName, String lastName, String businessTitle,
      String boxNumber, String boxName, String phone, String email,
      List<String> alternateBusinessNames) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.businessTitle = businessTitle;
    this.boxNumber = boxNumber;
    this.boxName = boxName;
    this.phone = phone;
    this.email = email;
    this.alternateBusinessNames = alternateBusinessNames == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(alternateBusinessNames));
  }

  /**
   * Returns the database id.
   *
   * @return the database id, or {@code 0} if not yet persisted
   */
  public int getId() {
    return id;
  }

  /**
   * Returns the holder's first name.
   *
   * @return the holder's first name
   */
  public String getFirstName() {
    return firstName;
  }

  /**
   * Returns the holder's last name.
   *
   * @return the holder's last name
   */
  public String getLastName() {
    return lastName;
  }

  /**
   * Returns the holder's primary business title.
   *
   * @return the holder's primary business title
   */
  public String getBusinessTitle() {
    return businessTitle;
  }

  /**
   * Returns the physical box number.
   *
   * @return the physical box number
   */
  public String getBoxNumber() {
    return boxNumber;
  }

  /**
   * Returns the box's nickname/label.
   *
   * @return the box's nickname/label, or {@code null} if none was set
   */
  public String getBoxName() {
    return boxName;
  }

  /**
   * Returns the holder's phone number.
   *
   * @return the holder's phone number
   */
  public String getPhone() {
    return phone;
  }

  /**
   * Returns the holder's email address.
   *
   * @return the holder's email address
   */
  public String getEmail() {
    return email;
  }

  /**
   * Returns the additional business names (DBAs) that also receive mail
   * at this box.
   *
   * @return the alternate business names; never {@code null}
   */
  public List<String> getAlternateBusinessNames() {
    return alternateBusinessNames;
  }

}
