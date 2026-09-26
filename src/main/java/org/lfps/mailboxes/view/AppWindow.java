package org.lfps.mailboxes.view;

import java.nio.file.Path;
import java.sql.SQLException;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;

/**
 * Shows a screen in the main window beneath the app's menu bar, and applies
 * app-wide display settings such as the text size. The menu bar sits at the
 * top of the window on every platform for now; moving it to the macOS system
 * menu bar is planned. Add new menus and items in {@link #buildMenuBar(Stage)}.
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

  private static TextSize textSize() {
    try {
      return TextSize.fromName(new SettingsRepository().get(Setting.TEXT_SIZE));
    } catch (SQLException e) {
      return TextSize.NORMAL;
    }
  }

  private static MenuBar buildMenuBar(Stage stage) {
    // "Shortcut" is Cmd on macOS and Ctrl elsewhere.
    var settingsItem = new MenuItem("Settings");
    settingsItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Comma"));
    settingsItem.setOnAction(e -> SettingsView.show(stage));

    var exitItem = new MenuItem("Exit");
    exitItem.setOnAction(e -> Platform.exit());

    var fileMenu = new Menu("File");
    fileMenu.getItems().addAll(settingsItem, new SeparatorMenuItem(), exitItem);

    return new MenuBar(fileMenu);
  }

  private AppWindow() {
  }

}
