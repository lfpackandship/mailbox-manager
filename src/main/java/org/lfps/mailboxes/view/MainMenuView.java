package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;

/**
 * The application's main menu: a grid linking to Add New Box, Manage Boxes,
 * Renewals, Calendar, Payments, and Box Inventory. Small bubbles on the
 * Renewals button count the boxes past due and due soon, so there's a hint
 * without anything in the way.
 */
public class MainMenuView {

  /**
   * Builds and displays the main menu on the given stage.
   *
   * @param stage the window to render the menu into
   */
  public static void show(Stage stage) {
    var addBoxBtn = new Button("Add New Box");
    var manageBoxesBtn = new Button("Manage Boxes");
    var calendarBtn = new Button("Calendar");
    var renewalsBtn = new Button("Renewals");
    var paymentsBtn = new Button("Payments");
    var inventoryBtn = new Button("Box Inventory");

    for (var btn : new Button[] { addBoxBtn, manageBoxesBtn, calendarBtn, renewalsBtn, paymentsBtn,
        inventoryBtn }) {
      btn.setPrefSize(250, 130);
    }

    addBoxBtn.setOnAction(e -> AddBoxView.show(stage));
    manageBoxesBtn.setOnAction(e -> ManageBoxesView.show(stage));
    calendarBtn.setOnAction(e -> CalendarView.show(stage));
    renewalsBtn.setOnAction(e -> RenewalsView.show(stage));
    paymentsBtn.setOnAction(e -> PaymentsView.show(stage));
    inventoryBtn.setOnAction(e -> BoxInventoryView.show(stage));

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20));
    grid.setAlignment(Pos.CENTER);
    grid.add(addBoxBtn, 0, 0);
    grid.add(manageBoxesBtn, 1, 0);
    grid.add(withBadge(renewalsBtn), 0, 1);
    grid.add(calendarBtn, 1, 1);
    grid.add(paymentsBtn, 0, 2);
    grid.add(inventoryBtn, 1, 2);

    AppWindow.show(stage, grid);
  }

  /**
   * Puts bubbles on the Renewals button's corner counting the boxes listed on
   * Renewals, with the same boxes and the Renewals look-ahead days set in
   * Settings: a red one for those past due and a yellow one for those due
   * soon, each left out if there are none. Pointing at the button says what
   * the numbers mean.
   *
   * @param renewalsBtn the Renewals button
   * @return the button with its bubbles, or the button alone if nothing is due
   *     or the boxes can't be read (the main menu still opens; Renewals shows
   *     the problem)
   */
  private static Node withBadge(Button renewalsBtn) {
    int pastDue;
    int upcoming;
    int days;
    try {
      var today = LocalDate.now();
      days = new SettingsRepository().getInt(Setting.RENEWAL_WINDOW_DAYS);
      var mailboxes = new MailboxRepository().findOpen();
      pastDue = RenewalsView.pastDue(mailboxes, today).size();
      upcoming = RenewalsView.upcoming(mailboxes, today, days).size();
    } catch (SQLException | RuntimeException e) {
      return renewalsBtn;
    }
    if (pastDue + upcoming == 0) {
      return renewalsBtn;
    }

    renewalsBtn.setTooltip(new Tooltip(dueSummary(pastDue, upcoming, days)));
    var badges = new HBox(3);
    if (pastDue > 0) {
      badges.getChildren().add(badge("pastDueBadge", pastDue, "#c62828", "white"));
    }
    if (upcoming > 0) {
      // Dark text, since white doesn't show up on yellow.
      badges.getChildren().add(badge("dueSoonBadge", upcoming, "#fbc02d", "#3e2723"));
    }
    badges.setAlignment(Pos.TOP_RIGHT);
    badges.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);
    // Clicks go through to the button underneath.
    badges.setMouseTransparent(true);
    StackPane.setAlignment(badges, Pos.TOP_RIGHT);
    StackPane.setMargin(badges, new Insets(5, 5, 0, 0));
    return new StackPane(renewalsBtn, badges);
  }

  /**
   * Makes one small round bubble with a number in it.
   *
   * @param id the bubble's id, for finding it in tests
   * @param count the number shown
   * @param background the bubble's colour, such as "#c62828"
   * @param text the number's colour
   * @return the bubble
   */
  private static Label badge(String id, int count, String background, String text) {
    var badge = new Label(String.valueOf(count));
    badge.setId(id);
    badge.setStyle("-fx-background-color: " + background + "; -fx-text-fill: " + text + "; "
        + "-fx-font-size: 0.75em; -fx-font-weight: bold; -fx-background-radius: 1em; -fx-padding: 0 0.45em;");
    return badge;
  }

  /**
   * Describes how many boxes are past due and how many end soon, such as "2
   * boxes are past due, and 5 end in the next 30 days."
   *
   * @param pastDue how many boxes ended before today
   * @param upcoming how many end from today through {@code days} days from now
   * @param days how many days ahead Renewals looks
   * @return the description, or an empty string if both are zero
   */
  static String dueSummary(int pastDue, int upcoming, int days) {
    String when;
    if (days == 0) {
      when = "today";
    } else if (days == 1) {
      when = "by tomorrow";
    } else {
      when = "in the next " + days + " days";
    }
    var ends = (upcoming == 1 ? " ends " : " end ") + when;
    if (pastDue == 0 && upcoming == 0) {
      return "";
    }
    var late = pastDue + (pastDue == 1 ? " box is past due" : " boxes are past due");
    if (upcoming == 0) {
      return late + ".";
    }
    if (pastDue == 0) {
      return upcoming + (upcoming == 1 ? " box" : " boxes") + ends + ".";
    }
    return late + ", and " + upcoming + ends + ".";
  }

  /** Not used: the screen is built with static methods. */
  private MainMenuView() {
  }

}
