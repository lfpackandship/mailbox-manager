package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

class ManageBoxesSearchTest {

  private static final Mailbox RIVERA = new Mailbox(1, "Tomás", "Rivera", "Rivera Landscaping",
      "205", "Garden", "(555) 200-0006", "tomas@riveralandscape.com",
      List.of("Rivera Tree Care"), LocalDate.of(2026, 9, 20),
      List.of(new ForwardingAddress("88 Palm Way", "Unit 3B", "Naples", "FL", "34102", "winter")));

  private static final Mailbox SPARSE = new Mailbox(2, "Michael", "Brennan", null,
      "207", null, "(555) 200-0008", null, null, null, null);

  @ParameterizedTest
  @ValueSource(strings = { "", "   ", "tomás", "RIVERA", "landscap", "205", "garden",
      "tree care", "riveralandscape.com", "(555) 200", "2000006", "rivera 205", "  rivera   tree  ",
      "naples", "palm way", "3b", "34102", "fl 34102", "winter", "rivera naples" })
  void matches(String query) {
    assertTrue(ManageBoxesView.matches(RIVERA, query));
  }

  @ParameterizedTest
  @ValueSource(strings = { "brennan", "rivera 207", "999", "2000008", "summer", "tampa" })
  void doesNotMatch(String query) {
    assertFalse(ManageBoxesView.matches(RIVERA, query));
  }

  @Test
  void nullQueryMatchesEverything() {
    assertTrue(ManageBoxesView.matches(RIVERA, null));
  }

  @Test
  void toleratesMissingOptionalFields() {
    assertTrue(ManageBoxesView.matches(SPARSE, "brennan"));
    assertTrue(ManageBoxesView.matches(SPARSE, "2000008"));
    assertFalse(ManageBoxesView.matches(SPARSE, "null"));
  }

}
