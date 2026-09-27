package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
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
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for opening a box's details from the Renewals screen.
 */
class RenewalsViewTest {

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void showRenewals() throws SQLException {
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
    var mailboxes = new MailboxRepository();
    mailboxes.insert(box("101", "Overdue", LocalDate.now().minusDays(2)));
    mailboxes.insert(box("102", "Soon", LocalDate.now().plusDays(5)));

    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      RenewalsView.show(stage);
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
  void doubleClickingAPastDueBoxOpensItsDetails() {
    FxTestSupport.run(() -> Event.fireEvent(firstRow("pastDueTable"), new MouseEvent(MouseEvent.MOUSE_CLICKED,
        0, 0, 0, 0, MouseButton.PRIMARY, 2, false, false, false, false, true, false, false, true, false, false,
        null)));

    var details = detailsWindow();
    assertNotNull(details);
    assertEquals("Box 101 – Ada Overdue", FxTestSupport.call(details::getTitle));
  }

  @Test
  void enterOnAnUpcomingBoxOpensItsDetails() {
    FxTestSupport.run(() -> {
      var table = table("upcomingTable");
      table.getSelectionModel().select(0);
      FxTestSupport.press(table, KeyCode.ENTER);
    });

    assertEquals("Box 102 – Ada Soon", FxTestSupport.call(detailsWindow()::getTitle));
  }

  @Test
  void viewButtonOpensTheSelectedBoxsDetails() {
    FxTestSupport.run(() -> {
      table("upcomingTable").getSelectionModel().select(0);
      button("upcomingViewButton").fire();
    });

    assertEquals("Box 102 – Ada Soon", FxTestSupport.call(detailsWindow()::getTitle));
  }

  @Test
  void editingFromTheDetailsReturnsToRenewals() {
    FxTestSupport.run(() -> {
      table("pastDueTable").getSelectionModel().select(0);
      button("pastDueViewButton").fire();
    });
    var details = detailsWindow();

    FxTestSupport.run(() -> ((Button) details.getScene().lookup("#detailsEditButton")).fire());
    assertNull(detailsWindow());
    assertNull(FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookup("#pastDueTable")));

    FxTestSupport.run(() -> mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> "Save".equals(b.getText()))
        .findFirst()
        .orElseThrow()
        .fire());

    assertTrue(FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookup("#pastDueTable") != null));
  }

  @Test
  void closedBoxesAreLeftOff() throws SQLException {
    new MailboxRepository().insert(new Mailbox(0, "Ada", "Closed", null, "103", null, "(555) 123-4567", null,
        null, LocalDate.now().minusDays(1), null, null, LocalDate.now().minusDays(1)));
    FxTestSupport.run(() -> RenewalsView.show(mainWindow));

    assertEquals(List.of("101"), FxTestSupport.call(() -> table("pastDueTable").getItems().stream()
        .map(Mailbox::getBoxNumber).collect(Collectors.toList())));
  }

  @Test
  void renewingFromTheDetailsRefreshesRenewals() {
    FxTestSupport.run(() -> {
      table("pastDueTable").getSelectionModel().select(0);
      button("pastDueViewButton").fire();
    });
    var details = detailsWindow();
    FxTestSupport.run(() -> ((Button) details.getScene().lookup("#detailsRenewButton")).fire());
    assertNull(detailsWindow());

    var renew = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#renewSaveButton") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow());
    FxTestSupport.run(() -> {
      ((DatePicker) renew.getScene().lookup("#renewEndField")).setValue(LocalDate.now().plusYears(1));
      ((Button) renew.getScene().lookup("#renewSaveButton")).fire();
    });

    assertEquals(List.of(), FxTestSupport.call(() -> List.copyOf(table("pastDueTable").getItems())));
  }

  @Test
  void renewIsDisabledUntilABoxIsSelected() {
    assertTrue(FxTestSupport.call(() -> button("upcomingRenewButton").isDisabled()));
    FxTestSupport.run(() -> table("upcomingTable").getSelectionModel().select(0));
    assertFalse(FxTestSupport.call(() -> button("upcomingRenewButton").isDisabled()));
  }

  private static Mailbox box(String boxNumber, String lastName, LocalDate endDate) {
    return new Mailbox(0, "Ada", lastName, null, boxNumber, null, "(555) 123-4567", null, null, endDate, null);
  }

  @SuppressWarnings("unchecked")
  private TableView<Mailbox> table(String id) {
    return (TableView<Mailbox>) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  private Button button(String id) {
    return (Button) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  @SuppressWarnings("unchecked")
  private TableRow<Mailbox> firstRow(String tableId) {
    var table = table(tableId);
    table.layout();
    return table.lookupAll(".table-row-cell").stream()
        .map(node -> (TableRow<Mailbox>) node)
        .filter(row -> row.getItem() != null)
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

}
