package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;

/**
 * Remembers where the main window was and how big when the app closes, in
 * {@link Setting#WINDOW_PLACEMENT}, and opens it the same way next time. The
 * first time, or if the saved place is no longer on any screen, as after
 * unplugging a second monitor, it opens at the normal size instead. {@link
 * org.lfps.mailboxes.App} calls {@link #restore} when it starts and {@link
 * #save} when it stops.
 */
public final class WindowPlacement {

  /** The window's size the first time, in pixels at normal text size. */
  private static final double NORMAL_WIDTH = 700;

  /** The window's height the first time, in pixels at normal text size. */
  private static final double NORMAL_HEIGHT = 600;

  /**
   * Where the window was before it was maximized, so that's what's saved and
   * un-maximizing next time goes back to it; {@code null} until it's known.
   */
  private static Rectangle2D normalBounds;

  /**
   * Places and sizes the main window as it was when the app last closed, or
   * at the normal size, made larger to match larger text, if it can't be.
   * Then keeps track of where it is until {@link #save}.
   *
   * @param stage the main window, not yet shown
   */
  public static void restore(Stage stage) {
    var screens = Screen.getScreens().stream().map(Screen::getVisualBounds).collect(Collectors.toList());
    String saved;
    try {
      saved = new SettingsRepository().get(Setting.WINDOW_PLACEMENT);
    } catch (SQLException e) {
      saved = "";
    }
    var bounds = parse(saved, screens);
    if (bounds != null) {
      stage.setX(bounds.getMinX());
      stage.setY(bounds.getMinY());
      stage.setWidth(bounds.getWidth());
      stage.setHeight(bounds.getHeight());
      stage.setMaximized(saved.trim().endsWith("true"));
    } else {
      var screen = Screen.getPrimary().getVisualBounds();
      var scale = AppWindow.textScale();
      stage.setWidth(Math.min(NORMAL_WIDTH * scale, screen.getWidth()));
      stage.setHeight(Math.min(NORMAL_HEIGHT * scale, screen.getHeight()));
      stage.centerOnScreen();
    }
    normalBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());

    // While it isn't maximized, note where it is, so a maximized window can
    // be saved with the size to go back to.
    Runnable track = () -> {
      if (!stage.isMaximized() && !stage.isIconified()) {
        normalBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
      }
    };
    stage.xProperty().addListener((obs, was, now) -> track.run());
    stage.yProperty().addListener((obs, was, now) -> track.run());
    stage.widthProperty().addListener((obs, was, now) -> track.run());
    stage.heightProperty().addListener((obs, was, now) -> track.run());
  }

  /**
   * Saves where the main window is and how big, for next time. A problem
   * saving is ignored; the window just opens at the normal size next time.
   *
   * @param stage the main window
   */
  public static void save(Stage stage) {
    if (normalBounds == null) {
      return;
    }
    try {
      new SettingsRepository().put(Setting.WINDOW_PLACEMENT, format(normalBounds, stage.isMaximized()));
    } catch (SQLException e) {
      // Not worth bothering anyone about.
    }
  }

  /**
   * Formats a window's place for {@link Setting#WINDOW_PLACEMENT}.
   *
   * @param bounds where the window is and its size, when not maximized
   * @param maximized whether it's maximized
   * @return such as "100,80,900,700,false"
   */
  static String format(Rectangle2D bounds, boolean maximized) {
    return String.format(Locale.ROOT, "%.0f,%.0f,%.0f,%.0f,%b", bounds.getMinX(), bounds.getMinY(),
        bounds.getWidth(), bounds.getHeight(), maximized);
  }

  /**
   * Reads a saved window place, if it's still on one of the screens.
   *
   * @param saved the saved place, as made by {@link #format}, or empty
   * @param screens the screens' usable areas
   * @return where to put the window and its size, or {@code null} if nothing
   *     usable was saved or it doesn't fit on any screen
   */
  static Rectangle2D parse(String saved, List<Rectangle2D> screens) {
    if (saved == null) {
      return null;
    }
    var parts = saved.trim().split(",");
    if (parts.length != 5) {
      return null;
    }
    Rectangle2D bounds;
    try {
      bounds = new Rectangle2D(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
          Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    } catch (IllegalArgumentException notANumberOrNegativeSize) {
      return null;
    }
    // Most of the window, including its title bar, has to be on one screen,
    // or it could open where it can't be seen or moved.
    for (var screen : screens) {
      var fits = bounds.getWidth() >= 200 && bounds.getHeight() >= 150
          && bounds.getWidth() <= screen.getWidth() && bounds.getHeight() <= screen.getHeight()
          && screen.contains(bounds.getMinX() + bounds.getWidth() / 2, bounds.getMinY() + 10);
      if (fits) {
        return bounds;
      }
    }
    return null;
  }

  /** Not used: the window is placed with static methods. */
  private WindowPlacement() {
  }

}
