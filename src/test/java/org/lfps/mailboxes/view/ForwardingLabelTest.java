package org.lfps.mailboxes.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.image.ImageView;
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
 * Tests for forwarding labels: what's on them, how they fit the 4 by 6.5
 * inch label and copy paper, and the Print Forwarding Label window opened
 * from a box's details.
 */
class ForwardingLabelTest {

  private static final Printing.PaperPrinter REAL_PRINTER = Printing.paperPrinter;

  /** Measures text the way a printer would, without one. */
  private static final FontRenderContext MEASURE = new FontRenderContext(null, true, true);

  /** A printer's usual edge on copy paper: a quarter inch. */
  private static final double EDGE = 0.25 * 72;

  private static final ForwardingAddress HOME = new ForwardingAddress("1400 Elm Street", "Apt 12", "Highland Park",
      "il", "60035", "summer");

  private static final ForwardingAddress OFFICE = new ForwardingAddress("200 Market St", null, "Chicago", "IL",
      "60606", null);

  /** The labels sent to the printer. */
  private final List<ForwardingLabel> printed = new ArrayList<>();

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
  void fitsTheLabelWithTheAddressLarge() {
    var label = label(HOME, ForwardingLabel.Stock.LABEL);
    var layout = label.layout(MEASURE);

    assertEquals(ForwardingLabel.LARGEST_ADDRESS, layout.toSize);
    assertFits(label, layout);
    assertTrue(layout.toY >= ForwardingLabel.MARGIN + ForwardingLabel.blockHeight(label.from, layout.fromSize));
  }

  @Test
  void aLongAddressGetsSmallerToFit() {
    var longAddress = new ForwardingAddress("12345 North Sheridan Road", "Suite 1200", "Lake Forest", "IL",
        "60045", null);
    var label = label(longAddress, ForwardingLabel.Stock.LABEL);
    var layout = label.layout(MEASURE);

    assertTrue(layout.toSize < ForwardingLabel.LARGEST_ADDRESS);
    assertFits(label, layout);
  }

  @Test
  void onALabelItIsFullSize() {
    var label = label(HOME, ForwardingLabel.Stock.LABEL);

    assertEquals(1, label.scale(ForwardingLabelView.previewPage(ForwardingLabel.Stock.LABEL)));
  }

  @Test
  void onALabelPrinterSetToLetterPaperItStillFitsTheLabel() {
    var label = label(HOME, ForwardingLabel.Stock.LABEL);
    var letter = ForwardingLabelView.previewPage(ForwardingLabel.Stock.COPY_PAPER);

    var scale = label.scale(letter);

    assertTrue(EDGE + ForwardingLabel.WIDTH * scale <= ForwardingLabel.WIDTH + 0.001);
    assertTrue(EDGE + ForwardingLabel.HEIGHT * scale <= ForwardingLabel.HEIGHT + 0.001);
  }

  @Test
  void onCopyPaperItIsFullSizeWithALineToCutAlong() {
    var paper = label(HOME, ForwardingLabel.Stock.COPY_PAPER);
    var page = ForwardingLabelView.previewPage(ForwardingLabel.Stock.COPY_PAPER);
    assertEquals(1, paper.scale(page));

    var withLine = draw(paper, page);
    var withoutLine = draw(label(HOME, ForwardingLabel.Stock.LABEL), page);
    // Down the label's left edge, below the return address.
    assertTrue(hasInk(withLine, EDGE - 2, 300, 4, 100));
    assertFalse(hasInk(withoutLine, EDGE - 2, 300, 4, 100));
  }

  @Test
  void staysInsideThePartThePrinterCanPrintOn() {
    var label = label(HOME, ForwardingLabel.Stock.COPY_PAPER);
    var paper = new Paper();
    paper.setSize(ForwardingLabel.WIDTH, ForwardingLabel.HEIGHT);
    paper.setImageableArea(18, 18, ForwardingLabel.WIDTH - 36, ForwardingLabel.HEIGHT - 36);
    var format = new PageFormat();
    format.setPaper(paper);

    var image = draw(label, format);

    assertFalse(hasInk(image, 0, 0, image.getWidth(), 17));
    assertFalse(hasInk(image, 0, 0, 17, image.getHeight()));
    assertFalse(hasInk(image, image.getWidth() - 17, 0, 17, image.getHeight()));
    assertFalse(hasInk(image, 0, image.getHeight() - 17, image.getWidth(), 17));
    assertTrue(hasInk(image, 18, 18, image.getWidth() - 36, image.getHeight() - 36));
  }

