package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
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
import org.lfps.mailboxes.model.DepositOutcome;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;

/**
 * UI tests for closing, deleting, renewing, filtering, and sorting boxes on
 * Manage Boxes.
 */
class ManageBoxesActionsTest {

  private static final Dialogs.Confirm REAL_CONFIRM = Dialogs.confirm;

  private static final Dialogs.ConfirmWithChoice REAL_CONFIRM_WITH_CHOICE = Dialogs.confirmWithChoice;

  private final MailboxRepository mailboxes = new MailboxRepository();

  /** The questions asked, each as "question | details". */
  private final List<String> questions = new ArrayList<>();

  private Stage mainWindow;

  private int adaId;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void showManageBoxes() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    adaId = mailboxes.insert(box("10", "Ada", "Lovelace", null));
    mailboxes.insert(box("9", "Grace", "Hopper", null));
    mailboxes.insert(box("100", "Alan", "Turing", LocalDate.of(2026, 1, 1)));

    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      ManageBoxesView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void closeAllWindows() {
    Dialogs.confirm = REAL_CONFIRM;
    Dialogs.confirmWithChoice = REAL_CONFIRM_WITH_CHOICE;
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void closingABoxAsksFirstThenClosesItToday() throws SQLException {
    answer(true);
    select("10");
    FxTestSupport.run(() -> button("closeButton").fire());

    assertEquals(List.of("Close box 10 for Ada Lovelace? | The box becomes free to rent to someone else. "
        + "Everything recorded for it is kept, and you can find it again by showing closed boxes."), questions);
    var ada = find(adaId);
    assertEquals(LocalDate.now(), ada.getClosedDate());
    assertEquals(List.of("9"), boxNumbersShown());
    assertFalse(mailboxes.isBoxNumberTaken("10", 0));
  }

  @Test
  void closingABoxWithADepositAsksWhatHappenedToIt() throws SQLException {
    var id = mailboxes.insert(new Mailbox(0, "Edsger", "Dijkstra", null, "20", null, "", null, null, null, null,
        null, null, 2, 2000L));
    var asked = new ArrayList<String>();
    Dialogs.confirmWithChoice = (owner, question, details, choiceQuestion, choices, initial, yes, no) -> {
      asked.add(choiceQuestion + " " + choices.get(initial));
      return KeyFields.indexOf(DepositOutcome.RETURNED);
    };
    FxTestSupport.run(() -> ManageBoxesView.show(mainWindow));
    select("20");
    FxTestSupport.run(() -> button("closeButton").fire());

    assertEquals(List.of("The $20.00 key deposit was: Not recorded yet"), asked);
    assertEquals(DepositOutcome.RETURNED, find(id).getKeyDepositOutcome());
    assertTrue(find(id).isClosed());
  }

  @Test
  void answeringNoToTheDepositQuestionLeavesTheBoxOpen() throws SQLException {
    var id = mailboxes.insert(new Mailbox(0, "Edsger", "Dijkstra", null, "20", null, "", null, null, null, null,
        null, null, 2, 2000L));
    Dialogs.confirmWithChoice = (owner, question, details, choiceQuestion, choices, initial, yes, no) -> -1;
    FxTestSupport.run(() -> ManageBoxesView.show(mainWindow));
    select("20");
    FxTestSupport.run(() -> button("closeButton").fire());

    assertFalse(find(id).isClosed());
  }

  @Test
  void answeringNoLeavesTheBoxOpen() throws SQLException {
    answer(false);
    select("10");
    FxTestSupport.run(() -> button("closeButton").fire());

    assertFalse(find(adaId).isClosed());
    assertEquals(List.of("9", "10"), boxNumbersShown());
  }

  @Test
  void closingKeepsTheSearch() {
    answer(true);
    FxTestSupport.run(() -> searchField().setText("Lovelace"));
    select("10");
    FxTestSupport.run(() -> button("closeButton").fire());

    assertEquals("Lovelace", FxTestSupport.call(() -> searchField().getText()));
    assertEquals(List.of(), boxNumbersShown());
  }

  @Test
  void deletingAsksThenErasesTheBoxAndItsHistory() throws SQLException {
    new RentalHistoryRepository().renew(adaId, new RentalPeriod(0, 0, LocalDate.now(), LocalDate.now(),
        LocalDate.now().plusMonths(1), 1000L, null, null));
    answer(true);
    select("10");
    FxTestSupport.run(() -> button("deleteButton").fire());

    assertTrue(questions.get(0).startsWith("Permanently delete box 10 for Ada Lovelace? | "), questions.get(0));
    assertTrue(questions.get(0).contains("close it instead"), questions.get(0));
    assertNull(find(adaId));
    assertEquals(List.of(), new RentalHistoryRepository().findForMailbox(adaId));
    assertEquals(List.of("9"), boxNumbersShown());
  }

  @Test
  void answeringNoKeepsTheBox() throws SQLException {
    answer(false);
    select("10");
    FxTestSupport.run(() -> button("deleteButton").fire());

    assertNotNull(find(adaId));
  }

  @Test
  void renewOpensTheRenewWindowForTheSelectedBox() {
    select("10");
    FxTestSupport.run(() -> button("renewButton").fire());

    var title = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#renewSaveButton") != null)
        .map(w -> ((Stage) w).getTitle())
        .findFirst()
        .orElse(null));
    assertEquals("Renew Box 10", title);
  }

