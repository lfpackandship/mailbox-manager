package org.lfps.mailboxes.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An immutable record of a rented mailbox: who holds it, how to reach them,
 * and the box it belongs to. When a holder gives up their box, the record is
 * closed rather than deleted, so its history is kept.
 */
public final class Mailbox {

  /**
   * The database id, or {@code 0} if not yet saved.
   */
  private final int id;

  /** The holder's first name. */
  private final String firstName;

  /** The holder's last name. */
  private final String lastName;

  /** The holder's main business name. */
  private final String businessTitle;

  /** The box number, such as 101. */
  private final String boxNumber;

  /**
   * The box's nickname, or {@code null} if it has none.
   */
  private final String boxName;

  /** The holder's phone number. */
  private final String phone;

  /** The holder's email address. */
  private final String email;

  /** Other business names mail for the box may come addressed to. */
  private final List<String> alternateBusinessNames;

  /**
   * The day the rental ends, or {@code null} if not set.
   */
  private final LocalDate endDate;

  /** Where the holder's mail can be forwarded. */
  private final List<ForwardingAddress> forwardingAddresses;

  /**
   * Notes about the box, or {@code null} if none were entered.
   */
  private final String notes;

  /**
   * The day the box was closed, or {@code null} if it is open.
   */
  private final LocalDate closedDate;

  /**
   * How many keys were given out, or {@code null} if not recorded.
   */
  private final Integer keyCount;

  /**
   * The key deposit paid, in cents, or {@code null} if not recorded.
   */
  private final Long keyDepositCents;

  /** Whether the shop only forwards the holder's mail, with no box rented here. */
  private final boolean forwardingOnly;

  /**
   * Creates an open mailbox record with no notes.
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
   * @param endDate the date the box rental ends, or {@code null} if not set
   * @param forwardingAddresses addresses the holder's mail can be forwarded
   *     to; {@code null} is treated as empty
   */
  public Mailbox(int id, String firstName, String lastName, String businessTitle,
      String boxNumber, String boxName, String phone, String email,
      List<String> alternateBusinessNames, LocalDate endDate, List<ForwardingAddress> forwardingAddresses) {
    this(id, firstName, lastName, businessTitle, boxNumber, boxName, phone, email, alternateBusinessNames,
        endDate, forwardingAddresses, null, null);
  }

  /**
   * Creates a mailbox record with no keys recorded.
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
   * @param endDate the date the box rental ends, or {@code null} if not set
   * @param forwardingAddresses addresses the holder's mail can be forwarded
   *     to; {@code null} is treated as empty
   * @param notes free-form notes about the box or holder, or blank/null if none
   * @param closedDate the date the box was closed, or {@code null} if it's open
   */
  public Mailbox(int id, String firstName, String lastName, String businessTitle,
      String boxNumber, String boxName, String phone, String email,
      List<String> alternateBusinessNames, LocalDate endDate, List<ForwardingAddress> forwardingAddresses,
      String notes, LocalDate closedDate) {
    this(id, firstName, lastName, businessTitle, boxNumber, boxName, phone, email, alternateBusinessNames,
        endDate, forwardingAddresses, notes, closedDate, null, null);
  }

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
   * @param endDate the date the box rental ends, or {@code null} if not set
   * @param forwardingAddresses addresses the holder's mail can be forwarded
   *     to; {@code null} is treated as empty
   * @param notes free-form notes about the box or holder, or blank/null if none
   * @param closedDate the date the box was closed, or {@code null} if it's open
   * @param keyCount how many keys the holder was given, or {@code null} if not recorded
   * @param keyDepositCents the refundable deposit paid for the keys, in
   *     cents, or {@code null} if not recorded
   */
  public Mailbox(int id, String firstName, String lastName, String businessTitle,
      String boxNumber, String boxName, String phone, String email,
      List<String> alternateBusinessNames, LocalDate endDate, List<ForwardingAddress> forwardingAddresses,
      String notes, LocalDate closedDate, Integer keyCount, Long keyDepositCents) {
    this(id, firstName, lastName, businessTitle, boxNumber, boxName, phone, email, alternateBusinessNames,
        endDate, forwardingAddresses, notes, closedDate, keyCount, keyDepositCents, false);
  }