  @Test
  void usesAsMuchOfThePaperAsThePrinterAllows() {
    var job = new FakePrinterJob(page -> {
      var checked = (PageFormat) page.clone();
      var paper = checked.getPaper();
      paper.setImageableArea(9, 9, paper.getWidth() - 18, paper.getHeight() - 18);
      checked.setPaper(paper);
      return checked;
    });

    var page = Printing.fullPage(job);

    assertEquals(9, page.getImageableX());
    assertEquals(4 * 72 - 18, page.getImageableWidth());
  }

  @Test
  void fallsBackToThePrintersOwnPageIfItsDriverMisbehaves() {
    var tiny = new FakePrinterJob(page -> {
      var checked = (PageFormat) page.clone();
      var paper = checked.getPaper();
      paper.setImageableArea(0, 0, 10, 10);
      checked.setPaper(paper);
      return checked;
    });
    assertEquals(36, Printing.fullPage(tiny).getImageableX());

    var broken = new FakePrinterJob(page -> {
      throw new IllegalArgumentException("bad paper");
    });
    assertEquals(36, Printing.fullPage(broken).getImageableX());
  }

  @Test
  void printLabelIsOnlyOfferedForABoxWithAForwardingAddress() {
    assertTrue(FxTestSupport.call(() -> labelButton(showDetails(box("Ada", "Lovelace", null))).isDisabled()));
  }

  @Test
  void theWindowPrintsALabelForTheOnlyAddress() {
    catchPrinting();
    var window = openLabelWindow(box("Ada", "Lovelace", null, HOME));

    assertEquals(List.of("Label (4 × 6.5 inches)", "Copy paper, to cut out"),
        FxTestSupport.call(() -> radioTexts(window)));
    FxTestSupport.run(() -> ((Button) window.getScene().lookup("#labelPrintButton")).fire());

    assertEquals(1, printed.size());
    assertEquals(ForwardingLabel.Stock.LABEL, printed.get(0).stock);
    assertEquals("1400 Elm Street Apt 12", printed.get(0).to.get(1));
    assertEquals("Printed", FxTestSupport.call(() -> ((Label) window.getScene()
        .lookup("#labelResultLabel")).getText()));
  }

  @Test
  void theAddressAndPaperCanBeChosenAndThePreviewFollows() {
    catchPrinting();
    var window = openLabelWindow(box("Ada", "Lovelace", null, HOME, OFFICE));
    var preview = FxTestSupport.call(() -> (ImageView) window.getScene().lookup("#labelPreview"));
    var labelShape = FxTestSupport.call(() -> preview.getImage().getWidth() / preview.getImage().getHeight());

    FxTestSupport.run(() -> {
      radio(window, OFFICE.toString()).fire();
      ((RadioButton) window.getScene().lookup("#labelStockPaper")).fire();
    });
    var paperShape = FxTestSupport.call(() -> preview.getImage().getWidth() / preview.getImage().getHeight());
    FxTestSupport.run(() -> ((Button) window.getScene().lookup("#labelPrintButton")).fire());

    assertEquals(4 / 6.5, labelShape, 0.01);
    assertEquals(8.5 / 11, paperShape, 0.01);
    assertEquals(ForwardingLabel.Stock.COPY_PAPER, printed.get(0).stock);
    assertEquals("200 Market St", printed.get(0).to.get(1));
  }

  /** Makes a label for Ada Lovelace's box with the shop's usual return address. */
  private static ForwardingLabel label(ForwardingAddress address, ForwardingLabel.Stock stock) {
    return ForwardingLabel.of(box("Ada", "Lovelace", "Analytical Engines LLC", address), address,
        "Lake Forest Pack and Ship", "736 N. Western Ave\nLake Forest, IL 60045", stock);
  }

