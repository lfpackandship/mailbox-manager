package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.Node;
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

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.RentalHistoryRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * UI tests for the Payments screen and recording a payment on Add New Box.
 */
class PaymentsViewTest {

  private static final LocalDate TODAY = LocalDate.now();

  private static final Dialogs.Confirm REAL_CONFIRM = Dialogs.confirm;

  private final MailboxRepository mailboxes = new MailboxRepository();

  private final RentalHistoryRepository history = new RentalHistoryRepository();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void openMainWindow() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      MainMenuView.show(stage);
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
  void showsThisMonthsPaymentsForOpenAndClosedBoxesWithTheTotal() throws SQLException {
    var open = mailboxes.insert(box("1", null));
    var closed = mailboxes.insert(box("2", TODAY));
    history.renew(open, new RentalPeriod(0, 0, TODAY, TODAY, TODAY.plusMonths(6), 6000L, "Cash", null));
    history.renew(closed, new RentalPeriod(0, 0, TODAY, TODAY, TODAY.plusMonths(3), 4550L, null, null));
    history.renew(closed, new RentalPeriod(0, 0, TODAY, TODAY, TODAY.plusMonths(3), null, null, "free month"));
    history.renew(open, new RentalPeriod(0, 0, TODAY.minusYears(1), TODAY, TODAY.plusMonths(1), 999L, null, null));

    FxTestSupport.run(() -> PaymentsView.show(mainWindow));

    assertEquals("3 entries, $105.50 paid", text("paymentsTotal"));
    assertEquals(3, FxTestSupport.call(() -> table().getItems().size()));
  }

  @Test
  void thisYearIncludesEarlierMonths() throws SQLException {
    var id = mailboxes.insert(box("1", null));
    history.renew(id, new RentalPeriod(0, 0, TODAY.withDayOfYear(1), TODAY, TODAY.plusMonths(1), 100L, null, null));

    FxTestSupport.run(() -> {
      PaymentsView.show(mainWindow);
      buttonLabeled("This Year").fire();
    });

    assertEquals("1 entry, $1.00 paid", text("paymentsTotal"));
  }

