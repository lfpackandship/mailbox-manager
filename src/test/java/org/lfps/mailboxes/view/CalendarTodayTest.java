package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.TestSandbox;

/**
 * UI tests for the Calendar's Today button, which comes back to this month.
 */
class CalendarTodayTest {

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void openMainWindow() {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    mainWindow = FxTestSupport.call(Stage::new);
  }

  @AfterEach
  void closeAllWindows() {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void todayComesBackToThisMonth() {
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.now().plusMonths(3)));
    assertFalse(FxTestSupport.call(() -> todayButton().isDisabled()));

    FxTestSupport.run(() -> todayButton().fire());

    var now = YearMonth.now();
    assertEquals(now.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + now.getYear(),
        FxTestSupport.call(() -> ((Label) mainWindow.getScene().lookup("#monthLabel")).getText()));
    assertTrue(FxTestSupport.call(() -> todayButton().isDisabled()));
  }

  /** Returns the Calendar's Today button. */
  private Button todayButton() {
    return (Button) mainWindow.getScene().lookup("#todayButton");
  }

}
