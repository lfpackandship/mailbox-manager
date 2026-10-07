package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for entering forwarding addresses on the Add and Edit Box forms,
 * and for names and addresses typed but not added before saving.
 */
class ForwardingAddressesTest {

  private static final ForwardingAddress NAPLES =
      new ForwardingAddress("88 Palm Way", "Unit 3B", "Naples", "FL", "34102", "winter");

  private final MailboxRepository mailboxes = new MailboxRepository();

  private Stage mainWindow;

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void openMainWindow() throws SQLException {
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
    mainWindow = FxTestSupport.call(() -> {
      var stage = new Stage();
      MainMenuView.show(stage);
      return stage;
    });
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
  void addingANewBoxSavesItsForwardingAddresses() throws SQLException {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("John (optional)").setText("Tomás");
      fieldWithPrompt("Doe (optional)").setText("Rivera");
      fieldWithPrompt("310 (required)").setText("205");
      fieldWithPrompt("(555) 123-4567 (optional)").setText("5552000006");
      enterAddress("88 Palm Way", "Unit 3B", "Naples", "fl", "34102", "winter");
      button("addForwardingButton").fire();
      enterAddress("1 Lake Rd", "", "Duluth", "MN", "55802-1234", "");
      button("addForwardingButton").fire();
      buttonLabeled("Save").fire();
    });

