package org.lfps.mailboxes.view;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Shows a screen in the main window beneath the app's menu bar. The menu bar
 * sits at the top of the window on every platform for now; moving it to the
 * macOS system menu bar is planned. Add new menus and items in
 * {@link #buildMenuBar(Stage)}.
 */
public final class AppWindow {

  /**
   * Replaces the window's contents with the given screen and the menu bar.
   *
   * @param stage the main window
   * @param content the screen to display
   */
  public static void show(Stage stage, Parent content) {
    var root = new BorderPane(content);
    root.setTop(buildMenuBar(stage));
    stage.setScene(new Scene(root));
    stage.show();
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
