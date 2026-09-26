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
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

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
    var headers = FxTestSupport.call(() -> table().getColumns().stream()
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
        "detailForwarding")) {
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
  void describesHowFarAwayTheEndDateIs() {
    var today = LocalDate.of(2026, 9, 26);
    assertEquals("2 days overdue", RenewalsView.dueStatus(today.minusDays(2), today));
    assertEquals("1 day overdue", RenewalsView.dueStatus(today.minusDays(1), today));
    assertEquals("Due today", RenewalsView.dueStatus(today, today));
    assertEquals("In 1 day", RenewalsView.dueStatus(today.plusDays(1), today));
    assertEquals("In 30 days", RenewalsView.dueStatus(today.plusDays(30), today));
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
