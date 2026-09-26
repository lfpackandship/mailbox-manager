package org.lfps.mailboxes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MailboxRepository {

  private static final String INSERT_SQL = "INSERT INTO mailboxes "
      + "(first_name, last_name, business_title, box_number, phone, email) "
      + "VALUES (?, ?, ?, ?, ?, ?)";

  private static final String SELECT_ALL_SQL = "SELECT id, first_name, last_name, "
      + "business_title, box_number, phone, email FROM mailboxes ORDER BY box_number";

  public void insert(Mailbox mailbox) throws SQLException {
    try (Connection conn = Database.connect();
        PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
      stmt.setString(1, mailbox.getFirstName());
      stmt.setString(2, mailbox.getLastName());
      stmt.setString(3, mailbox.getBusinessTitle());
      stmt.setString(4, mailbox.getBoxNumber());
      stmt.setString(5, mailbox.getPhone());
      stmt.setString(6, mailbox.getEmail());
      stmt.executeUpdate();
    }
  }

  public List<Mailbox> findAll() throws SQLException {
    var mailboxes = new ArrayList<Mailbox>();

    try (Connection conn = Database.connect();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_ALL_SQL)) {
      while (rs.next()) {
        mailboxes.add(new Mailbox(
            rs.getInt("id"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("business_title"),
            rs.getString("box_number"),
            rs.getString("phone"),
            rs.getString("email")));
      }
    }

    return mailboxes;
  }

}
