package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.TestSandbox;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

/**
 * Tests for forwarding labels: what's on them, how they're laid out on
 * different labels and paper, and printing one from a box's details.
 */
class ForwardingLabelTest {

  private static final Printing.PaperPrinter REAL_PRINTER = Printing.paperPrinter;

  private static final Dialogs.ConfirmWithChoice REAL_CONFIRM_WITH_CHOICE = Dialogs.confirmWithChoice;

  /** Measures text the way a printer would, without one. */
  private static final FontRenderContext MEASURE = new FontRenderContext(null, true, true);

  private static final ForwardingAddress HOME = new ForwardingAddress("1400 Elm Street", "Apt 12", "Highland Park",
      "il", "60035", "summer");

  private static final ForwardingAddress OFFICE = new ForwardingAddress("200 Market St", null, "Chicago", "IL",
      "60606", null);

  /** The labels sent to the printer, each as its job name and the label. */
  private final List<Object[]> printed = new ArrayList<>();

  @BeforeAll
  static void startJavaFx() {
    FxTestSupport.start();
  }

  @BeforeEach
  void prepareDatabase() {
    TestSandbox.require();
    Database.prepareDataDir();
    Database.initSchema();
  }

  @AfterEach
  void restoreAndCloseAllWindows() {
    Printing.paperPrinter = REAL_PRINTER;
    Dialogs.confirmWithChoice = REAL_CONFIRM_WITH_CHOICE;
    FxTestSupport.run(() -> {
      for (var window : List.copyOf(Window.getWindows())) {
        window.hide();
      }
    });
  }

  @Test
  void isAddressedToTheHolderAndBusinessWithTheUnitOnTheStreetLine() {
    assertEquals(List.of("Ada Lovelace", "Analytical Engines", "1400 Elm Street Apt 12", "Highland Park, IL 60035"),
        ForwardingLabel.recipient(box("Ada", "Lovelace", "Analytical Engines", HOME), HOME));
    assertEquals(List.of("200 Market St", "Chicago, IL 60606"),
        ForwardingLabel.recipient(box("", "", null, OFFICE), OFFICE));
  }

  @Test
  void theReturnAddressIsTheShopWithoutItsPhoneNumber() {
    assertEquals(List.of("Lake Forest Pack and Ship", "736 N. Western Ave", "Lake Forest, IL 60045"),
        ForwardingLabel.returnAddress("Lake Forest Pack and Ship",
            "736 N. Western Ave\nLake Forest, IL 60045\n(847) 615-0222\n"));
    assertEquals(List.of(), ForwardingLabel.returnAddress(" ", null));
  }

  @Test
  void fitsOnEveryCommonSizeOfLabelEnvelopeAndPaper() {
    var label = ForwardingLabel.of(box("Ada", "Lovelace", "Analytical Engines", HOME), HOME,
        "Lake Forest Pack and Ship", "736 N. Western Ave\nLake Forest, IL 60045");
    // Width and height in inches: Zebra shipping labels both ways round,
    // smaller Zebra labels, a #10 envelope, and letter paper.
    double[][] sizes = { { 4, 6 }, { 6, 4 }, { 4, 2 }, { 2.25, 1.25 }, { 9.5, 4.125 }, { 8.5, 11 } };
    for (var size : sizes) {
      var width = size[0] * 72;
      var height = size[1] * 72;
      var layout = label.layout(width, height, MEASURE);
      var what = size[0] + " x " + size[1];

      var fromRight = layout.fromX + ForwardingLabel.blockWidth(label.from, layout.fromSize, MEASURE);
      var fromBottom = layout.fromY + ForwardingLabel.blockHeight(label.from, layout.fromSize);
      var toRight = layout.toX + ForwardingLabel.blockWidth(label.to, layout.toSize, MEASURE);
      var toBottom = layout.toY + ForwardingLabel.blockHeight(label.to, layout.toSize);
      assertTrue(fromRight <= width && fromBottom <= height, what + ": return address off the paper");
      assertTrue(toRight <= width && toBottom <= height, what + ": forwarding address off the paper");
      assertTrue(layout.toY >= fromBottom, what + ": the addresses overlap");
      assertTrue(layout.toSize >= layout.fromSize, what + ": the forwarding address is smaller");
    }
  }

  @Test
  void onASheetOfPaperItsLaidOutLikeAnEnvelopeAcrossTheTop() {
    var label = ForwardingLabel.of(box("Ada", "Lovelace", null, HOME), HOME, "Shop", "1 Main St\nTown, IL 60000");
    var layout = label.layout(8.5 * 72, 11 * 72, MEASURE);

    assertTrue(layout.toY + ForwardingLabel.blockHeight(label.to, layout.toSize)
        <= ForwardingLabel.SHEET_LABEL_HEIGHT);
    assertEquals(ForwardingLabel.LARGEST_ADDRESS, layout.toSize);
  }

