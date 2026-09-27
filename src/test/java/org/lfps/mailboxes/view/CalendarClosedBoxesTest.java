package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Checks that closed boxes are left off the Calendar.
 */
class CalendarClosedBoxesTest {

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @AfterEach
  void closeAllWindows() {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void onlyOpenBoxesAreShown() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    var day = LocalDate.of(2026, 3, 15);
    var mailboxes = new MailboxRepository();
    mailboxes.insert(new Mailbox(0, "Ada", "Open", null, "1", null, "5551000001", null, null, day, null));
    mailboxes.insert(new Mailbox(0, "Ada", "Closed", null, "2", null, "5551000002", null, null, day, null,
        null, day.minusDays(1)));

    var stage = FxTestSupport.call(() -> {
      var s = new Stage();
      CalendarView.show(s, YearMonth.of(2026, 3));
      return s;
    });

    var entries = FxTestSupport.call(() -> stage.getScene().getRoot().lookupAll(".label").stream()
        .map(node -> ((Label) node).getText())
        .filter(text -> text.startsWith("Box "))
        .collect(Collectors.toList()));
    assertEquals(List.of("Box 1"), entries);
  }

}
