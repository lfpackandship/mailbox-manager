package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.ErrorLog;
import org.lfps.mailboxes.data.TestSandbox;

/**
 * Tests for catching errors nothing else handles: they're written in the
 * error log and a plain message is shown, whichever thread they happen on.
 */
class UnexpectedErrorsTest {

  private static final String TITLE = "Something Went Wrong";

  private final Thread.UncaughtExceptionHandler realHandler = Thread.getDefaultUncaughtExceptionHandler();

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void setUp() throws IOException {
    TestSandbox.require();
    Database.prepareDataDir();
    Files.deleteIfExists(ErrorLog.file());
    UnexpectedErrors.install();
  }

  @AfterEach
  void tearDown() {
    Thread.setDefaultUncaughtExceptionHandler(realHandler);
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void anErrorInTheScreensIsLoggedAndShown() throws Exception {
    Platform.runLater(() -> {
      throw new IllegalStateException("The box list broke");
    });

    var message = waitForMessage();
    assertTrue(message.contains("what you just did may not have finished"), message);
    assertTrue(message.contains("(The box list broke)"), message);
    assertTrue(message.contains("errors.log"), message);
    var log = Files.readString(ErrorLog.file());
    assertTrue(log.contains("JavaFX Application Thread"), log);
    assertTrue(log.contains("java.lang.IllegalStateException: The box list broke"), log);
  }

  @Test
  void anErrorOnAnotherThreadIsShownOnTheScreen() throws Exception {
    var thread = new Thread(() -> {
      throw new IllegalStateException("Background work broke");
    }, "Background work");
    thread.start();
    thread.join();

    assertTrue(waitForMessage().contains("(Background work broke)"));
    assertTrue(Files.readString(ErrorLog.file()).contains("\"Background work\" thread"));
  }

  @Test
  void anErrorThatKeepsHappeningShowsOneMessage() throws Exception {
    for (var i = 0; i < 5; i++) {
      Platform.runLater(() -> {
        throw new IllegalStateException("Again");
      });
    }

    waitForMessage();
    assertEquals(1, messageWindows().size());
  }

  @Test
  void anErrorWithoutAnExplanationLeavesNoBrackets() {
    var message = UnexpectedErrors.message(new NullPointerException());

    assertFalse(message.contains("("), message);
    assertFalse(message.contains("null"), message);
  }

  /**
   * Waits a few seconds for the message to show, after letting JavaFX finish
   * what it was given, and returns its text.
   */
  private static String waitForMessage() throws InterruptedException {
    for (var i = 0; i < 50; i++) {
      // Lets the JavaFX thread run everything queued before this.
      FxTestSupport.run(() -> { });
      var windows = messageWindows();
      if (!windows.isEmpty()) {
        return FxTestSupport.call(() -> ((DialogPane) windows.get(0).getScene().getRoot()).getContentText());
      }
      Thread.sleep(100);
    }
    throw new AssertionError("The message wasn't shown");
  }

  /** Returns the open "Something Went Wrong" messages. */
  private static List<Stage> messageWindows() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && TITLE.equals(((Stage) w).getTitle()))
        .map(w -> (Stage) w)
        .collect(Collectors.toList()));
  }

}
