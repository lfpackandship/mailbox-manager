package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
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
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.Mailbox;

/**
 * UI tests for leaving Add New Box and Edit Box: they ask before throwing
 * away changes, and only when there are some.
 */
class UnsavedChangesTest {

  /** The real question, put back after each test. */
  private static final Dialogs.Confirm REAL_CONFIRM = Dialogs.confirm;

  private final MailboxRepository mailboxes = new MailboxRepository();

  /** The questions asked, in order. */
  private final List<String> asked = new ArrayList<>();

  /** The answer given to each question. */
  private boolean answer;

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
      stmt.execute("DELETE FROM box_inventory");
      stmt.execute("DELETE FROM rental_periods");
      stmt.execute("DELETE FROM business_names");
      stmt.execute("DELETE FROM forwarding_addresses");
      stmt.execute("DELETE FROM mailboxes");
    }
    Dialogs.confirm = (owner, question, details) -> {
      asked.add(question);
      return answer;
    };
    mainWindow = FxTestSupport.call(Stage::new);
  }

  @AfterEach
  void closeAllWindows() {
    Dialogs.confirm = REAL_CONFIRM;
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void backOnAnUntouchedFormLeavesWithoutAsking() {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      buttonLabeled("Back").fire();
    });

    assertTrue(asked.isEmpty());
    assertNotNull(FxTestSupport.call(() -> buttonLabeled("Manage Boxes")));
  }

  @Test
  void backAfterTypingAsksAndStaysIfTheAnswerIsNo() {
    answer = false;
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("310").setText("12");
      buttonLabeled("Back").fire();
    });

    assertEquals(List.of("Leave without saving?"), asked);
    assertEquals("12", FxTestSupport.call(() -> fieldWithPrompt("310").getText()));
  }

  @Test
  void backAfterTypingLeavesIfTheAnswerIsYes() {
    answer = true;
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("310").setText("12");
      buttonLabeled("Back").fire();
    });

    assertEquals(List.of("Leave without saving?"), asked);
    assertNotNull(FxTestSupport.call(() -> buttonLabeled("Manage Boxes")));
  }

  @Test
  void addingAForwardingAddressCountsAsAChange() throws SQLException {
    var left = new boolean[1];
    mailboxes.insert(new Mailbox(0, "Ada", "Lovelace", null, "1", null, "", null, null, null, null));
    var box = mailboxes.findAll().get(0);

    FxTestSupport.run(() -> {
      EditBoxView.show(mainWindow, box, () -> left[0] = true);
      ((TextField) mainWindow.getScene().lookup("#streetField")).setText("1 Lake Rd");
      ((TextField) mainWindow.getScene().lookup("#cityField")).setText("Duluth");
      ((TextField) mainWindow.getScene().lookup("#stateField")).setText("MN");
      ((TextField) mainWindow.getScene().lookup("#zipField")).setText("55802");
      ((Button) mainWindow.getScene().lookup("#addForwardingButton")).fire();
      buttonLabeled("Cancel").fire();
    });

    assertEquals(List.of("Leave without saving?"), asked);
    assertFalse(left[0]);
  }

  @Test
  void openingEditBoxFilledInIsntAChange() throws SQLException {
    var left = new boolean[1];
    mailboxes.insert(new Mailbox(0, "Ada", "Lovelace", null, "1", null, "(555) 100-0001", null,
        List.of("Analytical Engines"), null, null, "A note", null, 2, 2000L, false));
    var box = mailboxes.findAll().get(0);

    FxTestSupport.run(() -> {
      EditBoxView.show(mainWindow, box, () -> left[0] = true);
      buttonLabeled("Cancel").fire();
    });

    assertTrue(asked.isEmpty());
    assertTrue(left[0]);
  }

  @Test
  void savingMeansThereIsNothingToLose() {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("310").setText("12");
      buttonLabeled("Submit").fire();
      buttonLabeled("Back").fire();
    });

    assertTrue(asked.isEmpty());
  }

  @Test
  void addingABoxEmptiesTheFormAndSaysWhichBoxWasSaved() throws SQLException {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("John").setText("Ada");
      fieldWithPrompt("Doe").setText("Lovelace");
      fieldWithPrompt("310").setText("12");
      buttonLabeled("Submit").fire();
    });

    assertEquals(1, mailboxes.findAll().size());
    assertEquals("", FxTestSupport.call(() -> fieldWithPrompt("310").getText()));
    assertEquals("Saved Box 12 – Ada Lovelace. The form is empty again, ready for the next box.",
        FxTestSupport.call(() -> ((Label) mainWindow.getScene().lookup("#resultLabel")).getText()));
  }

  @Test
  void theGoMenuAndClosingTheWindowAskToo() {
    answer = false;
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("310").setText("12");
    });

    assertFalse(FxTestSupport.call(AppWindow::mayLeave));
    assertEquals(List.of("Leave without saving?"), asked);
  }

  @Test
  void aNewScreenHasNothingToAskAbout() {
    FxTestSupport.run(() -> {
      AddBoxView.show(mainWindow);
      fieldWithPrompt("310").setText("12");
      MainMenuView.show(mainWindow);
    });

    assertTrue(FxTestSupport.call(AppWindow::mayLeave));
    assertTrue(asked.isEmpty());
  }

  /** Returns the text field on the main window showing the given hint while empty. */
  private TextField fieldWithPrompt(String prompt) {
    return mainWindow.getScene().getRoot().lookupAll(".text-field").stream()
        .map(node -> (TextField) node)
        .filter(f -> prompt.equals(f.getPromptText()))
        .findFirst()
        .orElseThrow();
  }

  /** Returns the button on the main window with the given text. */
  private Button buttonLabeled(String text) {
    return mainWindow.getScene().getRoot().lookupAll(".button").stream()
        .map(node -> (Button) node)
        .filter(b -> text.equals(b.getText()))
        .findFirst()
        .orElseThrow();
  }

}
