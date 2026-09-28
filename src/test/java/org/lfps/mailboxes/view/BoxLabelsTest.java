package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.model.Mailbox;

class BoxLabelsTest {

  private static final Mailbox ADA = box("Ada", "Lovelace", null);
  private static final Mailbox BUSINESS = box("", "", "Engines Ltd");
  private static final Mailbox NAMELESS = box("", "", null);

  @Test
  void boxAndHolderLeavesOutAMissingHolder() {
    assertEquals("Box 12 – Ada Lovelace", BoxLabels.boxAndHolder(ADA));
    assertEquals("Box 12 – Engines Ltd", BoxLabels.boxAndHolder(BUSINESS));
    assertEquals("Box 12", BoxLabels.boxAndHolder(NAMELESS));
  }

  @Test
  void forHolderLeavesOutAMissingHolder() {
    assertEquals(" for Ada Lovelace", BoxLabels.forHolder(ADA));
    assertEquals("", BoxLabels.forHolder(NAMELESS));
  }

  @Test
  void holderOrUsesTheFallbackOnlyWhenTheresNoHolder() {
    assertEquals("Engines Ltd", BoxLabels.holderOr(BUSINESS, "its holder"));
    assertEquals("its holder", BoxLabels.holderOr(NAMELESS, "its holder"));
  }

  private static Mailbox box(String first, String last, String businessTitle) {
    return new Mailbox(0, first, last, businessTitle, "12", null, "5551000001", null, null, null, null);
  }

}
