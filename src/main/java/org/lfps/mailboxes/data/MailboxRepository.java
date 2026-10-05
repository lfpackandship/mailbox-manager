package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.lfps.mailboxes.model.ForwardingAddress;
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
      + "closed_date, key_count, key_deposit_cents, forwarding_only) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  /** Reads every mailbox. */
  private static final String SELECT_ALL_SQL = "SELECT id, first_name, last_name, "
      + "business_title, box_number, box_name, phone, email, end_date, notes, closed_date, key_count, "
      + "key_deposit_cents, forwarding_only FROM mailboxes";

  /** Replaces a mailbox's details. */
  private static final String UPDATE_SQL = "UPDATE mailboxes SET first_name = ?, last_name = ?, "
      + "business_title = ?, box_number = ?, box_name = ?, phone = ?, email = ?, end_date = ?, notes = ?, "
      + "closed_date = ?, key_count = ?, key_deposit_cents = ?, forwarding_only = ? WHERE id = ?";

  /** Checks whether another open, rented box has a box number. */
  private static final String BOX_NUMBER_TAKEN_SQL = "SELECT 1 FROM mailboxes "
      + "WHERE TRIM(box_number) = ? COLLATE NOCASE AND id <> ? AND closed_date IS NULL AND forwarding_only = 0 "
      + "LIMIT 1";

  /** Closes or reopens a mailbox. */
  private static final String SET_CLOSED_DATE_SQL = "UPDATE mailboxes SET closed_date = ? WHERE id = ?";

  /** Deletes a mailbox. */
  private static final String DELETE_SQL = "DELETE FROM mailboxes WHERE id = ?";

  /** Deletes a mailbox's rental history. */
  private static final String DELETE_RENTAL_PERIODS_SQL = "DELETE FROM rental_periods WHERE mailbox_id = ?";

  /** Adds an alternate business name to a mailbox. */
  private static final String INSERT_BUSINESS_NAME_SQL =
      "INSERT INTO business_names (mailbox_id, name) VALUES (?, ?)";

  /** Reads a mailbox's alternate business names, in the order they were added. */
  private static final String SELECT_BUSINESS_NAMES_SQL =
      "SELECT name FROM business_names WHERE mailbox_id = ? ORDER BY id";

  /** Deletes a mailbox's alternate business names. */
  private static final String DELETE_BUSINESS_NAMES_SQL =
      "DELETE FROM business_names WHERE mailbox_id = ?";

  /** Adds a forwarding address to a mailbox. */
  private static final String INSERT_FORWARDING_ADDRESS_SQL = "INSERT INTO forwarding_addresses "
      + "(mailbox_id, street, unit, city, state, zip, note) VALUES (?, ?, ?, ?, ?, ?, ?)";

  /** Reads a mailbox's forwarding addresses, in the order they were added. */
  private static final String SELECT_FORWARDING_ADDRESSES_SQL = "SELECT street, unit, city, state, zip, note "
      + "FROM forwarding_addresses WHERE mailbox_id = ? ORDER BY id";

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
        insertForwardingAddresses(conn, mailboxId, mailbox.getForwardingAddresses());
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
    return findAll().stream().filter(m -> !m.isClosed()).collect(Collectors.toList());
  }

  /**
   * Returns every mailbox, open and closed, ordered by box number (see
   * {@link BoxNumbers#ORDER}), with its alternate business names and
   * forwarding addresses loaded.
   *
   * @return all mailboxes in the database
   * @throws SQLException if the query fails
   */
  public List<Mailbox> findAll() throws SQLException {
    var mailboxes = new ArrayList<Mailbox>();

    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_ALL_SQL)) {
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
            findBusinessNames(conn, id),
            date(rs.getString("end_date")),
            findForwardingAddresses(conn, id),
            rs.getString("notes"),
            date(rs.getString("closed_date")),
            rs.getObject("key_count") == null ? null : rs.getInt("key_count"),
            rs.getObject("key_deposit_cents") == null ? null : rs.getLong("key_deposit_cents"),
            rs.getInt("forwarding_only") != 0));
      }
    }

    mailboxes.sort((a, b) -> BoxNumbers.ORDER.compare(a.getBoxNumber(), b.getBoxNumber()));
    return mailboxes;
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
          stmt.setInt(14, mailbox.getId());
          stmt.executeUpdate();
        }
        deleteBusinessNames(conn, mailbox.getId());
        insertBusinessNames(conn, mailbox.getId(), mailbox.getAlternateBusinessNames());
        deleteForwardingAddresses(conn, mailbox.getId());
        insertForwardingAddresses(conn, mailbox.getId(), mailbox.getForwardingAddresses());
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Checks whether another open mailbox already uses the given box number,
   * ignoring surrounding whitespace and letter case. Closed and
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
   * Closes a box on the given day, or reopens it. Its record and history
   * are kept.
   *
   * @param id the id of the mailbox
   * @param closedDate the day it closed, or {@code null} to reopen it
   * @throws SQLException if the update fails
   */
  public void setClosedDate(int id, LocalDate closedDate) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SET_CLOSED_DATE_SQL)) {
      stmt.setString(1, closedDate == null ? null : closedDate.toString());
      stmt.setInt(2, id);
      stmt.executeUpdate();
    }
  }

  /**
   * Permanently deletes a mailbox with its alternate business names,
   * forwarding addresses, and rental history.
   *
   * @param id the id of the mailbox to delete
   * @throws SQLException if the delete fails
   */
  public void delete(int id) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        deleteBusinessNames(conn, id);
        deleteForwardingAddresses(conn, id);
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_RENTAL_PERIODS_SQL)) {
          stmt.setInt(1, id);
          stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
          stmt.setInt(1, id);
          stmt.executeUpdate();
        }
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Fills in a mailbox's details as the first 13 parameters of an insert or
   * update.
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
   * Adds forwarding addresses to a mailbox.
   *
   * @param conn the connection, which may be in a transaction
   * @param mailboxId the mailbox's id
   * @param addresses the addresses
   * @throws SQLException if they can't be added
   */
  private void insertForwardingAddresses(Connection conn, int mailboxId, List<ForwardingAddress> addresses)
      throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(INSERT_FORWARDING_ADDRESS_SQL)) {
      for (var address : addresses) {
        stmt.setInt(1, mailboxId);
        stmt.setString(2, address.getStreet());
        stmt.setString(3, address.getUnit());
        stmt.setString(4, address.getCity());
        stmt.setString(5, address.getState());
        stmt.setString(6, address.getZip());
        stmt.setString(7, address.getNote());
        stmt.addBatch();
      }
      stmt.executeBatch();
    }
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
          addresses.add(new ForwardingAddress(rs.getString("street"), rs.getString("unit"),
              rs.getString("city"), rs.getString("state"), rs.getString("zip"), rs.getString("note")));
        }
      }
    }

    return addresses;
  }

  /**
   * Reads a mailbox's alternate business names.
   *
   * @param conn the connection
   * @param mailboxId the mailbox's id
   * @return the names, in the order they were added
   * @throws SQLException if they can't be read
   */
  private List<String> findBusinessNames(Connection conn, int mailboxId) throws SQLException {
    var names = new ArrayList<String>();

    try (PreparedStatement stmt = conn.prepareStatement(SELECT_BUSINESS_NAMES_SQL)) {
      stmt.setInt(1, mailboxId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          names.add(rs.getString("name"));
        }
      }
    }

    return names;
  }

}
