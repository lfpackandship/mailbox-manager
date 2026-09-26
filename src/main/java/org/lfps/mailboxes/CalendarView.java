package org.lfps.mailboxes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class CalendarView {

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
