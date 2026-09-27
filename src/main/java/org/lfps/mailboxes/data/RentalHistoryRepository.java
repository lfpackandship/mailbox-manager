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

  private static final String INSERT_SQL = "INSERT INTO rental_periods "
      + "(mailbox_id, recorded_on, start_date, end_date, amount_cents, payment_method, note) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?)";

  private static final String COLUMNS =
      "SELECT id, mailbox_id, recorded_on, start_date, end_date, amount_cents, payment_method, note "
      + "FROM rental_periods ";

  private static final String SELECT_FOR_MAILBOX_SQL = COLUMNS + "WHERE mailbox_id = ? ORDER BY start_date, id";

  private static final String SELECT_RECORDED_BETWEEN_SQL = COLUMNS
      + "WHERE recorded_on BETWEEN ? AND ? ORDER BY recorded_on DESC, id DESC";

  private static final String UPDATE_END_DATE_SQL = "UPDATE mailboxes SET end_date = ? WHERE id = ?";

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
   */
  static void insert(Connection conn, int mailboxId, RentalPeriod period) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
      stmt.setInt(1, mailboxId);
      stmt.setString(2, period.getRecordedOn().toString());
      stmt.setString(3, period.getStartDate().toString());
      stmt.setString(4, period.getEndDate().toString());
      if (period.getAmountCents() == null) {
        stmt.setNull(5, Types.INTEGER);
      } else {
        stmt.setLong(5, period.getAmountCents());
      }
      stmt.setString(6, period.getPaymentMethod());
      stmt.setString(7, period.getNote());
      stmt.executeUpdate();
    }
  }

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
