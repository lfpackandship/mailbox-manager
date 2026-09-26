package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.lfps.mailboxes.model.Mailbox;

/**
 * Provides CRUD access to mailboxes and their alternate business names,
 * stored across the {@code mailboxes} and {@code business_names} tables.
 */
public class MailboxRepository {

  private static final String INSERT_SQL = "INSERT INTO mailboxes "
      + "(first_name, last_name, business_title, box_number, box_name, phone, email, end_date) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String SELECT_ALL_SQL = "SELECT id, first_name, last_name, "
      + "business_title, box_number, box_name, phone, email, end_date "
      + "FROM mailboxes ORDER BY box_number";

  private static final String UPDATE_SQL = "UPDATE mailboxes SET first_name = ?, last_name = ?, "
      + "business_title = ?, box_number = ?, box_name = ?, phone = ?, email = ?, end_date = ? "
      + "WHERE id = ?";

  private static final String DELETE_SQL = "DELETE FROM mailboxes WHERE id = ?";

  private static final String INSERT_BUSINESS_NAME_SQL =
      "INSERT INTO business_names (mailbox_id, name) VALUES (?, ?)";

  private static final String SELECT_BUSINESS_NAMES_SQL =
      "SELECT name FROM business_names WHERE mailbox_id = ? ORDER BY id";

  private static final String DELETE_BUSINESS_NAMES_SQL =
      "DELETE FROM business_names WHERE mailbox_id = ?";

  /**
   * Inserts a new mailbox along with its alternate business names.
   *
   * @param mailbox the mailbox to persist; its id is ignored
   * @throws SQLException if the insert fails
   */
  public void insert(Mailbox mailbox) throws SQLException {
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
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Returns every mailbox, ordered by box number, with its alternate
   * business names loaded.
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
            rs.getString("end_date") == null ? null : LocalDate.parse(rs.getString("end_date"))));
      }
    }

    return mailboxes;
  }

  /**
   * Updates an existing mailbox and replaces its alternate business names.
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
          stmt.setInt(9, mailbox.getId());
          stmt.executeUpdate();
        }
        deleteBusinessNames(conn, mailbox.getId());
        insertBusinessNames(conn, mailbox.getId(), mailbox.getAlternateBusinessNames());
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Deletes a mailbox and its alternate business names.
   *
   * @param id the id of the mailbox to delete
   * @throws SQLException if the delete fails
   */
  public void delete(int id) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        deleteBusinessNames(conn, id);
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

  private void bindMailboxFields(PreparedStatement stmt, Mailbox mailbox) throws SQLException {
    stmt.setString(1, mailbox.getFirstName());
    stmt.setString(2, mailbox.getLastName());
    stmt.setString(3, mailbox.getBusinessTitle());
    stmt.setString(4, mailbox.getBoxNumber());
    stmt.setString(5, mailbox.getBoxName());
    stmt.setString(6, mailbox.getPhone());
    stmt.setString(7, mailbox.getEmail());
    stmt.setString(8, mailbox.getEndDate() == null ? null : mailbox.getEndDate().toString());
  }

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

  private void deleteBusinessNames(Connection conn, int mailboxId) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(DELETE_BUSINESS_NAMES_SQL)) {
      stmt.setInt(1, mailboxId);
      stmt.executeUpdate();
    }
  }

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
