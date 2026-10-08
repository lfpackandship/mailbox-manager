package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
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
 * Tests for the menu bar at the top of the main window.
 */
class MenuBarTest {

  private final Function<Window, File> realChooseFolder = SettingsView.chooseFolder;

  private final Predicate<String> realConfirm = RestoreView.confirm;

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
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM box_inventory");
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
    SettingsView.chooseFolder = realChooseFolder;
    RestoreView.confirm = realConfirm;
  }

  @Test
  void theMenusHoldWhatsDoneMostOften() {
    assertEquals(List.of("File", "Go", "Help"), FxTestSupport.call(() -> menuBar().getMenus().stream()
        .map(menu -> menu.getText()).collect(Collectors.toList())));
    assertEquals(List.of("Print Price Sheet…", "Print Renewal Reminders…", "Forwarding", "Back Up Now…",
        "Restore a Backup…", "Settings", "Exit"), items("File"));
    assertEquals(List.of("Main Menu", "Add New Box", "Manage Boxes", "Find a Box", "Renewals", "Calendar",
        "Payments", "Box Inventory", "Prices…"), items("Go"));
  }

  @Test
  void findABoxOpensManageBoxesReadyToSearch() {
    FxTestSupport.run(() -> item("Go", "Find a Box").fire());
    // The cursor is placed once the screen has appeared.
    FxTestSupport.run(() -> { });

    var search = FxTestSupport.call(() -> (TextField) mainWindow.getScene().getRoot().lookup("#searchField"));
    assertTrue(FxTestSupport.call(() -> mainWindow.getScene().getFocusOwner() == search));
    assertEquals("Shortcut+F", FxTestSupport.call(() -> item("Go", "Find a Box").getAccelerator().getName()));
  }

  @Test
  void findABoxOnManageBoxesSelectsTheSearchSoTypingReplacesIt() {
    FxTestSupport.run(() -> {
      ManageBoxesView.show(mainWindow);
      ((TextField) mainWindow.getScene().getRoot().lookup("#searchField")).setText("lovelace");
    });
    FxTestSupport.run(() -> { });
    FxTestSupport.run(() -> item("Go", "Find a Box").fire());
    FxTestSupport.run(() -> { });

    var search = FxTestSupport.call(() -> (TextField) mainWindow.getScene().getRoot().lookup("#searchField"));
    assertEquals("lovelace", FxTestSupport.call(search::getSelectedText));
    assertTrue(FxTestSupport.call(() -> mainWindow.getScene().getFocusOwner() == search));
  }

  @Test
  void manageBoxesStartsWithTheCursorInTheSearch() {
    // Checked before the window is next drawn too: a cursor placed later than
    // that could lose the race with the screen giving it to Back.
    assertTrue(FxTestSupport.call(() -> {
      item("Go", "Manage Boxes").fire();
      return mainWindow.getScene().getFocusOwner() == mainWindow.getScene().getRoot().lookup("#searchField");
    }));
    FxTestSupport.run(() -> { });

    assertTrue(FxTestSupport.call(() -> mainWindow.getScene().getFocusOwner()
        == mainWindow.getScene().getRoot().lookup("#searchField")));
  }

  @Test
  void forwardingOpensTheForwardingScreen() {
    FxTestSupport.run(() -> item("File", "Forwarding").fire());

    assertTrue(FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookup("#forwardingTabs") != null));
  }

  @Test
  void goOpensEachScreen() {
    FxTestSupport.run(() -> item("Go", "Renewals").fire());
    assertNotNull(FxTestSupport.call(() -> mainWindow.getScene().lookup("#pastDueTable")));

    FxTestSupport.run(() -> item("Go", "Manage Boxes").fire());
    assertNotNull(FxTestSupport.call(() -> mainWindow.getScene().lookup("#boxTable")));

    // Every screen has the menu bar.
    FxTestSupport.run(() -> item("Go", "Payments").fire());
    assertNotNull(FxTestSupport.call(() -> mainWindow.getScene().lookup("#paymentsTable")));
  }

  @Test
  void printPriceSheetOpensThePrintWindow() {
    FxTestSupport.run(() -> item("File", "Print Price Sheet…").fire());

    assertEquals("Print Price Sheet", FxTestSupport.call(() -> windowWith("#printButton").getTitle()));
  }

  @Test
  void printRenewalRemindersOffersTheBoxesDue() throws SQLException {
    new MailboxRepository().insert(new Mailbox(0, "Ada", "Overdue", null, "101", null, "", null, null,
        LocalDate.now().minusDays(2), null));
    new MailboxRepository().insert(new Mailbox(0, "Ada", "Later", null, "102", null, "", null, null,
        LocalDate.now().plusYears(1), null));

    FxTestSupport.run(() -> item("File", "Print Renewal Reminders…").fire());

    var window = FxTestSupport.call(() -> windowWith("#printBoxList"));
    assertEquals(1, FxTestSupport.call(() -> ((ListView<?>) window.getScene().lookup("#printBoxList"))
        .getItems().size()));
  }

  @Test
  void backUpNowSavesABackupWhereChosen() throws Exception {
    var folder = Files.createTempDirectory(Database.dataDir(), "usb");
    SettingsView.chooseFolder = owner -> folder.toFile();

    FxTestSupport.run(() -> item("File", "Back Up Now…").fire());

    try (var files = Files.list(folder)) {
      assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("mailboxes-backup-")));
    }
  }

  @Test
  void restoringFromTheFileMenuKeepsTheMainWindowOpen() {
    Database.backupDaily();
    RestoreView.confirm = message -> true;

    FxTestSupport.run(() -> item("File", "Restore a Backup…").fire());
    var restore = FxTestSupport.call(() -> windowWith("#backupList"));
    FxTestSupport.run(() -> {
      ((ListView<?>) restore.getScene().lookup("#backupList")).getSelectionModel().select(0);
      ((Button) restore.getScene().lookup("#restoreSelectedButton")).fire();
    });

    assertTrue(FxTestSupport.call(mainWindow::isShowing));
    assertTrue(FxTestSupport.call(() -> !restore.isShowing()));
  }

  /** Returns the main window's menu bar. */
  private MenuBar menuBar() {
    return (MenuBar) mainWindow.getScene().getRoot().lookup(".menu-bar");
  }

  /** Returns the names of a menu's items, leaving out separators. */
  private List<String> items(String menu) {
    return FxTestSupport.call(() -> menuBar().getMenus().stream()
        .filter(m -> m.getText().equals(menu))
        .flatMap(m -> m.getItems().stream())
        .filter(item -> !(item instanceof SeparatorMenuItem))
        .map(MenuItem::getText)
        .collect(Collectors.toList()));
  }

  /** Returns the item with the given name in a menu. */
  private MenuItem item(String menu, String text) {
    return menuBar().getMenus().stream()
        .filter(m -> m.getText().equals(menu))
        .flatMap(m -> m.getItems().stream())
        .filter(item -> text.equals(item.getText()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * Returns the open window containing something matching a selector, or {@code
   * null}.
   */
  private static Stage windowWith(String selector) {
    return Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup(selector) != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow();
  }

}
