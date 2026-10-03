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
import org.lfps.mailboxes.model.Mailbox;

/**
 * A window for printing the price sheet, either plain for new customers or
 * as renewal reminders to put in boxes, with a preview of what will print.
 * The wording is set in Settings, and the prices and sizes on the Prices
 * window.
 */
final class PriceSheetView {

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
    show(owner, List.of());
  }

  /**
   * Opens a window for printing renewal reminders, each on its own page,
   * with all the boxes ticked to start with.
   *
   * @param owner the window it belongs to
   * @param boxes the boxes that can be printed, which should have end dates
   */
  static void showReminders(Stage owner, List<Mailbox> boxes) {
    show(owner, boxes);
  }

  private static void show(Stage owner, List<Mailbox> boxes) {
    if (window != null) {
      window.close();
    }
    var reminders = !boxes.isEmpty();
    var today = LocalDate.now();

    var resultLabel = new Label();
    resultLabel.setId("printResultLabel");
    resultLabel.setWrapText(true);

    PriceSheet.Content loaded = null;
    try {
      loaded = PriceSheet.load();
    } catch (SQLException e) {
      showError(resultLabel, "Couldn't load the prices: " + e.getMessage());
    }
    var content = loaded;

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
      printBtn.setText(count == 1 ? "Print 1 Reminder" : "Print " + count + " Reminders");
      printBtn.setDisable(content == null || count == 0);
    };
    for (var box : boxes) {
      // A forwarding-only box has no box here to put a reminder in.
      var property = new SimpleBooleanProperty(!box.isForwardingOnly());
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
      var page = page(content, box, today);
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

      var explanation = new Label("Tick the boxes to print a reminder for. Each reminder prints on its own page. "
          + "Click a box to preview its reminder."
          + (boxes.stream().anyMatch(Mailbox::isForwardingOnly)
              ? " Forwarding-only boxes start unticked, as there's no box to put a reminder in." : ""));
      explanation.setWrapText(true);
      left.getChildren().addAll(explanation, list, new HBox(8, allBtn, noneBtn));
      list.getSelectionModel().select(0);
    }

    var wording = new Label("To change the shop's details or the wording, go to File → Settings. To change the "
        + "prices or what each size measures, click Prices… in Settings.");
    wording.setWrapText(true);
    left.getChildren().add(wording);
    left.setPrefWidth(300);
    left.setMinWidth(Region.USE_PREF_SIZE);

    printBtn.setOnAction(e -> {
      var pages = pages(content, boxes, ticked, today);
      Printing.print(reminders ? "Mailbox renewal reminders" : "Mailbox price sheet", pages, resultLabel,
          reminders ? (pages.size() == 1 ? "Printed 1 reminder" : "Printed " + pages.size() + " reminders")
              : "Printed");
    });

    var pdfBtn = new Button("Save as PDF…");
    pdfBtn.setId("savePdfButton");
    pdfBtn.disableProperty().bind(printBtn.disableProperty());
    pdfBtn.setOnAction(e -> Printing.savePdf(stage,
        (reminders ? "renewal-reminders-" : "price-sheet-") + today + ".pdf", pages(content, boxes, ticked, today),
        resultLabel));

    var closeBtn = new Button("Close");
    closeBtn.setId("printCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var title = new Label(reminders ? "Print Renewal Reminders" : "Print Price Sheet");
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
    stage.show();
  }

  /**
   * Builds the pages to print: a reminder for each ticked box, or the plain
   * price sheet if there are no boxes.
   */
  private static List<Region> pages(PriceSheet.Content content, List<Mailbox> boxes,
      Map<Mailbox, BooleanProperty> ticked, LocalDate today) {
    var pages = new ArrayList<Region>();
    if (boxes.isEmpty()) {
      pages.add(Printing.layOut(page(content, null, today)));
    }
    for (var box : boxes) {
      if (ticked.get(box).get()) {
        pages.add(Printing.layOut(page(content, box, today)));
      }
    }
    return pages;
  }

  /** Builds the page for a box, or the plain price sheet if there's no box. */
  private static Region page(PriceSheet.Content content, Mailbox box, LocalDate today) {
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
    return PriceSheet.page(content, box, size, today);
  }

  private static void showError(Label label, String message) {
    label.setStyle("-fx-text-fill: red;");
    label.setText(message);
  }

  private PriceSheetView() {
  }

}
