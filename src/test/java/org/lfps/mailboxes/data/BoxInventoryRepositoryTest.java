package org.lfps.mailboxes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.InventoryBox;

class BoxInventoryRepositoryTest {

  private final BoxInventoryRepository inventory = new BoxInventoryRepository();

  @BeforeEach
  @AfterEach
  void emptyInventory() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM box_inventory");
    }
  }

  @Test
  void startsEmpty() throws SQLException {
    assertTrue(inventory.isEmpty());
  }

  @Test
  void addsBoxesInBoxNumberOrderAndSkipsOnesAlreadyThere() throws SQLException {
    assertEquals(3, inventory.add(List.of("10", "2", "12A"), "Small"));
    assertEquals(1, inventory.add(List.of("12a", "1"), "Large"));

    assertFalse(inventory.isEmpty());
    assertEquals(List.of("1 Large", "2 Small", "10 Small", "12A Small"), describe(inventory.findAll()));
  }

  @Test
  void containsIgnoresCaseAndSpaces() throws SQLException {
    inventory.add(List.of("12A"), null);
    assertTrue(inventory.contains(" 12a "));
    assertFalse(inventory.contains("12B"));
  }

  @Test
  void setsAndClearsSizes() throws SQLException {
    inventory.add(List.of("1", "2", "3"), null);
    inventory.setSize(List.of("1", "3"), " Medium ");
    inventory.setSize(List.of("3"), "");

    var boxes = inventory.findAll();
    assertEquals("Medium", boxes.get(0).getSize());
    assertNull(boxes.get(1).getSize());
    assertNull(boxes.get(2).getSize());
  }

  @Test
  void removesBoxes() throws SQLException {
    inventory.add(List.of("1", "2", "3"), null);
    inventory.remove(List.of("1", "3"));
    assertEquals(List.of("2 null"), describe(inventory.findAll()));
  }

  @Test
  void looksUpABoxsSize() throws SQLException {
    inventory.add(List.of("12A"), "Large");
    inventory.add(List.of("13"), null);

    assertEquals("Large", inventory.sizeOf(" 12a "));
    assertNull(inventory.sizeOf("13"));
    assertNull(inventory.sizeOf("99"));
  }

  @Test
  void listsEachSizeOnceIgnoringCase() throws SQLException {
    inventory.add(List.of("1", "2"), "small");
    inventory.add(List.of("3"), "Small");
    inventory.add(List.of("4"), "Large");
    inventory.add(List.of("5"), null);

    assertEquals(List.of("Large", "Small"), inventory.sizes());
  }

  private static List<String> describe(List<InventoryBox> boxes) {
    return boxes.stream().map(box -> box.getBoxNumber() + " " + box.getSize()).collect(Collectors.toList());
  }

}
