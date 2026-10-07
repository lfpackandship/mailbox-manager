package org.lfps.mailboxes.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.lfps.mailboxes.model.PrintedLabel;

/**
 * Hands out forwarding label numbers and keeps a record of every label
 * printed, in the {@code labels} table.
 *
 * <p>A number is the day it was printed and that day's count, such as
 * "261007-03" for the third label on October 7, 2026, and is never handed out
 * twice. The database won't store the same number twice, and the last number
 * handed out is also kept in a small file next to the database, which
 * restoring a backup doesn't replace; so after restoring an older backup,
 * numbers carry on from the last one printed rather than repeating.
 */
public class LabelRepository {

  /** The file next to the database holding the last number handed out. */
  static final String LAST_NUMBER_FILE = "last-label-number.txt";

  /** The day part of a number, such as 261007. */
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

  /** Records a printed label. */
  private static final String INSERT_SQL =
      "INSERT INTO labels (number, mailbox_id, printed_at, address) VALUES (?, ?, ?, ?)";

  /** Reads the numbers handed out on a day. */
  private static final String NUMBERS_FOR_DAY_SQL = "SELECT number FROM labels WHERE number LIKE ?";

  /** Reads every printed label, newest first. */
  private static final String SELECT_ALL_SQL =
      "SELECT number, mailbox_id, printed_at, address FROM labels ORDER BY printed_at DESC, id DESC";

  /** Deletes a label's record. */
  private static final String DELETE_SQL = "DELETE FROM labels WHERE number = ?";

  /** Makes a repository for the app's database. */
  public LabelRepository() {
  }

  /**
   * Returns the number the next label printed today will most likely get,
   * for showing in a preview. Nothing is handed out.
   *
   * @param today today's date
   * @return the number
   * @throws SQLException if the numbers already handed out can't be read
   */
  public String peekNextNumber(LocalDate today) throws SQLException {
    try (Connection conn = Database.connect()) {
      return nextNumber(today, numbersFor(conn, today), readLastNumber());
    }
  }

  /**
   * Hands out the next number and records a label printed with it. Called
   * just before the label is printed, so the number can go on it; if it then
   * isn't printed, call {@link #delete} to take the record back.
   *
   * @param mailboxId the id of the box it's for
   * @param address who and where it's addressed to, one line each
   * @param now the time it's printed
   * @return the record, with its number
   * @throws SQLException if it can't be recorded; nothing is recorded
   */
  public PrintedLabel record(int mailboxId, List<String> address, LocalDateTime now) throws SQLException {
    var text = String.join("\n", address);
    try (Connection conn = Database.connect()) {
      conn.setAutoCommit(false);
      try {
        var number = nextNumber(now.toLocalDate(), numbersFor(conn, now.toLocalDate()), readLastNumber());
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
          stmt.setString(1, number);
          stmt.setInt(2, mailboxId);
          stmt.setString(3, now.withNano(0).toString());
          stmt.setString(4, text);
          stmt.executeUpdate();
        }
        conn.commit();
        writeLastNumber(number);
        return new PrintedLabel(number, mailboxId, now.withNano(0), text);
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Returns every printed label, newest first.
   *
   * @return the labels
   * @throws SQLException if they can't be read
   */
  public List<PrintedLabel> findAll() throws SQLException {
    var labels = new ArrayList<PrintedLabel>();
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
        ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        labels.add(new PrintedLabel(rs.getString("number"), rs.getInt("mailbox_id"),
            LocalDateTime.parse(rs.getString("printed_at")), rs.getString("address")));
      }
    }
    return labels;
  }

  /**
   * Deletes a label's record, for a label that wasn't printed after all.
   * Its number isn't handed out again.
   *
   * @param number the label's number
   * @throws SQLException if it can't be deleted
   */
  public void delete(String number) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
      stmt.setString(1, number);
      stmt.executeUpdate();
    }
  }

  /**
   * Works out the next number for a day: one more than the highest count
   * already used that day, in the database or the last number handed out.
   *
   * @param day the day
   * @param usedThatDay the numbers in the database for that day
   * @param lastNumber the last number handed out, or {@code null} if none
   * @return the number, such as "261007-03"; the count has at least two digits
   */
  static String nextNumber(LocalDate day, Collection<String> usedThatDay, String lastNumber) {
    var prefix = day.format(DAY) + "-";
    var highest = 0;
    var seen = new ArrayList<String>(usedThatDay);
    if (lastNumber != null) {
      seen.add(lastNumber);
    }
    for (var number : seen) {
      if (number.startsWith(prefix)) {
        try {
          highest = Math.max(highest, Integer.parseInt(number.substring(prefix.length())));
        } catch (NumberFormatException notACount) {
          // Not one of ours; ignore it.
        }
      }
    }
    return prefix + String.format("%02d", highest + 1);
  }

  /**
   * Reads the numbers in the database for a day.
   *
   * @param conn an open connection
   * @param day the day
   * @return the numbers
   * @throws SQLException if they can't be read
   */
  private static List<String> numbersFor(Connection conn, LocalDate day) throws SQLException {
    var numbers = new ArrayList<String>();
    try (PreparedStatement stmt = conn.prepareStatement(NUMBERS_FOR_DAY_SQL)) {
      stmt.setString(1, day.format(DAY) + "-%");
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          numbers.add(rs.getString(1));
        }
      }
    }
    return numbers;
  }

  /**
   * Returns where the last number handed out is kept.
   *
   * @return the file, next to the database
   */
  private static Path lastNumberFile() {
    return Database.dataDir().resolve(LAST_NUMBER_FILE);
  }

  /**
   * Reads the last number handed out.
   *
   * @return the number, or {@code null} if there's no file yet or it can't
   *     be read, in which case the database alone decides
   */
  private static String readLastNumber() {
    try {
      var path = lastNumberFile();
      return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).strip() : null;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Saves the last number handed out. If it can't be saved, the database
   * still keeps numbers from repeating unless an older backup is restored.
   *
   * @param number the number
   */
  private static void writeLastNumber(String number) {
    try {
      Files.writeString(lastNumberFile(), number, StandardCharsets.UTF_8);
    } catch (IOException e) {
      // See above: the database still has the record.
    }
  }

}
