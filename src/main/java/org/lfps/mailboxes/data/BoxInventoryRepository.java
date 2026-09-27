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

  private static final String SELECT_ALL_SQL = "SELECT box_number, size FROM box_inventory";

  private static final String INSERT_SQL = "INSERT OR IGNORE INTO box_inventory (box_number, size) VALUES (?, ?)";

  private static final String UPDATE_SIZE_SQL = "UPDATE box_inventory SET size = ? WHERE box_number = ?";

  private static final String DELETE_SQL = "DELETE FROM box_inventory WHERE box_number = ?";

  private static final String CONTAINS_SQL = "SELECT 1 FROM box_inventory WHERE box_number = ? LIMIT 1";

  private static final String COUNT_SQL = "SELECT COUNT(*) FROM box_inventory";

  private static final String SIZE_OF_SQL = "SELECT size FROM box_inventory WHERE box_number = ?";

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
        boxes.add(new InventoryBox(rs.getString("box_number"), rs.getString("size")));
      }
    }
    boxes.sort((a, b) -> BoxNumbers.ORDER.compare(a.getBoxNumber(), b.getBoxNumber()));
    return boxes;
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

  private interface Binder {
    void bind(PreparedStatement stmt, String boxNumber) throws SQLException;
  }

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