  @Test
  void addingABoxWithAPaymentRecordsTheRental() throws SQLException {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("John (optional)").setText("Tomás");
      fieldWithPrompt("Doe (optional)").setText("Rivera");
      fieldWithPrompt("310 (required)").setText("205");
      fieldWithPrompt("(555) 123-4567 (optional)").setText("5552000006");
      buttonLabeled(RentalLengths.label(6)).fire();
      ((TextField) lookup("#amountField")).setText("$75");
      ((ComboBox<?>) lookup("#paymentMethodField")).getEditor().setText("Check");
      buttonLabeled("Save").fire();
    });

    var saved = mailboxes.findAll().get(0);
    assertEquals(TODAY.plusMonths(6), saved.getEndDate());
    var rentals = history.findForMailbox(saved.getId());
    assertEquals(1, rentals.size());
    assertEquals(TODAY, rentals.get(0).getStartDate());
    assertEquals(TODAY.plusMonths(6), rentals.get(0).getEndDate());
    assertEquals(7500L, rentals.get(0).getAmountCents());
    assertEquals("Check", rentals.get(0).getPaymentMethod());
  }

  @Test
  void aPaymentNeedsAnEndDate() throws SQLException {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("John (optional)").setText("Tomás");
      fieldWithPrompt("Doe (optional)").setText("Rivera");
      fieldWithPrompt("310 (required)").setText("205");
      fieldWithPrompt("(555) 123-4567 (optional)").setText("5552000006");
      ((TextField) lookup("#amountField")).setText("75");
      buttonLabeled("Save").fire();
    });

    var messages = FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookupAll(".label").stream()
        .map(node -> ((Label) node).getText())
        .collect(Collectors.toList()));
    assertTrue(messages.contains("Set an end date after today to record a payment."), messages.toString());
    assertEquals(List.of(), mailboxes.findAll());
  }

  @Test
  void aBoxWithNoEndDateHasNoHistory() throws SQLException {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("John (optional)").setText("Tomás");
      fieldWithPrompt("Doe (optional)").setText("Rivera");
      fieldWithPrompt("310 (required)").setText("205");
      fieldWithPrompt("(555) 123-4567 (optional)").setText("5552000006");
      buttonLabeled("Save").fire();
    });

    assertEquals(List.of(), history.findForMailbox(mailboxes.findAll().get(0).getId()));
  }

  @Test
  void deleteEntryAsksThenRemovesItButLeavesTheEndDate() throws SQLException {
    var id = mailboxes.insert(box("1", null));
    history.renew(id, new RentalPeriod(0, 0, TODAY, TODAY, TODAY.plusMonths(6), 6000L, null, null));
    var questions = new ArrayList<String>();
    Dialogs.confirm = (owner, question, details, yes, no) -> questions.add(question);
    try {
      FxTestSupport.run(() -> PaymentsView.show(mainWindow));
      FxTestSupport.run(() -> {
        table().getSelectionModel().select(0);
        ((Button) lookup("#paymentsDeleteButton")).fire();
      });
    } finally {
      Dialogs.confirm = REAL_CONFIRM;
    }

    assertEquals(List.of("Delete this entry from the rental history?"), questions);
    assertEquals(List.of(), history.findForMailbox(id));
    assertEquals(TODAY.plusMonths(6), mailboxes.findAll().get(0).getEndDate());
    assertEquals("0 entries, $0.00 paid", text("paymentsTotal"));
  }

  @Test
  void answeringNoKeepsTheEntry() throws SQLException {
    var id = mailboxes.insert(box("1", null));
    history.renew(id, new RentalPeriod(0, 0, TODAY, TODAY, TODAY.plusMonths(6), 6000L, null, null));
    Dialogs.confirm = (owner, question, details, yes, no) -> false;
    try {
      FxTestSupport.run(() -> PaymentsView.show(mainWindow));
      FxTestSupport.run(() -> {
        table().getSelectionModel().select(0);
        ((Button) lookup("#paymentsDeleteButton")).fire();
      });
    } finally {
      Dialogs.confirm = REAL_CONFIRM;
    }

    assertEquals(1, history.findForMailbox(id).size());
  }

  @Test
  void viewBoxOpensTheBoxsDetails() throws SQLException {
    var id = mailboxes.insert(box("7", null));
    history.renew(id, new RentalPeriod(0, 0, TODAY, TODAY, TODAY.plusMonths(6), 6000L, null, null));
    FxTestSupport.run(() -> PaymentsView.show(mainWindow));
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(0);
      ((Button) lookup("#paymentsViewButton")).fire();
    });

    var title = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#detailTitle") != null)
        .map(w -> ((Stage) w).getTitle())
        .findFirst()
        .orElse(null));
    assertEquals("Box 7 – Ada Lovelace", title);
  }

  @Test
  void lastMonthShowsOnlyLastMonth() throws SQLException {
    var id = mailboxes.insert(box("1", null));
    var lastMonth = TODAY.withDayOfMonth(1).minusMonths(1);
    history.renew(id, new RentalPeriod(0, 0, lastMonth, TODAY, TODAY.plusMonths(1), 100L, null, null));
    history.renew(id, new RentalPeriod(0, 0, lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()), TODAY,
        TODAY.plusMonths(1), 200L, null, null));
    history.renew(id, new RentalPeriod(0, 0, lastMonth.minusDays(1), TODAY, TODAY.plusMonths(1), 400L, null, null));
    history.renew(id, new RentalPeriod(0, 0, TODAY.withDayOfMonth(1), TODAY, TODAY.plusMonths(1), 800L, null, null));

    FxTestSupport.run(() -> PaymentsView.show(mainWindow));
    FxTestSupport.run(() -> buttonLabeled("Last Month").fire());

    assertEquals("2 entries, $3.00 paid", text("paymentsTotal"));
  }

  @Test
  void chosenDatesAreChecked() {
    FxTestSupport.run(() -> PaymentsView.show(mainWindow));
    FxTestSupport.run(() -> {
      ((DatePicker) lookup("#paymentsFromField")).setValue(TODAY);
      ((DatePicker) lookup("#paymentsToField")).setValue(TODAY.minusDays(1));
      ((Button) lookup("#paymentsShowButton")).fire();
    });

    var messages = FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookupAll(".label").stream()
        .map(node -> ((Label) node).getText())
        .collect(Collectors.toList()));
    assertTrue(messages.contains("The second date must be on or after the first."), messages.toString());
  }

  /** Makes a box with the given closing date. */
  private static Mailbox box(String boxNumber, LocalDate closedDate) {
    return new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "5551000001", null, null, null, null,
        null, closedDate);
  }

  @SuppressWarnings("unchecked")
  private TableView<PaymentsView.Entry> table() {
    return (TableView<PaymentsView.Entry>) lookup("#paymentsTable");
  }

  /** Returns what matches a selector on the main window. */
  private Node lookup(String selector) {
    return mainWindow.getScene().getRoot().lookup(selector);
  }

  /** Returns the text of the label with the given id on the main window. */
  private String text(String id) {
    return FxTestSupport.call(() -> ((Label) lookup("#" + id)).getText());
  }

  /** Returns the text field on the main window showing the given hint while empty. */
  private TextField fieldWithPrompt(String prompt) {
    return mainWindow.getScene().getRoot().lookupAll(".text-field").stream()
        .map(node -> (TextField) node)
        .filter(f -> prompt.equals(f.getPromptText()))
        .findFirst()
        .orElseThrow();
  }

  /** Returns the button on the main window with the given text. */
  private Button buttonLabeled(String text) {
    return mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> text.equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

}
