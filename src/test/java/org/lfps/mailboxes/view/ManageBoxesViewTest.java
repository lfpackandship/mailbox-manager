package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.stream.Collectors;

import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.RentalPeriod;

/**
 * UI tests for the Manage Boxes table and the box details window.
 */
class ManageBoxesViewTest {

  private static final LocalDate END = LocalDate.now().plusDays(3);

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

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
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM mailboxes");
    }
    mailboxes.insert(new Mailbox(0, "Tomás", "Rivera", "Rivera Landscaping", "205", "Garden", "5552000006",
        "tomas@example.com", List.of("Rivera Tree Care", "Green Thumb"), END,
        List.of(new ForwardingAddress("88 Palm Way", "Unit 3B", "Naples", "FL", "34102", "winter"))));
    mailboxes.insert(new Mailbox(0, "Michael", "Brennan", "", "207", "", "(555) 200-0008", "",
        null, null, null));

    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      ManageBoxesView.show(stage);
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
  void tableShowsOnlyTheKeyDetails() {
    var headers = FxTestSupport.call(() -> table().getVisibleLeafColumns().stream()
        .map(column -> column.getText())
        .collect(Collectors.toList()));

    assertEquals(List.of("Box Number", "First Name", "Last Name", "Business Title", "Phone"), headers);
  }

  @Test
  void viewShowsTheFullEntry() {
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(0);
      button("viewButton").fire();
    });

    var details = detailsWindow();
    assertNotNull(details);
    assertEquals("Box 205 – Tomás Rivera", FxTestSupport.call(details::getTitle));
    assertEquals("Box 205 – Garden", detail(details, "detailTitle"));
    assertEquals("Tomás Rivera", detail(details, "detailHolder"));
    assertEquals("Rivera Landscaping", detail(details, "detailBusinessTitle"));
    assertEquals("Rivera Tree Care\nGreen Thumb", detail(details, "detailAlternateNames"));
    assertEquals("(555) 200-0006", detail(details, "detailPhone"));
    assertEquals("tomas@example.com", detail(details, "detailEmail"));
    assertEquals(END.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + " (in 3 days)",
        detail(details, "detailEndDate"));
    assertEquals("88 Palm Way, Unit 3B, Naples, FL 34102 (winter)", detail(details, "detailForwarding"));
  }

  @Test
  void missingDetailsShowADash() {
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(1);
      button("viewButton").fire();
    });

    var details = detailsWindow();
    assertEquals("Box 207", detail(details, "detailTitle"));
    for (var id : List.of("detailBusinessTitle", "detailAlternateNames", "detailEmail", "detailEndDate",
        "detailForwarding", "detailNotes", "detailHistory")) {
      assertEquals(BoxDetailsView.NONE, detail(details, id), id);
    }
  }

  @Test
  void viewIsDisabledUntilABoxIsSelected() {
    assertTrue(FxTestSupport.call(() -> button("viewButton").isDisabled()));
    FxTestSupport.run(() -> table().getSelectionModel().select(0));
    assertFalse(FxTestSupport.call(() -> button("viewButton").isDisabled()));
  }

  @Test
  void doubleClickingARowOpensItsDetails() {
    FxTestSupport.run(() -> {
      var row = rowFor("207");
      Event.fireEvent(row, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
          false, false, false, false, true, false, false, true, false, false, null));
    });

    assertEquals("Box 207 – Michael Brennan", FxTestSupport.call(detailsWindow()::getTitle));
  }

  @Test
  void aSingleClickOnlySelects() {
    FxTestSupport.run(() -> Event.fireEvent(rowFor("207"), new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
        MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, false, null)));

    assertNull(detailsWindow());
  }

  @Test
  void enterOnASelectedRowOpensItsDetails() {
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(0);
      FxTestSupport.press(table(), KeyCode.ENTER);
    });

    assertEquals("Box 205 – Tomás Rivera", FxTestSupport.call(detailsWindow()::getTitle));
  }

  @Test
  void viewingAnotherBoxReplacesTheDetailsWindow() {
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(0);
      button("viewButton").fire();
      table().getSelectionModel().select(1);
      button("viewButton").fire();
    });

    var titles = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && ((Stage) w).getTitle() != null
            && ((Stage) w).getTitle().startsWith("Box "))
        .map(w -> ((Stage) w).getTitle())
        .collect(Collectors.toList()));
    assertEquals(List.of("Box 207 – Michael Brennan"), titles);
  }

  @Test
  void editFromTheDetailsWindowOpensTheEditForm() {
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(0);
      button("viewButton").fire();
    });
    var details = detailsWindow();

    FxTestSupport.run(() -> ((Button) details.getScene().lookup("#detailsEditButton")).fire());

    assertNull(detailsWindow());
    var firstNames = FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookupAll(".text-field").stream()
        .map(node -> ((TextField) node).getText())
        .collect(Collectors.toList()));
    assertTrue(firstNames.contains("Tomás"), firstNames.toString());
  }

  @Test
  void closedBoxesAreHiddenUntilChosen() throws SQLException {
    mailboxes.insert(closedBox("209"));
    FxTestSupport.run(() -> ManageBoxesView.show(mainWindow));

    assertEquals(List.of("205", "207"), boxNumbersShown());

    FxTestSupport.run(() -> showChoice().setValue(ManageBoxesView.Show.CLOSED));
    assertEquals(List.of("209"), boxNumbersShown());

    FxTestSupport.run(() -> showChoice().setValue(ManageBoxesView.Show.ALL));
    assertEquals(List.of("205", "207", "209"), boxNumbersShown());
  }

  @Test
  void aClosedBoxCanBeReopenedButNotRenewed() throws SQLException {
    mailboxes.insert(closedBox("209"));
    FxTestSupport.run(() -> {
      ManageBoxesView.show(mainWindow);
      showChoice().setValue(ManageBoxesView.Show.CLOSED);
      table().getSelectionModel().select(0);
    });

    assertTrue(FxTestSupport.call(() -> button("renewButton").isDisabled()));
    assertEquals("Reopen", FxTestSupport.call(() -> button("closeButton").getText()));

    FxTestSupport.run(() -> button("closeButton").fire());

    assertEquals(List.of("205", "207", "209"), mailboxes.findOpen().stream()
        .map(Mailbox::getBoxNumber).collect(Collectors.toList()));
    // The screen keeps showing closed boxes, and the reopened one has left the list.
    assertEquals(List.of(), boxNumbersShown());
  }

  @Test
  void aClosedBoxCantBeReopenedIfItsNumberWasGivenToSomeoneElse() throws SQLException {
    mailboxes.insert(closedBox("205"));
    FxTestSupport.run(() -> {
      ManageBoxesView.show(mainWindow);
      showChoice().setValue(ManageBoxesView.Show.CLOSED);
      table().getSelectionModel().select(0);
      button("closeButton").fire();
    });

    assertTrue(FxTestSupport.call(() -> ((Label) mainWindow.getScene().getRoot().lookup("#statusLabel")).getText())
        .startsWith("Box 205 has been given to someone else"));
    assertEquals(2, mailboxes.findOpen().size());
  }

  @Test
  void detailsShowNotesClosingAndRentalHistory() throws SQLException {
    var id = mailboxes.insert(new Mailbox(0, "Grace", "Hopper", null, "209", null, "5552000009", null, null,
        LocalDate.of(2026, 7, 1), null, "ID on file", LocalDate.of(2026, 8, 15)));
    new RentalHistoryRepository().renew(id, new RentalPeriod(0, 0, LocalDate.of(2026, 1, 2),
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1), 6000L, "Check", "check #1042"));
    FxTestSupport.run(() -> {
      ManageBoxesView.show(mainWindow);
      showChoice().setValue(ManageBoxesView.Show.CLOSED);
      table().getSelectionModel().select(0);
      button("viewButton").fire();
    });

    var details = detailsWindow();
    assertEquals("ID on file", detail(details, "detailNotes"));
    assertEquals(LocalDate.of(2026, 8, 15).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        detail(details, "detailClosed"));
    var medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    assertEquals(LocalDate.of(2026, 1, 1).format(medium) + " – " + LocalDate.of(2026, 7, 1).format(medium)
        + ": $60.00, Check (check #1042)", detail(details, "detailHistory"));
    assertTrue(FxTestSupport.call(() -> details.getScene().lookup("#detailsRenewButton").isDisabled()));
  }

  private static Mailbox closedBox(String boxNumber) {
    return new Mailbox(0, "Grace", "Hopper", null, boxNumber, null, "5552000009", null, null, null, null,
        null, LocalDate.now().minusDays(10));
  }

  private List<String> boxNumbersShown() {
    return FxTestSupport.call(() -> table().getItems().stream()
        .map(Mailbox::getBoxNumber)
        .collect(Collectors.toList()));
  }

  @SuppressWarnings("unchecked")
  private ChoiceBox<ManageBoxesView.Show> showChoice() {
    return (ChoiceBox<ManageBoxesView.Show>) mainWindow.getScene().getRoot().lookup("#showChoice");
  }

  @SuppressWarnings("unchecked")
  private TableView<Mailbox> table() {
    return (TableView<Mailbox>) mainWindow.getScene().getRoot().lookup("#boxTable");
  }

  private Button button(String id) {
    return (Button) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  @SuppressWarnings("unchecked")
  private TableRow<Mailbox> rowFor(String boxNumber) {
    table().layout();
    return table().lookupAll(".table-row-cell").stream()
        .map(node -> (TableRow<Mailbox>) node)
        .filter(row -> row.getItem() != null && boxNumber.equals(row.getItem().getBoxNumber()))
        .findFirst()
        .orElseThrow();
  }

  private static Stage detailsWindow() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#detailTitle") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElse(null));
  }

  private static String detail(Stage details, String id) {
    return FxTestSupport.call(() -> ((Label) details.getScene().lookup("#" + id)).getText());
  }

}