  /** Checks that both addresses fit inside the label's margins. */
  private static void assertFits(ForwardingLabel label, ForwardingLabel.Layout layout) {
    var inside = ForwardingLabel.WIDTH - 2 * ForwardingLabel.MARGIN;
    assertTrue(ForwardingLabel.blockWidth(label.from, layout.fromSize, MEASURE) <= inside);
    assertTrue(ForwardingLabel.blockWidth(label.to, layout.toSize, MEASURE) <= inside);
    assertTrue(layout.toY + ForwardingLabel.blockHeight(label.to, layout.toSize)
        <= ForwardingLabel.HEIGHT - ForwardingLabel.MARGIN);
  }

  /** Makes printing note each label instead of printing it, as if it printed. */
  private void catchPrinting() {
    Printing.paperPrinter = (jobName, printable) -> {
      printed.add((ForwardingLabel) printable);
      return CompletableFuture.completedFuture(true);
    };
  }

  /** Draws a label on a white page the size of the given paper, one pixel per point. */
  private static BufferedImage draw(ForwardingLabel label, PageFormat format) {
    var image = new BufferedImage((int) format.getWidth(), (int) format.getHeight(), BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setColor(java.awt.Color.WHITE);
    g.fillRect(0, 0, image.getWidth(), image.getHeight());
    label.print(g, format, 0);
    g.dispose();
    return image;
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
    return findWindow("#detailTitle");
  }

  /** Opens the Print Forwarding Label window from a box's details and returns it. */
  private static Stage openLabelWindow(Mailbox mailbox) {
    return FxTestSupport.call(() -> {
      labelButton(showDetails(mailbox)).fire();
      return findWindow("#labelPreview");
    });
  }

  /** Returns the open window containing the given node. Must be called on the JavaFX thread. */
  private static Stage findWindow(String selector) {
    return Window.getWindows().stream()
        .filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().lookup(selector) != null)
        .map(w -> (Stage) w)
        .findFirst()
        .orElseThrow();
  }

  /** Returns the text of every choice on a window. */
  private static List<String> radioTexts(Stage window) {
    return window.getScene().getRoot().lookupAll(".radio-button").stream()
        .map(node -> ((RadioButton) node).getText())
        .collect(Collectors.toList());
  }

  /** Returns the choice on a window with the given text. */
  private static RadioButton radio(Stage window, String text) {
    return window.getScene().getRoot().lookupAll(".radio-button").stream()
        .map(node -> (RadioButton) node)
        .filter(radio -> text.equals(radio.getText()))
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

  private static final class FakePrinterJob extends PrinterJob {

    /** How the pretend driver checks a page. */
    private final UnaryOperator<PageFormat> validate;

    /**
     * Makes a job.
     *
     * @param validate how the pretend driver checks a page
     */
    FakePrinterJob(UnaryOperator<PageFormat> validate) {
      this.validate = validate;
    }

    @Override
    public PageFormat defaultPage(PageFormat page) {
      var paper = new Paper();
      paper.setSize(4 * 72, 2 * 72);
      paper.setImageableArea(36, 36, 4 * 72 - 72, 2 * 72 - 72);
      var format = (PageFormat) page.clone();
      format.setPaper(paper);
      return format;
    }

    @Override
    public PageFormat validatePage(PageFormat page) {
      return validate.apply(page);
    }

    @Override
    public void setPrintable(Printable painter) {
    }

    @Override
    public void setPrintable(Printable painter, PageFormat format) {
    }

    @Override
    public void setPageable(Pageable document) {
    }

    @Override
    public boolean printDialog() {
      return true;
    }

    @Override
    public PageFormat pageDialog(PageFormat page) {
      return page;
    }

    @Override
    public void print() {
    }

    @Override
    public void setCopies(int copies) {
    }

    @Override
    public int getCopies() {
      return 1;
    }

    @Override
    public String getUserName() {
      return "";
    }

    @Override
    public void setJobName(String jobName) {
    }

    @Override
    public String getJobName() {
      return "";
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

  }

}
