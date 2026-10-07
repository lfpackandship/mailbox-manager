package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;

/**
 * UI tests for the Settings window: opening it, its tabs, and saving or
 * rejecting settings.
 */
class SettingsViewTest {

  private final SettingsRepository settings = new SettingsRepository();

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
  void settingsMenuItemOpensWindowOnTopOfMainWindow() {
    FxTestSupport.run(() -> fileMenuItem("Settings").fire());

    var window = settingsWindow();
    assertNotNull(window);
    assertSame(mainWindow, FxTestSupport.call(window::getOwner));
    assertTrue(FxTestSupport.call(mainWindow::isShowing));
  }

  @Test
  void shortcutOpensSettings() {
    FxTestSupport.run(() ->
        FxTestSupport.pressWithShortcut(mainWindow.getScene().getRoot(), KeyCode.COMMA));

    assertNotNull(settingsWindow());
  }

  @Test
  void openingAgainReusesTheOpenWindow() {
    FxTestSupport.run(() -> SettingsView.show(mainWindow));
    var first = settingsWindow();

    FxTestSupport.run(() -> SettingsView.show(mainWindow));

    assertSame(first, settingsWindow());
    assertEquals(1, FxTestSupport.call(() -> settingsWindows().size()));
  }

  @Test
  void showsSavedValues() throws SQLException {
    settings.put(Setting.RENEWAL_WINDOW_DAYS, "45");

    var window = openSettings();

    assertEquals("45", FxTestSupport.call(() -> field(window, "renewalWindowField").getText()));
    assertEquals("30", FxTestSupport.call(() -> field(window, "backupsToKeepField").getText()));
  }

  @Test
  void saveStoresValidValues() throws SQLException {
    var window = openSettings();

    FxTestSupport.run(() -> {
      field(window, "renewalWindowField").setText(" 60 ");
      field(window, "backupsToKeepField").setText("10");
      button(window, "saveButton").fire();
    });

    assertEquals("Saved", FxTestSupport.call(() -> resultLabel(window).getText()));
    assertEquals(60, settings.getInt(Setting.RENEWAL_WINDOW_DAYS));
    assertEquals(10, settings.getInt(Setting.BACKUPS_TO_KEEP));
    assertTrue(FxTestSupport.call(window::isShowing));
  }

  @Test
  void savesTheKeyDepositAndThePriceSheetWording() throws SQLException {
    var window = openSettings();
    assertEquals("$10.00", FxTestSupport.call(() -> field(window, "keyDepositField").getText()));
    assertEquals("Lake Forest Pack and Ship", FxTestSupport.call(() -> field(window, "shopNameField").getText()));

    FxTestSupport.run(() -> {
      field(window, "keyDepositField").setText("12.5");
      field(window, "shopNameField").setText("  Box Shop ");
      ((TextInputControl) window.getScene().lookup("#reminderMessageField")).setText("Please renew.");
      button(window, "saveButton").fire();
    });

    assertEquals("Saved", FxTestSupport.call(() -> resultLabel(window).getText()));
    assertEquals("$12.50", settings.get(Setting.KEY_DEPOSIT));
    assertEquals("$12.50", FxTestSupport.call(() -> field(window, "keyDepositField").getText()));
    assertEquals("Box Shop", settings.get(Setting.SHOP_NAME));
    assertEquals("Please renew.", settings.get(Setting.REMINDER_MESSAGE));
  }

  @Test
  void aBlankKeyDepositMeansNoneAndANonsenseOneIsRejected() throws SQLException {
    var window = openSettings();

    FxTestSupport.run(() -> {
      field(window, "keyDepositField").setText("ten");
      button(window, "saveButton").fire();
    });
    assertTrue(FxTestSupport.call(() -> resultLabel(window).getText()).startsWith("Key deposit per key"));
    assertEquals("$10.00", settings.get(Setting.KEY_DEPOSIT));

    FxTestSupport.run(() -> {
      field(window, "keyDepositField").setText("");
      button(window, "saveButton").fire();
    });
    assertEquals("", settings.get(Setting.KEY_DEPOSIT));
  }

  @Test
  void saveRejectsInvalidValuesAndStoresNothing() throws SQLException {
    var window = openSettings();

    FxTestSupport.run(() -> {
      field(window, "renewalWindowField").setText("90");
      field(window, "backupsToKeepField").setText("0");
      button(window, "saveButton").fire();
    });

    var message = FxTestSupport.call(() -> resultLabel(window).getText());
    assertTrue(message.contains("Backups to keep must be a whole number from 1 to 365."), message);
    assertFalse(message.contains("Renewal window"), message);
    assertEquals(30, settings.getInt(Setting.RENEWAL_WINDOW_DAYS));
    assertEquals(30, settings.getInt(Setting.BACKUPS_TO_KEEP));
  }

  @Test
  void settingsAreGroupedIntoTabsThatEachScroll() {
    var window = openSettings();

    var names = FxTestSupport.call(() -> tabs(window).getTabs().stream()
        .map(Tab::getText).collect(Collectors.toList()));
    assertEquals(List.of("General", "Price Sheet and Reminders", "Backups", "About"), names);
    assertEquals("General", selectedTab(window));
    for (var tab : FxTestSupport.call(() -> tabs(window).getTabs())) {
      var content = FxTestSupport.call(tab::getContent);
      assertTrue(content instanceof ScrollPane, "Not scrollable: " + tab.getText());
    }
  }

