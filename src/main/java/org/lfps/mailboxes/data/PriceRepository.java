package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps the price of renting a box of each size for each rental length,
 * stored in the {@code prices} table. The {@link #DEFAULT_SIZE default} price
 * covers boxes with no size, or a size with no price of its own.
 */
public class PriceRepository {

  /** The size the default prices are stored under. */
  public static final String DEFAULT_SIZE = "";

  private static final String SELECT_ALL_SQL = "SELECT size, months, amount_cents FROM prices";

  private static final String SELECT_SQL = "SELECT amount_cents FROM prices WHERE size = ? AND months = ?";

  private static final String UPSERT_SQL = "INSERT INTO prices (size, months, amount_cents) VALUES (?, ?, ?) "
      + "ON CONFLICT(size, months) DO UPDATE SET amount_cents = excluded.amount_cents";

  private static final String DELETE_SQL = "DELETE FROM prices WHERE size = ? AND months = ?";

  /**
   * Returns the price of renting a box of a size for a number of months,
   * falling back to the default price if that size has none.
   *
   * @param size the box's size, ignoring letter case, or {@code null} if it has none
   * @param months the rental length
   * @return the price in cents, or {@code null} if neither the size nor the
   *     default has a price for that length
   * @throws SQLException if the query fails
   */
  public Long priceFor(String size, int months) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {
      var sizeKey = normalize(size);
      var price = find(stmt, sizeKey, months);
      if (price == null && !sizeKey.isEmpty()) {
        price = find(stmt, DEFAULT_SIZE, months);
      }
      return price;
    }
  }

  /**
   * Returns every price, keyed by {@link #key(String, int)}.
   *
   * @return the prices in cents
   * @throws SQLException if the query fails
   */
  public Map<String, Long> findAll() throws SQLException {
    var prices = new HashMap<String, Long>();
    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_ALL_SQL)) {
      while (rs.next()) {
        prices.put(key(rs.getString("size"), rs.getInt("months")), rs.getLong("amount_cents"));
      }
    }
    return prices;
  }

  /**
   * Saves prices, replacing earlier ones for the same size and length. Prices
   * not given are left as they are.
   *
   * @param prices the price in cents for each {@link #key(String, int)}, or
   *     {@code null} to clear that price
   * @throws SQLException if the prices can't be saved; none are changed
   */
  public void save(Map<String, Long> prices) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try (PreparedStatement upsert = conn.prepareStatement(UPSERT_SQL);
          PreparedStatement delete = conn.prepareStatement(DELETE_SQL)) {
        for (var entry : prices.entrySet()) {
          var separator = entry.getKey().lastIndexOf('|');
          var size = entry.getKey().substring(0, separator);
          var months = Integer.parseInt(entry.getKey().substring(separator + 1));
          if (entry.getValue() == null) {
            delete.setString(1, size);
            delete.setInt(2, months);
            delete.executeUpdate();
          } else {
            upsert.setString(1, size);
            upsert.setInt(2, months);
            upsert.setLong(3, entry.getValue());
            upsert.executeUpdate();
          }
        }
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Returns the key a price is stored under in the maps passed to and from
   * this repository.
   *
   * @param size the size, ignoring letter case, or {@link #DEFAULT_SIZE}
   * @param months the rental length
   * @return the key
   */
  public static String key(String size, int months) {
    return normalize(size) + "|" + months;
  }

  private static Long find(PreparedStatement stmt, String size, int months) throws SQLException {
    stmt.setString(1, size);
    stmt.setInt(2, months);
    try (ResultSet rs = stmt.executeQuery()) {
      return rs.next() ? rs.getLong("amount_cents") : null;
    }
  }

  private static String normalize(String size) {
    return size == null ? DEFAULT_SIZE : size.trim().toLowerCase(Locale.ROOT);
  }

}
