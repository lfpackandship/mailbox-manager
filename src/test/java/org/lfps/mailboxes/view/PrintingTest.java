package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for printing renewal reminders, the price sheet, and lists, and for
 * saving lists as spreadsheets, without a printer.
 */
class PrintingTest {

  private final Printing.Printer realPrinter = Printing.printer;

  private final TableOutput.ChooseFile realChooseFile = TableOutput.chooseFile;

  private final TableOutput.ChooseFile realChoosePdf = Printing.choosePdf;

  /** The pages given to the printer, one list per print. */
  private final List<List<Region>> printed = new ArrayList<>();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void setUp() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM prices");
      stmt.execute("DELETE FROM box_sizes");
      stmt.execute("DELETE FROM mailboxes");
    }
    new BoxInventoryRepository().add(List.of("101", "102", "103"), "Small");
    new PriceRepository().save(Map.of(PriceRepository.key("small", 3), 9000L));
    var mailboxes = new MailboxRepository();
    mailboxes.insert(box("101", "Overdue", LocalDate.now().minusDays(2)));
    mailboxes.insert(box("102", "Soon", LocalDate.now().plusDays(5)));
    mailboxes.insert(box("103", "Later", LocalDate.now().plusYears(1)));
    Printing.printer = (jobName, pages) -> {
      printed.add(pages);
      return CompletableFuture.completedFuture(true);
    };
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      RenewalsView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void tearDown() {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    Printing.printer = realPrinter;
    TableOutput.chooseFile = realChooseFile;
    Printing.choosePdf = realChoosePdf;
  }

  @Test
  void printsAReminderForEachBoxDueThatsTicked() {
    FxTestSupport.run(() -> button(mainWindow, "printRemindersButton").fire());
    var window = printWindow();
    assertEquals("Print Renewal Reminders", FxTestSupport.call(window::getTitle));
    assertEquals("Print 2 Reminders", FxTestSupport.call(() -> button(window, "printButton").getText()));

    FxTestSupport.run(() -> button(window, "printButton").fire());

    assertEquals(1, printed.size());
    assertEquals(List.of("Box 101 – Ada Overdue", "Box 102 – Ada Soon"), reminderBoxes(printed.get(0)));
    assertEquals("Printed 2 reminders", FxTestSupport.call(() -> label(window, "printResultLabel").getText()));
  }

  @Test
  void savesTheRemindersAsAPdfWithNoPrinter() throws Exception {
    var chosen = new File(Database.dataDir().toFile(), "reminders");
    Printing.choosePdf = (owner, suggestedName) -> {
      assertTrue(suggestedName.startsWith("renewal-reminders-"), suggestedName);
      return chosen;
    };
    FxTestSupport.run(() -> button(mainWindow, "printRemindersButton").fire());
    var window = printWindow();

    FxTestSupport.run(() -> button(window, "savePdfButton").fire());

    // ".pdf" is added if left off.
    var file = Path.of(chosen.getPath() + ".pdf");
    var deadline = System.currentTimeMillis() + 20_000;
    while (!FxTestSupport.call(() -> label(window, "printResultLabel").getText()).startsWith("Saved")) {
      assertTrue(System.currentTimeMillis() < deadline, "timed out saving");
      Thread.sleep(50);
    }
    assertEquals("Saved " + file, FxTestSupport.call(() -> label(window, "printResultLabel").getText()));
    assertTrue(printed.isEmpty());

    var bytes = Files.readAllBytes(file);
    var text = new String(bytes, StandardCharsets.ISO_8859_1);
    assertTrue(text.startsWith("%PDF-1.4"));
    assertTrue(text.contains("/Type /Pages /Kids [5 0 R 8 0 R] /Count 2"), text.substring(text.lastIndexOf("2 0 obj")));
    assertTrue(text.endsWith("%%EOF\n"));
    // Each object is where the table at the end says it is.
    var xref = Integer.parseInt(text.substring(text.lastIndexOf("startxref") + 10, text.lastIndexOf("%%EOF")).trim());
    var entries = text.substring(xref).split("\n");
    var count = Integer.parseInt(entries[1].split(" ")[1]);
    for (var number = 1; number < count; number++) {
      var offset = Integer.parseInt(entries[2 + number].substring(0, 10));
      assertTrue(text.startsWith(number + " 0 obj", offset), "object " + number);
    }
  }

  @Test
  void untickedBoxesArentPrinted() {
    FxTestSupport.run(() -> button(mainWindow, "printRemindersButton").fire());
    var window = printWindow();

    FxTestSupport.run(() -> button(window, "printUntickAllButton").fire());
    assertTrue(FxTestSupport.call(() -> button(window, "printButton").isDisabled()));

    FxTestSupport.run(() -> button(window, "printTickAllButton").fire());
    assertEquals("Print 2 Reminders", FxTestSupport.call(() -> button(window, "printButton").getText()));
  }

  @Test
  void previewsTheChosenBoxsReminder() {
    FxTestSupport.run(() -> button(mainWindow, "printRemindersButton").fire());
    var window = printWindow();
    assertEquals("Box 101 – Ada Overdue", previewedBox(window));

    FxTestSupport.run(() -> boxList(window).getSelectionModel().select(1));

    assertEquals("Box 102 – Ada Soon", previewedBox(window));
  }

  @Test
  void printsAReminderFromABoxsDetails() throws SQLException {
    var box = new MailboxRepository().findAll().get(2);
    FxTestSupport.run(() -> BoxDetailsView.show(mainWindow, box, () -> { }, () -> { }));
    var details = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#detailsReminderButton") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow());

    FxTestSupport.run(() -> button(details, "detailsReminderButton").fire());
    var window = printWindow();
    FxTestSupport.run(() -> button(window, "printButton").fire());

    assertEquals(List.of("Box 103 – Ada Later"), reminderBoxes(printed.get(0)));
  }

  @Test
  void printsThePlainPriceSheet() {
    FxTestSupport.run(() -> PriceSheetView.showPriceSheet(mainWindow));
    var window = printWindow();
    assertNull(FxTestSupport.call(() -> window.getScene().lookup("#printBoxList")));

    FxTestSupport.run(() -> button(window, "printButton").fire());

    assertEquals(1, printed.get(0).size());
    assertEquals(List.of(), reminderBoxes(printed.get(0)));
  }

  @Test
  void aPrintingProblemIsShownAndCancellingSaysNothing() {
    FxTestSupport.run(() -> button(mainWindow, "printRemindersButton").fire());
    var window = printWindow();

    Printing.printer = (jobName, pages) -> CompletableFuture.failedFuture(
        new IllegalStateException("No printer is set up on this computer."));
    FxTestSupport.run(() -> button(window, "printButton").fire());
    assertEquals("No printer is set up on this computer.",
        FxTestSupport.call(() -> label(window, "printResultLabel").getText()));

    Printing.printer = (jobName, pages) -> CompletableFuture.completedFuture(false);
    FxTestSupport.run(() -> {
      label(window, "printResultLabel").setText("");
      button(window, "printButton").fire();
    });
    assertEquals("", FxTestSupport.call(() -> label(window, "printResultLabel").getText()));
  }

  @Test
  void printsTheRenewalsList() {
    FxTestSupport.run(() -> button(mainWindow, "printListButton").fire());

    var text = pageText(printed.get(0).get(0));
    assertTrue(text.contains("Renewals"), text);
    assertTrue(text.contains("Past Due"), text);
    assertTrue(text.contains("Ada Overdue"), text);
    assertTrue(text.contains("Ada Soon"), text);
    assertFalse(text.contains("Ada Later"), text);
  }

  @Test
  void savesManageBoxesAsASpreadsheetWithEverythingRecorded() throws IOException {
    var saved = new File(Database.dataDir().toFile(), "boxes");
    TableOutput.chooseFile = (owner, suggestedName) -> {
      assertTrue(suggestedName.startsWith("boxes-"), suggestedName);
      return saved;
    };
    FxTestSupport.run(() -> ManageBoxesView.show(mainWindow));

    FxTestSupport.run(() -> button(mainWindow, "spreadsheetButton").fire());

    // ".csv" is added if left off.
    var file = Path.of(saved.getPath() + ".csv");
    var csv = Files.readString(file, StandardCharsets.UTF_8);
    Files.delete(file);
    assertTrue(csv.startsWith("﻿Box Number,Name,First Name,Last Name,Business Title,Phone,Box Name,Email,End Date,"),
        csv);
    assertTrue(csv.contains("101,Ada Overdue,Ada,Overdue,,(555) 123-4567,,,"), csv);
    assertEquals(4, csv.split("\r\n").length);
  }

  @Test
  void csvQuotesCellsWithCommasQuotesAndLineBreaks() {
    var table = FxTestSupport.call(() -> table(List.of(
        new String[] { "1", "Smith, Jones & Co" },
        new String[] { "2", "The \"Best\" Shop" },
        new String[] { "3", "Line one\nline two" })));

    assertEquals("Box,Name\r\n1,\"Smith, Jones & Co\"\r\n2,\"The \"\"Best\"\" Shop\"\r\n3,Line one; line two\r\n",
        FxTestSupport.call(() -> TableOutput.csv(List.of(table))));
  }

  @Test
  void aLongListPrintsOnSeveralPagesWithTheHeadingsOnEach() {
    var rows = new ArrayList<String[]>();
    for (var i = 1; i <= 100; i++) {
      rows.add(new String[] { String.valueOf(i), "Holder " + i });
    }
    var table = FxTestSupport.call(() -> table(rows));

    var pages = FxTestSupport.call(() -> TableOutput.pages("Boxes", List.of(new TableOutput.Section(null, table)),
        LocalDate.of(2026, 10, 3)));

    assertEquals(3, pages.size());
    var last = pageText(pages.get(2));
    assertTrue(last.contains("Box") && last.contains("Name"), last);
    assertTrue(last.contains("Holder 100"), last);
    assertTrue(last.contains("Page 3 of 3"), last);
  }

  @Test
  void amountsArePrintedAsMoney() {
    var table = FxTestSupport.call(() -> {
      var view = new TableView<Long>(FXCollections.observableArrayList(12000L));
      var amount = new TableColumn<Long, Long>("Amount");
      amount.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
      TableOutput.formatWith(amount, org.lfps.mailboxes.util.Money::format);
      view.getColumns().add(amount);
      return view;
    });

    assertEquals("Amount\r\n$120.00\r\n", FxTestSupport.call(() -> TableOutput.csv(List.of(table))));
  }

  /** Makes a table with a Box and a Name column holding the given rows. */
  private static TableView<String[]> table(List<String[]> rows) {
    var table = new TableView<String[]>(FXCollections.observableArrayList(rows));
    var box = new TableColumn<String[], String>("Box");
    box.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue()[0]));
    var name = new TableColumn<String[], String>("Name");
    name.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue()[1]));
    table.getColumns().add(box);
    table.getColumns().add(name);
    return table;
  }

  /** Makes a box with the given holder and end date. */
  private static Mailbox box(String boxNumber, String lastName, LocalDate endDate) {
    return new Mailbox(0, "Ada", lastName, null, boxNumber, null, "(555) 123-4567", null, null, endDate, null);
  }

  /** Returns the box each reminder page is for, as printed on it. */
  private static List<String> reminderBoxes(List<Region> pages) {
    return FxTestSupport.call(() -> {
      var boxes = new ArrayList<String>();
      for (var page : pages) {
        var label = (Label) page.lookup("#reminderBox");
        if (label != null) {
          boxes.add(label.getText());
        }
      }
      return boxes;
    });
  }

  /** Returns all the text on a page, one label per line. */
  private static String pageText(Region page) {
    return FxTestSupport.call(() -> {
      var text = new StringBuilder();
      page.lookupAll(".label").forEach(node -> text.append(((Label) node).getText()).append('\n'));
      return text.toString();
    });
  }

  /** Returns the box the preview on the print window shows, as printed on it. */
  private static String previewedBox(Stage window) {
    return FxTestSupport.call(() -> ((Label) window.getScene().lookup("#printPreview").lookup("#reminderBox"))
        .getText());
  }

  /** Returns the list of boxes to tick on the print window. */
  @SuppressWarnings("unchecked")
  private static ListView<Mailbox> boxList(Stage window) {
    return (ListView<Mailbox>) window.getScene().lookup("#printBoxList");
  }

  /** Returns the open print window, failing if there isn't one. */
  private static Stage printWindow() {
    var window = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#printButton") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElse(null));
    assertNotNull(window);
    return window;
  }

  /** Returns the button with the given id on a window. */
  private static Button button(Stage window, String id) {
    return (Button) window.getScene().lookup("#" + id);
  }

  /** Returns the label with the given id on a window. */
  private static Label label(Stage window, String id) {
    return (Label) window.getScene().lookup("#" + id);
  }

}
