package org.lfps.mailboxes.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps the price of renting a box of each size for each rental length,
 * stored in the {@code prices} table. The {@link #DEFAULT_SIZE default} price
 * covers boxes with no size, or a size with no price of its own. Also keeps
 * what each size measures, in the {@code box_sizes} table, for the price
 * sheet.
 *
 * <p>A price change can be scheduled to start on a later date, in the
 * {@code scheduled_prices} table. On that date its prices replace the current
 * ones, the first time any prices are read, so it takes effect even if the
 * app is left open overnight. There's only one change at a time.
 */
public class PriceRepository {

  /** Makes a repository for the app's database. */
  public PriceRepository() {
  }

  /** A price change that starts on a later date. */
  public static final class PriceChange {

    /** The day the new prices start. */
    public final LocalDate startsOn;

    /**
     * The new prices in cents, keyed by {@link #key(String, int)}. A key
     * with a {@code null} price means that price is cleared.
     */
    public final Map<String, Long> prices;

    /**
     * Makes a price change.
     *
     * @param startsOn the day the new prices start
     * @param prices the new prices, keyed by {@link #key(String, int)}; a
     *     {@code null} price clears it
     */
    PriceChange(LocalDate startsOn, Map<String, Long> prices) {
      this.startsOn = startsOn;
      this.prices = prices;
    }

    /**
     * Returns what prices will be once the change starts.
     *
     * @param current today's prices, keyed by {@link #key(String, int)}
     * @return today's prices with the change's in place of them
     */
    public Map<String, Long> applyTo(Map<String, Long> current) {
      var after = new HashMap<>(current);
      for (var entry : prices.entrySet()) {
        if (entry.getValue() == null) {
          after.remove(entry.getKey());
        } else {
          after.put(entry.getKey(), entry.getValue());
        }
      }
      return after;
    }

  }

  /** The size the default prices are stored under. */
  public static final String DEFAULT_SIZE = "";

  /** Reads every price. */
  private static final String SELECT_ALL_SQL = "SELECT size, months, amount_cents FROM prices";

  /** Reads one size's price for a rental length. */
  private static final String SELECT_SQL = "SELECT amount_cents FROM prices WHERE size = ? AND months = ?";

  /** Saves a price, replacing an earlier one. */
  private static final String UPSERT_SQL = "INSERT INTO prices (size, months, amount_cents) VALUES (?, ?, ?) "
      + "ON CONFLICT(size, months) DO UPDATE SET amount_cents = excluded.amount_cents";

  /** Clears a price. */
  private static final String DELETE_SQL = "DELETE FROM prices WHERE size = ? AND months = ?";

  /** Reads the price change's prices and start date. */
  private static final String SELECT_SCHEDULED_SQL =
      "SELECT size, months, amount_cents, starts_on FROM scheduled_prices";

  /** Adds a price to the price change. */
  private static final String INSERT_SCHEDULED_SQL =
      "INSERT INTO scheduled_prices (size, months, amount_cents, starts_on) VALUES (?, ?, ?, ?)";

  /** Reads what each size measures. */
  private static final String SELECT_DESCRIPTIONS_SQL = "SELECT size, description FROM box_sizes";

  /** Saves what a size measures, replacing an earlier description. */
  private static final String UPSERT_DESCRIPTION_SQL = "INSERT INTO box_sizes (size, description) VALUES (?, ?) "
      + "ON CONFLICT(size) DO UPDATE SET description = excluded.description";

  /** Clears what a size measures. */
  private static final String DELETE_DESCRIPTION_SQL = "DELETE FROM box_sizes WHERE size = ?";

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
    applyDueChange(LocalDate.now());
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
    applyDueChange(LocalDate.now());
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
   * Returns the price change scheduled to start after today, if there is one.
   *
   * @return the change, or {@code null} if none is scheduled
   * @throws SQLException if the query fails
   */
  public PriceChange findChange() throws SQLException {
    applyDueChange(LocalDate.now());
    LocalDate startsOn = null;
    var prices = new HashMap<String, Long>();
    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_SCHEDULED_SQL)) {
      while (rs.next()) {
        var amount = rs.getLong("amount_cents");
        prices.put(key(rs.getString("size"), rs.getInt("months")), rs.wasNull() ? null : amount);
        startsOn = LocalDate.parse(rs.getString("starts_on"));
      }
    }
    return startsOn == null ? null : new PriceChange(startsOn, prices);
  }

  /**
   * Schedules a price change, replacing any already scheduled.
   *
   * @param startsOn the day the new prices start
   * @param prices the new price in cents for each {@link #key(String, int)},
   *     or {@code null} to clear that price; prices not given stay as they are
   * @throws SQLException if it can't be saved; nothing is changed
   */
  public void scheduleChange(LocalDate startsOn, Map<String, Long> prices) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try (Statement clear = conn.createStatement();
          PreparedStatement insert = conn.prepareStatement(INSERT_SCHEDULED_SQL)) {
        clear.execute("DELETE FROM scheduled_prices");
        for (var entry : prices.entrySet()) {
          var separator = entry.getKey().lastIndexOf('|');
          insert.setString(1, entry.getKey().substring(0, separator));
          insert.setInt(2, Integer.parseInt(entry.getKey().substring(separator + 1)));
          if (entry.getValue() == null) {
            insert.setNull(3, Types.INTEGER);
          } else {
            insert.setLong(3, entry.getValue());
          }
          insert.setString(4, startsOn.toString());
          insert.executeUpdate();
        }
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Cancels the scheduled price change, if there is one.
   *
   * @throws SQLException if it can't be cancelled
   */
  public void cancelChange() throws SQLException {
    try (Connection conn = Database.connect(); Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM scheduled_prices");
    }
  }

  /**
   * Makes the scheduled price change the current prices if it has started.
   *
   * @param today the date to check against
   * @return {@code true} if the prices changed
   * @throws SQLException if they can't be changed; nothing is changed
   */
  public boolean applyDueChange(LocalDate today) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try (Statement stmt = conn.createStatement()) {
        try (ResultSet rs = stmt.executeQuery("SELECT MIN(starts_on) FROM scheduled_prices")) {
          var startsOn = rs.next() ? rs.getString(1) : null;
          if (startsOn == null || LocalDate.parse(startsOn).isAfter(today)) {
            conn.rollback();
            return false;
          }
        }
        stmt.execute("DELETE FROM prices WHERE (size, months) IN "
            + "(SELECT size, months FROM scheduled_prices WHERE amount_cents IS NULL)");
        stmt.execute("INSERT INTO prices (size, months, amount_cents) "
            + "SELECT size, months, amount_cents FROM scheduled_prices WHERE amount_cents IS NOT NULL "
            + "ON CONFLICT(size, months) DO UPDATE SET amount_cents = excluded.amount_cents");
        stmt.execute("DELETE FROM scheduled_prices");
        conn.commit();
        return true;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Returns what each box size measures.
   *
   * @return the description of each size, such as 3¾" x 5" x 14", keyed by
   *     the size in lower case
   * @throws SQLException if the query fails
   */
  public Map<String, String> findDescriptions() throws SQLException {
    var descriptions = new HashMap<String, String>();
    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_DESCRIPTIONS_SQL)) {
      while (rs.next()) {
        descriptions.put(normalize(rs.getString("size")), rs.getString("description"));
      }
    }
    return descriptions;
  }

  /**
   * Saves what box sizes measure, replacing earlier descriptions. Sizes not
   * given are left as they are.
   *
   * @param descriptions the description of each size, ignoring letter case;
   *     a blank one clears that size's description
   * @throws SQLException if the descriptions can't be saved; none are changed
   */
  public void saveDescriptions(Map<String, String> descriptions) throws SQLException {
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try (PreparedStatement upsert = conn.prepareStatement(UPSERT_DESCRIPTION_SQL);
          PreparedStatement delete = conn.prepareStatement(DELETE_DESCRIPTION_SQL)) {
        for (var entry : descriptions.entrySet()) {
          var size = normalize(entry.getKey());
          var description = entry.getValue() == null ? "" : entry.getValue().strip();
          if (description.isEmpty()) {
            delete.setString(1, size);
            delete.executeUpdate();
          } else {
            upsert.setString(1, size);
            upsert.setString(2, description);
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
   * Puts sizes in order of price, cheapest first, as smaller boxes cost
   * less: by each size's lowest price for any rental length. Sizes with no
   * price come last, in alphabetical order, as do sizes that cost the same.
   *
   * @param sizes the sizes
   * @param prices the prices, as returned by {@link #findAll()}
   * @return the sizes in order
   */
  public static List<String> cheapestFirst(List<String> sizes, Map<String, Long> prices) {
    var lowest = new HashMap<String, Long>();
    for (var entry : prices.entrySet()) {
      var size = entry.getKey().substring(0, entry.getKey().lastIndexOf('|'));
      lowest.merge(size, entry.getValue(), Math::min);
    }
    var ordered = new ArrayList<>(sizes);
    ordered.sort(Comparator.comparing((String size) -> lowest.getOrDefault(normalize(size), Long.MAX_VALUE))
        .thenComparing(String.CASE_INSENSITIVE_ORDER));
    return ordered;
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

  /**
   * Runs the query for one price.
   *
   * @param stmt the query, {@link #SELECT_SQL}
   * @param size the size, already normalized
   * @param months the rental length
   * @return the price in cents, or {@code null} if there's none
   * @throws SQLException if the query fails
   */
  private static Long find(PreparedStatement stmt, String size, int months) throws SQLException {
    stmt.setString(1, size);
    stmt.setInt(2, months);
    try (ResultSet rs = stmt.executeQuery()) {
      return rs.next() ? rs.getLong("amount_cents") : null;
    }
  }

  /**
   * Puts a size in the form prices are stored under: trimmed and in lower case.
   *
   * @param size the size, or {@code null}
   * @return the size, or {@link #DEFAULT_SIZE} for {@code null}
   */
  private static String normalize(String size) {
    return size == null ? DEFAULT_SIZE : size.trim().toLowerCase(Locale.ROOT);
  }

}
