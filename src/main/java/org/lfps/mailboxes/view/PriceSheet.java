package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.Money;
import org.lfps.mailboxes.util.RentalLengths;

/**
 * The printed price sheet handed to customers: the shop's name and details,
 * what the service includes, and a table of prices with a column for each box
 * size and a row for each rental length. As a renewal reminder, it also says
 * which box it's for and when its rental ends, with the end date and the
 * box's size circled.
 */
final class PriceSheet {

  /** How wide a page is laid out, in points (1/72 inch): letter paper less margins. */
  static final double PAGE_WIDTH = 540;

  /** The color of the circles, like a red pen. It prints dark grey in black and white. */
  private static final Color INK = Color.web("#c62828");

  /** One column of prices: a box size, or the default prices. */
  static final class Column {

    /** The size, or {@link PriceRepository#DEFAULT_SIZE} for the default prices. */
    final String size;

    /** What the column is headed with. */
    final String heading;

    /** What the size measures, or an empty string. */
    final String description;

    /** The price in cents for each rental length that has one. */
    final Map<Integer, Long> prices;

    Column(String size, String heading, String description, Map<Integer, Long> prices) {
      this.size = size;
      this.heading = heading;
      this.description = description;
      this.prices = prices;
    }

  }

  /** Everything printed on the sheet apart from the box it's for. */
  static final class Content {

    final String shopName;
    final String shopDetails;
    final String intro;
    final String note;
    final String reminderMessage;

    /** The rental lengths with a price, one row each, shortest first. */
    final List<Integer> lengths;

    /** The sizes with a price, one column each, then the default prices if there are any. */
    final List<Column> columns;

    Content(String shopName, String shopDetails, String intro, String note, String reminderMessage,
        List<Integer> lengths, List<Column> columns) {
      this.shopName = shopName;
      this.shopDetails = shopDetails;
      this.intro = intro;
      this.note = note;
      this.reminderMessage = reminderMessage;
      this.lengths = lengths;
      this.columns = columns;
    }

    /**
     * Returns the column holding the prices a box of a size is charged: its
     * size's column, or the default prices if its size has none.
     *
     * @param size the box's size, or {@code null} if it has none
     * @return the column, or {@code null} if no prices apply to the box
     */
    Column columnFor(String size) {
      var key = size == null ? PriceRepository.DEFAULT_SIZE : size.trim().toLowerCase(Locale.ROOT);
      Column fallback = null;
      for (var column : columns) {
        var columnKey = column.size.toLowerCase(Locale.ROOT);
        if (!key.isEmpty() && columnKey.equals(key)) {
          return column;
        }
        if (columnKey.isEmpty()) {
          fallback = column;
        }
      }
      return fallback;
    }

  }

  /**
   * Loads the sheet's wording from the settings, and the prices, sizes, and
   * rental lengths.
   *
   * @return the content
   * @throws SQLException if it can't be read
   */
  static Content load() throws SQLException {
    var settings = new SettingsRepository();
    List<Integer> lengths;
    try {
      lengths = RentalLengths.parse(settings.get(Setting.RENTAL_LENGTHS));
    } catch (IllegalArgumentException badSetting) {
      lengths = RentalLengths.parse(Setting.RENTAL_LENGTHS.defaultValue());
    }
    var prices = new PriceRepository();
    var saved = prices.findAll();
    var descriptions = prices.findDescriptions();

    var columns = new ArrayList<Column>();
    for (var size : new BoxInventoryRepository().sizes()) {
      var sizePrices = pricesFor(size, lengths, saved);
      if (!sizePrices.isEmpty()) {
        columns.add(new Column(size, size, descriptions.getOrDefault(size.toLowerCase(Locale.ROOT), ""),
            sizePrices));
      }
    }
    var defaultPrices = pricesFor(PriceRepository.DEFAULT_SIZE, lengths, saved);
    if (!defaultPrices.isEmpty()) {
      columns.add(new Column(PriceRepository.DEFAULT_SIZE, columns.isEmpty() ? "Price" : "Other sizes", "",
          defaultPrices));
    }

    var pricedLengths = new ArrayList<Integer>();
    for (var months : lengths) {
      if (columns.stream().anyMatch(column -> column.prices.containsKey(months))) {
        pricedLengths.add(months);
      }
    }

    return new Content(settings.get(Setting.SHOP_NAME), settings.get(Setting.SHOP_DETAILS),
        settings.get(Setting.PRICE_SHEET_INTRO), settings.get(Setting.PRICE_SHEET_NOTE),
        settings.get(Setting.REMINDER_MESSAGE), pricedLengths, columns);
  }

