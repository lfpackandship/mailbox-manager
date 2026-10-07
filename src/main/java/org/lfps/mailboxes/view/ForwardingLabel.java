package org.lfps.mailboxes.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

/**
 * A mailing label for forwarding a holder's mail, printed from the Print
 * Forwarding Label window ({@link ForwardingLabelView}): the shop's address in
 * the top corner as the return address, and the holder's name and forwarding
 * address below it in large type.
 *
 * <p>The label is always drawn the size of the shop's Zebra labels, 4 inches
 * wide and 6.5 inches tall, from the top left corner of the part of the paper
 * the printer can print on. Printed on a label, it fills the label; if the
 * label printer's driver is set to a bigger paper size by mistake, the label
 * still lands on the label rather than off its edge. Printed on copy paper,
 * it has a dashed line around it to cut along. Sizes here are in points (1/72
 * inch).
 */
final class ForwardingLabel implements Printable {

  /** What the label is printed on, chosen on the Print Forwarding Label window. */
  enum Stock {

    /** A 4 by 6.5 inch label in the label printer. */
    LABEL("Label (4 × 6.5 inches)"),

    /** Ordinary paper, with a line around the label to cut along. */
    COPY_PAPER("Copy paper, to cut out");

    /** What the choice is called on screen. */
    private final String label;

    /**
     * Makes a choice.
     *
     * @param label what it's called on screen
     */
    Stock(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }

  }

  /** The label's width: 4 inches. */
  static final double WIDTH = 4 * 72;

  /** The label's height: 6.5 inches. */
  static final double HEIGHT = 6.5 * 72;

  /** The space left around the edges of the label. */
  static final double MARGIN = 0.2 * 72;

  /** How far down the label the forwarding address starts, at the least. */
  static final double ADDRESS_TOP = 1.75 * 72;

  /** The largest the forwarding address is printed. */
  static final double LARGEST_ADDRESS = 20;

  /** The smallest the text is printed, even if it doesn't quite fit. */
  static final double SMALLEST_TEXT = 6;

  /** The size the return address is printed, unless it has to be smaller to fit. */
  static final double RETURN_SIZE = 10;

  /** The space from one line to the next, as a multiple of the type size. */
  static final double LINE_SPACING = 1.2;

  /** The return address, one line each, such as the shop's name, street, and town. */
  final List<String> from;

  /** The holder's name and forwarding address, one line each. */
  final List<String> to;

  /** What the label is printed on. */
  final Stock stock;

  /**
   * Makes a label.
   *
   * @param from the return address, one line each
   * @param to the holder's name and forwarding address, one line each
   * @param stock what it's printed on
   */
  ForwardingLabel(List<String> from, List<String> to, Stock stock) {
    this.from = List.copyOf(from);
    this.to = List.copyOf(to);
    this.stock = stock;
  }

  /**
   * Makes the label for a box and one of its forwarding addresses, with the
   * shop's name and address from Settings as the return address.
   *
   * @param mailbox the box
   * @param address where its mail is forwarded
   * @param shopName the shop's name, from Settings
   * @param shopDetails the shop's address and phone, one item per line, from Settings
   * @param stock what it's printed on
   * @return the label
   */
  static ForwardingLabel of(Mailbox mailbox, ForwardingAddress address, String shopName, String shopDetails,
      Stock stock) {
    return new ForwardingLabel(returnAddress(shopName, shopDetails), recipient(mailbox, address), stock);
  }

  /**
   * Returns the return address: the shop's name, then the lines of its
   * details that have letters in them, which leaves out the phone number.
   *
   * @param shopName the shop's name, or blank for none
   * @param shopDetails the shop's address and phone, one item per line, or
   *     blank for none
   * @return the lines, blank ones left out
   */
  static List<String> returnAddress(String shopName, String shopDetails) {
    var lines = new ArrayList<String>();
    if (shopName != null && !shopName.isBlank()) {
      lines.add(shopName.strip());
    }
    if (shopDetails != null) {
      shopDetails.lines()
          .map(String::strip)
          .filter(line -> line.chars().anyMatch(Character::isLetter))
          .forEach(lines::add);
    }
    return lines;
  }

  /**
   * Returns who the label is addressed to: the holder's name and business,
   * if the box has them, then the forwarding address, with any apartment or
   * suite on the street line, as the post office prefers.
   *
   * @param mailbox the box
   * @param address where its mail is forwarded
   * @return the lines
   */
  static List<String> recipient(Mailbox mailbox, ForwardingAddress address) {
    var lines = new ArrayList<String>();
    var name = mailbox.getFullName();
    if (!name.isBlank()) {
      lines.add(name);
    }
    if (mailbox.getBusinessTitle() != null && !mailbox.getBusinessTitle().isBlank()) {
      lines.add(mailbox.getBusinessTitle().strip());
    }
    lines.add(join(" ", address.getStreet(), address.getUnit()));
    lines.add(join(" ", join(", ", address.getCity(), address.getState()), address.getZip()));
    return lines.stream().filter(line -> !line.isBlank()).collect(Collectors.toList());
  }

