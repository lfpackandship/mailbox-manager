package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Checks that a day with many boxes on the Calendar fits in its square
 * instead of spilling into the day below.
 */
class CalendarOverflowTest {

  private static final LocalDate DAY = LocalDate.of(2027, 7, 6);

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    mainWindow = FxTestSupport.call(Stage::new);
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

  @ParameterizedTest
  @EnumSource(TextSize.class)
  void everyCountOfBoxesFitsInTheDayAtEveryTextSize(TextSize size) throws SQLException {
    new SettingsRepository().put(Setting.TEXT_SIZE, size.name());
    for (var count = 1; count <= 8; count++) {
      addBox(String.valueOf(100 + count));
      FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.from(DAY)));

      var finalCount = count;
      var overflow = FxTestSupport.call(() -> {
        var root = mainWindow.getScene().getRoot();
        root.applyCss();
        root.layout();
        var cell = dayCell(DAY);
        var bottom = cell.getHeight() - cell.getPadding().getBottom();
        return cell.getChildren().stream()
            .filter(child -> child.getBoundsInParent().getMaxY() > bottom + 0.5)
            .map(child -> ((Label) child).getText())
            .collect(Collectors.toList());
      });
      assertEquals(List.of(), overflow, finalCount + " boxes at " + size + " text spill out of the day");

      // The day is the same size as a day with no boxes.
      var heights = FxTestSupport.call(() -> List.of(dayCell(DAY).getHeight(), dayCell(DAY.plusDays(7)).getHeight()));
      assertEquals(heights.get(1), heights.get(0), 0.5, finalCount + " boxes at " + size + " text");
    }
  }

  @Test
  void upToTwoBoxesAreAllListed() throws SQLException {
    for (var boxNumber : List.of("1", "2")) {
      addBox(boxNumber);
    }
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.from(DAY)));

    assertEquals(List.of("6", "Box 1", "Box 2"), texts());
  }

  @Test
  void moreThanTwoShowsTheFirstAndHowManyMore() throws SQLException {
    for (var boxNumber : List.of("107", "118", "131", "136")) {
      addBox(boxNumber);
    }
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.from(DAY)));

    assertEquals(List.of("6", "Box 107", "+3 more"), texts());
  }

  @Test
  void theMoreLinesTooltipNamesTheOtherBoxes() throws SQLException {
    for (var boxNumber : List.of("1", "2", "3")) {
      addBox(boxNumber);
    }
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.from(DAY)));

    var tooltip = FxTestSupport.call(() -> ((Label) dayCell(DAY).getChildren().get(2)).getTooltip().getText());
    assertEquals("Box 2 – Ada Lovelace\nBox 3 – Ada Lovelace\nClick the day to see them all", tooltip);
  }

  @Test
  void theMoreLineOpensTheDaysFullList() throws SQLException {
    for (var boxNumber : List.of("1", "2", "3", "4", "5")) {
      addBox(boxNumber);
    }
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.from(DAY)));
    var more = FxTestSupport.call(() -> dayCell(DAY).getChildren().get(2));
    FxTestSupport.run(() -> Event.fireEvent(more, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
        MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, false, null)));

    var listed = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#dayTable") != null)
        .map(w -> ((TableView<?>) w.getScene().lookup("#dayTable")).getItems().size())
        .findFirst()
        .orElse(0));
    assertEquals(5, listed);
  }

  private void addBox(String boxNumber) throws SQLException {
    mailboxes.insert(new Mailbox(0, "Ada", "Lovelace", null, boxNumber, null, "5551000001", null, null, DAY,
        null));
  }

  private List<String> texts() {
    return FxTestSupport.call(() -> dayCell(DAY).getChildren().stream()
        .map(node -> ((Label) node).getText())
        .collect(Collectors.toList()));
  }

  private VBox dayCell(LocalDate date) {
    Node cell = mainWindow.getScene().getRoot().lookup("#day-" + date);
    assertTrue(cell instanceof VBox, "No cell for " + date);
    return (VBox) cell;
  }

}
