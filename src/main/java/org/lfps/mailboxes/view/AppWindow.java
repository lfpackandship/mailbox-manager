package org.lfps.mailboxes.view;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.SystemInfo;

/**
 * Shows a screen in the main window beneath the app's menu bar, and applies
 * app-wide display settings such as the text size. The menu bar sits at the
 * top of the window on every platform for now; moving it to the macOS system
 * menu bar is planned. Its File, Go, and Help menus put the things done most
 * often, such as printing and going to each screen, a click away. Add new
 * menus and items in {@link #buildMenuBar(Stage)}.
 */
public final class AppWindow {

  private static HostServices hostServices;

  /**
   * Gives the app access to the operating system, for opening folders in
   * Explorer or Finder. Called once at start-up.
   *
   * @param services the application's host services
   */
  public static void init(HostServices services) {
    hostServices = services;
  }

  /**
   * Replaces the window's contents with the given screen and the menu bar.
   * The screen scrolls if it doesn't fit, as happens with larger text.
   *
   * @param stage the main window
   * @param content the screen to display
   */
  public static void show(Stage stage, Parent content) {
    // Fill the window when there's room, but never squeeze a screen shorter
    // than it wants to be; scroll instead.
    if (content instanceof Region) {
      ((Region) content).setMinHeight(Region.USE_PREF_SIZE);
    }
    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    scrollPane.setFitToHeight(true);
    scrollPane.getStyleClass().add("edge-to-edge");

    var root = new BorderPane(scrollPane);
    root.setTop(buildMenuBar(stage));
    applyTextSize(root);
    stage.setScene(new Scene(root));
    stage.show();
  }

  /**
   * Applies the text size setting to a window's contents.
   *
   * @param root the root of the window's scene
   */
  public static void applyTextSize(Parent root) {
    root.setStyle(textSize().style());
  }

  /**
   * Applies the text size setting to a dialog, such as an alert.
   *
   * @param dialog the dialog to size
   */
  public static void applyTextSize(Dialog<?> dialog) {
    dialog.getDialogPane().setStyle(textSize().style());
  }

  /**
   * Re-applies the text size setting to every open window, after it changes.
   */
  public static void applyTextSizeToOpenWindows() {
    var style = textSize().style();
    for (var window : Window.getWindows()) {
      if (window.getScene() != null && window.getScene().getRoot() != null) {
        window.getScene().getRoot().setStyle(style);
      }
    }
  }

  /**
   * Returns how much larger than normal the text is, for sizing the main
   * window to match.
   *
   * @return 1.0 for normal text, more for larger text
   */
  public static double textScale() {
    return textSize().scale();
  }

  /**
   * Returns whether folders can be opened in Explorer or Finder.
   *
   * @return {@code false} before {@link #init(HostServices)} is called, as in tests
   */
  public static boolean canOpenFolders() {
    return hostServices != null;
  }

  /**
   * Opens a folder in Explorer, Finder, or the Linux file manager.
   *
   * @param folder the folder to open
   */
  public static void openFolder(Path folder) {
    if (hostServices != null) {
      hostServices.showDocument(folder.toUri().toString());
    }
  }

  /**
   * Opens a web page in the user's browser.
   *
   * @param url the page's address
   */
  public static void openWebPage(String url) {
    if (hostServices != null) {
      hostServices.showDocument(url);
    }
  }

  private static TextSize textSize() {
    try {
      return TextSize.fromName(new SettingsRepository().get(Setting.TEXT_SIZE));
    } catch (SQLException e) {
      return TextSize.NORMAL;
    }
  }

