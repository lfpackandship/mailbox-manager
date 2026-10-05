package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.PriceRepository;
import org.lfps.mailboxes.model.Mailbox;

/**
 * A window for printing the price sheet, either plain for new customers, as
 * renewal reminders to put in boxes, or as notices that prices are changing,
 * with a preview of what will print. The wording is set in Settings, and the
 * prices and sizes on the Prices window.
 */
final class PriceSheetView {

  /** What the window prints. */
  private enum Kind {
    /** The plain price sheet, for new customers. */
    PRICE_SHEET("Print Price Sheet", "Mailbox price sheet", "price-sheet-", "", ""),
    /** Renewal reminders, one for each box. */
    REMINDERS("Print Renewal Reminders", "Mailbox renewal reminders", "renewal-reminders-", "reminder",
        "reminders"),
    /** Notices that prices are changing, one for each box. */
    NOTICES("Print Price Change Notices", "Mailbox price change notices", "price-change-notices-", "notice",
        "notices");

    /** The window's title. */
    final String title;

    /** What the print job is called in the printer queue. */
    final String jobName;

    /** Starts the name suggested for a PDF, which ends with the date. */
    final String filePrefix;

    /** What one page is called, such as "reminder". */
    final String one;

    /** What several pages are called, such as "reminders". */
    final String many;

    /**
     * Makes a kind of printing.
     *
     * @param title the window's title
     * @param jobName what the print job is called
     * @param filePrefix starts the name suggested for a PDF
     * @param one what one page is called
     * @param many what several pages are called
     */
    Kind(String title, String jobName, String filePrefix, String one, String many) {
      this.title = title;
      this.jobName = jobName;
      this.filePrefix = filePrefix;
      this.one = one;
      this.many = many;
    }

    /**
     * Says how many pages, such as "3 reminders".
     *
     * @param pages how many pages
     * @return the count and what they're called
     */
    String count(long pages) {
      return pages + " " + (pages == 1 ? one : many);
    }
  }

  /** How large the preview is, compared with the printed page. */
  private static final double PREVIEW_SCALE = 0.8;

  /** The open window, or {@code null} if none is open. */
  private static Stage window;

  /**
   * Opens a window for printing the plain price sheet.
   *
   * @param owner the window it belongs to
   */
  static void showPriceSheet(Stage owner) {
    show(owner, Kind.PRICE_SHEET, List.of(), null);
  }

  /**
   * Opens a window for printing renewal reminders, each on its own page,
   * with all the boxes ticked to start with.
   *
   * @param owner the window it belongs to
   * @param boxes the boxes that can be printed, which should have end dates
   */
  static void showReminders(Stage owner, List<Mailbox> boxes) {
    show(owner, Kind.REMINDERS, boxes, null);
  }

  /**
   * Opens a window for printing notices that prices are changing, each on
   * its own page, with all the boxes ticked to start with.
   *
   * @param owner the window it belongs to
   * @param boxes the boxes that can be printed; must not be empty
   * @param change the scheduled price change
   */
  static void showNotices(Stage owner, List<Mailbox> boxes, PriceRepository.PriceChange change) {
    show(owner, Kind.NOTICES, boxes, change);
  }

