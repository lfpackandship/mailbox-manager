package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import javafx.geometry.Rectangle2D;

import org.junit.jupiter.api.Test;

/**
 * Tests for saving and reading the main window's place, and for not
 * restoring a place that's no longer on any screen.
 */
class WindowPlacementTest {

  private static final Rectangle2D LAPTOP = new Rectangle2D(0, 0, 1440, 870);

  private static final Rectangle2D SECOND_MONITOR = new Rectangle2D(1440, 0, 1920, 1050);

  @Test
  void aSavedPlaceComesBackTheSame() {
    var bounds = new Rectangle2D(100, 80, 900, 700);

    var saved = WindowPlacement.format(bounds, true);

    assertEquals("100,80,900,700,true", saved);
    assertEquals(bounds, WindowPlacement.parse(saved, List.of(LAPTOP)));
  }

  @Test
  void aPlaceOnASecondMonitorIsKeptWhileItsPluggedIn() {
    var onSecond = "1600,100,1200,800,false";

    assertEquals(new Rectangle2D(1600, 100, 1200, 800),
        WindowPlacement.parse(onSecond, List.of(LAPTOP, SECOND_MONITOR)));
    assertNull(WindowPlacement.parse(onSecond, List.of(LAPTOP)));
  }

  @Test
  void aWindowBiggerThanTheScreenIsntRestored() {
    assertNull(WindowPlacement.parse("0,0,1920,1050,false", List.of(LAPTOP)));
  }

  @Test
  void nothingSavedOrSomethingUnreadableIsIgnored() {
    assertNull(WindowPlacement.parse("", List.of(LAPTOP)));
    assertNull(WindowPlacement.parse(null, List.of(LAPTOP)));
    assertNull(WindowPlacement.parse("100,80,wide,700,false", List.of(LAPTOP)));
    assertNull(WindowPlacement.parse("100,80,-5,700,false", List.of(LAPTOP)));
    assertNull(WindowPlacement.parse("100,80,20,10,false", List.of(LAPTOP)));
  }

}
