package org.lfps.mailboxes;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX App
 */
public class App extends Application {

  @Override
  public void start(Stage stage) {
    Database.initSchema();
    stage.setTitle("Mailbox Manager");
    MainMenuView.show(stage);
  }

  public static void main(String[] args) {
    launch();
  }

}
