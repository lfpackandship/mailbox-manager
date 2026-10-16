package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.lfps.mailboxes.model.InventoryBox;
import org.lfps.mailboxes.util.BoxNumbers;

/**
 * Keeps the box inventory, the list of physical boxes that can be rented,
 * stored in the {@code box_inventory} table. An empty inventory means it
 * hasn't been set up, and box numbers aren't checked against it.
 */
public class BoxInventoryRepository {

  /** Makes a repository for the app's database. */
  public BoxInventoryRepository() {
  }

  /** Reads every box in the inventory. */
  private static final String SELECT_ALL_SQL =
      "SELECT box_number, size, out_of_service, out_of_service_reason FROM box_inventory";

  /** Reads one box. */
  private static final String SELECT_ONE_SQL = SELECT_ALL_SQL + " WHERE box_number = ?";

  /** Marks a box out of service, or back in service. */
  private static final String SET_OUT_OF_SERVICE_SQL =
      "UPDATE box_inventory SET out_of_service = ?, out_of_service_reason = ? WHERE box_number = ?";

  /** Adds a box, unless one with that number is already there. */
  private static final String INSERT_SQL = "INSERT OR IGNORE INTO box_inventory (box_number, size) VALUES (?, ?)";

  /** Changes a box's size. */
  private static final String UPDATE_SIZE_SQL = "UPDATE box_inventory SET size = ? WHERE box_number = ?";

  /** Removes a box. */
  private static final String DELETE_SQL = "DELETE FROM box_inventory WHERE box_number = ?";

  /** Checks whether a box is in the inventory. */
  private static final String CONTAINS_SQL = "SELECT 1 FROM box_inventory WHERE box_number = ? LIMIT 1";

  /** Counts the boxes in the inventory. */
  private static final String COUNT_SQL = "SELECT COUNT(*) FROM box_inventory";

  /** Reads a box's size. */
  private static final String SIZE_OF_SQL = "SELECT size FROM box_inventory WHERE box_number = ?";

  /** Reads the different sizes, counting sizes that differ only in letter case as one. */
  private static final String SIZES_SQL =
      "SELECT MIN(size) AS size FROM box_inventory WHERE size IS NOT NULL GROUP BY size COLLATE NOCASE";

