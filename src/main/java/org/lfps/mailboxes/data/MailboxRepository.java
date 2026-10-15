package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lfps.mailboxes.model.DepositOutcome;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.MailHold;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;
import org.lfps.mailboxes.util.BoxNumbers;

/**
 * Provides CRUD access to mailboxes with their alternate business names and
 * forwarding addresses, stored across the {@code mailboxes},
 * {@code business_names}, and {@code forwarding_addresses} tables. Closed
 * boxes stay in the database; only open ones count as holding a box number.
 */
public class MailboxRepository {

  /** Makes a repository for the app's database. */
  public MailboxRepository() {
  }

  /** Adds a mailbox. */
  private static final String INSERT_SQL = "INSERT INTO mailboxes "
      + "(first_name, last_name, business_title, box_number, box_name, phone, email, end_date, notes, "
      + "closed_date, key_count, key_deposit_cents, forwarding_only, key_deposit_outcome, hold_from, hold_until, "
      + "hold_note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  /** Reads mailboxes' details; a WHERE clause picks which. */
  private static final String SELECT_COLUMNS_SQL = "SELECT id, first_name, last_name, "
      + "business_title, box_number, box_name, phone, email, end_date, notes, closed_date, key_count, "
      + "key_deposit_cents, forwarding_only, key_deposit_outcome, deleted_on, hold_from, hold_until, hold_note "
      + "FROM mailboxes";

  /** Reads every mailbox that hasn't been deleted, open or closed. */
  private static final String SELECT_ALL_SQL = SELECT_COLUMNS_SQL + " WHERE deleted_on IS NULL";

  /** Reads every open mailbox's details; see {@link Mailbox#isClosed()}. */
  private static final String SELECT_OPEN_SQL = SELECT_ALL_SQL + " AND closed_date IS NULL";

  /** Reads every deleted mailbox, as listed under Recently deleted. */
  private static final String SELECT_DELETED_SQL = SELECT_COLUMNS_SQL + " WHERE deleted_on IS NOT NULL";

  /** Replaces a mailbox's details. */
  private static final String UPDATE_SQL = "UPDATE mailboxes SET first_name = ?, last_name = ?, "
      + "business_title = ?, box_number = ?, box_name = ?, phone = ?, email = ?, end_date = ?, notes = ?, "
      + "closed_date = ?, key_count = ?, key_deposit_cents = ?, forwarding_only = ?, key_deposit_outcome = ?, "
      + "hold_from = ?, hold_until = ?, hold_note = ? WHERE id = ?";

  /** Checks whether another open, rented box that isn't deleted has a box number. */
  private static final String BOX_NUMBER_TAKEN_SQL = "SELECT 1 FROM mailboxes "
      + "WHERE TRIM(box_number) = ? COLLATE NOCASE AND id <> ? AND closed_date IS NULL AND forwarding_only = 0 "
      + "AND deleted_on IS NULL LIMIT 1";

  /** Closes or reopens a mailbox, and records what happened to its key deposit. */
  private static final String SET_CLOSED_DATE_SQL =
      "UPDATE mailboxes SET closed_date = ?, key_deposit_outcome = ? WHERE id = ?";

  /** Moves a mailbox to Recently deleted, or restores it. */
  private static final String SET_DELETED_ON_SQL = "UPDATE mailboxes SET deleted_on = ? WHERE id = ?";

  /** Reads the deleted mailboxes that are due to be erased for good. */
  private static final String SELECT_DELETED_BEFORE_SQL = "SELECT id FROM mailboxes WHERE deleted_on < ?";

  /** Remembers the number of a mailbox about to be erased for good, for its payments. */
  private static final String INSERT_ERASED_SQL = "INSERT OR REPLACE INTO erased_boxes (mailbox_id, box_number) "
      + "SELECT id, box_number FROM mailboxes WHERE id = ?";

  /** Reads the numbers of the mailboxes erased for good. */
  private static final String SELECT_ERASED_SQL = "SELECT mailbox_id, box_number FROM erased_boxes";

  /** Erases a mailbox for good. */
  private static final String DELETE_SQL = "DELETE FROM mailboxes WHERE id = ?";

  /** Deletes the records of a mailbox's printed forwarding labels. */
  private static final String DELETE_LABELS_SQL = "DELETE FROM labels WHERE mailbox_id = ?";

