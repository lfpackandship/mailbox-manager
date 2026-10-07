package org.lfps.mailboxes.view;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Prints pages, such as renewal reminders, through the computer's own print
 * dialog: the Mac's Print window, with its preview and Save as PDF, or the
 * Windows Print dialog. Replaceable so tests can check what would be printed
 * without a printer. Pages can also be saved as a PDF, which needs no
 * printer.
 */
final class Printing {

  /** Prints pages. */
  interface Printer {
    /**
     * Asks which printer to use, then prints the pages, each on its own sheet
     * of paper.
     *
     * @param jobName what the print job is called in the printer's queue
     * @param pages the pages, laid out by {@link #layOut(Region)}
     * @return completes with {@code true} once they're sent to the printer,
     *     {@code false} if the user cancelled, or an
     *     {@link IllegalStateException} with a message suitable for showing
     *     to the user if they couldn't be printed
     */
    CompletableFuture<Boolean> print(String jobName, List<Region> pages);
  }

  /** Prints something that draws itself to fit the paper, such as a label. */
  interface PaperPrinter {
    /**
     * Asks which printer to use, then prints.
     *
     * @param jobName what the print job is called in the printer's queue
     * @param printable what to print, drawn to fit the paper the printer has
     * @return completes like {@link Printer#print}
     */
    CompletableFuture<Boolean> print(String jobName, Printable printable);
  }

  /** Whether the app is running on a Mac, whose print dialog behaves differently. */
  private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

  /** How sharply pages are printed, in dots per inch. */
  private static final double DPI = 300;

  /** Prints pages after showing the computer's print dialog. */
  static Printer printer = Printing::printWithSystemDialog;

  /** Prints labels after showing the computer's print dialog. */
  static PaperPrinter paperPrinter = Printing::printToPaperWithSystemDialog;

  /** Whether a print dialog is open, so a second click on Print doesn't open another. */
  private static boolean printing;

  /**
   * Prints pages, then says how it went: the message in green once printed,
   * or what went wrong in red. Nothing is shown if the user cancels. Must be
   * called on the JavaFX thread.
   *
   * @param jobName what the print job is called in the printer's queue
   * @param pages the pages, laid out by {@link #layOut(Region)}
   * @param status where to say how it went
   * @param printedMessage what to say once printed, such as "Printed 3 reminders"
   */
  static void print(String jobName, List<Region> pages, Label status, String printedMessage) {
    if (printing) {
      return;
    }
    printing = true;
    status.setText("");
    printer.print(jobName, pages).whenComplete((printed, error) -> Platform.runLater(() -> {
      printing = false;
      if (error != null) {
        var cause = error.getCause() instanceof IllegalStateException ? error.getCause() : error;
        status.setStyle("-fx-text-fill: red;");
        status.setText(cause.getMessage());
      } else if (printed) {
        status.setStyle("-fx-text-fill: green;");
        status.setText(printedMessage);
      }
    }));
  }

  /**
   * Prints something drawn to fit the paper, such as a forwarding label, and
   * says in a message if it couldn't be printed. Nothing is shown once it's
   * printed or if the user cancels. Must be called on the JavaFX thread.
   *
   * @param jobName what the print job is called in the printer's queue
   * @param printable what to print
   * @param owner the window the message belongs to
   */
  static void printLabel(String jobName, Printable printable, Stage owner) {
    if (printing) {
      return;
    }
    printing = true;
    paperPrinter.print(jobName, printable).whenComplete((printed, error) -> Platform.runLater(() -> {
      printing = false;
      if (error != null) {
        var cause = error.getCause() instanceof IllegalStateException ? error.getCause() : error;
        AppWindow.inform(owner, AlertType.ERROR, "The label couldn't be printed.", cause.getMessage());
      }
    }));
  }

  /**
   * Asks where to save a PDF. Replaceable so tests can answer without a real
   * dialog.
   */
  static TableOutput.ChooseFile choosePdf = (owner, suggestedName) -> {
    var chooser = new FileChooser();
    chooser.setTitle("Save as PDF");
    chooser.setInitialFileName(suggestedName);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
    return chooser.showSaveDialog(owner);
  };

