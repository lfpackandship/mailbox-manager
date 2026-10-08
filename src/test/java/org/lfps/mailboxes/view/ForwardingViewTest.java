package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.LabelRepository;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.model.PrintedLabel;

/**
 * Tests for the Forwarding screen: the list of who mail is forwarded for,
 * printing a new label from it, the list of labels printed, reprinting one,
 * and finding a box by label number on Manage Boxes.
 */
class ForwardingViewTest {

  private static final Printing.PaperPrinter REAL_PRINTER = Printing.paperPrinter;

  private static final ForwardingAddress HOME = new ForwardingAddress("1 Elm St", null, "Town", "IL", "60000",
      null);

  private static final ForwardingAddress SUMMER = new ForwardingAddress("88 Lakeshore Dr", null, "Door County",
      "WI", "54235", "summer");

  private final MailboxRepository mailboxes = new MailboxRepository();

  private final LabelRepository labels = new LabelRepository();

  /** The labels sent to the printer. */
  private final List<ForwardingLabel> printed = new ArrayList<>();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void emptyDatabase() throws SQLException, IOException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM labels");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    Files.deleteIfExists(Database.dataDir().resolve("last-label-number.txt"));
    Printing.paperPrinter = (jobName, printable) -> {
      printed.add((ForwardingLabel) printable);
      return CompletableFuture.completedFuture(true);
    };
    mainWindow = FxTestSupport.call(Stage::new);
  }

  @AfterEach
  void restoreAndCloseAllWindows() {
    Printing.paperPrinter = REAL_PRINTER;
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void addressesAreNewestFirstWithUnknownDaysLast() {
    var old = box(1, "300", HOME.withAddedOn(LocalDate.of(2026, 1, 1)));
    var recent = box(2, "200", SUMMER.withAddedOn(LocalDate.of(2026, 9, 1)));
    var unknownLater = box(3, "12", HOME);
    var unknownEarlier = box(4, "9", HOME);

    var rows = ForwardingView.addressRows(List.of(old, recent, unknownLater, unknownEarlier));

    assertEquals(List.of("200", "300", "9", "12"), rows.stream().map(row -> row.mailbox.getBoxNumber())
        .collect(Collectors.toList()));
  }

  @Test
  void listsEveryForwardingAddressIncludingClosedBoxesAndSearches() throws SQLException {
    mailboxes.insert(saved("Ada", "300", null, HOME, SUMMER));
    mailboxes.insert(saved("Grace", "301", LocalDate.now(), HOME));
    mailboxes.insert(saved("Alan", "302", null));
    show();

    assertEquals(3, FxTestSupport.call(() -> addressTable().getItems().size()));
    FxTestSupport.run(() -> lookup("#forwardingAddressSearch", TextField.class).setText("door county"));
    assertEquals(1, FxTestSupport.call(() -> addressTable().getItems().size()));
    FxTestSupport.run(() -> lookup("#forwardingAddressSearch", TextField.class).setText("grace"));
    assertEquals("301", FxTestSupport.call(() -> addressTable().getItems().get(0).mailbox.getBoxNumber()));
  }

  @Test
  void printLabelOpensTheLabelWindowWithThatAddressChosen() throws SQLException {
    mailboxes.insert(saved("Ada", "300", null, HOME, SUMMER));
    show();
    FxTestSupport.run(() -> {
      lookup("#forwardingAddressSearch", TextField.class).setText("summer");
      addressTable().getSelectionModel().select(0);
      lookup("#forwardingPrintLabelButton", Button.class).fire();
    });
    var window = labelWindow();

    assertTrue(FxTestSupport.call(() -> window.getScene().getRoot().lookupAll(".radio-button").stream()
        .map(node -> (RadioButton) node)
        .anyMatch(radio -> radio.isSelected() && SUMMER.toString().equals(radio.getText()))));
  }

  @Test
  void listsTheLabelsPrintedNewestFirstAndSearchesByNumber() throws SQLException {
    var id = mailboxes.insert(saved("Ada", "300", null, HOME));
    labels.record(id, List.of("Ada Lovelace", "1 Elm St", "Town, IL 60000"), LocalDateTime.of(2026, 10, 6, 9, 0));
    labels.record(id, List.of("Ada Lovelace", "1 Elm St", "Town, IL 60000"), LocalDateTime.of(2026, 10, 7, 9, 0));
    show();
    FxTestSupport.run(() -> tabs().getSelectionModel().select(1));

    assertEquals(List.of("261007-01", "261006-01"), FxTestSupport.call(() -> labelTable().getItems().stream()
        .map(row -> row.label.getNumber()).collect(Collectors.toList())));
    FxTestSupport.run(() -> lookup("#forwardingLabelSearch", TextField.class).setText("261006"));
    assertEquals(1, FxTestSupport.call(() -> labelTable().getItems().size()));
  }

  @Test
  void reprintingKeepsTheNumberAndAddressAndRecordsNothingNew() throws SQLException {
    var id = mailboxes.insert(saved("Ada", "300", null, HOME));
    labels.record(id, List.of("Ada Lovelace", "9 Old Rd", "Town, IL 60000"), LocalDateTime.of(2026, 10, 6, 9, 0));
    show();
    FxTestSupport.run(() -> {
      tabs().getSelectionModel().select(1);
      labelTable().getSelectionModel().select(0);
      lookup("#forwardingReprintButton", Button.class).fire();
    });
    var window = labelWindow();
    FxTestSupport.run(() -> ((Button) window.getScene().lookup("#labelPrintButton")).fire());

    assertEquals("Reprint Forwarding Label", FxTestSupport.call(window::getTitle));
    assertEquals("261006-01", printed.get(0).number);
    assertEquals("9 Old Rd", printed.get(0).to.get(1));
    assertEquals(1, labels.findAll().size());
  }

  @Test
  void anAddressSearchLooksOnlyAtThatAddressAndItsBox() {
    var box = new Mailbox(1, "Ada", "Lovelace", "Engines Ltd", "300", null, "", null, List.of("Analytical Co"),
        null, List.of(HOME, SUMMER));
    var home = new ForwardingView.AddressRow(box, HOME);
    var summer = new ForwardingView.AddressRow(box, SUMMER);

    assertTrue(ForwardingView.matches(summer, "door county"));
    assertFalse(ForwardingView.matches(home, "door county"));
    assertTrue(ForwardingView.matches(home, "300 lovelace"));
    assertTrue(ForwardingView.matches(home, "analytical"));
    assertTrue(ForwardingView.matches(summer, "SUMMER"));
  }

  @Test
  void aLabelSearchMatchesItsNumberBoxNameOrAddress() {
    var box = box(1, "300", HOME);
    var row = new ForwardingView.LabelRow(new PrintedLabel("261007-03", 1, LocalDateTime.now(),
        "Ada Lovelace\n1 Elm St\nTown, IL 60000"), box);

    assertTrue(ForwardingView.matches(row, "261007-03"));
    assertTrue(ForwardingView.matches(row, "300 elm"));
    assertFalse(ForwardingView.matches(row, "261007-04"));
    assertTrue(ForwardingView.matches(row, " "));
  }

  @Test
  void manageBoxesFindsABoxByItsLabelNumber() {
    var box = box(1, "300", HOME);

    assertTrue(ManageBoxesView.matches(box, "261007-03", List.of("261007-01", "261007-03")));
    assertFalse(ManageBoxesView.matches(box, "261007-02", List.of("261007-01", "261007-03")));
  }

  @Test
  void theLabelsListShowsLabelsJustPrintedWhenOpened() throws SQLException {
    var id = mailboxes.insert(saved("Ada", "300", null, HOME));
    show();
    labels.record(id, List.of("Ada"), LocalDateTime.now());

    FxTestSupport.run(() -> tabs().getSelectionModel().select(1));

    assertEquals(1, FxTestSupport.call(() -> labelTable().getItems().size()));
  }

  /** Shows the Forwarding screen in the main window. */
  private void show() {
    FxTestSupport.run(() -> ForwardingView.show(mainWindow));
  }

  /** Returns the open Print Forwarding Label window. */
  private static Stage labelWindow() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#labelPreview") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow());
  }

  /** Returns what matches a selector on the main window, as the given type. */
  private <T> T lookup(String selector, Class<T> type) {
    return type.cast(mainWindow.getScene().getRoot().lookup(selector));
  }

  /** Returns the Forwarding screen's tabs. */
  private TabPane tabs() {
    return lookup("#forwardingTabs", TabPane.class);
  }

  /** Returns the Who We Forward For table. */
  @SuppressWarnings("unchecked")
  private TableView<ForwardingView.AddressRow> addressTable() {
    return lookup("#forwardingAddressTable", TableView.class);
  }

  /** Returns the Labels Printed table. */
  @SuppressWarnings("unchecked")
  private TableView<ForwardingView.LabelRow> labelTable() {
    return lookup("#forwardingLabelTable", TableView.class);
  }

  /** Makes a box with an id, number, and forwarding addresses, not saved. */
  private static Mailbox box(int id, String boxNumber, ForwardingAddress... addresses) {
    return new Mailbox(id, "Ada", "Lovelace", null, boxNumber, null, "", null, null, null, List.of(addresses));
  }

  /** Makes a box to save, closed on the given day or open. */
  private static Mailbox saved(String firstName, String boxNumber, LocalDate closedOn,
      ForwardingAddress... addresses) {
    return new Mailbox(0, firstName, "Lovelace", null, boxNumber, null, "", null, null, null, List.of(addresses),
        null, closedOn);
  }

}
