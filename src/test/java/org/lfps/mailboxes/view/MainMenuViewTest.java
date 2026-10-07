package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for the bubbles on the main menu's Renewals button counting the boxes
 * past due and due soon, and the sentence shown when pointing at the button.
 */
class MainMenuViewTest {

  private static final LocalDate TODAY = LocalDate.now();

  private final MailboxRepository mailboxes = new MailboxRepository();

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
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
  void describesWhatsDue() {
    assertEquals("", MainMenuView.dueSummary(0, 0, 30));
    assertEquals("1 box is past due.", MainMenuView.dueSummary(1, 0, 30));
    assertEquals("3 boxes are past due.", MainMenuView.dueSummary(3, 0, 30));
    assertEquals("1 box ends in the next 30 days.", MainMenuView.dueSummary(0, 1, 30));
    assertEquals("2 boxes end in the next 30 days.", MainMenuView.dueSummary(0, 2, 30));
    assertEquals("2 boxes are past due, and 5 end in the next 30 days.", MainMenuView.dueSummary(2, 5, 30));
    assertEquals("1 box is past due, and 1 ends by tomorrow.", MainMenuView.dueSummary(1, 1, 1));
    assertEquals("4 boxes end today.", MainMenuView.dueSummary(0, 4, 0));
  }

  @Test
  void theRenewalsBubblesCountTheBoxesPastDueAndDueSoon() throws SQLException {
    new SettingsRepository().put(Setting.RENEWAL_WINDOW_DAYS, "10");
    mailboxes.insert(box("1", TODAY.minusDays(3), null));
    mailboxes.insert(box("2", TODAY.plusDays(10), null));
    mailboxes.insert(box("3", TODAY.plusDays(5), null));
    mailboxes.insert(box("4", TODAY.plusDays(11), null));
    mailboxes.insert(box("5", TODAY.minusDays(3), TODAY));
    var stage = showMenu();

    assertEquals("1", FxTestSupport.call(() -> badge(stage, "pastDueBadge").getText()));
    assertEquals("2", FxTestSupport.call(() -> badge(stage, "dueSoonBadge").getText()));
    assertEquals("1 box is past due, and 2 end in the next 10 days.",
        FxTestSupport.call(() -> renewalsButton(stage).getTooltip().getText()));
  }

  @Test
  void eachBubbleIsLeftOutWhenItsCountIsZero() throws SQLException {
    var id = mailboxes.insert(box("1", TODAY.plusDays(3), null));
    var dueSoon = showMenu();
    assertNull(FxTestSupport.call(() -> badge(dueSoon, "pastDueBadge")));
    assertNotNull(FxTestSupport.call(() -> badge(dueSoon, "dueSoonBadge")));

    mailboxes.setClosedDate(id, TODAY);
    mailboxes.insert(box("2", TODAY.minusDays(3), null));
    var pastDue = showMenu();
    assertNotNull(FxTestSupport.call(() -> badge(pastDue, "pastDueBadge")));
    assertNull(FxTestSupport.call(() -> badge(pastDue, "dueSoonBadge")));
  }

  @Test
  void theRenewalsButtonStillOpensRenewals() throws SQLException {
    mailboxes.insert(box("1", TODAY.minusDays(3), null));
    var stage = showMenu();

    FxTestSupport.run(() -> renewalsButton(stage).fire());

    assertNotNull(FxTestSupport.call(() -> stage.getScene().getRoot().lookup("#pastDueTable")));
  }

  @Test
  void thereIsNoBubbleWhenNothingIsDue() throws SQLException {
    mailboxes.insert(box("1", TODAY.plusYears(1), null));
    var stage = showMenu();

    assertNull(FxTestSupport.call(() -> badge(stage, "pastDueBadge")));
    assertNull(FxTestSupport.call(() -> badge(stage, "dueSoonBadge")));
    assertNull(FxTestSupport.call(() -> renewalsButton(stage).getTooltip()));
  }

  /** Opens the main menu in a new window. */
  private static Stage showMenu() {
    return FxTestSupport.call(() -> {
      var stage = new Stage();
      MainMenuView.show(stage);
      return stage;
    });
  }

  /** Returns a bubble on the Renewals button by its id, or {@code null} if it isn't shown. */
  private static Label badge(Stage stage, String id) {
    return (Label) stage.getScene().getRoot().lookup("#" + id);
  }

  /** Returns the Renewals button on the main menu. */
  private static Button renewalsButton(Stage stage) {
    return stage.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> "Renewals".equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

  /** Makes a box with the given number, end date, and closing date. */
  private static Mailbox box(String boxNumber, LocalDate endDate, LocalDate closedDate) {
    return new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "", null, null, endDate, null, null,
        closedDate);
  }

}