  /** Adds an alternate business name to a mailbox. */
  private static final String INSERT_BUSINESS_NAME_SQL =
      "INSERT INTO business_names (mailbox_id, name) VALUES (?, ?)";

  /** Reads every mailbox's alternate business names, in the order they were added. */
  private static final String SELECT_ALL_BUSINESS_NAMES_SQL =
      "SELECT mailbox_id, name FROM business_names ORDER BY id";

  /** Deletes a mailbox's alternate business names. */
  private static final String DELETE_BUSINESS_NAMES_SQL =
      "DELETE FROM business_names WHERE mailbox_id = ?";

  /** Adds a forwarding address to a mailbox. */
  private static final String INSERT_FORWARDING_ADDRESS_SQL = "INSERT INTO forwarding_addresses "
      + "(mailbox_id, street, unit, city, state, zip, note, added_on) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

  /** Reads a mailbox's forwarding addresses, in the order they were added. */
  private static final String SELECT_FORWARDING_ADDRESSES_SQL =
      "SELECT street, unit, city, state, zip, note, added_on "
      + "FROM forwarding_addresses WHERE mailbox_id = ? ORDER BY id";

  /** Reads every mailbox's forwarding addresses, in the order they were added. */
  private static final String SELECT_ALL_FORWARDING_ADDRESSES_SQL =
      "SELECT mailbox_id, street, unit, city, state, zip, note, added_on "
      + "FROM forwarding_addresses ORDER BY id";

  /** Deletes a mailbox's forwarding addresses. */
  private static final String DELETE_FORWARDING_ADDRESSES_SQL =
      "DELETE FROM forwarding_addresses WHERE mailbox_id = ?";

  /**
   * Inserts a new mailbox along with its alternate business names and
   * forwarding addresses.
   *
   * @param mailbox the mailbox to persist; its id is ignored
   * @return the new mailbox's id
   * @throws SQLException if the insert fails
   */
  public int insert(Mailbox mailbox) throws SQLException {
    return insert(mailbox, null);
  }