  @Test
  void theCloseButtonSaysReopenOnlyForClosedBoxes() {
    assertEquals("Close Box", FxTestSupport.call(() -> button("closeButton").getText()));
    FxTestSupport.run(() -> showChoice().setValue(ManageBoxesView.Show.ALL));
    select("100");
    assertEquals("Reopen", FxTestSupport.call(() -> button("closeButton").getText()));
    select("9");
    assertEquals("Close Box", FxTestSupport.call(() -> button("closeButton").getText()));
  }

  @Test
  void searchAndTheShowMenuWorkTogether() {
    FxTestSupport.run(() -> {
      showChoice().setValue(ManageBoxesView.Show.ALL);
      searchField().setText("Turing");
    });
    assertEquals(List.of("100"), boxNumbersShown());

    FxTestSupport.run(() -> showChoice().setValue(ManageBoxesView.Show.OPEN));
    assertEquals(List.of(), boxNumbersShown());
  }

  @Test
  void sortingByBoxNumberUsesNumberOrder() {
    FxTestSupport.run(() -> {
      showChoice().setValue(ManageBoxesView.Show.ALL);
      var column = boxNumberColumn();
      column.setSortType(TableColumn.SortType.DESCENDING);
      table().getSortOrder().setAll(List.of(column));
      table().sort();
    });

    assertEquals(List.of("100", "10", "9"), boxNumbersShown());
  }

  @Test
  void closedBoxesAreGreyedOut() {
    FxTestSupport.run(() -> showChoice().setValue(ManageBoxesView.Show.ALL));

    assertEquals("-fx-text-background-color: gray;", rowStyle("100"));
    assertEquals("", rowStyle("9"));
  }

  /** Makes every question the app asks get the same answer, noting each question and its details. */
  private void answer(boolean yes) {
    Dialogs.confirm = (owner, question, details, yesButton, noButton) -> {
      questions.add(question + " | " + details);
      return yes;
    };
  }

  /** Selects a box in the table. */
  private void select(String boxNumber) {
    FxTestSupport.run(() -> {
      var items = table().getItems();
      for (var i = 0; i < items.size(); i++) {
        if (items.get(i).getBoxNumber().equals(boxNumber)) {
          table().getSelectionModel().select(i);
          return;
        }
      }
      throw new AssertionError("Box " + boxNumber + " isn't shown");
    });
  }

  /**
   * Reads a box from the database by its id, or returns {@code null} if it's
   * gone.
   */
  private Mailbox find(int id) throws SQLException {
    return mailboxes.findAll().stream().filter(m -> m.getId() == id).findFirst().orElse(null);
  }

  /** Returns the box numbers in the table, as shown. */
  private List<String> boxNumbersShown() {
    return FxTestSupport.call(() -> table().getItems().stream()
        .map(Mailbox::getBoxNumber)
        .collect(Collectors.toList()));
  }

  /** Returns the style of a box's row in the table, such as the grey of a closed box. */
  @SuppressWarnings("unchecked")
  private String rowStyle(String boxNumber) {
    return FxTestSupport.call(() -> {
      table().layout();
      return table().lookupAll(".table-row-cell").stream()
          .map(node -> (TableRow<Mailbox>) node)
          .filter(row -> row.getItem() != null && boxNumber.equals(row.getItem().getBoxNumber()))
          .findFirst()
          .orElseThrow()
          .getStyle();
    });
  }

  /** Returns the table's Box Number column. */
  @SuppressWarnings("unchecked")
  private TableColumn<Mailbox, String> boxNumberColumn() {
    return (TableColumn<Mailbox, String>) table().getColumns().get(0);
  }

  /** Returns the table of boxes. */
  @SuppressWarnings("unchecked")
  private TableView<Mailbox> table() {
    return (TableView<Mailbox>) mainWindow.getScene().getRoot().lookup("#boxTable");
  }

  @SuppressWarnings("unchecked")
  private ChoiceBox<ManageBoxesView.Show> showChoice() {
    return (ChoiceBox<ManageBoxesView.Show>) mainWindow.getScene().getRoot().lookup("#showChoice");
  }

  /** Returns the search field. */
  private TextField searchField() {
    return (TextField) mainWindow.getScene().getRoot().lookup("#searchField");
  }

  /** Returns the button with the given id on the main window. */
  private Button button(String id) {
    return (Button) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  /** Makes a box with the given names and closing date. */
  private static Mailbox box(String boxNumber, String first, String last, LocalDate closedDate) {
    return new Mailbox(0, first, last, null, boxNumber, null, "5551000001", null, null, null, null, null,
        closedDate);
  }

}
