package org.lfps.mailboxes.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/**
 * The application's main menu: a grid linking to Add New Box, Manage Boxes,
 * Renewals, Calendar, Payments, and Box Inventory.
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
    var renewalsBtn = new Button("Renewals");
    var paymentsBtn = new Button("Payments");
    var inventoryBtn = new Button("Box Inventory");

    for (var btn : new Button[] { addBoxBtn, manageBoxesBtn, calendarBtn, renewalsBtn, paymentsBtn,
        inventoryBtn }) {
      btn.setPrefSize(250, 130);
    }

    addBoxBtn.setOnAction(e -> AddBoxView.show(stage));
    manageBoxesBtn.setOnAction(e -> ManageBoxesView.show(stage));
    calendarBtn.setOnAction(e -> CalendarView.show(stage));
    renewalsBtn.setOnAction(e -> RenewalsView.show(stage));
    paymentsBtn.setOnAction(e -> PaymentsView.show(stage));
    inventoryBtn.setOnAction(e -> BoxInventoryView.show(stage));

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20));
    grid.setAlignment(Pos.CENTER);
    grid.add(addBoxBtn, 0, 0);
    grid.add(manageBoxesBtn, 1, 0);
    grid.add(renewalsBtn, 0, 1);
    grid.add(calendarBtn, 1, 1);
    grid.add(paymentsBtn, 0, 2);
    grid.add(inventoryBtn, 1, 2);

    AppWindow.show(stage, grid);
  }

  private MainMenuView() {
  }

}
