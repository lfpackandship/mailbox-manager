package org.lfps.mailboxes.view;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Placeholder screen for the upcoming calendar feature.
 */
public class CalendarView {

  /**
   * Displays the Calendar placeholder on the given stage.
   *
   * @param stage the window to render the placeholder into
   */
  public static void show(Stage stage) {
    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var layout = new VBox(10, backBtn, new Label("Calendar - coming soon"));
    layout.setPadding(new Insets(20));

    stage.setScene(new Scene(layout, 640, 480));
    stage.show();
  }

  private CalendarView() {
  }

}
