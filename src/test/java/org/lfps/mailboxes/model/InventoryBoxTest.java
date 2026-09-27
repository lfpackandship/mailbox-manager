package org.lfps.mailboxes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class InventoryBoxTest {

  @Test
  void trimsTheSizeAndStoresBlankAsNone() {
    assertEquals("Large", new InventoryBox("1", "  Large ").getSize());
    assertNull(new InventoryBox("1", "   ").getSize());
    assertNull(new InventoryBox("1", null).getSize());
  }

}
