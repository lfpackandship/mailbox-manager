package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for recording the keys given out for a box and the deposit paid for
 * them.
 */
class KeysTest {

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void emptyDatabase() throws SQLException {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
    try (var conn = Database.connect(); var stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM settings");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM mailboxes");
    }
  }

  @AfterEach
  void closeAllWindows() {
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void enteringTheNumberOfKeysFillsInTheDeposit() {
    var keys = FxTestSupport.call(() -> new KeyFields(null, null));

    FxTestSupport.run(() -> keys.countField.setText("2"));
    assertEquals("$20.00", FxTestSupport.call(keys.depositField::getText));

    FxTestSupport.run(() -> keys.countField.setText("3"));
    assertEquals("$30.00", FxTestSupport.call(keys.depositField::getText));
    assertEquals(3, FxTestSupport.call(keys::count));
    assertEquals(3000L, FxTestSupport.call(keys::depositCents));
  }

  @Test
  void aDepositTypedInIsKept() {
    var keys = FxTestSupport.call(() -> new KeyFields(null, null));

    FxTestSupport.run(() -> {
      keys.countField.setText("2");
      keys.depositField.setText("15");
      keys.countField.setText("3");
    });

    assertEquals("15", FxTestSupport.call(keys.depositField::getText));
  }

  @Test
  void theDepositPerKeyComesFromSettings() throws SQLException {
    new SettingsRepository().put(Setting.KEY_DEPOSIT, "$7.50");
    var keys = FxTestSupport.call(() -> new KeyFields(null, null));

    FxTestSupport.run(() -> keys.countField.setText("2"));

    assertEquals("$15.00", FxTestSupport.call(keys.depositField::getText));
  }

  @Test
  void withNoDepositPerKeyNothingIsFilledIn() throws SQLException {
    new SettingsRepository().put(Setting.KEY_DEPOSIT, "");
    var keys = FxTestSupport.call(() -> new KeyFields(null, null));

    FxTestSupport.run(() -> keys.countField.setText("2"));

    assertEquals("", FxTestSupport.call(keys.depositField::getText));
  }

  @Test
  void aRecordedDepositThatMatchesTheKeysFollowsThem() {
    var keys = FxTestSupport.call(() -> new KeyFields(2, 2000L));
    assertEquals("$20.00", FxTestSupport.call(keys.depositField::getText));

    FxTestSupport.run(() -> keys.countField.setText("1"));

    assertEquals("$10.00", FxTestSupport.call(keys.depositField::getText));
  }

  @Test
  void blankFieldsMeanNothingRecordedAndNonsenseIsRejected() {
    var keys = FxTestSupport.call(() -> new KeyFields(null, null));
    assertNull(FxTestSupport.call(keys::count));
    assertNull(FxTestSupport.call(keys::depositCents));

    FxTestSupport.run(() -> keys.countField.setText("two"));
    assertEquals("Keys must be a whole number from 0 to 20.",
        assertThrows(IllegalArgumentException.class, keys::count).getMessage());
    FxTestSupport.run(() -> keys.countField.setText("21"));
    assertThrows(IllegalArgumentException.class, keys::count);
    FxTestSupport.run(() -> keys.depositField.setText("lots"));
    assertThrows(IllegalArgumentException.class, keys::depositCents);
  }

  @Test
  void describesTheKeys() {
    assertEquals("2 keys, $20.00 deposit", KeyFields.describe(2, 2000L));
    assertEquals("1 key", KeyFields.describe(1, null));
    assertEquals("$10.00 deposit", KeyFields.describe(null, 1000L));
    assertEquals("", KeyFields.describe(null, null));
  }

  @Test
  void closingABoxRemindsToCollectTheKeysAndGiveBackTheDeposit() {
    assertEquals("Remember to collect the 2 keys and give back the $20.00 key deposit. ",
        ManageBoxesView.keysReminder(box(2, 2000L, null)));
    assertEquals("Remember to collect the key. ", ManageBoxesView.keysReminder(box(1, null, null)));
    assertEquals("Remember to give back the $10.00 key deposit. ",
        ManageBoxesView.keysReminder(box(null, 1000L, null)));
    assertEquals("", ManageBoxesView.keysReminder(box(0, 0L, null)));
    assertEquals("", ManageBoxesView.keysReminder(box(null, null, null)));
  }

  @Test
  void paymentsShowsTheDepositsHeldForOpenBoxesOnly() {
    assertEquals("Key deposits held for open boxes: $30.00 (not included above)", PaymentsView.depositsHeld(
        List.of(box(2, 2000L, null), box(1, 1000L, null), box(1, 1000L, LocalDate.now()), box(null, null, null))));
    assertEquals("", PaymentsView.depositsHeld(List.of(box(null, null, null))));
  }

  @Test
  void editBoxSavesTheKeysAndTheDetailsShowThem() throws SQLException {
    var repository = new MailboxRepository();
    repository.insert(box(null, null, null));
    var saved = repository.findAll().get(0);
    var stage = FxTestSupport.call(() -> {
      var window = new Stage();
      EditBoxView.show(window, saved, () -> { });
      return window;
    });

    FxTestSupport.run(() -> {
      ((TextField) stage.getScene().lookup("#keyCountField")).setText("2");
      stage.getScene().getRoot().lookupAll(".button").stream()
          .map(node -> (Button) node)
          .filter(b -> "Save".equals(b.getText()))
          .findFirst()
          .orElseThrow()
          .fire();
    });

    var updated = repository.findAll().get(0);
    assertEquals(2, updated.getKeyCount());
    assertEquals(2000L, updated.getKeyDepositCents());

    FxTestSupport.run(() -> BoxDetailsView.show(stage, updated, () -> { }, () -> { }));
    var details = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#detailKeys") != null)
        .findFirst()
        .orElseThrow());
    assertEquals("2 keys, $20.00 deposit",
        FxTestSupport.call(() -> ((Label) details.getScene().lookup("#detailKeys")).getText()));
  }

  @Test
  void editBoxRejectsANonsenseNumberOfKeys() throws SQLException {
    var repository = new MailboxRepository();
    repository.insert(box(null, null, null));
    var saved = repository.findAll().get(0);
    var stage = FxTestSupport.call(() -> {
      var window = new Stage();
      EditBoxView.show(window, saved, () -> { });
      return window;
    });

    FxTestSupport.run(() -> {
      ((TextField) stage.getScene().lookup("#keyCountField")).setText("lots");
      stage.getScene().getRoot().lookupAll(".button").stream()
          .map(node -> (Button) node)
          .filter(b -> "Save".equals(b.getText()))
          .findFirst()
          .orElseThrow()
          .fire();
    });

    assertTrue(FxTestSupport.call(() -> ((Label) stage.getScene().lookup("#resultLabel")).getText())
        .contains("Keys must be a whole number"));
    assertNull(repository.findAll().get(0).getKeyCount());
  }

  private static Mailbox box(Integer keys, Long deposit, LocalDate closed) {
    return new Mailbox(0, "Ada", "Lovelace", null, "12", null, "", null, null, null, null, null, closed, keys,
        deposit);
  }

}