  /**
   * Creates a mailbox record.
   *
   * @param id the database id, or {@code 0} for a not-yet-persisted mailbox
   * @param firstName the holder's first name
   * @param lastName the holder's last name
   * @param businessTitle the holder's primary business title, or blank/null if none
   * @param boxNumber the physical box number, or for a forwarding-only box
   *     the number the holder's mail is addressed to
   * @param boxName an optional nickname/label for the box itself
   * @param phone the holder's phone number
   * @param email the holder's email address, or blank/null if none
   * @param alternateBusinessNames additional business names (DBAs) that also
   *     receive mail at this box; {@code null} is treated as empty
   * @param endDate the date the box rental ends, or {@code null} if not set
   * @param forwardingAddresses addresses the holder's mail can be forwarded
   *     to; {@code null} is treated as empty
   * @param notes free-form notes about the box or holder, or blank/null if none
   * @param closedDate the date the box was closed, or {@code null} if it's open
   * @param keyCount how many keys the holder was given, or {@code null} if not recorded
   * @param keyDepositCents the refundable deposit paid for the keys, in
   *     cents, or {@code null} if not recorded
   * @param forwardingOnly whether the shop only forwards the holder's mail,
   *     with no box rented here; see {@link #isForwardingOnly()}
   */
  public Mailbox(int id, String firstName, String lastName, String businessTitle,
      String boxNumber, String boxName, String phone, String email,
      List<String> alternateBusinessNames, LocalDate endDate, List<ForwardingAddress> forwardingAddresses,
      String notes, LocalDate closedDate, Integer keyCount, Long keyDepositCents, boolean forwardingOnly) {
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
    this.endDate = endDate;
    this.forwardingAddresses = forwardingAddresses == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(forwardingAddresses));
    this.notes = notes;
    this.closedDate = closedDate;
    this.keyCount = keyCount;
    this.keyDepositCents = keyDepositCents;
    this.forwardingOnly = forwardingOnly;
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

  /**
   * Returns the date the box rental ends.
   *
   * @return the end date, or {@code null} if not set
   */
  public LocalDate getEndDate() {
    return endDate;
  }

  /**
   * Returns the addresses the holder's mail can be forwarded to.
   *
   * @return the forwarding addresses; never {@code null}
   */
  public List<ForwardingAddress> getForwardingAddresses() {
    return forwardingAddresses;
  }

  /**
   * Returns the notes about the box or holder.
   *
   * @return the notes, or {@code null} if none were entered
   */
  public String getNotes() {
    return notes;
  }

  /**
   * Returns the date the box was closed. A closed box keeps its record but
   * no longer counts as rented, so its number can be given to someone else.
   *
   * @return the closing date, or {@code null} if the box is open
   */
  public LocalDate getClosedDate() {
    return closedDate;
  }

  /**
   * Returns how many keys the holder was given.
   *
   * @return the number of keys, or {@code null} if not recorded
   */
  public Integer getKeyCount() {
    return keyCount;
  }

  /**
   * Returns the refundable deposit paid for the box's keys, to be given back
   * when the keys are returned.
   *
   * @return the deposit in cents, or {@code null} if not recorded
   */
  public Long getKeyDepositCents() {
    return keyDepositCents;
  }

  /**
   * Returns whether the shop only forwards this holder's mail, with no box
   * rented here. Mail still arrives addressed to their box number, often
   * that of a box they used to rent, which may now be rented to someone
   * else; so a forwarding-only box doesn't hold its number.
   *
   * @return {@code true} if the box is forwarding only
   */
  public boolean isForwardingOnly() {
    return forwardingOnly;
  }

  /**
   * Returns whether the box has been closed.
   *
   * @return {@code true} if the box is closed
   */
  public boolean isClosed() {
    return closedDate != null;
  }

  /**
   * Returns a copy of this mailbox with a different rental end date.
   *
   * @param newEndDate the new end date, or {@code null} for none
   * @return the updated copy
   */
  public Mailbox withEndDate(LocalDate newEndDate) {
    return new Mailbox(id, firstName, lastName, businessTitle, boxNumber, boxName, phone, email,
        alternateBusinessNames, newEndDate, forwardingAddresses, notes, closedDate, keyCount, keyDepositCents,
        forwardingOnly);
  }

  /**
   * Returns a copy of this mailbox that is closed on the given date, or
   * reopened.
   *
   * @param newClosedDate the closing date, or {@code null} to reopen
   * @return the updated copy
   */
  public Mailbox withClosedDate(LocalDate newClosedDate) {
    return new Mailbox(id, firstName, lastName, businessTitle, boxNumber, boxName, phone, email,
        alternateBusinessNames, endDate, forwardingAddresses, notes, newClosedDate, keyCount, keyDepositCents,
        forwardingOnly);
  }

  /**
   * Returns the holder's first and last name, leaving out either one if it's
   * blank.
   *
   * @return the full name, such as "Ada Lovelace", or an empty string if the
   *     box has no name
   */
  public String getFullName() {
    return Stream.of(firstName, lastName)
        .filter(name -> name != null && !name.isBlank())
        .map(String::trim)
        .collect(Collectors.joining(" "));
  }

  /**
   * Returns the name to show for whoever holds the box: their full name, or
   * the business title if the box has no name.
   *
   * @return the holder's name or business title, or an empty string if it
   *     has neither
   */
  public String getHolderName() {
    var fullName = getFullName();
    if (!fullName.isEmpty() || businessTitle == null) {
      return fullName;
    }
    return businessTitle.trim();
  }

}
