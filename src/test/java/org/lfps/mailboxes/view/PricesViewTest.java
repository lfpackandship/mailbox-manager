package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * UI tests for setting prices and for Add New Box filling them in.
 */
class PricesViewTest {

  private final PriceRepository prices = new PriceRepository();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void openMainWindow() throws SQLException {
    emptyDatabase();
    new BoxInventoryRepository().add(List.of("1", "2"), "Large");
    new BoxInventoryRepository().add(List.of("3"), "small");
    new BoxInventoryRepository().add(List.of("4"), null);
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      BoxInventoryView.show(stage);
      return stage;
    });
  }

  @AfterEach
  void closeAllWindows() throws SQLException {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
    // Other tests add boxes that aren't in an inventory, and don't expect prices.
    emptyDatabase();
  }

  @Test
  void showsARowForEachSizeAndTheDefaultWithSavedPrices() throws SQLException {
    prices.save(Map.of(PriceRepository.key("large", 6), 9000L));
    var window = openPrices();

    for (var id : List.of("price-large-1", "price-large-12", "price-small-3", "price-default-6")) {
      assertNotNull(FxTestSupport.call(() -> field(window, id)), id);
    }
    assertEquals("$90.00", FxTestSupport.call(() -> field(window, "price-large-6").getText()));
    assertEquals("", FxTestSupport.call(() -> field(window, "price-small-6").getText()));
  }

  @Test
  void savesPricesAndTidiesThem() throws SQLException {
    var window = openPrices();

    FxTestSupport.run(() -> {
      field(window, "price-large-6").setText("90");
      field(window, "price-default-12").setText("$110.5");
      ((Button) window.getScene().lookup("#pricesSaveButton")).fire();
    });

    assertEquals("Saved", text(window));
    assertEquals(Map.of(PriceRepository.key("Large", 6), 9000L,
        PriceRepository.key(PriceRepository.DEFAULT_SIZE, 12), 11050L), prices.findAll());
    assertEquals("$110.50", FxTestSupport.call(() -> field(window, "price-default-12").getText()));
  }

  @Test
  void rejectsAPriceThatIsntMoneyAndSavesNothing() throws SQLException {
    var window = openPrices();

    FxTestSupport.run(() -> {
      field(window, "price-large-6").setText("90");
      field(window, "price-small-1").setText("ten");
      ((Button) window.getScene().lookup("#pricesSaveButton")).fire();
    });

    assertEquals("\"ten\" isn't a price. Enter prices in dollars and cents, like 60 or 60.00.", text(window));
    assertEquals(Map.of(), prices.findAll());
  }

  @Test
  void openingAgainReusesTheOpenWindow() {
    var first = openPrices();
    FxTestSupport.run(() -> ((Button) mainWindow.getScene().getRoot().lookup("#inventoryPricesButton")).fire());
    assertSame(first, pricesWindow());
  }

  @Test
  void addNewBoxFillsInThePriceOnceTheBoxAndLengthAreChosen() throws SQLException {
    prices.save(Map.of(PriceRepository.key("Large", 6), 9000L, PriceRepository.key("small", 6), 5000L));
    FxTestSupport.run(() -> AddBoxView.show(mainWindow));
    var amount = FxTestSupport.call(() -> (TextField) mainWindow.getScene().getRoot().lookup("#amountField"));

    // No box number yet, and no default price, so nothing to fill in.
    FxTestSupport.run(() -> buttonLabeled(RentalLengths.label(6)).fire());
    assertEquals("", FxTestSupport.call(amount::getText));

    FxTestSupport.run(() -> boxNumberField().setText("2"));
    assertEquals("$90.00", FxTestSupport.call(amount::getText));

    FxTestSupport.run(() -> boxNumberField().setText("3"));
    assertEquals("$50.00", FxTestSupport.call(amount::getText));
  }

  private Stage openPrices() {
    FxTestSupport.run(() -> ((Button) mainWindow.getScene().getRoot().lookup("#inventoryPricesButton")).fire());
    var window = pricesWindow();
    assertNotNull(window);
    return window;
  }

  private static Stage pricesWindow() {
    return FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#pricesSaveButton") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElse(null));
  }

  private static TextField field(Stage window, String id) {
    return (TextField) window.getScene().lookup("#" + id);
  }

  private static String text(Stage window) {
    return FxTestSupport.call(() -> ((Label) window.getScene().lookup("#pricesResultLabel")).getText());
  }

  private TextField boxNumberField() {
    return mainWindow.getScene().getRoot().lookupAll(".text-field").stream()
        .map(node -> (TextField) node)
        .filter(f -> "310".equals(f.getPromptText()))
        .findFirst()
        .orElseThrow();
  }

  private Button buttonLabeled(String text) {
    return mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> text.equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

  private static void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM prices");
      stmt.execute("DELETE FROM box_inventory");
    }
  }

}