    var saved = mailboxes.findAll();
    assertEquals(1, saved.size());
    assertEquals(List.of(NAPLES, new ForwardingAddress("1 Lake Rd", null, "Duluth", "MN", "55802-1234", null)),
        saved.get(0).getForwardingAddresses());
  }

  @Test
  void anAddedAddressShowsInTheListAndClearsTheFields() {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      enterAddress("88 Palm Way", "Unit 3B", "Naples", "FL", "34102", "winter");
      button("addForwardingButton").fire();
    });

    assertEquals(List.of(NAPLES), FxTestSupport.call(() -> List.copyOf(addressList().getItems())));
    assertEquals("", FxTestSupport.call(() -> field("streetField").getText()));
    assertEquals("", FxTestSupport.call(() -> errorText()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "''          | ''      | ''    | ''         | Enter the street, city, two-letter state, and ZIP code (12345 or 12345-6789).",
      "1 Lake Rd   | Duluth  | Minn  | 55802      | Enter the two-letter state.",
      "1 Lake Rd   | Duluth  | MN    | 5580       | Enter the ZIP code (12345 or 12345-6789).",
      "''          | Duluth  | MN    | 55802      | Enter the street.",
      "1 Lake Rd   | ''      | MN    | 55802-12   | Enter the city and ZIP code (12345 or 12345-6789).",
  })
  void incompleteAddressesAreRejectedWithTheMissingParts(String street, String city, String state, String zip,
      String message) {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      enterAddress(street, "", city, state, zip, "");
      button("addForwardingButton").fire();
    });

    assertEquals(message, FxTestSupport.call(this::errorText));
    assertTrue(FxTestSupport.call(() -> addressList().getItems().isEmpty()));
  }

  @Test
  void editingABoxShowsItsAddressesAndSavesChanges() throws SQLException {
    mailboxes.insert(new Mailbox(0, "Tomás", "Rivera", null, "205", null, "(555) 200-0006", null,
        null, null, List.of(NAPLES)));
    var box = mailboxes.findAll().get(0);
    var replacement = new ForwardingAddress("1 Lake Rd", null, "Duluth", "MN", "55802", "summer");

    FxTestSupport.run(() -> EditBoxView.show(mainWindow, box, () -> { }));
    assertEquals(List.of(NAPLES), FxTestSupport.call(() -> List.copyOf(addressList().getItems())));

    FxTestSupport.run(() -> {
      addressList().getSelectionModel().select(0);
      button("removeForwardingButton").fire();
      enterAddress("1 Lake Rd", "", "Duluth", "MN", "55802", "summer");
      button("addForwardingButton").fire();
      buttonLabeled("Save").fire();
    });

    assertEquals(List.of(replacement), mailboxes.findAll().get(0).getForwardingAddresses());
  }

  @Test
  void removeIsDisabledUntilAnAddressIsSelected() throws SQLException {
    mailboxes.insert(new Mailbox(0, "Tomás", "Rivera", null, "205", null, "(555) 200-0006", null,
        null, null, List.of(NAPLES)));
    var box = mailboxes.findAll().get(0);

    FxTestSupport.run(() -> EditBoxView.show(mainWindow, box, () -> { }));

    assertTrue(FxTestSupport.call(() -> button("removeForwardingButton").isDisabled()));
  }

  @Test
  void anAddressTypedButNotAddedIsSavedWithTheBox() throws SQLException {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("310 (required)").setText("205");
      enterAddress("88 Palm Way", "Unit 3B", "Naples", "FL", "34102", "winter");
      buttonLabeled("Save").fire();
    });

    assertEquals(List.of(NAPLES), mailboxes.findAll().get(0).getForwardingAddresses());
  }

  @Test
  void anUnfinishedAddressStopsTheSaveAndSaysWhatsMissing() throws SQLException {
    mailboxes.insert(new Mailbox(0, "Tomás", "Rivera", null, "205", null, "(555) 200-0006", null,
        null, null, List.of()));
    var box = mailboxes.findAll().get(0);
    var saved = new boolean[1];

    FxTestSupport.run(() -> {
      EditBoxView.show(mainWindow, box, () -> saved[0] = true);
      enterAddress("1 Lake Rd", "", "Duluth", "", "", "");
      buttonLabeled("Save").fire();
    });

    assertFalse(saved[0]);
    assertEquals("Finish the forwarding address or clear its fields. "
        + "Enter the two-letter state and ZIP code (12345 or 12345-6789).",
        FxTestSupport.call(() -> ((Label) mainWindow.getScene().getRoot().lookup("#resultLabel")).getText()));
    assertTrue(mailboxes.findAll().get(0).getForwardingAddresses().isEmpty());
  }

  @Test
  void aBusinessNameTypedButNotAddedIsSavedWithTheBox() throws SQLException {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("310 (required)").setText("205");
      field("businessNameField").setText("Rivera Imports");
      buttonLabeled("Save").fire();
    });

    assertEquals(List.of("Rivera Imports"), mailboxes.findAll().get(0).getAlternateBusinessNames());
  }

  @Test
  void onAddNewBoxBothSectionsStartFoldedUp() {
    FxTestSupport.run(() -> AddBoxView.show(mainWindow));

    assertEquals(List.of("Alternate Business Names", "Forwarding Addresses"), FxTestSupport.call(this::sectionTitles));
    assertTrue(FxTestSupport.call(() -> sections().stream().noneMatch(TitledPane::isExpanded)));
  }

  @Test
  void onEditBoxASectionWithEntriesStartsOpenAndCountsThem() throws SQLException {
    mailboxes.insert(new Mailbox(0, "Tomás", "Rivera", null, "205", null, "(555) 200-0006", null,
        null, null, List.of(NAPLES)));
    var box = mailboxes.findAll().get(0);

    FxTestSupport.run(() -> {
      EditBoxView.show(mainWindow, box, () -> { });
      enterAddress("1 Lake Rd", "", "Duluth", "MN", "55802", "");
      button("addForwardingButton").fire();
    });

    assertEquals(List.of("Alternate Business Names", "Forwarding Addresses (2)"),
        FxTestSupport.call(this::sectionTitles));
    assertEquals(List.of(false, true), FxTestSupport.call(() -> sections().stream()
        .map(TitledPane::isExpanded).collect(Collectors.toList())));
  }

  /** Returns the folding sections on the main window, top first. */
  private List<TitledPane> sections() {
    return mainWindow.getScene().getRoot().lookupAll(".titled-pane").stream()
        .map(node -> (TitledPane) node)
        .collect(Collectors.toList());
  }

  /** Returns the headings of the folding sections on the main window, top first. */
  private List<String> sectionTitles() {
    return sections().stream().map(TitledPane::getText).collect(Collectors.toList());
  }

  /** Types an address into the forwarding address fields. */
  private void enterAddress(String street, String unit, String city, String state, String zip, String note) {
    field("streetField").setText(street);
    field("unitField").setText(unit);
    field("cityField").setText(city);
    field("stateField").setText(state);
    field("zipField").setText(zip);
    field("noteField").setText(note);
  }

  /** Returns the text field with the given id on the main window. */
  private TextField field(String id) {
    return (TextField) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  /** Returns the text field on the main window showing the given hint while empty. */
  private TextField fieldWithPrompt(String prompt) {
    return mainWindow.getScene().getRoot().lookupAll(".text-field").stream()
        .map(node -> (TextField) node)
        .filter(f -> prompt.equals(f.getPromptText()))
        .findFirst()
        .orElseThrow();
  }

  /** Returns the button with the given id on the main window. */
  private Button button(String id) {
    return (Button) mainWindow.getScene().getRoot().lookup("#" + id);
  }

  /** Returns the button on the main window with the given text. */
  private Button buttonLabeled(String text) {
    return mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> text.equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

  /** Returns the list of forwarding addresses added. */
  @SuppressWarnings("unchecked")
  private ListView<ForwardingAddress> addressList() {
    return (ListView<ForwardingAddress>) mainWindow.getScene().getRoot().lookup("#forwardingList");
  }

  /** Returns the problem shown under the forwarding address fields. */
  private String errorText() {
    return ((Label) mainWindow.getScene().getRoot().lookup("#forwardingErrorLabel")).getText();
  }

}
