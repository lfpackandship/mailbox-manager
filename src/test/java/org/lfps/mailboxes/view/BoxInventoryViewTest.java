package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
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
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.InventoryBox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for the Box Inventory screen and choosing an empty box on Add New
 * Box.
 */
class BoxInventoryViewTest {

  private static final Dialogs.Confirm REAL_CONFIRM = Dialogs.confirm;

  private static final Dialogs.AskText REAL_ASK_TEXT = Dialogs.askText;

  private final BoxInventoryRepository inventory = new BoxInventoryRepository();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void showInventory() throws SQLException {
    emptyDatabase();
    var mailboxes = new MailboxRepository();
    mailboxes.insert(new Mailbox(0, "Ada", "Lovelace", null, "5", null, "5551000001", null, null,
        LocalDate.of(2027, 1, 1), null));
    mailboxes.insert(new Mailbox(0, "Grace", "Hopper", null, "6", null, "5551000002", null, null, null, null,
        null, LocalDate.of(2026, 1, 1)));

    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      BoxInventoryView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void closeAllWindows() throws SQLException {
    Dialogs.confirm = REAL_CONFIRM;
    Dialogs.askText = REAL_ASK_TEXT;
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    // Other tests add boxes that aren't in an inventory.
    emptyDatabase();
  }

  @Test
  void anEmptyInventoryExplainsItselfAndOffersToAddRentedBoxes() {
    assertTrue(text("inventorySummary").startsWith("The box inventory hasn't been set up yet."));
    // The closed box 6 doesn't count as rented.
    assertEquals("Box 5 is rented but isn't in the inventory.", text("missingBoxesLabel"));

    FxTestSupport.run(() -> button("addMissingButton").fire());

    assertEquals("1 box: 1 rented, 0 empty.", text("inventorySummary"));
    assertNull(FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookup("#missingBoxesLabel")));
  }

  @Test
  void addsARangeOfBoxesAndShowsWhichAreRented() throws SQLException {
    FxTestSupport.run(() -> {
      field("addBoxesField").setText("1-10, 12A");
      field("addSizeField").setText("Small");
      button("addBoxesButton").fire();
    });

    assertEquals("Added 11 boxes.", text("inventoryStatusLabel"));
    assertEquals("11 boxes: 1 rented, 10 empty.", text("inventorySummary"));
    assertEquals("Small", inventory.findAll().get(0).getSize());

    FxTestSupport.run(() -> showChoice().setValue(BoxInventoryView.Show.RENTED));
    assertEquals(List.of("5"), boxNumbersShown());

    FxTestSupport.run(() -> showChoice().setValue(BoxInventoryView.Show.EMPTY));
    assertEquals(List.of("1", "2", "3", "4", "6", "7", "8", "9", "10", "12A"), boxNumbersShown());
  }

  @Test
  void addingBoxesAlreadyThereSaysSo() throws SQLException {
    inventory.add(List.of("1", "2"), null);
    FxTestSupport.run(() -> {
      BoxInventoryView.show(mainWindow);
      field("addBoxesField").setText("1-3");
      button("addBoxesButton").fire();
    });

    assertEquals("Added 1 box; 2 already in the inventory were left as they were.", text("inventoryStatusLabel"));
  }

  @Test
  void aBadRangeIsExplained() {
    FxTestSupport.run(() -> {
      field("addBoxesField").setText("10-1");
      button("addBoxesButton").fire();
    });

    assertEquals("The range 10-1 goes backwards. Put the smaller number first.", text("inventoryStatusLabel"));
  }

