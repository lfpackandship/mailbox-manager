package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for forwarding-only boxes: holders who don't rent a box here, whose
 * mail is forwarded, and who may use a box number someone else now rents.
 */
class ForwardingOnlyTest {

  private static final ForwardingAddress NAPLES =
      new ForwardingAddress("88 Palm Way", null, "Naples", "FL", "34102", null);

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

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
      stmt.execute("DELETE FROM prices");
      stmt.execute("DELETE FROM mailboxes");
    }
    mainWindow = FxTestSupport.call(Stage::new);
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
  void aForwardingOnlyBoxRoundTripsAndDoesntHoldItsNumber() throws SQLException {
    mailboxes.insert(rented("12", "Holder"));
    mailboxes.insert(forwarding("12", "Former"));

    var former = mailboxes.findAll().stream().filter(Mailbox::isForwardingOnly).findFirst().orElseThrow();
    assertEquals("Former", former.getLastName());
    assertTrue(former.withEndDate(LocalDate.now()).withClosedDate(null).isForwardingOnly());
    // The rented box holds the number; the forwarding one doesn't.
    assertTrue(mailboxes.isBoxNumberTaken("12", former.getId()));
    var holder = mailboxes.findAll().stream().filter(m -> !m.isForwardingOnly()).findFirst().orElseThrow();
    assertFalse(mailboxes.isBoxNumberTaken("12", holder.getId()));
  }

  @Test
  void addNewBoxAcceptsATakenNumberForForwarding() throws SQLException {
    new BoxInventoryRepository().add(List.of("12"), "Small");
    mailboxes.insert(rented("12", "Holder"));

    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("John").setText("Ada");
      fieldWithPrompt("Doe").setText("Former");
      fieldWithPrompt("310").setText("12");
      checkBox().setSelected(true);
      buttonLabeled("Submit").fire();
    });

    var saved = mailboxes.findAll().stream().filter(Mailbox::isForwardingOnly).collect(Collectors.toList());
    assertEquals(1, saved.size());
    assertEquals("12", saved.get(0).getBoxNumber());
  }

  @Test
  void theDetailsSayWhenThereIsNoForwardingAddressYet() throws SQLException {
    mailboxes.insert(new Mailbox(0, "Ada", "Former", null, "12", null, "", null, null, null, null, null, null,
        null, null, true));
    var box = mailboxes.findAll().get(0);

    FxTestSupport.run(() -> BoxDetailsView.show(mainWindow, box, () -> { }, () -> { }));

    var details = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#detailForwardingOnly") != null)
        .findFirst()
        .orElseThrow());
    assertTrue(FxTestSupport.call(() -> ((Label) details.getScene().lookup("#detailForwardingOnly")).getText())
        .endsWith("No forwarding address is recorded yet."));
  }

  @Test
  void forwardingTurnsOffTheKeys() {
    FxTestSupport.run(() -> AddBoxView.show(mainWindow));

    FxTestSupport.run(() -> checkBox().setSelected(true));
    assertTrue(FxTestSupport.call(() -> mainWindow.getScene().lookup("#keyCountField").isDisabled()));

    FxTestSupport.run(() -> checkBox().setSelected(false));
    assertFalse(FxTestSupport.call(() -> mainWindow.getScene().lookup("#keyCountField").isDisabled()));
  }

  @Test
  void turningAHolderIntoForwardingFreesTheirBox() throws SQLException {
    mailboxes.insert(new Mailbox(0, "Ada", "Moving", null, "12", null, "", null, null, null, List.of(NAPLES)));
    var box = mailboxes.findAll().get(0);

    FxTestSupport.run(() -> {
      EditBoxView.show(mainWindow, box, () -> { });
      checkBox().setSelected(true);
      buttonLabeled("Save").fire();
    });

    assertTrue(mailboxes.findAll().get(0).isForwardingOnly());
    assertFalse(mailboxes.isBoxNumberTaken("12", 0));
  }

  @Test
  void turningForwardingBackIntoARentedBoxChecksTheNumberIsFree() throws SQLException {
    mailboxes.insert(rented("12", "Holder"));
    mailboxes.insert(forwarding("12", "Former"));
    var former = mailboxes.findAll().stream().filter(Mailbox::isForwardingOnly).findFirst().orElseThrow();

    FxTestSupport.run(() -> {
      EditBoxView.show(mainWindow, former, () -> { });
      checkBox().setSelected(false);
      buttonLabeled("Save").fire();
    });

    assertTrue(FxTestSupport.call(() -> ((Label) mainWindow.getScene().lookup("#resultLabel")).getText())
        .contains("Box 12 is already assigned to someone else."));
    assertTrue(mailboxes.findAll().stream().anyMatch(Mailbox::isForwardingOnly));
  }

  @Test
  void manageBoxesCanShowJustForwardingAndSearchFindsIt() throws SQLException {
    var rented = rented("12", "Holder");
    var forwarding = forwarding("12", "Former");

    assertTrue(ManageBoxesView.Show.FORWARDING.includes(forwarding));
    assertFalse(ManageBoxesView.Show.FORWARDING.includes(rented));
    assertTrue(ManageBoxesView.Show.OPEN.includes(forwarding));
    assertFalse(ManageBoxesView.Show.FORWARDING.includes(forwarding.withClosedDate(LocalDate.now())));
    assertTrue(ManageBoxesView.matches(forwarding, "forwarding"));
    assertFalse(ManageBoxesView.matches(rented, "forwarding"));
  }

  @Test
  void theBoxInventoryDoesntCountForwardingAsRenting() throws SQLException {
    new BoxInventoryRepository().add(List.of("12"), null);
    mailboxes.insert(forwarding("12", "Former"));

    FxTestSupport.run(() -> BoxInventoryView.show(mainWindow));

    assertEquals("1 box: 0 rented, 1 empty.",
        FxTestSupport.call(() -> ((Label) mainWindow.getScene().lookup("#inventorySummary")).getText()));
  }

  @Test
  void remindersForForwardingBoxesStartTicked() throws SQLException {
    mailboxes.insert(rented("12", "Holder").withEndDate(LocalDate.now().minusDays(1)));
    mailboxes.insert(forwarding("12", "Former").withEndDate(LocalDate.now().minusDays(1)));

    var boxes = mailboxes.findAll();
    FxTestSupport.run(() -> PriceSheetView.showReminders(mainWindow, boxes));

    var window = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#printButton") != null)
        .findFirst()
        .orElseThrow());
    assertEquals("Print 2 Reminders",
        FxTestSupport.call(() -> ((Button) window.getScene().lookup("#printButton")).getText()));
  }

  @Test
  void renewingForwardingFillsInNoBoxPrice() throws SQLException {
    new BoxInventoryRepository().add(List.of("12"), "Small");
    new org.lfps.mailboxes.data.PriceRepository().save(java.util.Map.of(
        org.lfps.mailboxes.data.PriceRepository.key("small", 3), 9000L));
    mailboxes.insert(forwarding("12", "Former").withEndDate(LocalDate.now()));
    var former = mailboxes.findAll().get(0);

    FxTestSupport.run(() -> RenewBoxView.show(mainWindow, former, () -> { }));
    var renew = FxTestSupport.call(() -> Window.getWindows().stream()
        .filter(w -> w.getScene() != null && w.getScene().lookup("#renewSaveButton") != null)
        .findFirst()
        .orElseThrow());
    FxTestSupport.run(() -> renew.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> b.getText().startsWith("3"))
        .findFirst()
        .orElseThrow()
        .fire());

    assertEquals("", FxTestSupport.call(() -> ((TextField) renew.getScene().lookup("#amountField")).getText()));
  }

  private static Mailbox rented(String boxNumber, String lastName) {
    return new Mailbox(0, "Ada", lastName, null, boxNumber, null, "", null, null, null, null);
  }

  private static Mailbox forwarding(String boxNumber, String lastName) {
    return new Mailbox(0, "Ada", lastName, null, boxNumber, null, "", null, null, null, List.of(NAPLES), null,
        null, null, null, true);
  }

  private CheckBox checkBox() {
    return (CheckBox) mainWindow.getScene().lookup("#forwardingOnlyBox");
  }

  private TextField fieldWithPrompt(String prompt) {
    return mainWindow.getScene().getRoot().lookupAll(".text-field").stream()
        .filter(node -> node instanceof TextField)
        .map(node -> (TextField) node)
        .filter(f -> prompt.equals(f.getPromptText()))
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

}
