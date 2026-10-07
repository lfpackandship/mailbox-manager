package org.lfps.mailboxes.view;

import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.sql.SQLException;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.ForwardingAddress;
import org.lfps.mailboxes.model.Mailbox;

/**
 * The Print Forwarding Label window, opened with "Print Label…" on a box's
 * details: choose which forwarding address to use, if the box has more than
 * one, and whether to print on a label or on copy paper, see a preview of
 * exactly what will print, and print it. Laid out like the window for
 * printing renewal reminders.
 */
final class ForwardingLabelView {

  /** How the preview is drawn: pixels per point, for sharp text. */
  private static final double PREVIEW_DENSITY = 2;

  /** How tall the preview is shown, in pixels. */
  private static final double PREVIEW_HEIGHT = 480;

  /** The printer's edge assumed for the preview on copy paper: a quarter inch. */
  private static final double PAPER_EDGE = 0.25 * 72;

  /** The open window, or {@code null} if none is open. */
  private static Stage window;

  /**
   * Opens the window for a box, closing one already open. Does nothing for a
   * box with no forwarding address.
   *
   * @param owner the window it belongs to
   * @param mailbox the box
   */
  static void show(Stage owner, Mailbox mailbox) {
    var addresses = mailbox.getForwardingAddresses();
    if (addresses.isEmpty()) {
      return;
    }
    if (window != null) {
      window.close();
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
    var name = shopName;
    var details = shopDetails;

    var resultLabel = new Label();
    resultLabel.setId("labelResultLabel");
    resultLabel.setWrapText(true);

    var addressGroup = new ToggleGroup();
    var addressChoices = new VBox(6);
    for (var address : addresses) {
      var radio = new RadioButton(address.toString());
      radio.setUserData(address);
      radio.setToggleGroup(addressGroup);
      radio.setWrapText(true);
      addressChoices.getChildren().add(radio);
    }
    addressGroup.selectToggle(addressGroup.getToggles().get(0));

    var stockGroup = new ToggleGroup();
    var stockChoices = new VBox(6);
    for (var stock : ForwardingLabel.Stock.values()) {
      var radio = new RadioButton(stock.toString());
      radio.setId(stock == ForwardingLabel.Stock.LABEL ? "labelStockLabel" : "labelStockPaper");
      radio.setUserData(stock);
      radio.setToggleGroup(stockGroup);
      stockChoices.getChildren().add(radio);
    }
    stockGroup.selectToggle(stockGroup.getToggles().get(0));

    var previewImage = new ImageView();
    previewImage.setId("labelPreview");
    previewImage.setPreserveRatio(true);
    previewImage.setFitHeight(PREVIEW_HEIGHT);
    var preview = new StackPane(previewImage);
    preview.setPadding(new Insets(10));
    preview.setAlignment(Pos.TOP_CENTER);
    preview.setStyle("-fx-background-color: #9e9e9e;");
    // Wide enough for a sheet of paper, the wider of the two, so the window
    // doesn't change size when switching.
    preview.setMinWidth(PREVIEW_HEIGHT * 8.5 / 11 + 20);
    preview.setPrefWidth(PREVIEW_HEIGHT * 8.5 / 11 + 20);
    HBox.setHgrow(preview, Priority.ALWAYS);

    Runnable showPreview = () -> {
      var label = ForwardingLabel.of(mailbox, (ForwardingAddress) addressGroup.getSelectedToggle().getUserData(),
          name, details, (ForwardingLabel.Stock) stockGroup.getSelectedToggle().getUserData());
      previewImage.setImage(preview(label));
    };
    addressGroup.selectedToggleProperty().addListener((obs, was, now) -> {
      if (now == null) {
        addressGroup.selectToggle(was);
      } else {
        showPreview.run();
      }
    });
    stockGroup.selectedToggleProperty().addListener((obs, was, now) -> {
      if (now == null) {
        stockGroup.selectToggle(was);
      } else {
        showPreview.run();
      }
    });

    var left = new VBox(10);
    if (addresses.size() > 1) {
      left.getChildren().addAll(bold("Forward to:"), addressChoices);
    } else {
      var only = new Label(addresses.get(0).toString());
      only.setWrapText(true);
      left.getChildren().addAll(bold("Forward to:"), only);
    }
    var explanation = new Label("Print opens the Print window: choose the label printer for a label, or an "
        + "ordinary printer for copy paper. The shop's address is changed in File → Settings.");
    explanation.setWrapText(true);
    left.getChildren().addAll(bold("Print on:"), stockChoices, explanation);
    left.setPrefWidth(280);
    left.setMinWidth(280);

    var stage = new Stage();

    var printBtn = new Button("Print…");
    printBtn.setId("labelPrintButton");
    printBtn.setDefaultButton(true);
    printBtn.setOnAction(e -> {
      var label = ForwardingLabel.of(mailbox, (ForwardingAddress) addressGroup.getSelectedToggle().getUserData(),
          name, details, (ForwardingLabel.Stock) stockGroup.getSelectedToggle().getUserData());
      Printing.printLabel("Forwarding label, box " + mailbox.getBoxNumber(), label, resultLabel, "Printed");
    });

    var closeBtn = new Button("Close");
    closeBtn.setId("labelCloseButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var title = new Label("Forwarding Label for " + BoxLabels.boxAndHolder(mailbox));
    title.setStyle("-fx-font-size: 1.25em; -fx-font-weight: bold;");
    title.setWrapText(true);

    var root = new VBox(12, title, new HBox(15, left, preview), new HBox(10, printBtn, closeBtn), resultLabel);
    root.setPadding(new Insets(20));
    AppWindow.applyTextSize(root);
    showPreview.run();

    stage.initOwner(owner);
    stage.setTitle("Print Forwarding Label");
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
   * Makes a heading for a group of choices.
   *
   * @param text the heading
   * @return the label
   */
  private static Label bold(String text) {
    var label = new Label(text);
    label.setStyle("-fx-font-weight: bold;");
    return label;
  }

  /**
   * Returns the page the label prints on, for the preview: the label itself,
   * or a sheet of letter paper with a printer's usual quarter-inch edge.
   *
   * @param stock what the label is printed on
   * @return the page
   */
  static PageFormat previewPage(ForwardingLabel.Stock stock) {
    var paper = new Paper();
    if (stock == ForwardingLabel.Stock.LABEL) {
      paper.setSize(ForwardingLabel.WIDTH, ForwardingLabel.HEIGHT);
      paper.setImageableArea(0, 0, ForwardingLabel.WIDTH, ForwardingLabel.HEIGHT);
    } else {
      paper.setSize(8.5 * 72, 11 * 72);
      paper.setImageableArea(PAPER_EDGE, PAPER_EDGE, 8.5 * 72 - 2 * PAPER_EDGE, 11 * 72 - 2 * PAPER_EDGE);
    }
    var format = new PageFormat();
    format.setPaper(paper);
    return format;
  }

  /**
   * Draws a picture of the label as it will print, on its page, using the
   * same drawing as printing does.
   *
   * @param label the label
   * @return the picture
   */
  static Image preview(ForwardingLabel label) {
    var page = previewPage(label.stock);
    var width = (int) Math.round(page.getWidth() * PREVIEW_DENSITY);
    var height = (int) Math.round(page.getHeight() * PREVIEW_DENSITY);
    var picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    var g = picture.createGraphics();
    g.setColor(java.awt.Color.WHITE);
    g.fillRect(0, 0, width, height);
    g.scale(PREVIEW_DENSITY, PREVIEW_DENSITY);
    label.print(g, page, 0);
    g.dispose();

    var pixels = new int[width * height];
    picture.getRGB(0, 0, width, height, pixels, 0, width);
    var image = new WritableImage(width, height);
    image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
    return image;
  }

  /** Not used: the window is built with static methods. */
  private ForwardingLabelView() {
  }

}
