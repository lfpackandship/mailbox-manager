package org.lfps.mailboxes.view;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Helpers for tests that open real windows: starting JavaFX, running code on
 * its thread, and pressing keys. Windows appear briefly on screen while the
 * tests run; CI provides a virtual display.
 */
public final class FxTestSupport {

  private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

  /**
   * Starts the JavaFX runtime if it isn't running, and keeps it running when
   * a test closes the last open window.
   */
  public static void start() {
    try {
      Platform.startup(() -> { });
    } catch (IllegalStateException alreadyStarted) {
      // Another test class already started it.
    }
    Platform.setImplicitExit(false);
  }

  /**
   * Runs an action on the JavaFX thread and waits for its result, rethrowing
   * anything it throws.
   */
  public static <T> T call(Callable<T> action) {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> {
      try {
        result.complete(action.call());
      } catch (Throwable t) {
        result.completeExceptionally(t);
      }
    });
    try {
      return result.get(10, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      if (e.getCause() instanceof Error) {
        throw (Error) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Runs an action on the JavaFX thread and waits for it to finish.
   */
  public static void run(Runnable action) {
    call(() -> {
      action.run();
      return null;
    });
  }

  /**
   * Presses a key on a node, as the user would with it focused. Must be
   * called on the JavaFX thread.
   */
  public static void press(Node target, KeyCode key) {
    press(target, key, false);
  }

  /**
   * Presses a key together with the platform's shortcut modifier (Cmd on
   * macOS, Ctrl elsewhere). Must be called on the JavaFX thread.
   */
  public static void pressWithShortcut(Node target, KeyCode key) {
    press(target, key, true);
  }

  private static void press(Node target, KeyCode key, boolean shortcut) {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key,
        false, shortcut && !MAC, false, shortcut && MAC));
  }

  private FxTestSupport() {
  }

}
