package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.stream.Collectors;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
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

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for clicking a day or a box on the Calendar.
 */
class CalendarDayViewTest {

  private static final LocalDate DAY = LocalDate.of(2026, 3, 15);

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void showCalendar() throws SQLException {
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
    mailboxes.insert(box("12", "Ada", "Lovelace", "Engines Ltd", DAY));
    mailboxes.insert(box("3", "Grace", "Hopper", null, DAY));
    mailboxes.insert(box("40", "Alan", "Turing", null, DAY.plusDays(1)));

    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      CalendarView.show(stage, YearMonth.from(DAY));
      return stage;
    });
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
  void clickingADayListsItsBoxesWithTheirDetails() {
    clickDay(DAY);

    var day = window("#dayTitle");
    assertNotNull(day);
    assertEquals("2 rentals end on " + DAY.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        text(day, "dayTitle"));
    assertEquals(RenewalsView.dueStatus(DAY, LocalDate.now()), text(day, "dayStatus"));
    var rows = FxTestSupport.call(() -> dayTable(day).getItems().stream()
        .map(m -> m.getBoxNumber() + " " + m.getFullName() + " " + m.getBusinessTitle())
        .collect(Collectors.toList()));
    assertEquals(List.of("3 Grace Hopper null", "12 Ada Lovelace Engines Ltd"), rows);
  }

  @Test
  void clickingADayWithNoBoxesDoesNothing() {
    clickDay(DAY.minusDays(1));
    assertNull(window("#dayTitle"));
  }

  @Test
  void clickingAnotherDayReplacesTheList() {
    clickDay(DAY);
    clickDay(DAY.plusDays(1));

    var titles = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#dayTitle") != null)
        .count());
    assertEquals(1L, titles);
    assertEquals("1 rental ends on " + DAY.plusDays(1).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        text(window("#dayTitle"), "dayTitle"));
  }

  @Test
  void viewOpensTheSelectedBoxsDetails() {
    clickDay(DAY);
    var day = window("#dayTitle");
    FxTestSupport.run(() -> {
      dayTable(day).getSelectionModel().select(1);
      ((Button) day.getScene().lookup("#dayViewButton")).fire();
    });

    assertNull(window("#dayTitle"));
    assertEquals("Box 12 – Ada Lovelace", FxTestSupport.call(window("#detailTitle")::getTitle));
  }

  @Test
  void renewingFromTheDayMovesTheBoxOnTheCalendar() {
    clickDay(DAY);
    var day = window("#dayTitle");
    FxTestSupport.run(() -> ((Button) day.getScene().lookup("#dayRenewButton")).fire());

    var renew = window("#renewSaveButton");
    FxTestSupport.run(() -> {
      ((DatePicker) renew.getScene().lookup("#renewEndField")).setValue(DAY.plusDays(10));
      ((Button) renew.getScene().lookup("#renewSaveButton")).fire();
    });

    assertEquals(List.of("Box 12"), entries(DAY));
    assertEquals(List.of("Box 3"), entries(DAY.plusDays(10)));
  }

  @Test
  void clickingABoxOnTheDayOpensTheDaysList() {
    var entry = FxTestSupport.call(() -> dayCell(DAY).getChildren().stream()
        .filter(node -> node instanceof Label && "Box 12".equals(((Label) node).getText()))
        .findFirst()
        .orElseThrow());
    click(entry);

    assertNotNull(window("#dayTitle"));
    assertNull(window("#detailTitle"));
  }

  @Test
  void todayIsOutlinedAndLabelled() {
    var today = LocalDate.now();
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.from(today)));

    var todays = FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookupAll(".today").stream()
        .map(Node::getId)
        .collect(Collectors.toList()));
    assertEquals(List.of("day-" + today), todays);
    assertEquals(today.getDayOfMonth() + " · Today",
        FxTestSupport.call(() -> ((Label) dayCell(today).getChildren().get(0)).getText()));
  }

  @Test
  void otherMonthsHaveNoToday() {
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.now().plusMonths(1)));

    assertEquals(0, FxTestSupport.call(() -> mainWindow.getScene().getRoot().lookupAll(".today").size()));
  }

  @Test
  void prevAndNextStayInPlaceAsTheMonthChanges() {
    // May is the shortest month name and September the longest.
    var may = buttonPositions(YearMonth.of(2026, 5));
    var september = buttonPositions(YearMonth.of(2026, 9));

    assertEquals(may.get(0), september.get(0), 0.5);
    assertEquals(may.get(1), september.get(1), 0.5);
  }

  @Test
  void nextAndPrevChangeTheMonth() {
    FxTestSupport.run(() -> ((Button) mainWindow.getScene().getRoot().lookup("#nextMonthButton")).fire());
    assertEquals("April 2026", monthShown());
    for (var i = 0; i < 2; i++) {
      FxTestSupport.run(() -> ((Button) mainWindow.getScene().getRoot().lookup("#prevMonthButton")).fire());
    }
    assertEquals("February 2026", monthShown());
  }

  /** Returns the month the calendar shows, such as "October 2026". */
  private String monthShown() {
    return FxTestSupport.call(() -> ((Label) mainWindow.getScene().getRoot().lookup("#monthLabel")).getText());
  }

  /** Returns where the Prev and Next buttons start, across the window. */
  private List<Double> buttonPositions(YearMonth month) {
    FxTestSupport.run(() -> CalendarView.show(mainWindow, month));
    return FxTestSupport.call(() -> {
      var root = mainWindow.getScene().getRoot();
      root.applyCss();
      root.layout();
      return List.of(root.lookup("#prevMonthButton").localToScene(0, 0).getX(),
          root.lookup("#nextMonthButton").localToScene(0, 0).getX());
    });
  }

  @Test
  void changingMonthClosesTheDaysList() {
    clickDay(DAY);
    FxTestSupport.run(() -> CalendarView.show(mainWindow, YearMonth.from(DAY).plusMonths(1)));

    assertNull(window("#dayTitle"));
  }

  /** Returns the calendar's cell for a day. */
  private VBox dayCell(LocalDate date) {
    return (VBox) mainWindow.getScene().getRoot().lookup("#day-" + date);
  }

  /** Returns the boxes listed in a day's cell. */
  private List<String> entries(LocalDate date) {
    return FxTestSupport.call(() -> dayCell(date).getChildren().stream()
        .map(node -> ((Label) node).getText())
        .filter(text -> text.startsWith("Box "))
        .collect(Collectors.toList()));
  }

  /** Clicks a day on the calendar. */
  private void clickDay(LocalDate date) {
    click(FxTestSupport.call(() -> dayCell(date)));
  }

  /** Clicks something once with the main mouse button. */
  private static void click(Node node) {
    FxTestSupport.run(() -> Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
        MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, false, null)));
  }

  /** Returns the table of boxes on a day's window. */
  @SuppressWarnings("unchecked")
  private static TableView<Mailbox> dayTable(Stage day) {
    return (TableView<Mailbox>) day.getScene().lookup("#dayTable");
  }

  /**
   * Returns the open window containing something matching a selector, or {@code
   * null}.
   */
  private static Stage window(String marker) {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup(marker) != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElse(null));
  }

  /** Returns the text of the label with the given id on a window. */
  private static String text(Stage window, String id) {
    return FxTestSupport.call(() -> ((Label) window.getScene().lookup("#" + id)).getText());
  }

  /** Makes a box with the given names and end date. */
  private static Mailbox box(String boxNumber, String first, String last, String business, LocalDate end) {
    return new Mailbox(0, first, last, business, boxNumber, null, "5551000001", null, null, end, null);
  }

}
