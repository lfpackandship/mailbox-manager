package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Shows a month-grid calendar with open mailboxes marked on the day their box
 * rental ends, and Prev/Next buttons to browse other months. Clicking a day
 * lists its boxes with more details (see {@link CalendarDayView}). Weeks start
 * on the day set by {@link Setting#WEEK_START}.
 */
public class CalendarView {

  /** How many boxes, or boxes and a "+N more" line, fit under a day's date. */
  private static final int MAX_LINES = 2;

  /**
   * Displays the calendar for the current month on the given stage.
   *
   * @param stage the window to render the calendar into
   */
  public static void show(Stage stage) {
    show(stage, YearMonth.now());
  }

  /**
   * Displays the calendar for the given month on the given stage.
   *
   * @param stage the window to render the calendar into
   * @param month the month to display
   */
  public static void show(Stage stage, YearMonth month) {
    // A day's list from the month being left would be out of date.
    CalendarDayView.close();
    var statusLabel = new Label();
    var mailboxesByEndDate = Map.<LocalDate, List<Mailbox>>of();

    try {
      mailboxesByEndDate = new MailboxRepository().findOpen().stream()
          .filter(m -> m.getEndDate() != null)
          .collect(Collectors.groupingBy(Mailbox::getEndDate));
    } catch (SQLException e) {
      statusLabel.setStyle("-fx-text-fill: red;");
      statusLabel.setText("Failed to load mailboxes: " + e.getMessage());
    }

    var prevBtn = new Button("< Prev");
    prevBtn.setId("prevMonthButton");
    prevBtn.setOnAction(e -> show(stage, month.minusMonths(1)));

    var nextBtn = new Button("Next >");
    nextBtn.setId("nextMonthButton");
    nextBtn.setOnAction(e -> show(stage, month.plusMonths(1)));

    var monthLabel = new Label(
        month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.getYear());
    monthLabel.setId("monthLabel");
    // A fixed width, wide enough for the longest month name, keeps Prev and
    // Next in the same place as the month changes, so they can be clicked
    // repeatedly without moving the mouse.
    monthLabel.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;"
        + " -fx-min-width: 10em; -fx-pref-width: 10em; -fx-max-width: 10em;");
    monthLabel.setAlignment(Pos.CENTER);

    // Comes back to this month after looking ahead or back.
    var todayBtn = new Button("Today");
    todayBtn.setId("todayButton");
    todayBtn.setDisable(month.equals(YearMonth.now()));
    todayBtn.setOnAction(e -> show(stage, YearMonth.now()));

    var header = new HBox(10, prevBtn, monthLabel, nextBtn, todayBtn);
    header.setAlignment(Pos.CENTER);

    var calendarGrid = new GridPane();
    calendarGrid.setHgap(4);
    calendarGrid.setVgap(4);

    var firstDay = weekStart();
    for (var col = 0; col < 7; col++) {
      var dayNameLabel = new Label(firstDay.plus(col).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
      dayNameLabel.setStyle("-fx-font-weight: bold;");
      calendarGrid.add(dayNameLabel, col, 0);
    }

    var firstOfMonth = month.atDay(1);
    var startCol = column(firstOfMonth.getDayOfWeek(), firstDay);
    var daysInMonth = month.lengthOfMonth();

    var row = 1;
    var col = startCol;
    for (var day = 1; day <= daysInMonth; day++) {
      var date = month.atDay(day);
      calendarGrid.add(buildDayCell(stage, month, date, mailboxesByEndDate.getOrDefault(date, List.of())), col, row);
      col++;
      if (col > 6) {
        col = 0;
        row++;
      }
    }

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var layout = new VBox(8, backBtn, header, calendarGrid, statusLabel);
    layout.setPadding(new Insets(15));

    AppWindow.show(stage, layout);
  }

  /**
   * Returns the calendar column, from 0 to 6, that a day of the week falls in
   * when weeks start on {@code firstDay}.
   *
   * @param day the day of the week
   * @param firstDay the day the week starts on
   * @return the column, from 0 for the first day to 6
   */
  static int column(DayOfWeek day, DayOfWeek firstDay) {
    return (day.getValue() - firstDay.getValue() + 7) % 7;
  }

  /**
   * Returns the day the week starts on, from the settings.
   *
   * @return Sunday or Monday; Sunday if the setting can't be read
   */
  private static DayOfWeek weekStart() {
    try {
      return DayOfWeek.valueOf(new SettingsRepository().get(Setting.WEEK_START));
    } catch (SQLException | IllegalArgumentException e) {
      return DayOfWeek.SUNDAY;
    }
  }

  /**
   * Makes the text for a box listed in a day of the calendar, in small orange
   * type.
   *
   * @param text the text
   * @return the label
   */
  private static Label entryLabel(String text) {
    var label = new Label(text);
    label.setStyle("-fx-font-size: 0.8em; -fx-text-fill: #b34700;");
    return label;
  }

  /**
   * Builds a day of the calendar, listing the boxes ending that day. Clicking
   * it opens the day's list.
   *
   * @param stage the main window
   * @param month the month shown
   * @param date the day
   * @param mailboxes the boxes ending that day
   * @return the day's cell
   */
  private static VBox buildDayCell(Stage stage, YearMonth month, LocalDate date, List<Mailbox> mailboxes) {
    var cell = new VBox(2);
    cell.setId("day-" + date);
    // Today gets a thick blue outline; its padding shrinks by as much as the
    // border grows, so the day keeps the same size as the others.
    var isToday = date.equals(LocalDate.now());
    cell.setPadding(new Insets(isToday ? 2 : 4));
    cell.setStyle("-fx-pref-width: 7em; -fx-pref-height: 5.4em; -fx-min-width: 7em; -fx-min-height: 5.4em;"
        + (isToday
            ? " -fx-border-color: #1565c0; -fx-border-width: 2.5;"
            : " -fx-border-color: lightgray; -fx-border-width: 0.5;")
        + (mailboxes.isEmpty() ? "" : " -fx-background-color: #ffe0b2;"));

    var dayLabel = new Label(String.valueOf(date.getDayOfMonth()));
    if (isToday) {
      cell.getStyleClass().add("today");
      dayLabel.setText(date.getDayOfMonth() + " · Today");
      dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1565c0;");
    }
    cell.getChildren().add(dayLabel);
    if (mailboxes.isEmpty()) {
      return cell;
    }

    // A day has room for two lines under its date. When more boxes end that
    // day, show the first and how many more, rather than spilling into the
    // day below; clicking the day lists them all.
    var shown = mailboxes.size() <= MAX_LINES ? mailboxes : mailboxes.subList(0, MAX_LINES - 1);
    for (var mailbox : shown) {
      var entryLabel = entryLabel("Box " + mailbox.getBoxNumber());
      entryLabel.setTooltip(new Tooltip(BoxLabels.boxAndHolder(mailbox) + " ends " + mailbox.getEndDate()));
      cell.getChildren().add(entryLabel);
    }
    if (shown.size() < mailboxes.size()) {
      var hidden = mailboxes.subList(shown.size(), mailboxes.size());
      var moreLabel = entryLabel("+" + hidden.size() + " more");
      moreLabel.setStyle(moreLabel.getStyle() + " -fx-font-weight: bold;");
      moreLabel.setTooltip(new Tooltip(hidden.stream()
          .map(BoxLabels::boxAndHolder)
          .collect(Collectors.joining("\n")) + "\nClick the day to see them all"));
      cell.getChildren().add(moreLabel);
    }

    cell.setCursor(Cursor.HAND);
    Tooltip.install(cell, new Tooltip("Click to see the boxes ending this day"));
    cell.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY) {
        CalendarDayView.show(stage, date, mailboxes, () -> show(stage, month));
      }
    });

    return cell;
  }

  /** Not used: the screen is built with static methods. */
  private CalendarView() {
  }

}