  @Test
  void addNewBoxRejectsANumberNotInTheInventory() throws SQLException {
    inventory.add(List.of("1", "2"), null);
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("John").setText("Tomás");
      fieldWithPrompt("Doe").setText("Rivera");
      fieldWithPrompt("310").setText("99");
      fieldWithPrompt("(555) 123-4567").setText("5552000006");
      buttonLabeled("Submit").fire();
    });

    var messages = FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookupAll(".label").stream()
        .map(node -> ((Label) node).getText())
        .collect(Collectors.toList()));
    assertTrue(messages.contains("Box 99 isn't in the box inventory. Check the number, or add the box on the "
        + "Box Inventory screen."), messages.toString());
    assertEquals(2, new MailboxRepository().findAll().size());
  }

  @Test
  void addNewBoxCanChooseAnEmptyBox() throws SQLException {
    inventory.add(List.of("4", "5", "6"), null);
    inventory.setSize(List.of("6"), "Large");
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      button("chooseBoxButton").fire();
    });

    var chooser = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#emptyBoxList") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow());
    @SuppressWarnings("unchecked")
    var list = FxTestSupport.call(() -> (ListView<InventoryBox>) chooser.getScene().lookup("#emptyBoxList"));
    // Box 5 is rented; box 6's holder closed it, so it's free again.
    assertEquals(List.of("4", "6"), FxTestSupport.call(() -> list.getItems().stream()
        .map(InventoryBox::getBoxNumber).collect(Collectors.toList())));

    FxTestSupport.run(() -> {
      list.getSelectionModel().select(1);
      ((Button) chooser.getScene().lookup("#chooseEmptyBoxButton")).fire();
    });

    assertEquals("6", FxTestSupport.call(() -> fieldWithPrompt("310").getText()));
  }

  @Test
  void countsEmptyBoxesBySize() throws SQLException {
    inventory.add(List.of("1", "2"), "small");
    inventory.add(List.of("3", "5"), "Small");
    inventory.add(List.of("4"), "Large");
    inventory.add(List.of("6"), null);
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));

    // Box 5 is rented; box 6 was closed, so it's empty.
    assertEquals("Large: 1 of 1 empty · small: 3 of 4 empty · No size: 1 of 1 empty", text("inventorySizeCounts"));
  }

  @Test
  void sizeCountsAreHiddenWhenNoBoxHasASize() throws SQLException {
    inventory.add(List.of("1", "2"), null);
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));

    assertNull(FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookup("#inventorySizeCounts")));
  }

  @Test
  void setSizeChangesTheSelectedBoxes() throws SQLException {
    inventory.add(List.of("1", "2", "3"), "Small");
    var questions = new ArrayList<String>();
    Dialogs.askText = (owner, title, question, initial) -> {
      questions.add(question + " [" + initial + "]");
      return Optional.of("Large");
    };
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));
    FxTestSupport.run(() -> {
      table().getSelectionModel().selectIndices(0, 2);
      button("setSizeButton").fire();
    });

    assertEquals(List.of("Size for 2 boxes, such as Small, Medium, or Large. Leave it blank to clear it. []"),
        questions);
    assertEquals(List.of("Large", "Small", "Large"), sizes());
  }

  @Test
  void setSizeForOneBoxStartsWithItsSizeAndCanBeCancelled() throws SQLException {
    inventory.add(List.of("1"), "Small");
    var initials = new ArrayList<String>();
    Dialogs.askText = (owner, title, question, initial) -> {
      initials.add(initial);
      return Optional.empty();
    };
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(0);
      button("setSizeButton").fire();
    });

    assertEquals(List.of("Small"), initials);
    assertEquals(List.of("Small"), sizes());
  }

  @Test
  void removeAsksThenRemovesTheSelectedBoxes() throws SQLException {
    inventory.add(List.of("1", "2", "3"), null);
    var questions = new ArrayList<String>();
    Dialogs.confirm = (owner, question, details) -> questions.add(question + " | " + details);
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(1);
      button("removeBoxesButton").fire();
    });

    assertEquals(List.of("Remove box 2 from the box inventory? | Anyone renting it keeps their box."), questions);
    assertEquals("Removed box 2.", text("inventoryStatusLabel"));
    assertEquals(List.of("1", "3"), inventory.findAll().stream().map(InventoryBox::getBoxNumber)
        .collect(Collectors.toList()));
  }

  @Test
  void answeringNoKeepsTheBoxes() throws SQLException {
    inventory.add(List.of("1"), null);
    Dialogs.confirm = (owner, question, details) -> false;
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));
    FxTestSupport.run(() -> {
      table().getSelectionModel().select(0);
      button("removeBoxesButton").fire();
    });

    assertEquals(1, inventory.findAll().size());
  }

  @Test
  void chooseSaysWhenTheInventoryIsntSetUp() {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      button("chooseBoxButton").fire();
    });

    var headers = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane)
        .map(w -> ((DialogPane) w.getScene().getRoot()).getHeaderText())
        .collect(Collectors.toList()));
    assertEquals(List.of("The box inventory hasn't been set up"), headers);
  }

  @Test
  void rentedBoxesShowWhoHasThem() throws SQLException {
    inventory.add(List.of("5", "7"), null);
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));

    var rows = FxTestSupport.call(() -> List.copyOf(table().getItems()));
    assertEquals("Ada Lovelace", rows.get(0).holder.getFullName());
    assertNull(rows.get(1).holder);
  }

  @Test
  void clickingAColumnHeaderSorts() throws SQLException {
    inventory.add(List.of("9", "10", "100"), null);
    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));
    FxTestSupport.run(() -> {
      var column = table().getColumns().get(0);
      column.setSortType(TableColumn.SortType.DESCENDING);
      table().getSortOrder().setAll(List.of(column));
      table().sort();
    });

    assertEquals(List.of("100", "10", "9"), boxNumbersShown());
  }

  @Test
  void describesLongListsOfBoxesBriefly() {
    assertEquals("1, 2, 3", BoxInventoryView.describeList(List.of("1", "2", "3")));
    assertEquals("1, 2, 3, 4, 5, 6", BoxInventoryView.describeList(List.of("1", "2", "3", "4", "5", "6")));
    assertEquals("1, 2, 3, 4, 5, and 2 more",
        BoxInventoryView.describeList(List.of("1", "2", "3", "4", "5", "6", "7")));
  }

  private static void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM prices");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
  }

  private List<String> sizes() throws SQLException {
    return inventory.findAll().stream().map(InventoryBox::getSize).collect(Collectors.toList());
  }

  private List<String> boxNumbersShown() {
    return FxTestSupport.call(() -> table().getItems().stream()
        .map(row -> row.box.getBoxNumber())
        .collect(Collectors.toList()));
  }

  @SuppressWarnings("unchecked")
  private TableView<BoxInventoryView.Row> table() {
    return (TableView<BoxInventoryView.Row>) mainWindow.getScene().getRoot().lookup("#inventoryTable");
  }

  @SuppressWarnings("unchecked")
  private ChoiceBox<BoxInventoryView.Show> showChoice() {
    return (ChoiceBox<BoxInventoryView.Show>) mainWindow.getScene().getRoot().lookup("#inventoryShowChoice");
  }

  private String text(String id) {
    return FxTestSupport.call(() -> ((Label) mainWindow.getScene().getRoot().lookup("#" + id)).getText());
  }

  private TextField field(String id) {
    return (TextField) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  private Button button(String id) {
    return (Button) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  private TextField fieldWithPrompt(String prompt) {
    return mainWindow.getScene().getRoot().lookupAll(".text-field").stream()
        .map(node -> (TextField) node)
        .filter(f -> prompt.equals(f.getPromptText()))
        .findFirst()
        .orElseThrow();
  }

  private Button buttonLabeled(String text) {
    return mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> text.equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

}