  /**
   * Inserts a new mailbox along with its alternate business names,
   * forwarding addresses, and the first entry in its rental history.
   *
   * @param mailbox the mailbox to persist; its id is ignored
   * @param firstPeriod the rental being paid for now, or {@code null} to
   *     record none; its mailbox id is ignored
   * @return the new mailbox's id
   * @throws SQLException if the insert fails
   */
  public int insert(Mailbox mailbox, RentalPeriod firstPeriod) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        int mailboxId;
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
          bindMailboxFields(stmt, mailbox);
          stmt.executeUpdate();
          try (ResultSet keys = stmt.getGeneratedKeys()) {
            keys.next();
            mailboxId = keys.getInt(1);
          }
        }
        insertBusinessNames(conn, mailboxId, mailbox.getAlternateBusinessNames());
        insertForwardingAddresses(conn, mailboxId, mailbox.getForwardingAddresses(), List.of());
        if (firstPeriod != null) {
          RentalHistoryRepository.insert(conn, mailboxId, firstPeriod);
        }
        conn.commit();
        return mailboxId;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Returns the boxes that are open (not closed), ordered by box number.
   *
   * @return the open mailboxes
   * @throws SQLException if the query fails
   */
  public List<Mailbox> findOpen() throws SQLException {
    return find(SELECT_OPEN_SQL);
  }

  /**
   * Returns every mailbox, open and closed, ordered by box number (see
   * {@link BoxNumbers#ORDER}), with its alternate business names and
   * forwarding addresses loaded. Deleted boxes are left out; see
   * {@link #findDeleted()}.
   *
   * @return all mailboxes in the database that haven't been deleted
   * @throws SQLException if the query fails
   */
  public List<Mailbox> findAll() throws SQLException {
    return find(SELECT_ALL_SQL);
  }

  /**
   * Returns the boxes listed under Recently deleted on Manage Boxes: those
   * deleted but not yet restored or erased for good, ordered by box number.
   *
   * @return the deleted mailboxes
   * @throws SQLException if the query fails
   */
  public List<Mailbox> findDeleted() throws SQLException {
    return find(SELECT_DELETED_SQL);
  }

  /**
   * Reads mailboxes with their alternate business names and forwarding
   * addresses, ordered by box number. The names and addresses of every box
   * are read in one query each, rather than two queries per box, which
   * would take thousands of queries in a large shop.
   *
   * @param sql {@link #SELECT_ALL_SQL}, {@link #SELECT_OPEN_SQL}, or
   *     {@link #SELECT_DELETED_SQL}
   * @return the mailboxes it finds
   * @throws SQLException if the query fails
   */
  private List<Mailbox> find(String sql) throws SQLException {
    var mailboxes = new ArrayList<Mailbox>();

    try (Connection conn = Database.connect()) {
      var businessNames = findAllBusinessNames(conn);
      var forwardingAddresses = findAllForwardingAddresses(conn);
      try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
        readMailboxes(rs, businessNames, forwardingAddresses, mailboxes);
      }
    }

    mailboxes.sort((a, b) -> BoxNumbers.ORDER.compare(a.getBoxNumber(), b.getBoxNumber()));
    return mailboxes;
  }

  /**
   * Turns each row of a mailbox query into a {@link Mailbox}.
   *
   * @param rs the rows, from a query starting with {@link #SELECT_COLUMNS_SQL}
   * @param businessNames every box's alternate business names, by mailbox id
   * @param forwardingAddresses every box's forwarding addresses, by mailbox id
   * @param mailboxes the list to add the mailboxes to
   * @throws SQLException if a row can't be read
   */
  private static void readMailboxes(ResultSet rs, Map<Integer, List<String>> businessNames,
      Map<Integer, List<ForwardingAddress>> forwardingAddresses, List<Mailbox> mailboxes) throws SQLException {
    while (rs.next()) {
      var id = rs.getInt("id");
      mailboxes.add(new Mailbox(
          id,
          rs.getString("first_name"),
          rs.getString("last_name"),
          rs.getString("business_title"),
          rs.getString("box_number"),
          rs.getString("box_name"),
          rs.getString("phone"),
          rs.getString("email"),
          businessNames.get(id),
          date(rs.getString("end_date")),
          forwardingAddresses.get(id),
          rs.getString("notes"),
          date(rs.getString("closed_date")),
          rs.getObject("key_count") == null ? null : rs.getInt("key_count"),
          rs.getObject("key_deposit_cents") == null ? null : rs.getLong("key_deposit_cents"),
          rs.getInt("forwarding_only") != 0,
          DepositOutcome.fromName(rs.getString("key_deposit_outcome")),
          date(rs.getString("deleted_on")),
          mailHold(rs)));
    }
  }

  /**
   * Updates an existing mailbox and replaces its alternate business names
   * and forwarding addresses.
   *
   * @param mailbox the mailbox to save, identified by {@link Mailbox#getId()}
   * @throws SQLException if the update fails
   */
  public void update(Mailbox mailbox) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
          bindMailboxFields(stmt, mailbox);
          stmt.setInt(18, mailbox.getId());
          stmt.executeUpdate();
        }
        deleteBusinessNames(conn, mailbox.getId());
        insertBusinessNames(conn, mailbox.getId(), mailbox.getAlternateBusinessNames());
        // Addresses kept as they were keep the day they were added.
        var previous = findForwardingAddresses(conn, mailbox.getId());
        deleteForwardingAddresses(conn, mailbox.getId());
        insertForwardingAddresses(conn, mailbox.getId(), mailbox.getForwardingAddresses(), previous);
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Checks whether another open mailbox already uses the given box number,
   * ignoring surrounding whitespace and letter case. Closed, deleted, and
   * forwarding-only boxes don't count, so a number can be reused once its
   * holder leaves or switches to having their mail forwarded.
   *
   * @param boxNumber the box number to look for
   * @param excludeId the id of the mailbox being edited, or {@code 0} when adding
   * @return {@code true} if a different open mailbox already has this box number
   * @throws SQLException if the query fails
   */
  public boolean isBoxNumberTaken(String boxNumber, int excludeId) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(BOX_NUMBER_TAKEN_SQL)) {
      stmt.setString(1, boxNumber.trim());
      stmt.setInt(2, excludeId);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    }
  }

  /**
   * Closes a box on the given day with no key deposit outcome recorded, or
   * reopens it. Its record and history are kept.
   *
   * @param id the id of the mailbox
   * @param closedDate the day it closed, or {@code null} to reopen it
   * @throws SQLException if the update fails
   */
  public void setClosedDate(int id, LocalDate closedDate) throws SQLException {
    setClosedDate(id, closedDate, null);
  }

  /**
   * Closes a box on the given day, or reopens it, and records whether its
   * key deposit was given back or kept. Its record and history are kept.
   * Reopening a box always forgets the outcome.
   *
   * @param id the id of the mailbox
   * @param closedDate the day it closed, or {@code null} to reopen it
   * @param keyDepositOutcome what happened to the key deposit, or
   *     {@code null} if it isn't recorded
   * @throws SQLException if the update fails
   */
  public void setClosedDate(int id, LocalDate closedDate, DepositOutcome keyDepositOutcome) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SET_CLOSED_DATE_SQL)) {
      stmt.setString(1, closedDate == null ? null : closedDate.toString());
      stmt.setString(2, closedDate == null || keyDepositOutcome == null ? null : keyDepositOutcome.name());
      stmt.setInt(3, id);
      stmt.executeUpdate();
    }
  }

  /**
   * Deletes a box, which moves it to Recently deleted on Manage Boxes. It
   * disappears from every other list and frees its box number, but nothing
   * recorded for it is erased until {@link #eraseDeletedBefore} runs 30
   * days later, so it can be restored until then.
   *
   * @param id the id of the mailbox to delete
   * @param deletedOn the day it's deleted, normally today
   * @throws SQLException if the update fails
   */
  public void delete(int id, LocalDate deletedOn) throws SQLException {
    setDeletedOn(id, deletedOn);
  }

  /**
   * Brings a deleted box back from Recently deleted, as it was when deleted.
   * Callers check first that its box number hasn't been given to someone
   * else, as when reopening a closed box.
   *
   * @param id the id of the mailbox to restore
   * @throws SQLException if the update fails
   */
  public void restore(int id) throws SQLException {
    setDeletedOn(id, null);
  }

  /**
   * Records the day a box was deleted.
   *
   * @param id the id of the mailbox
   * @param deletedOn the day, or {@code null} if it isn't deleted
   * @throws SQLException if the update fails
   */
  private void setDeletedOn(int id, LocalDate deletedOn) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SET_DELETED_ON_SQL)) {
      stmt.setString(1, deletedOn == null ? null : deletedOn.toString());
      stmt.setInt(2, id);
      stmt.executeUpdate();
    }
  }

  /**
   * Erases a box for good, with its alternate business names, forwarding
   * addresses, and the records of its printed forwarding labels. Its rental
   * history is kept, so Payments still adds up for the days it paid, and its
   * box number is remembered for those payments (see
   * {@link #findErasedBoxNumbers()}).
   *
   * @param id the id of the mailbox to erase
   * @throws SQLException if it can't be erased; nothing is erased then
   */
  public void eraseForever(int id) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        erase(conn, id);
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Erases for good the boxes deleted before a day, as the app does when it
   * starts with those deleted more than 30 days ago. See
   * {@link #eraseForever(int)} for what's erased and what's kept.
   *
   * @param cutoff boxes deleted before this day are erased
   * @return how many boxes were erased
   * @throws SQLException if they can't be erased; none are erased then
   */
  public int eraseDeletedBefore(LocalDate cutoff) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        var ids = new ArrayList<Integer>();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_DELETED_BEFORE_SQL)) {
          stmt.setString(1, cutoff.toString());
          try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
              ids.add(rs.getInt("id"));
            }
          }
        }
        for (var id : ids) {
          erase(conn, id);
        }
        conn.commit();
        return ids.size();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Returns the box number of each box erased for good, for showing its
   * payments, which are kept.
   *
   * @return each erased box's number, by its old mailbox id
   * @throws SQLException if they can't be read
   */
  public Map<Integer, String> findErasedBoxNumbers() throws SQLException {
    var numbers = new HashMap<Integer, String>();

    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_ERASED_SQL)) {
      while (rs.next()) {
        numbers.put(rs.getInt("mailbox_id"), rs.getString("box_number"));
      }
    }

    return numbers;
  }

  /**
   * Erases a box for good; see {@link #eraseForever(int)}.
   *
   * @param conn the connection, in a transaction
   * @param id the id of the mailbox to erase
   * @throws SQLException if it can't be erased
   */
  private void erase(Connection conn, int id) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(INSERT_ERASED_SQL)) {
      stmt.setInt(1, id);
      stmt.executeUpdate();
    }
    deleteBusinessNames(conn, id);
    deleteForwardingAddresses(conn, id);
    try (PreparedStatement stmt = conn.prepareStatement(DELETE_LABELS_SQL)) {
      stmt.setInt(1, id);
      stmt.executeUpdate();
    }
    try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
      stmt.setInt(1, id);
      stmt.executeUpdate();
    }
  }

  /**
   * Fills in a mailbox's details as the first 17 parameters of an insert or
   * update. A mail hold that has ended isn't saved, so it's cleared the next
   * time the box is saved.
   *
   * @param stmt the insert or update
   * @param mailbox the mailbox
   * @throws SQLException if a parameter can't be set
   */
  private void bindMailboxFields(PreparedStatement stmt, Mailbox mailbox) throws SQLException {
    stmt.setString(1, mailbox.getFirstName());
    stmt.setString(2, mailbox.getLastName());
    stmt.setString(3, mailbox.getBusinessTitle());
    stmt.setString(4, mailbox.getBoxNumber());
    stmt.setString(5, mailbox.getBoxName());
    stmt.setString(6, mailbox.getPhone());
    stmt.setString(7, mailbox.getEmail());
    stmt.setString(8, mailbox.getEndDate() == null ? null : mailbox.getEndDate().toString());
    stmt.setString(9, mailbox.getNotes() == null || mailbox.getNotes().isBlank() ? null : mailbox.getNotes().strip());
    stmt.setString(10, mailbox.getClosedDate() == null ? null : mailbox.getClosedDate().toString());
    stmt.setObject(11, mailbox.getKeyCount());
    stmt.setObject(12, mailbox.getKeyDepositCents());
    stmt.setInt(13, mailbox.isForwardingOnly() ? 1 : 0);
    stmt.setString(14, mailbox.getKeyDepositOutcome() == null ? null : mailbox.getKeyDepositOutcome().name());
    var hold = mailbox.getMailHold(LocalDate.now());
    stmt.setString(15, hold == null ? null : hold.getFrom().toString());
    stmt.setString(16, hold == null ? null : hold.getUntil().toString());
    stmt.setString(17, hold == null ? null : hold.getNote());
  }

  /**
   * Reads the mail hold in the current row of a mailbox query.
   *
   * @param rs the query, on a row with the hold's columns
   * @return the hold, or {@code null} if there's none or it was stored
   *     without both of its days
   * @throws SQLException if a column can't be read
   */
  private static MailHold mailHold(ResultSet rs) throws SQLException {
    var from = date(rs.getString("hold_from"));
    var until = date(rs.getString("hold_until"));
    if (from == null || until == null || until.isBefore(from)) {
      return null;
    }
    return new MailHold(from, until, rs.getString("hold_note"));
  }

  /**
   * Reads a date stored as text, such as 2026-10-05.
   *
   * @param value the stored date, or {@code null}
   * @return the date, or {@code null} if there's none
   */
  private static LocalDate date(String value) {
    return value == null ? null : LocalDate.parse(value);
  }

  /**
   * Adds alternate business names to a mailbox.
   *
   * @param conn the connection, which may be in a transaction
   * @param mailboxId the mailbox's id
   * @param names the names; blank ones are skipped
   * @throws SQLException if they can't be added
   */
  private void insertBusinessNames(Connection conn, int mailboxId, List<String> names) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(INSERT_BUSINESS_NAME_SQL)) {
      for (var name : names) {
        if (name == null || name.isBlank()) {
          continue;
        }
        stmt.setInt(1, mailboxId);
        stmt.setString(2, name);
        stmt.addBatch();
      }
      stmt.executeBatch();
    }
  }

  /**
   * Deletes a mailbox's alternate business names.
   *
   * @param conn the connection, which may be in a transaction
   * @param mailboxId the mailbox's id
   * @throws SQLException if they can't be deleted
   */
  private void deleteBusinessNames(Connection conn, int mailboxId) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(DELETE_BUSINESS_NAMES_SQL)) {
      stmt.setInt(1, mailboxId);
      stmt.executeUpdate();
    }
  }

  /**
   * Adds forwarding addresses to a mailbox, each with the day it was added
   * (see {@link #addedOn}).
   *
   * @param conn the connection, which may be in a transaction
   * @param mailboxId the mailbox's id
   * @param addresses the addresses
   * @param previous the box's addresses before this save, or none for a new box
   * @throws SQLException if they can't be added
   */
  private void insertForwardingAddresses(Connection conn, int mailboxId, List<ForwardingAddress> addresses,
      List<ForwardingAddress> previous) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(INSERT_FORWARDING_ADDRESS_SQL)) {
      for (var address : addresses) {
        var addedOn = addedOn(address, previous);
        stmt.setInt(1, mailboxId);
        stmt.setString(2, address.getStreet());
        stmt.setString(3, address.getUnit());
        stmt.setString(4, address.getCity());
        stmt.setString(5, address.getState());
        stmt.setString(6, address.getZip());
        stmt.setString(7, address.getNote());
        stmt.setString(8, addedOn == null ? null : addedOn.toString());
        stmt.addBatch();
      }
      stmt.executeBatch();
    }
  }

  /**
   * Returns the day to save as an address's day added: the day it was first
   * added if it was already on the box unchanged, which keeps an unknown day
   * unknown for addresses from before version 1.10, or today if it's new or
   * changed.
   *
   * @param address the address being saved
   * @param previous the box's addresses before the save, or none for a new box
   * @return the day, or {@code null} if it isn't known
   */
  static LocalDate addedOn(ForwardingAddress address, List<ForwardingAddress> previous) {
    for (var old : previous) {
      if (old.equals(address)) {
        return old.getAddedOn();
      }
    }
    return address.getAddedOn() != null ? address.getAddedOn() : LocalDate.now();
  }

  /**
   * Deletes a mailbox's forwarding addresses.
   *
   * @param conn the connection, which may be in a transaction
   * @param mailboxId the mailbox's id
   * @throws SQLException if they can't be deleted
   */
  private void deleteForwardingAddresses(Connection conn, int mailboxId) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(DELETE_FORWARDING_ADDRESSES_SQL)) {
      stmt.setInt(1, mailboxId);
      stmt.executeUpdate();
    }
  }

  /**
   * Reads a mailbox's forwarding addresses.
   *
   * @param conn the connection
   * @param mailboxId the mailbox's id
   * @return the addresses, in the order they were added
   * @throws SQLException if they can't be read
   */
  private List<ForwardingAddress> findForwardingAddresses(Connection conn, int mailboxId) throws SQLException {
    var addresses = new ArrayList<ForwardingAddress>();

    try (PreparedStatement stmt = conn.prepareStatement(SELECT_FORWARDING_ADDRESSES_SQL)) {
      stmt.setInt(1, mailboxId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          addresses.add(forwardingAddress(rs));
        }
      }
    }

    return addresses;
  }

  /**
   * Reads every mailbox's alternate business names.
   *
   * @param conn the connection
   * @return each box's names in the order they were added, by mailbox id;
   *     a box with none isn't in the map
   * @throws SQLException if they can't be read
   */
  private Map<Integer, List<String>> findAllBusinessNames(Connection conn) throws SQLException {
    var names = new HashMap<Integer, List<String>>();

    try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(SELECT_ALL_BUSINESS_NAMES_SQL)) {
      while (rs.next()) {
        names.computeIfAbsent(rs.getInt("mailbox_id"), id -> new ArrayList<>()).add(rs.getString("name"));
      }
    }

    return names;
  }

  /**
   * Reads the forwarding address in the current row of a query.
   *
   * @param rs the query, on a row with the address's columns
   * @return the address
   * @throws SQLException if a column can't be read
   */
  private static ForwardingAddress forwardingAddress(ResultSet rs) throws SQLException {
    return new ForwardingAddress(rs.getString("street"), rs.getString("unit"), rs.getString("city"),
        rs.getString("state"), rs.getString("zip"), rs.getString("note"), date(rs.getString("added_on")));
  }

  /**
   * Reads every mailbox's forwarding addresses.
   *
   * @param conn the connection
   * @return each box's addresses in the order they were added, by mailbox id;
   *     a box with none isn't in the map
   * @throws SQLException if they can't be read
   */
  private Map<Integer, List<ForwardingAddress>> findAllForwardingAddresses(Connection conn) throws SQLException {
    var addresses = new HashMap<Integer, List<ForwardingAddress>>();

    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_ALL_FORWARDING_ADDRESSES_SQL)) {
      while (rs.next()) {
        addresses.computeIfAbsent(rs.getInt("mailbox_id"), id -> new ArrayList<>()).add(forwardingAddress(rs));
      }
    }

    return addresses;
  }

}
