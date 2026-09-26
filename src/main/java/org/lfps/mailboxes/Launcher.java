package org.lfps.mailboxes;

/**
 * Main class of the release jar and the installed apps. Java 17 and 21 refuse
 * to start a jar whose main class extends {@link javafx.application.Application}
 * unless JavaFX is loaded as a separate module ("JavaFX runtime components are
 * missing"), so the jar and the installed apps start here instead, which hands
 * off to {@link App}.
 */
public final class Launcher {

  /**
   * Launches the application.
   *
   * @param args command-line arguments, passed on to {@link App#main(String[])}
   */
  public static void main(String[] args) {
    App.main(args);
  }

  private Launcher() {
  }

}