  /**
   * Joins the parts that aren't blank.
   *
   * @param separator what goes between them
   * @param parts the parts, any of which may be {@code null}
   * @return the joined text, or an empty string if every part is blank
   */
  private static String join(String separator, String... parts) {
    var kept = new ArrayList<String>();
    for (var part : parts) {
      if (part != null && !part.isBlank()) {
        kept.add(part.strip());
      }
    }
    return String.join(separator, kept);
  }

  /** How big the label's text is and where the forwarding address goes, in points from the label's corner. */
  static final class Layout {

    /** The return address's type size. */
    final double fromSize;

    /** The forwarding address's type size. */
    final double toSize;

    /** The top of the forwarding address. */
    final double toY;

    /**
     * Makes a layout.
     *
     * @param fromSize the return address's type size
     * @param toSize the forwarding address's type size
     * @param toY the top of the forwarding address
     */
    Layout(double fromSize, double toSize, double toY) {
      this.fromSize = fromSize;
      this.toSize = toSize;
      this.toY = toY;
    }

  }

  /**
   * Works out the label's layout: the return address at its usual size in
   * the top corner, and the forwarding address below it as large as fits
   * the label's width, up to {@link #LARGEST_ADDRESS}. Both start at the
   * left margin. Text only gets smaller than usual when a line is too long.
   *
   * @param measure how text is measured, from the printer or a test
   * @return the layout
   */
  Layout layout(FontRenderContext measure) {
    var width = WIDTH - 2 * MARGIN;
    var fromSize = RETURN_SIZE;
    while (fromSize > SMALLEST_TEXT && blockWidth(from, fromSize, measure) > width) {
      fromSize -= 0.5;
    }
    var toY = Math.max(ADDRESS_TOP, MARGIN + blockHeight(from, fromSize) + 0.5 * 72);
    var toSize = LARGEST_ADDRESS;
    while (toSize > SMALLEST_TEXT && (blockWidth(to, toSize, measure) > width
        || toY + blockHeight(to, toSize) > HEIGHT - MARGIN)) {
      toSize -= 0.5;
    }
    return new Layout(fromSize, toSize, toY);
  }

  /**
   * Returns how wide lines of text are: the width of the longest.
   *
   * @param lines the lines
   * @param size the type size
   * @param measure how text is measured
   * @return the width
   */
  static double blockWidth(List<String> lines, double size, FontRenderContext measure) {
    var font = font(size);
    return lines.stream().mapToDouble(line -> font.getStringBounds(line, measure).getWidth()).max().orElse(0);
  }

  /**
   * Returns how tall lines of text are.
   *
   * @param lines the lines
   * @param size the type size
   * @return the height
   */
  static double blockHeight(List<String> lines, double size) {
    return lines.size() * size * LINE_SPACING;
  }

  /**
   * Returns the label's typeface at a size.
   *
   * @param size the type size
   * @return the font
   */
  private static Font font(double size) {
    return new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont((float) size);
  }

  /**
   * Returns how much the label is shrunk to fit the paper: not at all,
   * unless the part the printer can print on is smaller than the label. On
   * label stock, the label is measured from the paper's edge, so it fits
   * the label itself even if the printer thinks it has a bigger paper.
   *
   * @param format the paper and the part of it the printer can print on
   * @return the scale, 1 for full size
   */
  double scale(PageFormat format) {
    var width = format.getImageableWidth();
    var height = format.getImageableHeight();
    if (stock == Stock.LABEL) {
      width = Math.min(width, WIDTH - format.getImageableX());
      height = Math.min(height, HEIGHT - format.getImageableY());
    }
    return Math.min(1, Math.min(width / WIDTH, height / HEIGHT));
  }

  @Override
  public int print(Graphics graphics, PageFormat format, int pageIndex) {
    if (pageIndex > 0) {
      return NO_SUCH_PAGE;
    }
    var g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      // Only where the printer says it can print, so nothing is cut off.
      g.translate(format.getImageableX(), format.getImageableY());
      var scale = scale(format);
      g.scale(scale, scale);
      draw(g);
    } finally {
      g.dispose();
    }
    return PAGE_EXISTS;
  }

  /**
   * Draws the label from the corner it's given, at full size.
   *
   * @param g where to draw
   */
  void draw(Graphics2D g) {
    var layout = layout(g.getFontRenderContext());
    g.setColor(Color.BLACK);
    if (stock == Stock.COPY_PAPER) {
      g.setStroke(new BasicStroke(0.75f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
          new float[] { 4, 3 }, 0));
      g.draw(new Rectangle2D.Double(0.5, 0.5, WIDTH - 1, HEIGHT - 1));
    }
    drawLines(g, from, layout.fromSize, MARGIN, MARGIN);
    drawLines(g, to, layout.toSize, MARGIN, layout.toY);
  }

  /**
   * Draws lines of text.
   *
   * @param g where to draw
   * @param lines the lines
   * @param size the type size
   * @param x the left edge
   * @param top the top of the first line
   */
  private static void drawLines(Graphics2D g, List<String> lines, double size, double x, double top) {
    g.setFont(font(size));
    var ascent = g.getFontMetrics().getAscent();
    for (var i = 0; i < lines.size(); i++) {
      g.drawString(lines.get(i), (float) x, (float) (top + ascent + i * size * LINE_SPACING));
    }
  }

}