  @Test
  void aboutShowsTheAppsVersion() {
    var window = openSettings();

    assertEquals(AppWindow.appVersion(), FxTestSupport.call(() -> ((Label) window.getScene()
        .lookup("#appVersionLabel")).getText()));
  }

  @Test
  void aMistakeOnAnotherTabShowsThatTab() {
    var window = openSettings();

    FxTestSupport.run(() -> {
      field(window, "backupsToKeepField").setText("0");
      button(window, "saveButton").fire();
    });

    assertEquals("Backups", selectedTab(window));
  }

  @Test
  void theWindowFitsOnTheScreenWithExtraLargeText() throws SQLException {
    settings.put(Setting.TEXT_SIZE, TextSize.EXTRA_LARGE.name());
    try {
      var window = openSettings();

      var screen = FxTestSupport.call(() -> Screen.getPrimary().getVisualBounds());
      assertTrue(FxTestSupport.call(window::getHeight) <= screen.getHeight() + 0.5);
      assertTrue(FxTestSupport.call(window::getWidth) <= screen.getWidth() + 0.5);
    } finally {
      settings.put(Setting.TEXT_SIZE, TextSize.NORMAL.name());
    }
  }

  @Test
  void enterSaves() throws SQLException {
    var window = openSettings();

    FxTestSupport.run(() -> {
      var renewalWindow = field(window, "renewalWindowField");
      renewalWindow.setText("21");
      FxTestSupport.press(renewalWindow, KeyCode.ENTER);
    });

    assertEquals(21, settings.getInt(Setting.RENEWAL_WINDOW_DAYS));
  }

  @Test
  void closeButtonClosesWindowWithoutSaving() throws SQLException {
    var window = openSettings();

    FxTestSupport.run(() -> {
      field(window, "renewalWindowField").setText("90");
      button(window, "closeButton").fire();
    });

    assertFalse(FxTestSupport.call(window::isShowing));
    assertNull(settingsWindow());
    assertTrue(FxTestSupport.call(mainWindow::isShowing));
    assertEquals(30, settings.getInt(Setting.RENEWAL_WINDOW_DAYS));
  }

  @Test
  void escapeClosesWindow() {
    var window = openSettings();

    FxTestSupport.run(() -> FxTestSupport.press(field(window, "renewalWindowField"), KeyCode.ESCAPE));

    assertFalse(FxTestSupport.call(window::isShowing));
  }

  @Test
  void reopeningAfterCloseShowsNewlySavedValues() {
    var window = openSettings();
    FxTestSupport.run(() -> {
      field(window, "renewalWindowField").setText("14");
      button(window, "saveButton").fire();
      button(window, "closeButton").fire();
    });

    var reopened = openSettings();

    assertNotSame(window, reopened);
    assertEquals("14", FxTestSupport.call(() -> field(reopened, "renewalWindowField").getText()));
  }

  /** Opens the Settings window and returns it. */
  private Stage openSettings() {
    FxTestSupport.run(() -> SettingsView.show(mainWindow));
    return settingsWindow();
  }

  /** Returns the item with the given name in the File menu. */
  private MenuItem fileMenuItem(String text) {
    var menuBar = (MenuBar) mainWindow.getScene().getRoot().lookup(".menu-bar");
    return menuBar.getMenus().stream()
        .filter(menu -> menu.getText().equals("File"))
        .flatMap(menu -> menu.getItems().stream())
        .filter(item -> text.equals(item.getText()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * Returns the open Settings window, or {@code null} if there isn't one.
   */
  private static Stage settingsWindow() {
    return FxTestSupport.call(() -> {
      var windows = settingsWindows();
      return windows.isEmpty() ? null : windows.get(0);
    });
  }

  /** Returns every open Settings window, to check there is only one. */
  private static List<Stage> settingsWindows() {
    return Window.getWindows().stream()
        .filter(w -> w instanceof Stage && "Settings".equals(((Stage) w).getTitle()))
        .map(w -> (Stage) w)
        .collect(Collectors.toList());
  }

  /** Returns the text field with the given id on a window. */
  private static TextField field(Stage window, String id) {
    return (TextField) window.getScene().lookup("#" + id);
  }

  /** Returns the button with the given id on a window. */
  private static Button button(Stage window, String id) {
    return (Button) window.getScene().lookup("#" + id);
  }

  /** Returns the Settings window's tabs. */
  private static TabPane tabs(Stage window) {
    return (TabPane) window.getScene().lookup(".tab-pane");
  }

  /** Returns the name of the tab shown. */
  private static String selectedTab(Stage window) {
    return FxTestSupport.call(() -> tabs(window).getSelectionModel().getSelectedItem().getText());
  }

  /** Returns the label under the Save button. */
  private static Label resultLabel(Stage window) {
    return (Label) window.getScene().lookup("#resultLabel");
  }

}