  /**
   * Opens the window, closing one already open.
   *
   * @param owner the window it belongs to
   * @param kind what it prints
   * @param boxes the boxes to print a page for, or none for the plain price
   *     sheet
   * @param change the price change, for notices, or {@code null}
   */
  private static void show(Stage owner, Kind kind, List<Mailbox> boxes, PriceRepository.PriceChange change) {
    if (window != null) {
      window.close();
    }
    var reminders = kind != Kind.PRICE_SHEET;
    var today = LocalDate.now();

    var resultLabel = new Label();
    resultLabel.setId("printResultLabel");
    resultLabel.setWrapText(true);

    PriceSheet.Content loaded = null;
    PriceSheet.Content loadedAfter = null;
    try {
      loaded = PriceSheet.load();
      if (change != null) {
        loadedAfter = PriceSheet.loadAfter(change);
      }
    } catch (SQLException e) {
      showError(resultLabel, "Couldn't load the prices: " + e.getMessage());
      loaded = null;
    }
    var content = loaded;
    // For notices, what the sheet will be once the new prices start.
    var after = loadedAfter;

    var preview = new StackPane();
    preview.setId("printPreview");
    preview.setPadding(new Insets(10));
    preview.setStyle("-fx-background-color: #9e9e9e;");
    var previewScroll = new ScrollPane(preview);
    previewScroll.setFitToWidth(true);
    previewScroll.setPrefViewportWidth(PriceSheet.PAGE_WIDTH * PREVIEW_SCALE + 30);
    previewScroll.setPrefViewportHeight(560);
    HBox.setHgrow(previewScroll, Priority.ALWAYS);

    var stage = new Stage();
    var printBtn = new Button();
    printBtn.setId("printButton");
    printBtn.setDefaultButton(true);
    printBtn.setDisable(content == null);

    // Whether each box is ticked to be printed.
    Map<Mailbox, BooleanProperty> ticked = new IdentityHashMap<>();
    var list = new ListView<Mailbox>();
    list.setId("printBoxList");
    Runnable updatePrintButton = () -> {
      if (!reminders) {
        printBtn.setText("Print");
        return;
      }
      var count = boxes.stream().filter(box -> ticked.get(box).get()).count();
      var noun = count == 1 ? kind.one : kind.many;
      printBtn.setText("Print " + count + " " + Character.toUpperCase(noun.charAt(0)) + noun.substring(1));
      printBtn.setDisable(content == null || count == 0);
    };
    for (var box : boxes) {
      var property = new SimpleBooleanProperty(true);
      property.addListener((obs, was, now) -> updatePrintButton.run());
      ticked.put(box, property);
    }
    updatePrintButton.run();

    Runnable showPreview = () -> {
      if (content == null) {
        return;
      }
      var box = reminders ? list.getSelectionModel().getSelectedItem() : null;
      if (reminders && box == null) {
        box = boxes.get(0);
      }
      var page = page(kind, content, after, change, box, today);
      page.getTransforms().setAll(new Scale(PREVIEW_SCALE, PREVIEW_SCALE));
      preview.getChildren().setAll(new Group(page));
    };

    var left = new VBox(8);
    if (reminders) {
      var dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
      list.getItems().setAll(boxes);
      list.setCellFactory(CheckBoxListCell.forListView(ticked::get, new StringConverter<>() {
        @Override
        public String toString(Mailbox box) {
          return BoxLabels.boxAndHolder(box)
              + (box.getEndDate() == null ? "" : " (ends " + box.getEndDate().format(dateFormat) + ")")
              + (box.isForwardingOnly() ? " – forwarding only" : "");
        }

        @Override
        public Mailbox fromString(String text) {
          throw new UnsupportedOperationException();
        }
      }));
      list.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> showPreview.run());
      list.setPrefWidth(300);
      VBox.setVgrow(list, Priority.ALWAYS);

      var allBtn = new Button("Tick All");
      allBtn.setId("printTickAllButton");
      allBtn.setOnAction(e -> ticked.values().forEach(property -> property.set(true)));
      var noneBtn = new Button("Untick All");
      noneBtn.setId("printUntickAllButton");
      noneBtn.setOnAction(e -> ticked.values().forEach(property -> property.set(false)));

      var explanation = new Label("Tick the boxes to print a " + kind.one + " for. Each " + kind.one
          + " prints on its own page. Click a box to preview its " + kind.one + ".");
      explanation.setWrapText(true);
      left.getChildren().addAll(explanation, list, new HBox(8, allBtn, noneBtn));
      list.getSelectionModel().select(0);
    }

    var wording = new Label(kind == Kind.NOTICES
        ? "To change the new prices, their start date, or the notice's message, click Price Change… on the "
            + "Prices window. The shop's details are in File → Settings."
        : "To change the shop's details or the wording, go to File → Settings. To change the "
            + "prices or what each size measures, click Prices… in Settings.");
    wording.setWrapText(true);
    left.getChildren().add(wording);
    left.setPrefWidth(300);
    left.setMinWidth(Region.USE_PREF_SIZE);

