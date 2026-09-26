package org.lfps.mailboxes.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/**
 * The application's main menu: a 2x2 grid linking to Add New Box, Manage
 * Boxes, and Calendar, with a placeholder fourth tile.
 */
public class MainMenuView {

  /**
   * Builds and displays the main menu on the given stage.
   *
   * @param stage the window to render the menu into
   */
  public static void show(Stage stage) {
    var addBoxBtn = new Button("Add New Box");
    var manageBoxesBtn = new Button("Manage Boxes");
    var calendarBtn = new Button("Calendar");
    var comingSoonBtn = new Button("Coming Soon");
    comingSoonBtn.setDisable(true);

    for (var btn : new Button[] { addBoxBtn, manageBoxesBtn, calendarBtn, comingSoonBtn }) {
      btn.setPrefSize(250, 150);
    }

    addBoxBtn.setOnAction(e -> AddBoxView.show(stage));
    manageBoxesBtn.setOnAction(e -> ManageBoxesView.show(stage));
    calendarBtn.setOnAction(e -> CalendarView.show(stage));

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20));
    grid.setAlignment(Pos.CENTER);
    grid.add(addBoxBtn, 0, 0);
    grid.add(manageBoxesBtn, 1, 0);
    grid.add(calendarBtn, 0, 1);
    grid.add(comingSoonBtn, 1, 1);

    stage.setScene(new Scene(grid, 640, 480));
    stage.show();
  }

  private MainMenuView() {
  }

}
