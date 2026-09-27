package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;

/**
 * Checks that every screen picks up the text size setting and can scroll, so
 * nothing is cut off when larger text makes a screen bigger than the window.
 */
class ScreenLayoutTest {

  private static final List<Consumer<Stage>> SCREENS = List.of(
      MainMenuView::show, AddBoxView::show, ManageBoxesView::show, CalendarView::show, RenewalsView::show,
      PaymentsView::show, BoxInventoryView::show);

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void useExtraLargeText() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    new SettingsRepository().put(Setting.TEXT_SIZE, TextSize.EXTRA_LARGE.name());
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      stage.setWidth(700);
      stage.setHeight(600);
      return stage;
    });
  }

  @AfterEach
  void closeAllWindows() throws SQLException {
    new SettingsRepository().put(Setting.TEXT_SIZE, TextSize.NORMAL.name());
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void everyScreenUsesTheTextSizeAndScrollsWhenTooBig() {
    for (var screen : SCREENS) {
      FxTestSupport.run(() -> screen.accept(mainWindow));

      var root = FxTestSupport.call(() -> (BorderPane) mainWindow.getScene().getRoot());
      assertEquals(TextSize.EXTRA_LARGE.style(), FxTestSupport.call(root::getStyle));
      var scroll = FxTestSupport.call(() -> root.getCenter());
      assertTrue(scroll instanceof ScrollPane, "Not scrollable: " + scroll);
      assertTrue(FxTestSupport.call(() -> ((ScrollPane) scroll).isFitToWidth()));
    }
  }

  @Test
  void extraLargeContentThatDoesntFitCanBeScrolledTo() {
    FxTestSupport.run(() -> CalendarView.show(mainWindow));

    // At extra large text the calendar is taller than the default window, so the
    // scroll pane's content must extend past the visible area instead of being
    // squeezed into it.
    var sizes = FxTestSupport.call(() -> {
      var scroll = scrollPaneSizedLikeTheDefaultWindow();
      return new double[] { scroll.getContent().getLayoutBounds().getHeight(),
          scroll.getViewportBounds().getHeight() };
    });
    assertTrue(sizes[0] > sizes[1], "content " + sizes[0] + " should exceed viewport " + sizes[1]);
  }

  @Test
  void extraLargeCalendarScrollsSidewaysInsteadOfSqueezingTheDays() {
    FxTestSupport.run(() -> CalendarView.show(mainWindow));

    var widths = contentAndViewportWidths();
    assertTrue(widths[0] > widths[1], "content " + widths[0] + " should exceed viewport " + widths[1]);
  }

  @Test
  void manageBoxesAtNormalSizeFitsTheWindowWidth() throws SQLException {
    new SettingsRepository().put(Setting.TEXT_SIZE, TextSize.NORMAL.name());
    FxTestSupport.run(() -> ManageBoxesView.show(mainWindow));

    var widths = contentAndViewportWidths();
    assertEquals(widths[1], widths[0], 0.5);
  }

  @Test
  void manageBoxesAndRenewalsFitTheDefaultWindowWithoutScrolling() throws SQLException {
    new SettingsRepository().put(Setting.TEXT_SIZE, TextSize.NORMAL.name());
    for (Consumer<Stage> screen : List.<Consumer<Stage>>of(ManageBoxesView::show, RenewalsView::show)) {
      FxTestSupport.run(() -> screen.accept(mainWindow));

      var heights = FxTestSupport.call(() -> {
        var scroll = scrollPaneSizedLikeTheDefaultWindow();
        return new double[] { scroll.getContent().getLayoutBounds().getHeight(),
            scroll.getViewportBounds().getHeight() };
      });
      assertEquals(heights[1], heights[0], 0.5, "the screen should exactly fill the window's height");
    }
  }

  private double[] contentAndViewportWidths() {
    return FxTestSupport.call(() -> {
      var scroll = scrollPaneSizedLikeTheDefaultWindow();
      return new double[] { scroll.getContent().getLayoutBounds().getWidth(),
          scroll.getViewportBounds().getWidth() };
    });
  }

  /**
   * Lays out the screen's scroll pane at the size it has in the default
   * 700x600 window. Sized directly rather than through the window, because
   * without a window manager (as in CI) a window grows to fit its contents.
   */
  private ScrollPane scrollPaneSizedLikeTheDefaultWindow() {
    var scroll = (ScrollPane) ((BorderPane) mainWindow.getScene().getRoot()).getCenter();
    scroll.resize(700, 540);
    scroll.layout();
    return scroll;
  }

}