  /**
   * Asks where to save, then saves pages as a PDF in the background, which
   * can be printed later or sent on, then says how it went. Must be called
   * on the JavaFX thread.
   *
   * @param owner the window the dialog belongs to
   * @param suggestedName the file name to start with, such as "price-sheet.pdf"
   * @param pages the pages, laid out by {@link #layOut(Region)}
   * @param status where to say how it went
   */
  static void savePdf(Window owner, String suggestedName, List<Region> pages, Label status) {
    var chosen = choosePdf.choose(owner, suggestedName);
    if (chosen == null) {
      return;
    }
    var file = chosen.getName().toLowerCase().endsWith(".pdf")
        ? chosen.toPath()
        : chosen.toPath().resolveSibling(chosen.getName() + ".pdf");
    status.setStyle("");
    status.setText("Saving…");
    CompletableFuture.runAsync(() -> {
      try (var pdf = new Pdf(file)) {
        for (var page : pages) {
          var picture = Picture.of(page);
          pdf.addPage(picture.image, picture.width, picture.height);
        }
        pdf.finish();
      } catch (IOException | PrinterException e) {
        throw new IllegalStateException("Couldn't save " + file.getFileName() + ": " + e.getMessage(), e);
      }
    }).whenComplete((done, error) -> Platform.runLater(() -> {
      if (error != null) {
        var cause = error.getCause() instanceof IllegalStateException ? error.getCause() : error;
        status.setStyle("-fx-text-fill: red;");
        status.setText(cause.getMessage());
      } else {
        status.setStyle("-fx-text-fill: green;");
        status.setText("Saved " + file);
      }
    }));
  }

  /**
   * Sizes a page and positions everything on it, as needs doing before it's
   * printed, since it isn't shown in a window.
   *
   * @param page the page
   * @return the same page
   */
  static Region layOut(Region page) {
    new Scene(new Group(page));
    page.applyCss();
    page.autosize();
    page.layout();
    return page;
  }

  /**
   * Shows the computer's print dialog and prints. This runs on a thread of its
   * own: the dialog waits for the user, and on a Mac it needs the JavaFX thread
   * free to appear at all.
   *
   * @param jobName what the print job is called in the printer queue
   * @param pages the pages
   * @return completes with {@code true} once printed, {@code false} if the user
   *     cancelled, or with the problem
   */
  private static CompletableFuture<Boolean> printWithSystemDialog(String jobName, List<Region> pages) {
    var result = new CompletableFuture<Boolean>();
    var thread = new Thread(() -> {
      try {
        var job = PrinterJob.getPrinterJob();
        // With no printer set up, a Mac explains how to add one itself.
        if (job.getPrintService() == null && !MAC) {
          throw new IllegalStateException("No printer is set up on this computer.");
        }
        job.setJobName(jobName);
        job.setPrintable(new Pages(pages));
        if (!job.printDialog()) {
          result.complete(false);
          return;
        }
        job.print();
        result.complete(true);
      } catch (PrinterException e) {
        result.completeExceptionally(new IllegalStateException(
            "Printing didn't work. Check that the printer is on and try again. (" + e.getMessage() + ")", e));
      } catch (RuntimeException e) {
        result.completeExceptionally(e);
      }
    }, "Printing");
    thread.setDaemon(true);
    thread.start();
    return result;
  }

