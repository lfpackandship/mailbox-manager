package org.lfps.mailboxes;

import javafx.application.Application;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.view.MainMenuView;

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