  @Test
  void drawsTheAddressesOntoThePaper() {
    var label = ForwardingLabel.of(box("Ada", "Lovelace", null, HOME), HOME, "Shop", "1 Main St\nTown, IL 60000");
    var paper = new Paper();
    paper.setSize(4 * 72, 2 * 72);
    var format = new PageFormat();
    format.setPaper(paper);
    var image = new BufferedImage(4 * 72, 2 * 72, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setColor(java.awt.Color.WHITE);
    g.fillRect(0, 0, image.getWidth(), image.getHeight());

    assertEquals(Printable.PAGE_EXISTS, label.print(g, format, 0));
    assertEquals(Printable.NO_SUCH_PAGE, label.print(g, format, 1));
    var layout = label.layout(format.getWidth(), format.getHeight(), g.getFontRenderContext());
    assertTrue(hasInk(image, layout.fromX, layout.fromY, ForwardingLabel.blockWidth(label.from, layout.fromSize,
        g.getFontRenderContext()), ForwardingLabel.blockHeight(label.from, layout.fromSize)));
    assertTrue(hasInk(image, layout.toX, layout.toY, ForwardingLabel.blockWidth(label.to, layout.toSize,
        g.getFontRenderContext()), ForwardingLabel.blockHeight(label.to, layout.toSize)));
  }

  @Test
  void printLabelIsOnlyOfferedForABoxWithAForwardingAddress() {
    assertTrue(FxTestSupport.call(() -> labelButton(showDetails(box("Ada", "Lovelace", null))).isDisabled()));
  }

  @Test
  void aBoxWithOneAddressPrintsItsLabelWithoutAsking() {
    catchPrinting();
    Dialogs.confirmWithChoice = (owner, question, details, choiceQuestion, choices, initial, yes, no) -> {
      throw new AssertionError("Asked which address");
    };
    var details = FxTestSupport.call(() -> showDetails(box("Ada", "Lovelace", null, HOME)));

    FxTestSupport.run(() -> labelButton(details).fire());

    assertEquals(1, printed.size());
    assertEquals("Forwarding label, box 12", printed.get(0)[0]);
    assertEquals("1400 Elm Street Apt 12", ((ForwardingLabel) printed.get(0)[1]).to.get(1));
  }

  @Test
  void aBoxWithSeveralAddressesAsksWhichOne() {
    catchPrinting();
    var offered = new ArrayList<String>();
    Dialogs.confirmWithChoice = (owner, question, details, choiceQuestion, choices, initial, yes, no) -> {
      offered.addAll(choices);
      return 1;
    };
    var details = FxTestSupport.call(() -> showDetails(box("Ada", "Lovelace", null, HOME, OFFICE)));

    FxTestSupport.run(() -> labelButton(details).fire());

    assertEquals(List.of(HOME.toString(), OFFICE.toString()), offered);
    assertEquals("200 Market St", ((ForwardingLabel) printed.get(0)[1]).to.get(1));
  }

  @Test
  void cancellingTheQuestionPrintsNothing() {
    catchPrinting();
    Dialogs.confirmWithChoice = (owner, question, details, choiceQuestion, choices, initial, yes, no) -> -1;
    var details = FxTestSupport.call(() -> showDetails(box("Ada", "Lovelace", null, HOME, OFFICE)));

    FxTestSupport.run(() -> labelButton(details).fire());

    assertEquals(List.of(), printed);
  }

  /** Makes printing note each label instead of printing it, as if it printed. */
  private void catchPrinting() {
    Printing.paperPrinter = (jobName, printable) -> {
      printed.add(new Object[] { jobName, printable });
      return CompletableFuture.completedFuture(true);
    };
  }

  /** Checks whether any of a part of the image isn't white. */
  private static boolean hasInk(BufferedImage image, double x, double y, double width, double height) {
    for (var row = (int) y; row < Math.min(image.getHeight(), y + height); row++) {
      for (var column = (int) x; column < Math.min(image.getWidth(), x + width); column++) {
        if ((image.getRGB(column, row) & 0xFFFFFF) != 0xFFFFFF) {
          return true;
        }
      }
    }
    return false;
  }

  /** Opens a box's details window and returns it. Must be called on the JavaFX thread. */
  private static Stage showDetails(Mailbox mailbox) {
    var owner = new Stage();
    BoxDetailsView.show(owner, mailbox, () -> { }, () -> { });
    return Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup("#detailTitle") != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow();
  }

  /** Returns the Print Label button on a box's details window. */
  private static Button labelButton(Stage details) {
    return (Button) details.getScene().lookup("#detailsLabelButton");
  }

  /** Makes box 12 with the given holder and forwarding addresses. */
  private static Mailbox box(String first, String last, String business, ForwardingAddress... addresses) {
    return new Mailbox(1, first, last, business, "12", null, "", null, null, null, List.of(addresses));
  }

}