    printBtn.setOnAction(e -> {
      var pages = pages(kind, content, after, change, boxes, ticked, today);
      Printing.print(kind.jobName, pages, resultLabel, reminders ? "Printed " + kind.count(pages.size()) : "Printed");
    });

    var pdfBtn = new Button("Save as PDF…");
    pdfBtn.setId("savePdfButton");
    pdfBtn.disableProperty().bind(printBtn.disableProperty());
    pdfBtn.setOnAction(e -> Printing.savePdf(stage,
        kind.filePrefix + today + ".pdf", pages(kind, content, after, change, boxes, ticked, today),
        resultLabel));

    var closeBtn = new Button("Close");
    closeBtn.setId("printCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var title = new Label(kind.title);
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");

    var main = new HBox(15, left, previewScroll);
    VBox.setVgrow(main, Priority.ALWAYS);
    var root = new VBox(12, title, main, new HBox(10, printBtn, pdfBtn, closeBtn), resultLabel);
    root.setPadding(new Insets(20));
    AppWindow.applyTextSize(root);
    showPreview.run();

    stage.initOwner(owner);
    stage.setTitle(title.getText());
    stage.setScene(new Scene(root));
    stage.setOnHidden(e -> {
      if (window == stage) {
        window = null;
      }
    });
    window = stage;
    AppWindow.showWithinScreen(stage);
  }

  /**
   * Builds the pages to print: a reminder for each ticked box, or the plain
   * price sheet if there are no boxes.
   *
   * @param kind what's printed
   * @param content what's printed now, with today's prices
   * @param after for notices, what will be printed once new prices start, or
   *     {@code null}
   * @param change for notices, the price change, or {@code null}
   * @param boxes the boxes listed
   * @param ticked whether each box is ticked
   * @param today the date printed
   * @return the pages, laid out
   */
  private static List<Region> pages(Kind kind, PriceSheet.Content content, PriceSheet.Content after,
      PriceRepository.PriceChange change, List<Mailbox> boxes, Map<Mailbox, BooleanProperty> ticked,
      LocalDate today) {
    var pages = new ArrayList<Region>();
    if (boxes.isEmpty()) {
      pages.add(Printing.layOut(page(kind, content, after, change, null, today)));
    }
    for (var box : boxes) {
      if (ticked.get(box).get()) {
        pages.add(Printing.layOut(page(kind, content, after, change, box, today)));
      }
    }
    return pages;
  }

  /**
   * Builds the page for a box, or the plain price sheet if there's no box.
   *
   * @param kind what's printed
   * @param content what's printed now, with today's prices
   * @param after for notices, what will be printed once new prices start, or
   *     {@code null}
   * @param change for notices, the price change, or {@code null}
   * @param box the box, or {@code null} for the plain price sheet
   * @param today the date printed
   * @return the page
   */
  private static Region page(Kind kind, PriceSheet.Content content, PriceSheet.Content after,
      PriceRepository.PriceChange change, Mailbox box, LocalDate today) {
    if (box == null) {
      return PriceSheet.page(content, today);
    }
    String size = null;
    try {
      // A forwarding-only box doesn't rent the box with its number, so has no size.
      size = box.isForwardingOnly() ? null : new BoxInventoryRepository().sizeOf(box.getBoxNumber());
    } catch (SQLException e) {
      // Print it without circling a size.
    }
    return kind == Kind.NOTICES ? PriceSheet.notice(content, after, box, size, change.startsOn, today)
        : PriceSheet.page(content, box, size, today);
  }

  /**
   * Shows a problem in red.
   *
   * @param label where to show it
   * @param message the problem
   */
  private static void showError(Label label, String message) {
    label.setStyle("-fx-text-fill: red;");
    label.setText(message);
  }

  /** Not used: the window is built with static methods. */
  private PriceSheetView() {
  }

}
