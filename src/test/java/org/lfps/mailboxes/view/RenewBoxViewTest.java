package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
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
import org.lfps.mailboxes.data.RentalHistoryRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * UI tests for renewing a box from the Renewals screen.
 */
class RenewBoxViewTest {

  private static final LocalDate OLD_END = LocalDate.now().minusDays(2);

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

  private int boxId;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void openRenewWindow() throws SQLException {
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
    boxId = mailboxes.insert(new Mailbox(0, "Ada", "Lovelace", null, "101", null, "5551000001", null,
        null, OLD_END, null));

    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      RenewalsView.show(stage);
      pastDueTable(stage).getSelectionModel().select(0);
      ((Button) stage.getScene().getRoot().lookup("#pastDueRenewButton")).fire();
      return stage;
    });
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
  void opensWithTheCurrentEndDateAsTheStart() {
    var renew = renewWindow();
    assertNotNull(renew);
    assertEquals("Renew Box 101", FxTestSupport.call(renew::getTitle));
    assertEquals(OLD_END, FxTestSupport.call(() -> ((DatePicker) renew.getScene().lookup("#renewStartField"))
        .getValue()));
    assertEquals("The rental ends " + OLD_END.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + " (2 days overdue).", text(renew, "renewCurrentLabel"));
  }

  @Test
  void renewingRecordsThePaymentAndMovesTheEndDate() throws SQLException {
    var renew = renewWindow();
    FxTestSupport.run(() -> {
      buttonLabeled(renew, RentalLengths.label(12)).fire();
      ((TextField) renew.getScene().lookup("#amountField")).setText("120");
      comboEditor(renew).setText("Cash");
      ((TextField) renew.getScene().lookup("#renewNoteField")).setText("receipt 55");
      ((Button) renew.getScene().lookup("#renewSaveButton")).fire();
    });

    assertNull(renewWindow());
    assertEquals(OLD_END.plusMonths(12), mailboxes.findAll().get(0).getEndDate());
    var history = new RentalHistoryRepository().findForMailbox(boxId);
    assertEquals(1, history.size());
    assertEquals(OLD_END, history.get(0).getStartDate());
    assertEquals(OLD_END.plusMonths(12), history.get(0).getEndDate());
    assertEquals(LocalDate.now(), history.get(0).getRecordedOn());
    assertEquals(12000L, history.get(0).getAmountCents());
    assertEquals("Cash", history.get(0).getPaymentMethod());
    assertEquals("receipt 55", history.get(0).getNote());
    // The Renewals screen refreshes, so the box is no longer past due.
    assertTrue(FxTestSupport.call(() -> pastDueTable(mainWindow).getItems().isEmpty()));
  }

  @Test
  void aBadAmountIsRejectedAndNothingIsSaved() throws SQLException {
    var renew = renewWindow();
    FxTestSupport.run(() -> {
      buttonLabeled(renew, RentalLengths.label(1)).fire();
      ((TextField) renew.getScene().lookup("#amountField")).setText("sixty");
      ((Button) renew.getScene().lookup("#renewSaveButton")).fire();
    });

    assertEquals("Enter the amount paid in dollars and cents, like 60 or 60.00.", text(renew, "renewErrorLabel"));
    assertNotNull(renewWindow());
    assertEquals(OLD_END, mailboxes.findAll().get(0).getEndDate());
    assertEquals(List.of(), new RentalHistoryRepository().findForMailbox(boxId));
  }

  @Test
  void aLengthOrEndDateIsRequired() {
    var renew = renewWindow();
    FxTestSupport.run(() -> ((Button) renew.getScene().lookup("#renewSaveButton")).fire());

    assertEquals("Choose a rental length or the new end date.", text(renew, "renewErrorLabel"));
  }

  @Test
  void theEndDateMustBeAfterTheStart() {
    var renew = renewWindow();
    FxTestSupport.run(() -> {
      ((DatePicker) renew.getScene().lookup("#renewEndField")).setValue(OLD_END);
      ((Button) renew.getScene().lookup("#renewSaveButton")).fire();
    });

    assertEquals("The new end date must be after the date it's renewed from.", text(renew, "renewErrorLabel"));
  }

  @Test
  void choosingALengthFillsInThePriceForTheBoxsSize() throws SQLException {
    try {
      new BoxInventoryRepository().add(List.of("101"), "Large");
      new PriceRepository().save(Map.of(PriceRepository.key("Large", 12), 15000L,
          PriceRepository.key(PriceRepository.DEFAULT_SIZE, 1), 1500L));
      var renew = renewWindow();
      var amount = FxTestSupport.call(() -> (TextField) renew.getScene().lookup("#amountField"));

      FxTestSupport.run(() -> buttonLabeled(renew, RentalLengths.label(12)).fire());
      assertEquals("$150.00", FxTestSupport.call(amount::getText));

      // Large has no 1-month price, so the default is used.
      FxTestSupport.run(() -> buttonLabeled(renew, RentalLengths.label(1)).fire());
      assertEquals("$15.00", FxTestSupport.call(amount::getText));

      // No price at all clears the one filled in.
      FxTestSupport.run(() -> buttonLabeled(renew, RentalLengths.label(3)).fire());
      assertEquals("", FxTestSupport.call(amount::getText));

      // An amount typed in is kept.
      FxTestSupport.run(() -> {
        amount.setText("140");
        buttonLabeled(renew, RentalLengths.label(12)).fire();
      });
      assertEquals("140", FxTestSupport.call(amount::getText));
    } finally {
      try (var conn = Database.connect(); var stmt = conn.createStatement()) {
        stmt.execute("DELETE FROM prices");
        stmt.execute("DELETE FROM box_inventory");
      }
    }
  }

  @Test
  void aBoxWithNoEndDateRenewsFromToday() throws SQLException {
    var id = mailboxes.insert(new Mailbox(0, "Grace", "Hopper", null, "102", null, "5551000002", null,
        null, null, null));
    var box = mailboxes.findAll().stream().filter(m -> m.getId() == id).findFirst().orElseThrow();
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        if (window != mainWindow) {
          window.hide();
        }
      }
      RenewBoxView.show(mainWindow, box, () -> { });
    });
    var renew = renewWindow();

    assertEquals("This box has no end date set.", text(renew, "renewCurrentLabel"));
    assertEquals(LocalDate.now(), FxTestSupport.call(() -> ((DatePicker) renew.getScene()
        .lookup("#renewStartField")).getValue()));
    FxTestSupport.run(() -> {
      buttonLabeled(renew, RentalLengths.label(3)).fire();
      ((Button) renew.getScene().lookup("#renewSaveButton")).fire();
    });

    assertEquals(LocalDate.now().plusMonths(3), new RentalHistoryRepository().findForMailbox(id).get(0).getEndDate());
  }

  @Test
  void changingTheStartMovesWhereTheLengthButtonsCountFrom() {
    var renew = renewWindow();
    FxTestSupport.run(() -> {
      ((DatePicker) renew.getScene().lookup("#renewStartField")).setValue(LocalDate.now());
      buttonLabeled(renew, RentalLengths.label(6)).fire();
    });

    assertEquals(LocalDate.now().plusMonths(6), FxTestSupport.call(() -> ((DatePicker) renew.getScene()
        .lookup("#renewEndField")).getValue()));
  }

  @Test
  void cancelSavesNothing() throws SQLException {
    var renew = renewWindow();
    FxTestSupport.run(() -> {
      buttonLabeled(renew, RentalLengths.label(12)).fire();
      ((Button) renew.getScene().lookup("#renewCancelButton")).fire();
    });

    assertNull(renewWindow());
    assertEquals(OLD_END, mailboxes.findAll().get(0).getEndDate());
    assertEquals(List.of(), new RentalHistoryRepository().findForMailbox(boxId));
  }

  /** Returns the Past Due table on Renewals. */
  @SuppressWarnings("unchecked")
  private static TableView<Mailbox> pastDueTable(Stage stage) {
    return (TableView<Mailbox>) stage.getScene().getRoot().lookup("#pastDueTable");
  }

  /**
   * Returns the open Renew Box window, or {@code null} if there isn't one.
   */
  private static Stage renewWindow() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#renewSaveButton") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElse(null));
  }

  /** Returns where the payment method is typed on the Renew Box window. */
  private static TextField comboEditor(Stage window) {
    return ((ComboBox<?>) window.getScene().lookup("#paymentMethodField")).getEditor();
  }

  /** Returns the button on a window with the given text. */
  private static Button buttonLabeled(Stage window, String text) {
    return window.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> text.equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

  /** Returns the text of the label with the given id on a window. */
  private static String text(Stage window, String id) {
    return FxTestSupport.call(() -> ((Label) window.getScene().lookup("#" + id)).getText());
  }

}