  private static MenuBar buildMenuBar(Stage stage) {
    // "Shortcut" is Cmd on macOS and Ctrl elsewhere.
    var priceSheetItem = new MenuItem("Print Price Sheet…");
    priceSheetItem.setAccelerator(KeyCombination.keyCombination("Shortcut+P"));
    priceSheetItem.setOnAction(e -> PriceSheetView.showPriceSheet(stage));

    var remindersItem = new MenuItem("Print Renewal Reminders…");
    remindersItem.setOnAction(e -> printReminders(stage));

    var backUpItem = new MenuItem("Back Up Now…");
    backUpItem.setOnAction(e -> {
      try {
        var message = SettingsView.backUpNow(stage);
        if (message != null) {
          inform(stage, AlertType.INFORMATION, "Backup saved", message);
        }
      } catch (IllegalStateException ex) {
        inform(stage, AlertType.ERROR, "Backup not saved", ex.getMessage());
      }
    });

    var restoreItem = new MenuItem("Restore a Backup…");
    restoreItem.setOnAction(e -> RestoreView.show(stage, stage));

    var settingsItem = new MenuItem("Settings");
    settingsItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Comma"));
    settingsItem.setOnAction(e -> SettingsView.show(stage));

    var exitItem = new MenuItem("Exit");
    exitItem.setOnAction(e -> Platform.exit());

    var fileMenu = new Menu("File");
    fileMenu.getItems().addAll(priceSheetItem, remindersItem, new SeparatorMenuItem(),
        backUpItem, restoreItem, new SeparatorMenuItem(),
        settingsItem, new SeparatorMenuItem(), exitItem);

    // Every screen, in the main menu's order, so none is more than a click away.
    var goMenu = new Menu("Go");
    goMenu.getItems().addAll(
        goItem("Main Menu", "Shortcut+0", () -> MainMenuView.show(stage)),
        new SeparatorMenuItem(),
        goItem("Add New Box", "Shortcut+N", () -> AddBoxView.show(stage)),
        goItem("Manage Boxes", "Shortcut+1", () -> ManageBoxesView.show(stage)),
        goItem("Renewals", "Shortcut+2", () -> RenewalsView.show(stage)),
        goItem("Calendar", "Shortcut+3", () -> CalendarView.show(stage)),
        goItem("Payments", "Shortcut+4", () -> PaymentsView.show(stage)),
        goItem("Box Inventory", "Shortcut+5", () -> BoxInventoryView.show(stage)),
        new SeparatorMenuItem(),
        goItem("Prices…", null, () -> PricesView.show(stage)));

    var dataFolderItem = new MenuItem("Show Data Folder");
    dataFolderItem.setDisable(!canOpenFolders());
    dataFolderItem.setOnAction(e -> openFolder(Database.dataDir()));

    var aboutItem = new MenuItem("About Mailbox Manager");
    aboutItem.setOnAction(e -> inform(stage, AlertType.INFORMATION, "Mailbox Manager " + appVersion(),
        "Keeps track of rented mailboxes for Lake Forest Pack and Ship.\n\n"
            + "Your data is kept in " + Database.dataDir() + "\n\n"
            + "Java " + SystemInfo.javaVersion() + ", JavaFX " + SystemInfo.javafxVersion()));

    var helpMenu = new Menu("Help");
    helpMenu.getItems().addAll(dataFolderItem, new SeparatorMenuItem(), aboutItem);

    return new MenuBar(fileMenu, goMenu, helpMenu);
  }

  /** Opens the window for printing reminders for every box due, as listed on Renewals. */
  private static void printReminders(Stage stage) {
    List<Mailbox> boxes;
    try {
      boxes = RenewalsView.dueBoxes();
    } catch (SQLException e) {
      inform(stage, AlertType.ERROR, "Couldn't load the boxes", e.getMessage());
      return;
    }
    if (boxes.isEmpty()) {
      inform(stage, AlertType.INFORMATION, "No reminders to print",
          "No boxes are past due or due soon. Renewals lists them when there are.");
      return;
    }
    PriceSheetView.showReminders(stage, boxes);
  }

  private static MenuItem goItem(String text, String shortcut, Runnable show) {
    var item = new MenuItem(text);
    if (shortcut != null) {
      item.setAccelerator(KeyCombination.keyCombination(shortcut));
    }
    item.setOnAction(e -> show.run());
    return item;
  }

  /** Shows a message in a dialog, without waiting for it to be closed. */
  static void inform(Stage owner, AlertType type, String header, String message) {
    var alert = new Alert(type, message);
    alert.initOwner(owner);
    alert.setTitle("Mailbox Manager");
    alert.setHeaderText(header);
    applyTextSize(alert);
    alert.show();
  }

  /**
   * Returns the app's version, such as "1.7.0", from the jar or installer
   * it's running from.
   */
  static String appVersion() {
    var version = AppWindow.class.getPackage() == null ? null : AppWindow.class.getPackage().getImplementationVersion();
    return version == null ? "(development version)" : version;
  }

  private AppWindow() {
  }

}
