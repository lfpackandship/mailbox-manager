package org.lfps.mailboxes.view;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes pictures of pages to a PDF file, one per sheet of letter paper, so
 * pages can be saved without a printer. Pages are added one at a time, so
 * only one picture need be held in memory.
 */
final class Pdf implements AutoCloseable {

  /** Letter paper, in points (1/72 inch). */
  private static final double PAPER_WIDTH = 612;
  private static final double PAPER_HEIGHT = 792;

  /** The margin around each page, in points. */
  private static final double MARGIN = 36;

  private final Path target;
  private final Path partial;
  private final Counting out;

  /** Where each object starts in the file, by object number less one. */
  private final List<Long> offsets = new ArrayList<>();

  /** The object number of each page. */
  private final List<Integer> pageObjects = new ArrayList<>();

  private boolean finished;

  /**
   * Starts a PDF file. It's written under a temporary name and only takes
   * the target's name once finished, so a failure never leaves half a file.
   *
   * @param target the file to write
   * @throws IOException if it can't be created
   */
  Pdf(Path target) throws IOException {
    this.target = target;
    this.partial = target.resolveSibling(target.getFileName() + ".partial");
    this.out = new Counting(new BufferedOutputStream(Files.newOutputStream(partial)));
    write("%PDF-1.4\n%âãÏÓ\n");
    // The catalog and the list of pages are objects 1 and 2, written last.
    offsets.add(null);
    offsets.add(null);
  }

  /**
   * Adds a page, centred across the paper at the top, shrunk if it doesn't
   * fit within the margins.
   *
   * @param image the picture of the page
   * @param width the page's width, in points
   * @param height the page's height, in points
   * @throws IOException if it can't be written
   */
  void addPage(BufferedImage image, double width, double height) throws IOException {
    var scale = Math.min(1, Math.min((PAPER_WIDTH - 2 * MARGIN) / width, (PAPER_HEIGHT - 2 * MARGIN) / height));
    var drawnWidth = width * scale;
    var drawnHeight = height * scale;
    var x = (PAPER_WIDTH - drawnWidth) / 2;
    var y = PAPER_HEIGHT - MARGIN - drawnHeight;

    var pixels = new ByteArrayOutputStream();
    try (var deflate = new DeflaterOutputStream(pixels)) {
      var row = new byte[image.getWidth() * 3];
      for (var py = 0; py < image.getHeight(); py++) {
        for (var px = 0; px < image.getWidth(); px++) {
          var rgb = image.getRGB(px, py);
          row[px * 3] = (byte) (rgb >> 16);
          row[px * 3 + 1] = (byte) (rgb >> 8);
          row[px * 3 + 2] = (byte) rgb;
        }
        deflate.write(row);
      }
    }

    var imageObject = startObject();
    write("<< /Type /XObject /Subtype /Image /Width " + image.getWidth() + " /Height " + image.getHeight()
        + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length " + pixels.size()
        + " >>\nstream\n");
    pixels.writeTo(out);
    write("\nendstream\nendobj\n");

    var drawing = String.format(Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Page Do Q", drawnWidth, drawnHeight,
        x, y).getBytes(StandardCharsets.US_ASCII);
    var contentsObject = startObject();
    write("<< /Length " + drawing.length + " >>\nstream\n");
    out.write(drawing);
    write("\nendstream\nendobj\n");

    var pageObject = startObject();
    write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents " + contentsObject + " 0 R "
        + "/Resources << /XObject << /Page " + imageObject + " 0 R >> >> >>\nendobj\n");
    pageObjects.add(pageObject);
  }

  /**
   * Finishes the file and gives it the target's name, replacing any file
   * there.
   *
   * @throws IOException if it can't be written
   */
  void finish() throws IOException {
    offsets.set(0, out.count);
    write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
    offsets.set(1, out.count);
    var kids = new StringBuilder();
    for (var page : pageObjects) {
      kids.append(page).append(" 0 R ");
    }
    write("2 0 obj\n<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageObjects.size()
        + " >>\nendobj\n");

    var xref = out.count;
    var table = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
    for (var offset : offsets) {
      table.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
    }
    write(table.toString());
    write("trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
    out.close();
    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    finished = true;
  }

  /** Throws the file away unless it was finished. */
  @Override
  public void close() throws IOException {
    if (!finished) {
      out.close();
      Files.deleteIfExists(partial);
    }
  }

  private int startObject() throws IOException {
    offsets.add(out.count);
    var number = offsets.size();
    write(number + " 0 obj\n");
    return number;
  }

  private void write(String text) throws IOException {
    out.write(text.getBytes(StandardCharsets.ISO_8859_1));
  }

  /** Counts the bytes written, for the table of where each object starts. */
  private static final class Counting extends FilterOutputStream {

    long count;

    Counting(OutputStream out) {
      super(out);
    }

    @Override
    public void write(int b) throws IOException {
      out.write(b);
      count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
      count += len;
    }

  }

}