  /**
   * Returns every box in the inventory, ordered by box number (see
   * {@link BoxNumbers#ORDER}).
   *
   * @return the inventory
   * @throws SQLException if the query fails
   */
  public List<InventoryBox> findAll() throws SQLException {
    var boxes = new ArrayList<InventoryBox>();
    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_ALL_SQL)) {
      while (rs.next()) {
        boxes.add(inventoryBox(rs));
      }
    }
    boxes.sort((a, b) -> BoxNumbers.ORDER.compare(a.getBoxNumber(), b.getBoxNumber()));
    return boxes;
  }

  /**
   * Returns a box in the inventory, ignoring surrounding whitespace and
   * letter case in the box number.
   *
   * @param boxNumber the box number to look up
   * @return the box, or {@code null} if it isn't in the inventory
   * @throws SQLException if the query fails
   */
  public InventoryBox find(String boxNumber) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SELECT_ONE_SQL)) {
      stmt.setString(1, boxNumber.trim());
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? inventoryBox(rs) : null;
      }
    }
  }

  /**
   * Reads the box in the current row of a query.
   *
   * @param rs the query, on a row from {@link #SELECT_ALL_SQL}
   * @return the box
   * @throws SQLException if a column can't be read
   */
  private static InventoryBox inventoryBox(ResultSet rs) throws SQLException {
    return new InventoryBox(rs.getString("box_number"), rs.getString("size"), rs.getInt("out_of_service") != 0,
        rs.getString("out_of_service_reason"));
  }

  /**
   * Marks boxes out of service, such as with a broken lock, so Choose… on
   * Add New Box doesn't offer them and they aren't counted as empty. Anyone
   * renting one keeps it.
   *
   * @param boxNumbers the boxes
   * @param reason why, such as "broken lock", or blank/null for no reason;
   *     it replaces any reason already recorded
   * @throws SQLException if they can't be marked; none are changed
   */
  public void setOutOfService(List<String> boxNumbers, String reason) throws SQLException {
    var reasonValue = reason == null || reason.isBlank() ? null : reason.trim();
    inTransaction(SET_OUT_OF_SERVICE_SQL, boxNumbers, (stmt, boxNumber) -> {
      stmt.setInt(1, 1);
      stmt.setString(2, reasonValue);
      stmt.setString(3, boxNumber.trim());
    });
  }

  /**
   * Puts boxes back in service, forgetting why they were out of it.
   *
   * @param boxNumbers the boxes
   * @throws SQLException if they can't be changed; none are changed
   */
  public void setInService(List<String> boxNumbers) throws SQLException {
    inTransaction(SET_OUT_OF_SERVICE_SQL, boxNumbers, (stmt, boxNumber) -> {
      stmt.setInt(1, 0);
      stmt.setString(2, null);
      stmt.setString(3, boxNumber.trim());
    });
  }

  /**
   * Adds boxes to the inventory. Boxes already in it, ignoring letter case,
   * are left as they are.
   *
   * @param boxNumbers the box numbers to add
   * @param size the size of all of them, or blank/null if not recorded
   * @return how many boxes were added
   * @throws SQLException if the boxes can't be added; none are added
   */
  public int add(List<String> boxNumbers, String size) throws SQLException {
    var sizeValue = new InventoryBox("", size).getSize();
    return inTransaction(INSERT_SQL, boxNumbers, (stmt, boxNumber) -> {
      stmt.setString(1, boxNumber.trim());
      stmt.setString(2, sizeValue);
    });
  }

  /**
   * Sets the size of boxes in the inventory.
   *
   * @param boxNumbers the boxes to change
   * @param size the new size, or blank/null to clear it
   * @throws SQLException if the sizes can't be saved; none are changed
   */
  public void setSize(List<String> boxNumbers, String size) throws SQLException {
    var sizeValue = new InventoryBox("", size).getSize();
    inTransaction(UPDATE_SIZE_SQL, boxNumbers, (stmt, boxNumber) -> {
      stmt.setString(1, sizeValue);
      stmt.setString(2, boxNumber.trim());
    });
  }

  /**
   * Removes boxes from the inventory. This doesn't affect anyone renting
   * them.
   *
   * @param boxNumbers the boxes to remove
   * @throws SQLException if the boxes can't be removed; none are removed
   */
  public void remove(List<String> boxNumbers) throws SQLException {
    inTransaction(DELETE_SQL, boxNumbers, (stmt, boxNumber) -> stmt.setString(1, boxNumber.trim()));
  }

  /**
   * Checks whether a box is in the inventory, ignoring surrounding
   * whitespace and letter case.
   *
   * @param boxNumber the box number to look for
   * @return {@code true} if it's in the inventory
   * @throws SQLException if the query fails
   */
  public boolean contains(String boxNumber) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(CONTAINS_SQL)) {
      stmt.setString(1, boxNumber.trim());
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    }
  }

  /**
   * Returns the size of a box in the inventory, ignoring surrounding
   * whitespace and letter case in the box number.
   *
   * @param boxNumber the box number to look up
   * @return the size, or {@code null} if the box has no size or isn't in the
   *     inventory
   * @throws SQLException if the query fails
   */
  public String sizeOf(String boxNumber) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SIZE_OF_SQL)) {
      stmt.setString(1, boxNumber.trim());
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getString("size") : null;
      }
    }
  }

  /**
   * Returns the different sizes recorded in the inventory, ignoring letter
   * case, in alphabetical order.
   *
   * @return the sizes
   * @throws SQLException if the query fails
   */
  public List<String> sizes() throws SQLException {
    var sizes = new ArrayList<String>();
    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SIZES_SQL)) {
      while (rs.next()) {
        sizes.add(rs.getString("size"));
      }
    }
    sizes.sort(String.CASE_INSENSITIVE_ORDER);
    return sizes;
  }

  /**
   * Checks whether the inventory has been set up.
   *
   * @return {@code true} if there are no boxes in the inventory
   * @throws SQLException if the query fails
   */
  public boolean isEmpty() throws SQLException {
    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(COUNT_SQL)) {
      rs.next();
      return rs.getInt(1) == 0;
    }
  }

  /** Fills in a statement's parameters for one box. */
  private interface Binder {
    /**
     * Fills in the parameters.
     *
     * @param stmt the statement
     * @param boxNumber the box's number
     * @throws SQLException if a parameter can't be set
     */
    void bind(PreparedStatement stmt, String boxNumber) throws SQLException;
  }

  /**
   * Runs a statement once for each box, all or nothing.
   *
   * @param sql the statement
   * @param boxNumbers the boxes' numbers
   * @param binder fills in the statement's parameters for each box
   * @return how many rows were changed
   * @throws SQLException if a statement fails; nothing is changed
   */
  private static int inTransaction(String sql, List<String> boxNumbers, Binder binder) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        var changed = 0;
        for (var boxNumber : boxNumbers) {
          binder.bind(stmt, boxNumber);
          changed += stmt.executeUpdate();
        }
        conn.commit();
        return changed;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

}
