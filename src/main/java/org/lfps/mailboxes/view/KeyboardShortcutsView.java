package org.lfps.mailboxes.view;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * A separate window, opened with Help → Keyboard Shortcuts, listing every
 * keyboard shortcut in the app. The menu shortcuts are read from the menus
 * themselves, so one added to a menu shows up here without changing this
 * class; the keys that work in lists and windows are listed by hand below
 * them. Only one is open at a time.
 */
final class KeyboardShortcutsView {

  /**
   * The keys that work in lists and windows rather than from a menu, each as
   * the key and what it does. Keep it in step with the screens: Enter on a
   * list is {@link BoxDetailsView#openOnDoubleClickOrEnter}, and the blue
   * and Esc buttons are those set with {@code setDefaultButton} and
   * {@code setCancelButton}.
   */
  private static final List<String[]> OTHER_KEYS = List.of(
      new String[] { "Enter", "On Manage Boxes, Renewals, or a day on the Calendar, opens the selected box's details" },
      new String[] { "Enter", "Clicks the button shown in blue, such as Renew or Save" },
      new String[] { "Esc", "Closes a window with a Cancel or Close button, such as Renew or a box's details, "
          + "without saving" },
      new String[] { "Tab", "Moves to the next field or button; Shift+Tab moves back" },
      new String[] { "Space", "Clicks the button, or ticks the box, that's outlined" });

  /** The open shortcuts window, or {@code null} if none is open. */
  private static Stage window;

  /**
   * Opens the shortcuts window on top of the main window, or brings it to the
   * front if it's already open.
   *
   * @param owner the main window
   * @param menus the menus whose shortcuts to list, in the menu bar's order
   */
  static void show(Stage owner, List<Menu> menus) {
    if (window != null) {
      window.toFront();
      return;
    }

    var grid = new GridPane();
    grid.setId("shortcutsGrid");
    grid.setHgap(24);
    grid.setVgap(6);
    var row = 0;
    for (var menu : menus) {
      var first = true;
      for (var item : menu.getItems()) {
        if (item.getAccelerator() == null) {
          continue;
        }
        // Only menus with a shortcut get a heading; Help has none.
        if (first) {
          row = heading(grid, row, menu.getText() + " menu");
          first = false;
        }
        row = shortcut(grid, row, item.getAccelerator().getDisplayText(), menuItemName(item));
      }
    }
    row = heading(grid, row, "In lists and windows");
    for (var key : OTHER_KEYS) {
      row = shortcut(grid, row, key[0], key[1]);
    }

    var note = new Label("On a Mac, ⌘ is the Command key. Elsewhere, Ctrl is the Control key.");
    note.setWrapText(true);

    var stage = new Stage();
    var closeBtn = new Button("Close");
    closeBtn.setId("shortcutsCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setDefaultButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var scroll = new ScrollPane(grid);
    scroll.setFitToWidth(true);
    scroll.setStyle("-fx-background-color: transparent;");

    var content = new VBox(12, scroll, note, new HBox(closeBtn));
    content.setPadding(new Insets(20));
    content.setStyle("-fx-pref-width: 40em;");
    AppWindow.applyTextSize(content);

    stage.initOwner(owner);
    stage.setTitle("Keyboard Shortcuts");
    stage.setScene(new Scene(content));
    stage.setOnHidden(e -> {
      if (window == stage) {
        window = null;
      }
    });
    window = stage;
    AppWindow.showWithinScreen(stage);
  }

  /**
   * Returns a menu item's name as the list shows it, without the "…" that
   * means it opens a window.
   *
   * @param item the menu item
   * @return its name, such as "Print Price Sheet"
   */
  static String menuItemName(MenuItem item) {
    return item.getText().replace("…", "");
  }

  /**
   * Adds a section heading, such as "Go menu", to the list.
   *
   * @param grid the list
   * @param row the row to add it on
   * @param text the heading
   * @return the next free row
   */
  private static int heading(GridPane grid, int row, String text) {
    var label = new Label(text);
    label.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
    // Space above every heading but the first sets the sections apart.
    GridPane.setMargin(label, new Insets(row == 0 ? 0 : 12, 0, 0, 0));
    grid.add(label, 0, row, 2, 1);
    return row + 1;
  }

  /**
   * Adds a shortcut to the list: the keys, and what they do.
   *
   * @param grid the list
   * @param row the row to add it on
   * @param keys the keys, such as "Ctrl+P"
   * @param action what they do
   * @return the next free row
   */
  private static int shortcut(GridPane grid, int row, String keys, String action) {
    var keysLabel = new Label(keys);
    keysLabel.setStyle("-fx-font-weight: bold;");
    keysLabel.setMinWidth(Label.USE_PREF_SIZE);
    var actionLabel = new Label(action);
    actionLabel.setWrapText(true);
    grid.add(keysLabel, 0, row);
    grid.add(actionLabel, 1, row);
    return row + 1;
  }

  /**
   * Closes the shortcuts window if it's open.
   */
  static void close() {
    if (window != null) {
      window.close();
    }
  }

  /** Not used: the window is built with static methods. */
  private KeyboardShortcutsView() {
  }

}
