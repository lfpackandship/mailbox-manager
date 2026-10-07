package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.lfps.mailboxes.model.RentalPeriod;

/**
 * Records renewals and payments in each box's rental history, stored in the
 * {@code rental_periods} table.
 */
public class RentalHistoryRepository {

  /** Makes a repository for the app's database. */
  public RentalHistoryRepository() {
  }

  /** Adds an entry to the rental history. */
  private static final String INSERT_SQL = "INSERT INTO rental_periods "
      + "(mailbox_id, recorded_on, start_date, end_date, amount_cents, payment_method, note) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?)";

  /** Starts each query that reads entries. */
  private static final String COLUMNS =
      "SELECT id, mailbox_id, recorded_on, start_date, end_date, amount_cents, payment_method, note "
      + "FROM rental_periods ";

  /** Reads a box's history, oldest first. */
  private static final String SELECT_FOR_MAILBOX_SQL = COLUMNS + "WHERE mailbox_id = ? ORDER BY start_date, id";

  /** Reads the entries recorded between two days, newest first, for the Payments screen. */
  private static final String SELECT_RECORDED_BETWEEN_SQL = COLUMNS
      + "WHERE recorded_on BETWEEN ? AND ? ORDER BY recorded_on DESC, id DESC";

  /** Changes a box's end date. */
  private static final String UPDATE_END_DATE_SQL = "UPDATE mailboxes SET end_date = ? WHERE id = ?";

  /** Reads one entry. */
  private static final String SELECT_ONE_SQL = COLUMNS + "WHERE id = ?";

  /** Changes everything about an entry except the box it belongs to. */
  private static final String UPDATE_SQL = "UPDATE rental_periods SET "
      + "recorded_on = ?, start_date = ?, end_date = ?, amount_cents = ?, payment_method = ?, note = ? "
      + "WHERE id = ?";

  /** Changes a box's end date, but only if it's still the date given. */
  private static final String MOVE_END_DATE_SQL = "UPDATE mailboxes SET end_date = ? WHERE id = ? AND end_date = ?";

  /** Deletes an entry. */
  private static final String DELETE_SQL = "DELETE FROM rental_periods WHERE id = ?";

  /**
   * Renews a box: records the period in its history and moves the box's
   * rental end date to the end of the period.
   *
   * @param mailboxId the id of the box being renewed
   * @param period the period paid for; its mailbox id is ignored
   * @throws SQLException if the renewal can't be saved; nothing is changed
   */
  public void renew(int mailboxId, RentalPeriod period) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        insert(conn, mailboxId, period);
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_END_DATE_SQL)) {
          stmt.setString(1, period.getEndDate().toString());
          stmt.setInt(2, mailboxId);
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
   * Returns a box's rental history, oldest first.
   *
   * @param mailboxId the id of the box
   * @return its rental periods
   * @throws SQLException if the query fails
   */
  public List<RentalPeriod> findForMailbox(int mailboxId) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SELECT_FOR_MAILBOX_SQL)) {
      stmt.setInt(1, mailboxId);
      return read(stmt);
    }
  }

  /**
   * Returns the entries recorded from one day through another, newest first,
   * for every box, open or closed.
   *
   * @param from the first day, inclusive
   * @param to the last day, inclusive
   * @return the entries recorded in that time
   * @throws SQLException if the query fails
   */
  public List<RentalPeriod> findRecordedBetween(LocalDate from, LocalDate to) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SELECT_RECORDED_BETWEEN_SQL)) {
      stmt.setString(1, from.toString());
      stmt.setString(2, to.toString());
      return read(stmt);
    }
  }

  /**
   * Changes an entry, for example one recorded with the wrong amount or
   * dates. If the box's rental still ends on the entry's old end date, so the
   * entry is what set it, the box's end date moves to the new one too. A box
   * whose end date has since been changed some other way, such as on Edit
   * Box or by a later renewal, keeps it.
   *
   * @param period the entry with its new values; its id says which entry,
   *     and its mailbox id is ignored
   * @throws SQLException if the entry doesn't exist or can't be saved;
   *     nothing is changed
   */
  public void update(RentalPeriod period) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        RentalPeriod old;
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_ONE_SQL)) {
          stmt.setInt(1, period.getId());
          var found = read(stmt);
          if (found.isEmpty()) {
            throw new SQLException("The entry is no longer in the rental history.");
          }
          old = found.get(0);
        }
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
          setValues(stmt, 1, period);
          stmt.setInt(7, period.getId());
          stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement(MOVE_END_DATE_SQL)) {
          stmt.setString(1, period.getEndDate().toString());
          stmt.setInt(2, old.getMailboxId());
          stmt.setString(3, old.getEndDate().toString());
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
   * Deletes one entry, for example one recorded by mistake. The box's end
   * date is left as it is.
   *
   * @param id the id of the entry
   * @throws SQLException if the delete fails
   */
  public void delete(int id) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
      stmt.setInt(1, id);
      stmt.executeUpdate();
    }
  }

  /**
   * Inserts an entry using an open connection, so it can be part of a larger
   * transaction.
   *
   * @param conn the connection, which may be in a transaction
   * @param mailboxId the box's id
   * @param period the entry
   * @throws SQLException if it can't be added
   */
  static void insert(Connection conn, int mailboxId, RentalPeriod period) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
      stmt.setInt(1, mailboxId);
      setValues(stmt, 2, period);
      stmt.executeUpdate();
    }
  }

  /**
   * Fills in an entry's six values, from the day it was recorded through its
   * note, in the order of the {@code rental_periods} columns, shared by
   * adding and changing entries.
   *
   * @param stmt the statement to fill in
   * @param first the position of the first value, the day it was recorded
   * @param period the entry
   * @throws SQLException if a value can't be set
   */
  private static void setValues(PreparedStatement stmt, int first, RentalPeriod period) throws SQLException {
    stmt.setString(first, period.getRecordedOn().toString());
    stmt.setString(first + 1, period.getStartDate().toString());
    stmt.setString(first + 2, period.getEndDate().toString());
    if (period.getAmountCents() == null) {
      stmt.setNull(first + 3, Types.INTEGER);
    } else {
      stmt.setLong(first + 3, period.getAmountCents());
    }
    stmt.setString(first + 4, period.getPaymentMethod());
    stmt.setString(first + 5, period.getNote());
  }

  /**
   * Runs a query for entries.
   *
   * @param stmt the query, with its parameters filled in
   * @return the entries, in the query's order
   * @throws SQLException if the query fails
   */
  private static List<RentalPeriod> read(PreparedStatement stmt) throws SQLException {
    var periods = new ArrayList<RentalPeriod>();
    try (ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        // wasNull() reports on the last column read, so check it right away.
        Long amount = rs.getLong("amount_cents");
        if (rs.wasNull()) {
          amount = null;
        }
        periods.add(new RentalPeriod(
            rs.getInt("id"),
            rs.getInt("mailbox_id"),
            LocalDate.parse(rs.getString("recorded_on")),
            LocalDate.parse(rs.getString("start_date")),
            LocalDate.parse(rs.getString("end_date")),
            amount,
            rs.getString("payment_method"),
            rs.getString("note")));
      }
    }
    return periods;
  }

}
