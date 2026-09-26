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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Shows a month-grid calendar with mailboxes marked on the day their box
 * rental ends, and Prev/Next buttons to browse other months. Weeks start on
 * the day set by {@link Setting#WEEK_START}.
 */
public class CalendarView {

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
    var statusLabel = new Label();
    var mailboxesByEndDate = Map.<LocalDate, List<Mailbox>>of();

    try {
      mailboxesByEndDate = new MailboxRepository().findAll().stream()
          .filter(m -> m.getEndDate() != null)
          .collect(Collectors.groupingBy(Mailbox::getEndDate));
    } catch (SQLException e) {
      statusLabel.setStyle("-fx-text-fill: red;");
      statusLabel.setText("Failed to load mailboxes: " + e.getMessage());
    }

    var prevBtn = new Button("< Prev");
    prevBtn.setOnAction(e -> show(stage, month.minusMonths(1)));

    var nextBtn = new Button("Next >");
    nextBtn.setOnAction(e -> show(stage, month.plusMonths(1)));

    var monthLabel = new Label(
        month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.getYear());
    monthLabel.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    var header = new HBox(10, prevBtn, monthLabel, nextBtn);
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
      calendarGrid.add(buildDayCell(day, mailboxesByEndDate.getOrDefault(date, List.of())), col, row);
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
   */
  static int column(DayOfWeek day, DayOfWeek firstDay) {
    return (day.getValue() - firstDay.getValue() + 7) % 7;
  }

  private static DayOfWeek weekStart() {
    try {
      return DayOfWeek.valueOf(new SettingsRepository().get(Setting.WEEK_START));
    } catch (SQLException | IllegalArgumentException e) {
      return DayOfWeek.SUNDAY;
    }
  }

  private static VBox buildDayCell(int day, List<Mailbox> mailboxes) {
    var cell = new VBox(2);
    cell.setPadding(new Insets(4));
    cell.setStyle("-fx-pref-width: 7em; -fx-pref-height: 5.4em;"
        + " -fx-border-color: lightgray; -fx-border-width: 0.5;"
        + (mailboxes.isEmpty() ? "" : " -fx-background-color: #ffe0b2;"));

    cell.getChildren().add(new Label(String.valueOf(day)));

    for (var mailbox : mailboxes) {
      var entryLabel = new Label("Box " + mailbox.getBoxNumber());
      entryLabel.setStyle("-fx-font-size: 0.8em; -fx-text-fill: #b34700;");
      Tooltip.install(entryLabel, new Tooltip(mailbox.getFirstName() + " " + mailbox.getLastName()
          + " - Box " + mailbox.getBoxNumber() + " ends " + mailbox.getEndDate()));
      cell.getChildren().add(entryLabel);
    }

    return cell;
  }

  private CalendarView() {
  }

}
