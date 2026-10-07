package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for scheduling a price change on the Price Change window and
 * printing notices of it.
 */
class PriceChangeTest {

  private static final LocalDate NEXT_MONTH = LocalDate.now().plusMonths(1);

  private final PriceRepository prices = new PriceRepository();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void setUp() throws SQLException {
    emptyDatabase();
    new BoxInventoryRepository().add(List.of("1", "2"), "Small");
    prices.save(Map.of(PriceRepository.key("small", 3), 9000L, PriceRepository.key("small", 6), 15000L));
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      MainMenuView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void closeAllWindows() throws SQLException {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    emptyDatabase();
  }

  @Test
  void schedulesTheChangedPricesWithoutChangingTodays() throws SQLException {
    var window = openPriceChange();
    // The new prices start out as today's.
    assertEquals("$90.00", FxTestSupport.call(() -> field(window, "newPrice-small-3").getText()));

    FxTestSupport.run(() -> {
      date(window).setValue(NEXT_MONTH);
      field(window, "newPrice-small-3").setText("95");
      button(window, "priceChangeSaveButton").fire();
    });

    var change = prices.findChange();
    assertEquals(NEXT_MONTH, change.startsOn);
    assertEquals(Map.of(PriceRepository.key("small", 3), 9500L), change.prices);
    assertEquals(9000L, prices.priceFor("Small", 3));
    var pricesWindow = pricesWindow();
    assertTrue(FxTestSupport.call(() -> label(pricesWindow, "priceChangeLabel").getText())
        .startsWith("New prices start on"));
  }

  @Test
  void theStartDateMustBeAfterTodayAndSomethingMustChange() throws SQLException {
    var window = openPriceChange();

    FxTestSupport.run(() -> {
      date(window).setValue(LocalDate.now());
      field(window, "newPrice-small-3").setText("95");
      button(window, "priceChangeSaveButton").fire();
    });
    assertTrue(FxTestSupport.call(() -> result(window)).startsWith("Choose a day after today"));

    FxTestSupport.run(() -> {
      date(window).setValue(NEXT_MONTH);
      field(window, "newPrice-small-3").setText("90");
      button(window, "priceChangeSaveButton").fire();
    });
    assertTrue(FxTestSupport.call(() -> result(window)).startsWith("The new prices are the same as today's"));
    assertNull(prices.findChange());
  }

  @Test
  void reopeningShowsTheScheduledChangeWhichCanBeCancelled() throws SQLException {
    prices.scheduleChange(NEXT_MONTH, Map.of(PriceRepository.key("small", 6), 16000L));
    var window = openPriceChange();

    assertEquals(NEXT_MONTH, FxTestSupport.call(() -> date(window).getValue()));
    assertEquals("$160.00", FxTestSupport.call(() -> field(window, "newPrice-small-6").getText()));

    Dialogs.Confirm original = Dialogs.confirm;
    Dialogs.confirm = (owner, question, details, yes, no) -> true;
    try {
      FxTestSupport.run(() -> button(window, "cancelPriceChangeButton").fire());
    } finally {
      Dialogs.confirm = original;
    }

    assertNull(prices.findChange());
    assertEquals("$150.00", FxTestSupport.call(() -> field(window, "newPrice-small-6").getText()));
  }

  @Test
  void printNoticesSavesAndTicksEveryRentedBox() throws SQLException {
    var mailboxes = new MailboxRepository();
    mailboxes.insert(new Mailbox(0, "Ada", "Lovelace", null, "1", null, "", null, null, null, null));
    mailboxes.insert(new Mailbox(0, "Alan", "Turing", null, "2", null, "", null, null, null, null));
    // Box prices don't apply to forwarding, so there's no notice for it.
    mailboxes.insert(new Mailbox(0, "Grace", "Hopper", null, "2", null, "", null, null, null, null, null,
        null, null, null, true));
    var window = openPriceChange();

    FxTestSupport.run(() -> {
      date(window).setValue(NEXT_MONTH);
      field(window, "newPrice-small-3").setText("95");
      button(window, "printNoticesButton").fire();
    });

    assertNotNull(prices.findChange());
    var notices = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#printBoxList") != null)
        .findFirst()
        .orElseThrow());
    assertEquals(2, FxTestSupport.call(() -> ((ListView<?>) notices.getScene().lookup("#printBoxList"))
        .getItems().size()));
    assertEquals("Print 2 Notices",
        FxTestSupport.call(() -> ((Button) notices.getScene().lookup("#printButton")).getText()));
    assertEquals("Mailbox Price Change",
        FxTestSupport.call(() -> ((Label) notices.getScene().lookup("#noticeTitle")).getText()));
  }

  /** Opens the Prices window and then the Price Change window, and returns it. */
  private Stage openPriceChange() {
    FxTestSupport.run(() -> PricesView.show(mainWindow));
    var prices = pricesWindow();
    FxTestSupport.run(() -> button(prices, "priceChangeButton").fire());
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && "Price Change".equals(((Stage) w).getTitle()))
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow());
  }

  /** Returns the open Prices window, failing if there isn't one. */
  private static Stage pricesWindow() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && "Prices".equals(((Stage) w).getTitle()))
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow());
  }

  /** Returns the text field with the given id on a window. */
  private static TextField field(Stage window, String id) {
    return (TextField) window.getScene().lookup("#" + id);
  }

  /** Returns where the day the new prices start is chosen. */
  private static DatePicker date(Stage window) {
    return (DatePicker) window.getScene().lookup("#priceChangeDate");
  }

  /** Returns the button with the given id on a window. */
  private static Button button(Stage window, String id) {
    return (Button) window.getScene().lookup("#" + id);
  }

  /** Returns the label with the given id on a window. */
  private static Label label(Stage window, String id) {
    return (Label) window.getScene().lookup("#" + id);
  }

  /** Returns the message on the Price Change window. */
  private static String result(Stage window) {
    return label(window, "priceChangeResultLabel").getText();
  }

  /** Deletes the boxes, prices, inventory, and settings from the test database. */
  private static void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM prices");
      stmt.execute("DELETE FROM scheduled_prices");
      stmt.execute("DELETE FROM box_sizes");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM mailboxes");
    }
  }

}
