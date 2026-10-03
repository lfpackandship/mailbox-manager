package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for the printed price sheet and renewal reminders.
 */
class PriceSheetTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @Test
  void loadsTheSizesWithPricesAndOnlyTheLengthsThatHaveOne() throws SQLException {
    emptyDatabase();
    var inventory = new BoxInventoryRepository();
    inventory.add(List.of("1"), "Small");
    inventory.add(List.of("2"), "Large");
    inventory.add(List.of("3"), "Huge");
    var prices = new PriceRepository();
    prices.save(Map.of(
        PriceRepository.key("small", 3), 9000L,
        PriceRepository.key("small", 12), 24000L,
        PriceRepository.key("large", 12), 40000L));
    prices.saveDescriptions(Map.of("SMALL", " 3¾\" x 5\" x 14\" "));

    var content = PriceSheet.load();

    assertEquals("Lake Forest Pack and Ship", content.shopName);
    // Huge has no prices, and nothing is priced for 1 or 6 months. Cheapest first.
    assertEquals(List.of("Small", "Large"), headings(content));
    assertEquals(List.of(3, 12), content.lengths);
    assertEquals("3¾\" x 5\" x 14\"", content.columns.get(0).description);
    assertEquals(Map.of(12, 40000L), content.columns.get(1).prices);
  }

  @Test
  void theDefaultPricesAreTheirOwnColumn() throws SQLException {
    emptyDatabase();
    new BoxInventoryRepository().add(List.of("1"), "Small");
    new PriceRepository().save(Map.of(
        PriceRepository.key("small", 3), 9000L,
        PriceRepository.key(PriceRepository.DEFAULT_SIZE, 3), 10000L));

    var content = PriceSheet.load();

    assertEquals(List.of("Small", "Other sizes"), headings(content));
    assertSame(content.columns.get(0), content.columnFor("small "));
    // A box with no size, or a size with no prices, pays the default.
    assertSame(content.columns.get(1), content.columnFor(null));
    assertSame(content.columns.get(1), content.columnFor("Huge"));
  }

  @Test
  void withOnlyDefaultPricesTheColumnIsHeadedPrice() throws SQLException {
    emptyDatabase();
    new PriceRepository().save(Map.of(PriceRepository.key(PriceRepository.DEFAULT_SIZE, 6), 15000L));

    assertEquals(List.of("Price"), headings(PriceSheet.load()));
  }

  @Test
  void usesTheWordingFromSettings() throws SQLException {
    emptyDatabase();
    var settings = new SettingsRepository();
    settings.put(Setting.SHOP_NAME, "Box Shop");
    settings.put(Setting.PRICE_SHEET_NOTE, "");

    var page = layOut(PriceSheet.page(PriceSheet.load(), TODAY));

    assertEquals("Box Shop", FxTestSupport.call(() -> label(page, "sheetShopName").getText()));
    assertNull(FxTestSupport.call(() -> page.lookup("#sheetNote")));
    assertNotNull(FxTestSupport.call(() -> page.lookup("#sheetNoPrices")));
  }

  @Test
  void aReminderCirclesTheBoxsSizeAndEndDate() {
    var page = layOut(PriceSheet.page(content(), box(LocalDate.of(2026, 10, 31)), "medium", TODAY));

    assertEquals("Box 12 – Ada Lovelace", FxTestSupport.call(() -> label(page, "reminderBox").getText()));
    assertEquals("Saturday, October 31, 2026",
        FxTestSupport.call(() -> label(page, "reminderEndDate").getText()));

    var sizeCircle = FxTestSupport.call(() -> bounds(page, page.lookup("#sizeCircle")));
    var medium = FxTestSupport.call(() -> bounds(page, page.lookup("#sheetHeader-1")));
    var mediumPrice = FxTestSupport.call(() -> bounds(page, page.lookup("#sheetPrice-12-1")));
    var smallPrice = FxTestSupport.call(() -> bounds(page, page.lookup("#sheetPrice-12-0")));
    var largePrice = FxTestSupport.call(() -> bounds(page, page.lookup("#sheetPrice-12-2")));
    assertTrue(sizeCircle.contains(medium), "the Medium heading is circled");
    assertTrue(sizeCircle.contains(mediumPrice), "the Medium prices are circled");
    assertFalse(sizeCircle.contains(centre(smallPrice)), "the Small prices aren't circled");
    assertFalse(sizeCircle.contains(centre(largePrice)), "the Large prices aren't circled");

    var dateCircle = FxTestSupport.call(() -> bounds(page, page.lookup("#endDateCircle")));
    assertTrue(dateCircle.contains(FxTestSupport.call(() -> bounds(page, page.lookup("#reminderEndDate")))));
  }

  @Test
  void aBoxWithNoSizeHasNoSizeCircledWithoutDefaultPrices() {
    var page = layOut(PriceSheet.page(content(), box(LocalDate.of(2026, 10, 31)), null, TODAY));

    assertNull(FxTestSupport.call(() -> page.lookup("#sizeCircle")));
    assertNotNull(FxTestSupport.call(() -> page.lookup("#endDateCircle")));
  }

  @Test
  void aPastDueReminderSaysTheRentalEnded() {
    var page = layOut(PriceSheet.page(content(), box(LocalDate.of(2026, 9, 30)), "Small", TODAY));

    assertTrue(FxTestSupport.call(() -> page.lookupAll(".label").stream()
        .anyMatch(node -> "Your rental ended on".equals(((Label) node).getText()))));
  }

  @Test
  void thePlainPriceSheetHasNoReminderOrCircles() {
    var page = layOut(PriceSheet.page(content(), TODAY));

    assertNull(FxTestSupport.call(() -> page.lookup("#reminderBox")));
    assertTrue(FxTestSupport.call(() -> page.lookupAll("Rectangle").isEmpty()));
    assertEquals("Prices as of October 3, 2026", FxTestSupport.call(() -> label(page, "sheetDate").getText()));
  }

  @Test
  void fitsTheWidthOfThePage() {
    var page = layOut(PriceSheet.page(content(), box(LocalDate.of(2026, 10, 31)), "Large", TODAY));

    assertTrue(FxTestSupport.call(page::getWidth) <= PriceSheet.PAGE_WIDTH + 8);
    // The circle around the last column stays on the page.
    var circle = FxTestSupport.call(() -> bounds(page, page.lookup("#sizeCircle")));
    assertTrue(circle.getMaxX() <= FxTestSupport.call(page::getWidth));
  }

  @Test
  void drawsEachPageForThePrinterInRed() throws Exception {
    var page = layOut(PriceSheet.page(content(), box(LocalDate.of(2026, 10, 31)), "Medium", TODAY));
    var pages = new Printing.Pages(List.of(page));
    var paper = new java.awt.image.BufferedImage(612, 792, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var graphics = paper.createGraphics();
    graphics.setColor(java.awt.Color.WHITE);
    graphics.fillRect(0, 0, 612, 792);

    // The printer asks from a thread of its own, as here, not the JavaFX thread.
    assertEquals(java.awt.print.Printable.PAGE_EXISTS, pages.print(graphics, new java.awt.print.PageFormat(), 0));
    assertEquals(java.awt.print.Printable.NO_SUCH_PAGE, pages.print(graphics, new java.awt.print.PageFormat(), 1));

    var reddish = 0;
    for (var y = 0; y < paper.getHeight(); y++) {
      for (var x = 0; x < paper.getWidth(); x++) {
        var color = new java.awt.Color(paper.getRGB(x, y));
        if (color.getRed() > 150 && color.getGreen() < 100 && color.getBlue() < 100) {
          reddish++;
        }
      }
    }
    assertTrue(reddish > 200, "the circles are drawn: " + reddish);
  }

  /** The prices from the shop's price sheet. */
  private static PriceSheet.Content content() {
    return new PriceSheet.Content("Lake Forest Pack and Ship", "736 N. Western Ave\nLake Forest, IL 60045",
        "Mailbox Service Includes:\n- 24-Hour Access to Mailbox\n\n* requires funding", "Plus a key deposit",
        "Please renew at the counter.", List.of(3, 6, 12), List.of(
            new PriceSheet.Column("Small", "Small", "3¾\" x 5\" x 14\"", Map.of(3, 9000L, 6, 15000L, 12, 24000L)),
            new PriceSheet.Column("Medium", "Medium", "", Map.of(3, 10500L, 6, 18000L, 12, 30000L)),
            new PriceSheet.Column("Large", "Large", "", Map.of(3, 15000L, 6, 26000L, 12, 40000L))));
  }

  private static Mailbox box(LocalDate endDate) {
    return new Mailbox(0, "Ada", "Lovelace", null, "12", null, "", null, null, endDate, null);
  }

  private static Region layOut(Region page) {
    return FxTestSupport.call(() -> Printing.layOut(page));
  }

  private static Label label(Region page, String id) {
    return (Label) page.lookup("#" + id);
  }

  private static Bounds bounds(Region page, Node node) {
    if (node instanceof Rectangle) {
      var circle = (Rectangle) node;
      return new javafx.geometry.BoundingBox(circle.getX(), circle.getY(), circle.getWidth(), circle.getHeight());
    }
    return page.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
  }

  private static javafx.geometry.Point2D centre(Bounds bounds) {
    return new javafx.geometry.Point2D(bounds.getCenterX(), bounds.getCenterY());
  }

  private static List<String> headings(PriceSheet.Content content) {
    return content.columns.stream().map(column -> column.heading).collect(java.util.stream.Collectors.toList());
  }

  private static void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM prices");
      stmt.execute("DELETE FROM box_sizes");
      stmt.execute("DELETE FROM box_inventory");
    }
  }

}