  /**
   * Builds the plain price sheet, for handing to new customers.
   *
   * @param content what to print
   * @param today the date the prices are given as of
   * @return the page, laid out {@link #PAGE_WIDTH} wide
   */
  static Region page(Content content, LocalDate today) {
    return page(content, null, null, today);
  }

  /**
   * Builds the price sheet, as a renewal reminder for a box if one is given.
   *
   * @param content what to print
   * @param mailbox the box the reminder is for, or {@code null} for the plain
   *     price sheet
   * @param size the box's size in the inventory, or {@code null} if it has none
   * @param today the date the reminder is printed
   * @return the page, laid out {@link #PAGE_WIDTH} wide
   */
  static Region page(Content content, Mailbox mailbox, String size, LocalDate today) {
    var marked = new Marked();

    var name = new Label(content.shopName);
    name.setId("sheetShopName");
    name.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
    name.setWrapText(true);

    var details = new Label(content.shopDetails.strip());
    details.setId("sheetShopDetails");
    details.setStyle("-fx-font-weight: bold;");
    details.setWrapText(true);

    var body = new VBox(16, new VBox(4, name, details));

    if (mailbox != null) {
      body.getChildren().add(reminder(marked, content, mailbox, today));
    }
    if (!content.intro.isBlank()) {
      body.getChildren().add(intro(content.intro));
    }

    if (content.columns.isEmpty()) {
      var none = new Label("No prices have been set. Set them with Prices… in Settings.");
      none.setId("sheetNoPrices");
      body.getChildren().add(none);
    } else {
      body.getChildren().add(table(marked, content, mailbox == null ? null : content.columnFor(size)));
    }

    if (!content.note.isBlank()) {
      var note = new Label(content.note.strip());
      note.setId("sheetNote");
      note.setStyle("-fx-font-size: 15px;");
      note.setWrapText(true);
      body.getChildren().add(note);
    }

    var asOf = new Label((mailbox == null ? "Prices as of " : "Printed ")
        + today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)));
    asOf.setId("sheetDate");
    asOf.setStyle("-fx-font-size: 10px; -fx-text-fill: #555555;");
    body.getChildren().add(asOf);

    // Fixed sizes, so the page looks the same whatever the app's text size.
    body.setStyle("-fx-font-size: 13px; -fx-text-fill: black;");
    body.setPadding(new Insets(4));
    body.setPrefWidth(PAGE_WIDTH);
    body.setMinWidth(PAGE_WIDTH);
    body.setMaxWidth(PAGE_WIDTH);
    marked.getChildren().add(0, body);
    marked.setStyle("-fx-background-color: white;");
    return marked;
  }

  private static Node reminder(Marked marked, Content content, Mailbox mailbox, LocalDate today) {
    var title = new Label("Mailbox Renewal Reminder");
    title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

    var holder = mailbox.getHolderName();
    var box = new Label("Box " + mailbox.getBoxNumber().trim() + (holder.isEmpty() ? "" : " – " + holder));
    box.setId("reminderBox");
    box.setStyle("-fx-font-size: 15px;");

    var card = new VBox(6, title, box);
    var endDate = mailbox.getEndDate();
    if (endDate != null) {
      var date = new Label(endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
      date.setId("reminderEndDate");
      date.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
      date.setMinWidth(Region.USE_PREF_SIZE);
      var before = new Label(endDate.isBefore(today) ? "Your rental ended on" : "Your rental ends on");
      before.setStyle("-fx-font-size: 15px;");
      before.setMinWidth(Region.USE_PREF_SIZE);
      // Room between the words and the circle around the date.
      var line = new HBox(14, before, date);
      line.setAlignment(Pos.BASELINE_LEFT);
      // Room for the circle around the date.
      line.setPadding(new Insets(4, 0, 4, 0));
      card.getChildren().add(line);
      marked.circle("endDateCircle", 7, 3, date);
    }

    if (!content.reminderMessage.isBlank()) {
      var message = new Label(content.reminderMessage.strip());
      message.setId("reminderMessage");
      message.setWrapText(true);
      card.getChildren().add(message);
    }

    card.setPadding(new Insets(12));
    card.setStyle("-fx-border-color: black; -fx-border-width: 1.5; -fx-border-radius: 6;");
    return card;
  }

  /** Lays out the text above the prices, showing lines that start with "-" as bullet points. */
  private static Node intro(String text) {
    var lines = new VBox(3);
    lines.setId("sheetIntro");
    for (var line : text.strip().split("\\R")) {
      var trimmed = line.strip();
      if (trimmed.isEmpty()) {
        var gap = new Region();
        gap.setMinHeight(6);
        lines.getChildren().add(gap);
      } else if (trimmed.startsWith("-") || trimmed.startsWith("•")) {
        var bullet = new Label("•");
        bullet.setMinWidth(14);
        var item = new Label(trimmed.substring(1).strip());
        item.setWrapText(true);
        var row = new HBox(bullet, item);
        row.setPadding(new Insets(0, 0, 0, 20));
        lines.getChildren().add(row);
      } else {
        var label = new Label(trimmed);
        label.setWrapText(true);
        lines.getChildren().add(label);
      }
    }
    lines.setStyle("-fx-font-size: 15px;");
    return lines;
  }

  private static Node table(Marked marked, Content content, Column circled) {
    var grid = new GridPane();
    grid.setId("sheetPrices");
    grid.setStyle("-fx-border-color: black; -fx-border-width: 0 1 1 0;");
    grid.getColumnConstraints().add(new ColumnConstraints(110));
    for (var i = 0; i < content.columns.size(); i++) {
      var constraints = new ColumnConstraints();
      constraints.setHgrow(Priority.ALWAYS);
      constraints.setHalignment(HPos.CENTER);
      grid.getColumnConstraints().add(constraints);
    }
    // Room on either side for a circle around the first or last column.
    grid.setPrefWidth(PAGE_WIDTH - 24);
    grid.setMaxWidth(PAGE_WIDTH - 24);

    grid.add(cell(new Label(""), Pos.CENTER_LEFT), 0, 0);
    for (var col = 0; col < content.columns.size(); col++) {
      var column = content.columns.get(col);
      var heading = new Label(column.heading);
      heading.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
      var header = new VBox(2, heading);
      header.setAlignment(Pos.CENTER);
      if (!column.description.isBlank()) {
        var measures = new Label(column.description);
        measures.setWrapText(true);
        header.getChildren().add(measures);
      }
      var headerCell = cell(header, Pos.CENTER);
      headerCell.setId("sheetHeader-" + col);
      grid.add(headerCell, col + 1, 0);
    }

    for (var row = 0; row < content.lengths.size(); row++) {
      var months = content.lengths.get(row);
      var length = new Label(RentalLengths.label(months));
      length.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
      grid.add(cell(length, Pos.CENTER_LEFT), 0, row + 1);
      for (var col = 0; col < content.columns.size(); col++) {
        var price = content.columns.get(col).prices.get(months);
        var priceLabel = new Label(price == null ? "—" : Money.format(price));
        priceLabel.setStyle("-fx-font-size: 15px;");
        var priceCell = cell(priceLabel, Pos.CENTER);
        priceCell.setId("sheetPrice-" + months + "-" + col);
        grid.add(priceCell, col + 1, row + 1);
      }
    }

    if (circled != null) {
      var col = content.columns.indexOf(circled);
      var cells = new ArrayList<Node>();
      for (var node : grid.getChildren()) {
        var index = GridPane.getColumnIndex(node);
        if (index != null && index == col + 1) {
          cells.add(node);
        }
      }
      marked.circle("sizeCircle", 6, 6, cells.toArray(new Node[0]));
    }

    var holder = new HBox(grid);
    holder.setAlignment(Pos.CENTER);
    // Room above and below for the circle.
    holder.setPadding(new Insets(6, 0, 6, 0));
    return holder;
  }

  private static StackPane cell(Node content, Pos alignment) {
    var cell = new StackPane(content);
    StackPane.setAlignment(content, alignment);
    cell.setPadding(new Insets(8, 10, 8, 10));
    cell.setStyle("-fx-border-color: black; -fx-border-width: 1 0 0 1;");
    cell.setMaxWidth(Double.MAX_VALUE);
    cell.setMaxHeight(Double.MAX_VALUE);
    return cell;
  }

  private static Map<Integer, Long> pricesFor(String size, List<Integer> lengths, Map<String, Long> saved) {
    var prices = new LinkedHashMap<Integer, Long>();
    for (var months : lengths) {
      var price = saved.get(PriceRepository.key(size, months));
      if (price != null) {
        prices.put(months, price);
      }
    }
    return prices;
  }

  /**
   * A page with hand-drawn-looking circles around some of its parts, drawn
   * on top once the page is laid out.
   */
  static final class Marked extends StackPane {

    private final List<Node[]> targets = new ArrayList<>();
    private final List<Rectangle> circles = new ArrayList<>();
    private final List<double[]> paddings = new ArrayList<>();

    Marked() {
      setAlignment(Pos.TOP_LEFT);
    }

    /**
     * Circles the area covering the given parts of the page.
     */
    void circle(String id, double padX, double padY, Node... parts) {
      var circle = new Rectangle();
      circle.setId(id);
      circle.setManaged(false);
      circle.setFill(null);
      circle.setStroke(INK);
      circle.setStrokeWidth(2.5);
      circle.setMouseTransparent(true);
      targets.add(parts);
      circles.add(circle);
      paddings.add(new double[] { padX, padY });
      getChildren().add(circle);
    }

    @Override
    protected void layoutChildren() {
      super.layoutChildren();
      // Position everything on the page now, rather than after this, so the
      // circles go where the parts they surround end up.
      for (var child : getChildren()) {
        if (child instanceof Parent) {
          ((Parent) child).layout();
        }
      }
      for (var i = 0; i < circles.size(); i++) {
        var area = areaOf(targets.get(i));
        var circle = circles.get(i);
        var pad = paddings.get(i);
        if (area == null) {
          circle.setVisible(false);
          continue;
        }
        var width = area.getWidth() + 2 * pad[0];
        var height = area.getHeight() + 2 * pad[1];
        var round = Math.min(width, height);
        circle.setVisible(true);
        circle.setX(area.getMinX() - pad[0]);
        circle.setY(area.getMinY() - pad[1]);
        circle.setWidth(width);
        circle.setHeight(height);
        circle.setArcWidth(round);
        circle.setArcHeight(Math.min(round, height * 0.6));
      }
    }

    /** Returns the area covering the parts, in this page's coordinates. */
    private BoundingBox areaOf(Node[] parts) {
      double minX = Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      for (var part : parts) {
        var bounds = boundsHere(part);
        if (bounds == null) {
          continue;
        }
        minX = Math.min(minX, bounds.getMinX());
        minY = Math.min(minY, bounds.getMinY());
        maxX = Math.max(maxX, bounds.getMaxX());
        maxY = Math.max(maxY, bounds.getMaxY());
      }
      return minX > maxX ? null : new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    private Bounds boundsHere(Node node) {
      Bounds bounds = node.getBoundsInLocal();
      var current = node;
      while (current != this) {
        if (current == null) {
          return null;
        }
        bounds = current.localToParent(bounds);
        current = current.getParent();
      }
      return bounds;
    }

  }

  private PriceSheet() {
  }

}