  /**
   * Shows the computer's print dialog and prints something that draws itself
   * to fit the paper, on a thread of its own like
   * {@link #printWithSystemDialog}. It's given the whole sheet or label the
   * printer has, less only the edges the printer itself can't reach, rather
   * than the inch-wide margins Java assumes, which would leave nothing of a
   * small label.
   *
   * @param jobName what the print job is called in the printer queue
   * @param printable what to print
   * @return completes with {@code true} once printed, {@code false} if the user
   *     cancelled, or with the problem
   */
  private static CompletableFuture<Boolean> printToPaperWithSystemDialog(String jobName, Printable printable) {
    var result = new CompletableFuture<Boolean>();
    var thread = new Thread(() -> {
      try {
        var job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null && !MAC) {
          throw new IllegalStateException("No printer is set up on this computer.");
        }
        job.setJobName(jobName);
        job.setPrintable(printable);
        if (!job.printDialog()) {
          result.complete(false);
          return;
        }
        // The paper chosen in the dialog, with as much of it usable as the
        // printer allows.
        var format = job.defaultPage();
        var paper = format.getPaper();
        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        format.setPaper(paper);
        job.setPrintable(printable, job.validatePage(format));
        job.print();
        result.complete(true);
      } catch (PrinterException e) {
        result.completeExceptionally(new IllegalStateException(
            "Printing didn't work. Check that the printer is on and try again. (" + e.getMessage() + ")", e));
      } catch (RuntimeException e) {
        result.completeExceptionally(e);
      }
    }, "Printing");
    thread.setDaemon(true);
    thread.start();
    return result;
  }

  /**
   * Draws the pages for the printer, as a picture of each, made when the
   * printer asks for it so only one is held in memory at a time.
   */
  static final class Pages implements Printable {

    /** The pages to print. */
    private final List<Region> pages;

    /** The page last drawn, which the printer often asks for more than once. */
    private int pictureIndex = -1;

    /** A picture of that page. */
    private Picture picture;

    /**
     * Prepares to print pages.
     *
     * @param pages the pages, laid out
     */
    Pages(List<Region> pages) {
      this.pages = pages;
    }

    @Override
    public int print(Graphics graphics, PageFormat format, int pageIndex) throws PrinterException {
      if (pageIndex >= pages.size()) {
        return NO_SUCH_PAGE;
      }
      if (pageIndex != pictureIndex) {
        // Let go of the last picture first, so two are never held at once.
        picture = null;
        picture = Picture.of(pages.get(pageIndex));
        pictureIndex = pageIndex;
      }

      // Shrink a page that doesn't fit the paper, keeping its shape.
      var scale = Math.min(1, Math.min(format.getImageableWidth() / picture.width,
          format.getImageableHeight() / picture.height));
      var g = (Graphics2D) graphics;
      g.translate(format.getImageableX(), format.getImageableY());
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(picture.image, 0, 0, (int) Math.round(picture.width * scale),
          (int) Math.round(picture.height * scale), null);
      return PAGE_EXISTS;
    }

  }

  /** A picture of a page, and its size in points (1/72 inch). */
  private static final class Picture {

    /** The picture. */
    final BufferedImage image;

    /** The page's width, in points. */
    final double width;

    /** The page's height, in points. */
    final double height;

    /**
     * Makes a picture.
     *
     * @param image the picture
     * @param width the page's width, in points
     * @param height the page's height, in points
     */
    private Picture(BufferedImage image, double width, double height) {
      this.image = image;
      this.width = width;
      this.height = height;
    }

    /**
     * Takes a picture of a page on the JavaFX thread, waiting for it.
     *
     * @param page the page, laid out
     * @return the picture
     * @throws PrinterException if the picture can't be taken
     */
    static Picture of(Region page) throws PrinterException {
      var snapshot = new CompletableFuture<Image>();
      Platform.runLater(() -> {
        try {
          var parameters = new SnapshotParameters();
          parameters.setFill(Color.WHITE);
          parameters.setTransform(new Scale(DPI / 72, DPI / 72));
          snapshot.complete(page.snapshot(parameters, null));
        } catch (RuntimeException e) {
          snapshot.completeExceptionally(e);
        }
      });
      try {
        var image = snapshot.get();
        var width = (int) image.getWidth();
        var height = (int) image.getHeight();
        var pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        picture.setRGB(0, 0, width, height, pixels, 0, width);
        return new Picture(picture, width * 72 / DPI, height * 72 / DPI);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new PrinterException("Printing was stopped.");
      } catch (ExecutionException e) {
        throw new PrinterException("Couldn't draw the page: " + e.getCause().getMessage());
      }
    }

  }

  /** Not used: printing is done with static methods. */
  private Printing() {
  }

}
