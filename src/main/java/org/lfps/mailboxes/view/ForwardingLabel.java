package org.lfps.mailboxes.view;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.stage.Stage;

import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

/**
 * A mailing label for forwarding a holder's mail, printed with "Print
 * Label…" on a box's details: the shop's address in the top corner as the
 * return address, and the holder's name and forwarding address where the
 * post office expects it.
 *
 * <p>It's drawn to fit whatever paper the chosen printer has, so the same
 * label works on a label printer such as a Zebra, on an envelope, or on an
 * ordinary sheet of paper, where it's laid out like an envelope across the
 * top of the page, to cut out or fold into a window envelope. Sizes here are
 * in points (1/72 inch).
 */
final class ForwardingLabel implements Printable {

  /** Paper at least this wide is a sheet of paper rather than a label or envelope. */
  static final double SHEET_WIDTH = 7.5 * 72;

  /** How tall the label is on a sheet of paper: the height of a #10 envelope. */
  static final double SHEET_LABEL_HEIGHT = 4.125 * 72;

  /** The largest the forwarding address is printed. */
  static final double LARGEST_ADDRESS = 16;

  /** The smallest the forwarding address is printed, even if it doesn't quite fit. */
  static final double SMALLEST_TEXT = 5;

  /** The largest the return address is printed. */
  static final double LARGEST_RETURN = 10;

  /** The space from one line to the next, as a multiple of the type size. */
  static final double LINE_SPACING = 1.2;

  /** The return address, one line each, such as the shop's name, street, and town. */
  final List<String> from;

  /** The holder's name and forwarding address, one line each. */
  final List<String> to;

  /**
   * Makes a label.
   *
   * @param from the return address, one line each
   * @param to the holder's name and forwarding address, one line each
   */
  ForwardingLabel(List<String> from, List<String> to) {
    this.from = List.copyOf(from);
    this.to = List.copyOf(to);
  }

  /**
   * Makes the label for a box and one of its forwarding addresses, with the
   * shop's name and address from Settings as the return address.
   *
   * @param mailbox the box
   * @param address where its mail is forwarded
   * @param shopName the shop's name, from Settings
   * @param shopDetails the shop's address and phone, one item per line, from Settings
   * @return the label
   */
  static ForwardingLabel of(Mailbox mailbox, ForwardingAddress address, String shopName, String shopDetails) {
    return new ForwardingLabel(returnAddress(shopName, shopDetails), recipient(mailbox, address));
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

  /** Where each part of the label goes on the paper, and how big its type is, in points. */
  static final class Layout {

    /** The return address's type size. */
    final double fromSize;

    /** The left edge of the return address. */
    final double fromX;

    /** The top of the return address. */
    final double fromY;

    /** The forwarding address's type size. */
    final double toSize;

    /** The left edge of the forwarding address. */
    final double toX;

    /** The top of the forwarding address. */
    final double toY;

    /**
     * Makes a layout.
     *
     * @param fromSize the return address's type size
     * @param fromX the left edge of the return address
     * @param fromY the top of the return address
     * @param toSize the forwarding address's type size
     * @param toX the left edge of the forwarding address
     * @param toY the top of the forwarding address
     */
    Layout(double fromSize, double fromX, double fromY, double toSize, double toX, double toY) {
      this.fromSize = fromSize;
      this.fromX = fromX;
      this.fromY = fromY;
      this.toSize = toSize;
      this.toX = toX;
      this.toY = toY;
    }

  }

  /**
   * Works out where everything goes on paper of a given size. On a wide
   * label or envelope the forwarding address starts a third of the way
   * across and below the middle, as on a letter; on a tall label it goes
   * under the return address. Its type is as large as fits, up to
   * {@link #LARGEST_ADDRESS}.
   *
   * @param width the paper's width
   * @param height the paper's height
   * @param measure how text is measured, from the printer or a test
   * @return the layout
   */
  Layout layout(double width, double height, FontRenderContext measure) {
    // On a sheet of paper, only the top is used, the size of an envelope.
    var areaHeight = width >= SHEET_WIDTH ? Math.min(height, SHEET_LABEL_HEIGHT) : height;
    var margin = Math.max(0.08 * 72, Math.min(width, areaHeight) * 0.05);

    var fromSize = Math.max(SMALLEST_TEXT, Math.min(LARGEST_RETURN, areaHeight * 0.06));
    while (fromSize > SMALLEST_TEXT && blockWidth(from, fromSize, measure) > width * 0.6) {
      fromSize -= 0.5;
    }
    var fromBottom = margin + blockHeight(from, fromSize);

    var wide = width >= areaHeight * 1.3;
    var toX = wide ? width * 0.35 : margin + width * 0.1;
    var toY = wide ? Math.max(fromBottom + margin, areaHeight * 0.4) : fromBottom + areaHeight * 0.12;
    var toSize = LARGEST_ADDRESS;
    while (toSize > SMALLEST_TEXT && (blockWidth(to, toSize, measure) > width - toX - margin
        || blockHeight(to, toSize) > areaHeight - toY - margin)) {
      toSize -= 0.5;
    }
    return new Layout(fromSize, margin, margin, toSize, toX, toY);
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

  @Override
  public int print(Graphics graphics, PageFormat format, int pageIndex) {
    if (pageIndex > 0) {
      return NO_SUCH_PAGE;
    }
    var g = (Graphics2D) graphics;
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(java.awt.Color.BLACK);
    // The paper's own size, not the part the computer thinks is printable,
    // since label printers print almost to the edge.
    var layout = layout(format.getWidth(), format.getHeight(), g.getFontRenderContext());
    draw(g, from, layout.fromSize, layout.fromX, layout.fromY);
    draw(g, to, layout.toSize, layout.toX, layout.toY);
    return PAGE_EXISTS;
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
  private static void draw(Graphics2D g, List<String> lines, double size, double x, double top) {
    g.setFont(font(size));
    var ascent = g.getFontMetrics().getAscent();
    for (var i = 0; i < lines.size(); i++) {
      g.drawString(lines.get(i), (float) x, (float) (top + ascent + i * size * LINE_SPACING));
    }
  }

  /**
   * Prints a label for a box's forwarding address, through the computer's
   * Print window. A box with more than one address asks which first. Says
   * so in a message if it can't be printed.
   *
   * @param owner the window the questions belong to
   * @param mailbox the box, which has at least one forwarding address
   */
  static void print(Stage owner, Mailbox mailbox) {
    var addresses = mailbox.getForwardingAddresses();
    if (addresses.isEmpty()) {
      return;
    }
    var address = addresses.get(0);
    if (addresses.size() > 1) {
      var choice = Dialogs.confirmWithChoice.ask(owner, "Print a forwarding label?",
          "The label is addressed to " + BoxLabels.holderOr(mailbox, "the box holder")
              + " at the address chosen, with the shop's address as the return address.",
          "Which address?", addresses.stream().map(ForwardingAddress::toString).collect(Collectors.toList()),
          0, "Print…", "Cancel");
      if (choice < 0) {
        return;
      }
      address = addresses.get(choice);
    }

    String shopName;
    String shopDetails;
    try {
      var settings = new SettingsRepository();
      shopName = settings.get(Setting.SHOP_NAME);
      shopDetails = settings.get(Setting.SHOP_DETAILS);
    } catch (SQLException e) {
      shopName = Setting.SHOP_NAME.defaultValue();
      shopDetails = Setting.SHOP_DETAILS.defaultValue();
    }

    Printing.printLabel("Forwarding label, box " + mailbox.getBoxNumber(),
        of(mailbox, address, shopName, shopDetails), owner);
  }

}
