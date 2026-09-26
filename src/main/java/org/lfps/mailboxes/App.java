package org.lfps.mailboxes;

import javafx.application.Application;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.view.MainMenuView;

/**
 * Entry point for the Mailbox Manager JavaFX application.
 */
public class App extends Application {

  /**
   * Locates the database, initializes its schema, takes the daily backup,
   * and shows the main menu.
   *
   * @param stage the primary window supplied by the JavaFX runtime
   */
  @Override
  public void start(Stage stage) {
    Database.prepareDataDir();
    Database.initSchema();
    Database.backupDaily();
    stage.setTitle("Mailbox Manager");
    stage.setWidth(700);
    stage.setHeight(600);
    MainMenuView.show(stage);
  }

  /**
   * Launches the JavaFX application.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    launch();
  }

}
