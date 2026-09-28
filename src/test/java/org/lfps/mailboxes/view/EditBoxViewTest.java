package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for the Edit Box form's notes, closed boxes, and box number
 * checks.
 */
class EditBoxViewTest {

  private static final LocalDate CLOSED_ON = LocalDate.of(2026, 5, 1);

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

  private boolean done;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void emptyDatabase() throws SQLException {
    clear();
    mailboxes.insert(box("1", null, null));
    mailboxes.insert(box("2", "Old note", null));
    mailboxes.insert(box("3", null, CLOSED_ON));
    mainWindow = FxTestSupport.call(Stage::new);
  }

  @AfterEach
  void closeAllWindows() throws SQLException {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    // Other tests add boxes that aren't in an inventory.
    clear();
  }

  @Test
  void showsAndSavesNotes() throws SQLException {
    edit("2");
    assertEquals("Old note", FxTestSupport.call(() -> notesField().getText()));

    FxTestSupport.run(() -> {
      notesField().setText("New note\nSecond line");
      save();
    });

    assertTrue(done);
    assertEquals("New note\nSecond line", find("2").getNotes());
  }

  @Test
  void clearingNotesRemovesThem() throws SQLException {
    edit("2");
    FxTestSupport.run(() -> {
      notesField().setText("   ");
      save();
    });

    assertNull(find("2").getNotes());
  }

  @Test
  void savingAClosedBoxKeepsItClosed() throws SQLException {
    edit("3");
    FxTestSupport.run(() -> {
      notesField().setText("Moved away");
      save();
    });

    var closed = find("3");
    assertEquals(CLOSED_ON, closed.getClosedDate());
    assertEquals("Moved away", closed.getNotes());
  }

  @Test
  void aClosedBoxCanTakeANumberThatsInUse() throws SQLException {
    // So a closed box can be renumbered, e.g. back to its own number after a mix-up.
    edit("3");
    FxTestSupport.run(() -> {
      boxNumberField().setText("1");
      save();
    });

    assertTrue(done);
    assertEquals(2, mailboxes.findAll().stream().filter(m -> m.getBoxNumber().equals("1")).count());
  }

  @Test
  void anOpenBoxCantTakeAnotherOpenBoxsNumber() throws SQLException {
    edit("2");
    FxTestSupport.run(() -> {
      boxNumberField().setText(" 1 ");
      save();
    });

    assertEquals("Box 1 is already assigned to someone else.", result());
    assertEquals("Old note", find("2").getNotes());
  }

  @Test
  void aNewNumberMustBeInTheInventoryOnceItsSetUp() throws SQLException {
    new BoxInventoryRepository().add(List.of("1", "5"), null);
    edit("2");
    FxTestSupport.run(() -> {
      boxNumberField().setText("6");
      save();
    });

    assertEquals("Box 6 isn't in the box inventory. Check the number, or add the box on the Box Inventory screen.",
        result());

    FxTestSupport.run(() -> {
      boxNumberField().setText("5");
      save();
    });
    assertTrue(done);
    assertEquals("5", mailboxes.findAll().stream().filter(m -> "Old note".equals(m.getNotes()))
        .findFirst().orElseThrow().getBoxNumber());
  }

  @Test
  void keepingANumberThatIsntInTheInventoryIsAllowed() throws SQLException {
    // Box 2 was rented before the inventory was set up without it.
    new BoxInventoryRepository().add(List.of("1"), null);
    edit("2");
    FxTestSupport.run(() -> {
      notesField().setText("Still here");
      save();
    });

    assertTrue(done);
    assertEquals("Still here", find("2").getNotes());
  }

  @Test
  void theNamesCanBeLeftBlank() throws SQLException {
    edit("2");
    FxTestSupport.run(() -> {
      textField("#firstNameField").setText("");
      textField("#lastNameField").setText("  ");
      save();
    });

    assertTrue(done);
    assertEquals("", find("2").getFullName());
  }

  private void edit(String boxNumber) throws SQLException {
    var mailbox = find(boxNumber);
    FxTestSupport.run(() -> EditBoxView.show(mainWindow, mailbox, () -> done = true));
  }

  private Mailbox find(String boxNumber) throws SQLException {
    return mailboxes.findAll().stream().filter(m -> m.getBoxNumber().equals(boxNumber)).findFirst().orElseThrow();
  }

  private TextArea notesField() {
    return (TextArea) mainWindow.getScene().getRoot().lookup("#notesField");
  }

  private TextField boxNumberField() {
    return textField("#boxNumberField");
  }

  private TextField textField(String selector) {
    return (TextField) mainWindow.getScene().getRoot().lookup(selector);
  }

  private void save() {
    mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> "Save".equals(b.getText()))
        .findFirst()
        .orElseThrow()
        .fire();
  }

  private String result() {
    return FxTestSupport.call(() -> ((Label) mainWindow.getScene().getRoot().lookup("#resultLabel")).getText());
  }

  private static Mailbox box(String boxNumber, String notes, LocalDate closedDate) {
    return new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "(555) 100-0001", null, null, null, null,
        notes, closedDate);
  }

  private static void clear() throws SQLException {
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
  }

}
