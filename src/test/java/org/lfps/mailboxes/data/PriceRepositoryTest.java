package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for saving and finding prices, and for scheduled price changes.
 */
class PriceRepositoryTest {

  private final PriceRepository prices = new PriceRepository();

  @BeforeEach
  @AfterEach
  void noPrices() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM prices");
      stmt.execute("DELETE FROM scheduled_prices");
    }
  }

  @Test
  void aScheduledChangeWaitsUntilItsDate() throws SQLException {
    var tomorrow = LocalDate.now().plusDays(1);
    prices.save(Map.of(PriceRepository.key("Small", 3), 9000L));
    var newPrices = new HashMap<String, Long>();
    newPrices.put(PriceRepository.key("Small", 3), 9500L);
    prices.scheduleChange(tomorrow, newPrices);

    assertEquals(9000L, prices.priceFor("Small", 3));
    var change = prices.findChange();
    assertEquals(tomorrow, change.startsOn);
    assertEquals(Map.of(PriceRepository.key("Small", 3), 9500L), change.prices);

    assertFalse(prices.applyDueChange(LocalDate.now()));
    assertTrue(prices.applyDueChange(tomorrow));
    assertEquals(9500L, prices.priceFor("Small", 3));
    assertNull(prices.findChange());
  }

  @Test
  void aChangeStartingTodayIsAppliedWhenPricesAreRead() throws SQLException {
    prices.save(Map.of(PriceRepository.key("Small", 3), 9000L, PriceRepository.key("Small", 6), 15000L,
        PriceRepository.key("Large", 3), 15000L));
    var newPrices = new HashMap<String, Long>();
    newPrices.put(PriceRepository.key("Small", 3), 9500L);
    // A blank price in the change clears it; prices not in the change stay.
    newPrices.put(PriceRepository.key("Small", 6), null);
    prices.scheduleChange(LocalDate.now(), newPrices);

    assertEquals(Map.of(PriceRepository.key("Small", 3), 9500L, PriceRepository.key("Large", 3), 15000L),
        prices.findAll());
    assertNull(prices.findChange());
  }

  @Test
  void schedulingAgainReplacesTheChangeAndItCanBeCancelled() throws SQLException {
    prices.scheduleChange(LocalDate.now().plusDays(5), Map.of(PriceRepository.key("Small", 3), 9500L));
    prices.scheduleChange(LocalDate.now().plusDays(9), Map.of(PriceRepository.key("Large", 3), 16000L));

    var change = prices.findChange();
    assertEquals(LocalDate.now().plusDays(9), change.startsOn);
    assertEquals(Map.of(PriceRepository.key("Large", 3), 16000L), change.prices);

    prices.cancelChange();
    assertNull(prices.findChange());
  }

  @Test
  void findsThePriceForASizeIgnoringCase() throws SQLException {
    prices.save(Map.of(PriceRepository.key("Large", 6), 9000L, PriceRepository.key("Small", 6), 5000L));

    assertEquals(9000L, prices.priceFor("large", 6));
    assertEquals(5000L, prices.priceFor(" SMALL ", 6));
    assertNull(prices.priceFor("Large", 12));
  }

  @Test
  void fallsBackToTheDefaultPrice() throws SQLException {
    prices.save(Map.of(PriceRepository.key("Large", 6), 9000L,
        PriceRepository.key(PriceRepository.DEFAULT_SIZE, 6), 6000L,
        PriceRepository.key(PriceRepository.DEFAULT_SIZE, 12), 11000L));

    assertEquals(6000L, prices.priceFor(null, 6));
    assertEquals(6000L, prices.priceFor("Medium", 6));
    assertEquals(11000L, prices.priceFor("Large", 12));
    assertNull(prices.priceFor(null, 1));
  }

  @Test
  void aSizeWithTheKeySeparatorInItWorks() throws SQLException {
    prices.save(Map.of(PriceRepository.key("Large | tall", 3), 4000L));
    assertEquals(4000L, prices.priceFor("large | TALL", 3));
  }

  @Test
  void aBlankSizeUsesTheDefault() throws SQLException {
    prices.save(Map.of(PriceRepository.key(PriceRepository.DEFAULT_SIZE, 1), 1000L));
    assertEquals(1000L, prices.priceFor("   ", 1));
  }

  @Test
  void savingReplacesAndClearsPricesAndLeavesOthersAlone() throws SQLException {
    prices.save(Map.of(PriceRepository.key("Large", 6), 9000L, PriceRepository.key("Large", 12), 17000L,
        PriceRepository.key("Small", 6), 5000L));

    var changes = new HashMap<String, Long>();
    changes.put(PriceRepository.key("large", 6), 9500L);
    changes.put(PriceRepository.key("Large", 12), null);
    prices.save(changes);

    assertEquals(Map.of(PriceRepository.key("Large", 6), 9500L, PriceRepository.key("Small", 6), 5000L),
        prices.findAll());
  }

  @Test
  void ordersSizesCheapestFirstWithUnpricedOnesLast() {
    var prices = java.util.Map.of(
        PriceRepository.key("large", 3), 24000L,
        PriceRepository.key("medium", 3), 15000L,
        PriceRepository.key("small", 3), 9000L,
        PriceRepository.key("small", 12), 15000L,
        PriceRepository.key(PriceRepository.DEFAULT_SIZE, 3), 1000L);

    assertEquals(java.util.List.of("Small", "Medium", "Large", "Huge", "Tiny"), PriceRepository.cheapestFirst(
        java.util.List.of("Huge", "Large", "Medium", "Small", "Tiny"), prices));
  }

}
