package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.BoxInventoryRepository;
import org.lfps.mailboxes.data.MailboxRepository;
import org.lfps.mailboxes.model.InventoryBox;
import org.lfps.mailboxes.model.Mailbox;
import org.lfps.mailboxes.util.BoxNumbers;

/**
 * The box inventory: every physical box, whether it's rented or empty, with
 * tools to add boxes (singly or in ranges such as 1-200), set their sizes,
 * and remove them. Once the inventory is set up, Add New Box and Edit Box only
 * accept box numbers in it, and Add New Box can choose an empty box.
 */
public class BoxInventoryView {

  /** Which boxes the table shows. */
  enum Show {
    /** Every box in the inventory. */
    ALL("All boxes"),
    /** Boxes no one is renting. */
    EMPTY("Empty boxes"),
    /** Boxes someone is renting. */
    RENTED("Rented boxes");

    /** What the choice is called in the menu. */
    private final String label;

    /**
     * Makes a choice.
     *
     * @param label what the choice is called in the menu
     */
    Show(String label) {
      this.label = label;
    }

    /**
     * Returns whether a box belongs in the table when this is chosen.
     *
     * @param row the box
     * @return {@code true} if it's shown
     */
    boolean includes(Row row) {
      return this == ALL || (row.holder == null) == (this == EMPTY);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /** A box in the inventory and whoever is renting it. */
  static final class Row {

    /** The box. */
    final InventoryBox box;

    /**
     * Whoever is renting it, or {@code null} if it's empty.
     */
    final Mailbox holder;

    /**
     * Makes a row.
     *
     * @param box the box
     * @param holder whoever is renting it, or {@code null} if it's empty
     */
    Row(InventoryBox box, Mailbox holder) {
      this.box = box;
      this.holder = holder;
    }

  }

  /**
   * Builds and displays the box inventory on the given stage.
   *
   * @param stage the window to render the inventory into
   */
  public static void show(Stage stage) {
    show(stage, Show.ALL, "");
  }

  /**
   * Builds and displays the box inventory, showing some of the boxes and a
   * message, as after adding or removing boxes.
   *
   * @param stage the window to render the inventory into
   * @param initialShow which boxes to show
   * @param message the message under the table, or an empty string
   */
  private static void show(Stage stage, Show initialShow, String message) {
    var inventory = new BoxInventoryRepository();
    var statusLabel = new Label(message);
    statusLabel.setId("inventoryStatusLabel");
    statusLabel.setWrapText(true);

    var rows = new ArrayList<Row>();
    var missing = new ArrayList<String>();
    try {
      var holders = holdersByBoxNumber();
      var boxes = inventory.findAll();
      for (var box : boxes) {
        rows.add(new Row(box, holders.remove(BoxNumbers.key(box.getBoxNumber()))));
      }
      holders.values().stream()
          .map(Mailbox::getBoxNumber)
          .map(String::trim)
          .sorted(BoxNumbers.ORDER)
          .forEach(missing::add);
    } catch (SQLException e) {
      statusLabel.setStyle("-fx-text-fill: red;");
      statusLabel.setText("Failed to load the box inventory: " + e.getMessage());
    }

    var rented = rows.stream().filter(r -> r.holder != null).count();
    var summary = new Label(rows.isEmpty()
        ? "The box inventory hasn't been set up yet. Add the boxes you have below. After that, Add New Box "
            + "can choose an empty box for you, and box numbers that aren't in the inventory are caught."
        : rows.size() + (rows.size() == 1 ? " box: " : " boxes: ") + rented + " rented, "
            + (rows.size() - rented) + " empty.");
    summary.setId("inventorySummary");
    summary.setWrapText(true);
    summary.setStyle("-fx-font-weight: bold;");

    var table = new TableView<Row>();
    table.setId("inventoryTable");
    table.setStyle("-fx-pref-height: 12em;");
    VBox.setVgrow(table, Priority.ALWAYS);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    table.setPlaceholder(new Label("No boxes"));

    var boxNumberCol = new TableColumn<Row, String>("Box Number");
    boxNumberCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().box.getBoxNumber()));
    boxNumberCol.setComparator(BoxNumbers.ORDER);

    var sizeCol = new TableColumn<Row, String>("Size");
    sizeCol.setCellValueFactory(cell -> new SimpleStringProperty(orEmpty(cell.getValue().box.getSize())));

    var statusCol = new TableColumn<Row, String>("Status");
    statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().holder == null ? "Empty" : "Rented"));

    var holderCol = new TableColumn<Row, String>("Rented By");
    holderCol.setCellValueFactory(cell -> new SimpleStringProperty(
        cell.getValue().holder == null ? "" : cell.getValue().holder.getHolderName()));

    var endsCol = new TableColumn<Row, LocalDate>("Rental Ends");
    endsCol.setCellValueFactory(cell -> new SimpleObjectProperty<>(
        cell.getValue().holder == null ? null : cell.getValue().holder.getEndDate()));
    TableOutput.showDates(endsCol);

    table.getColumns().setAll(List.of(boxNumberCol, sizeCol, statusCol, holderCol, endsCol));

    var filtered = new FilteredList<>(FXCollections.observableArrayList(rows));
    // A filtered list can't be reordered, so sort a view of it instead;
    // otherwise clicking a column header wouldn't sort.
    var sorted = new SortedList<>(filtered);
    sorted.comparatorProperty().bind(table.comparatorProperty());
    table.setItems(sorted);

    var showChoice = new ChoiceBox<Show>();
    showChoice.setId("inventoryShowChoice");
    showChoice.getItems().setAll(Show.values());
    showChoice.valueProperty().addListener((obs, oldShow, show) -> filtered.setPredicate(show::includes));
    showChoice.setValue(initialShow);

    Consumer<String> reload = text -> show(stage, showChoice.getValue(), text);
    var selected = table.getSelectionModel().getSelectedItems();

    var setSizeBtn = new Button("Set Size…");
    setSizeBtn.setId("setSizeButton");
    setSizeBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    setSizeBtn.setOnAction(e -> {
      var numbers = boxNumbers(selected);
      var size = Dialogs.askText.ask(stage, "Set Size", "Size for " + describe(numbers) + ", such as Small, "
          + "Medium, or Large. Leave it blank to clear it.",
          numbers.size() == 1 ? orEmpty(selected.get(0).box.getSize()) : "");
      if (size.isEmpty()) {
        return;
      }
      try {
        inventory.setSize(numbers, size.get());
        reload.accept("");
      } catch (SQLException ex) {
        error(statusLabel, "Failed to save the size: " + ex.getMessage());
      }
    });

    var removeBtn = new Button("Remove");
    removeBtn.setId("removeBoxesButton");
    removeBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    removeBtn.setOnAction(e -> {
      var numbers = boxNumbers(selected);
      if (!Dialogs.confirm.ask(stage, "Remove " + describe(numbers) + " from the box inventory?",
          "Anyone renting " + (numbers.size() == 1 ? "it" : "them") + " keeps their box.")) {
        return;
      }
      try {
        inventory.remove(numbers);
        reload.accept("Removed " + describe(numbers) + ".");
      } catch (SQLException ex) {
        error(statusLabel, "Failed to remove boxes: " + ex.getMessage());
      }
    });

    var addField = new TextField();
    addField.setId("addBoxesField");
    addField.setPromptText("e.g. 1-200, 12A");
    HBox.setHgrow(addField, Priority.ALWAYS);

    var sizeField = new TextField();
    sizeField.setId("addSizeField");
    sizeField.setPromptText("Size (optional)");
    sizeField.setStyle("-fx-pref-width: 10em;");

    var addBtn = new Button("Add Boxes");
    addBtn.setId("addBoxesButton");
    addBtn.setDefaultButton(true);
    addBtn.setOnAction(e -> {
      try {
        var numbers = BoxNumbers.parseList(addField.getText());
        var added = inventory.add(numbers, sizeField.getText());
        var skipped = numbers.size() - added;
        reload.accept("Added " + added + (added == 1 ? " box" : " boxes")
            + (skipped == 0 ? "." : "; " + skipped + " already in the inventory " + (skipped == 1 ? "was" : "were")
                + " left as " + (skipped == 1 ? "it was." : "they were.")));
      } catch (IllegalArgumentException ex) {
        error(statusLabel, ex.getMessage());
      } catch (SQLException ex) {
        error(statusLabel, "Failed to add boxes: " + ex.getMessage());
      }
    });

    var backBtn = new Button("Back");
    backBtn.setOnAction(e -> MainMenuView.show(stage));

    var pricesBtn = new Button("Prices…");
    pricesBtn.setId("inventoryPricesButton");
    pricesBtn.setOnAction(e -> PricesView.show(stage));

    var addLabel = new Label("Add boxes (numbers or ranges, separated by commas):");

    var layout = new VBox(10, backBtn, summary);
    var sizeCounts = sizeCounts(rows);
    if (!sizeCounts.isEmpty()) {
      var sizeCountsLabel = new Label(sizeCounts);
      sizeCountsLabel.setId("inventorySizeCounts");
      sizeCountsLabel.setWrapText(true);
      layout.getChildren().add(sizeCountsLabel);
    }
    if (!missing.isEmpty()) {
      var missingLabel = new Label(missing.size() == 1
          ? "Box " + missing.get(0) + " is rented but isn't in the inventory."
          : missing.size() + " rented boxes aren't in the inventory: " + describeList(missing) + ".");
      missingLabel.setId("missingBoxesLabel");
      missingLabel.setWrapText(true);
      var addMissingBtn = new Button(missing.size() == 1 ? "Add It" : "Add Them");
      addMissingBtn.setId("addMissingButton");
      addMissingBtn.setOnAction(e -> {
        try {
          var added = inventory.add(missing, null);
          reload.accept("Added " + added + (added == 1 ? " box." : " boxes."));
        } catch (SQLException ex) {
          error(statusLabel, "Failed to add boxes: " + ex.getMessage());
        }
      });
      var missingRow = new HBox(10, missingLabel, addMissingBtn);
      HBox.setHgrow(missingLabel, Priority.ALWAYS);
      layout.getChildren().add(missingRow);
    }
    layout.getChildren().addAll(
        showChoice,
        table,
        new HBox(10, setSizeBtn, removeBtn, pricesBtn),
        addLabel,
        new HBox(10, addField, sizeField, addBtn),
        statusLabel);
    layout.setPadding(new Insets(20));

    AppWindow.show(stage, layout);
  }

  /**
   * Opens a window listing the empty boxes in the inventory to choose from.
   * If the inventory hasn't been set up, says so instead.
   *
   * @param owner the main window
   * @param onChoose called with the chosen box number
   */
  static void chooseEmptyBox(Stage owner, Consumer<String> onChoose) {
    List<InventoryBox> empty;
    try {
      var inventory = new BoxInventoryRepository();
      if (inventory.isEmpty()) {
        var info = new Alert(AlertType.INFORMATION,
            "To choose from the empty boxes, first add your boxes on the Box Inventory screen.");
        info.initOwner(owner);
        info.setHeaderText("The box inventory hasn't been set up");
        AppWindow.applyTextSize(info);
        info.show();
        return;
      }
      var holders = holdersByBoxNumber();
      empty = inventory.findAll().stream()
          .filter(box -> !holders.containsKey(BoxNumbers.key(box.getBoxNumber())))
          .collect(Collectors.toList());
    } catch (SQLException e) {
      var failed = new Alert(AlertType.ERROR, "Failed to load the box inventory: " + e.getMessage());
      failed.initOwner(owner);
      AppWindow.applyTextSize(failed);
      failed.show();
      return;
    }

    var list = new ListView<InventoryBox>(FXCollections.observableArrayList(empty));
    list.setId("emptyBoxList");
    list.setPlaceholder(new Label("Every box is rented."));
    list.setCellFactory(listView -> new ListCell<>() {
      @Override
      protected void updateItem(InventoryBox box, boolean isEmpty) {
        super.updateItem(box, isEmpty);
        setText(box == null ? null
            : "Box " + box.getBoxNumber() + (box.getSize() == null ? "" : " – " + box.getSize()));
      }
    });

    var stage = new Stage();
    Runnable choose = () -> {
      var box = list.getSelectionModel().getSelectedItem();
      if (box != null) {
        stage.close();
        onChoose.accept(box.getBoxNumber());
      }
    };
    list.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
        choose.run();
      }
    });
    list.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        choose.run();
      }
    });

    var chooseBtn = new Button("Choose");
    chooseBtn.setId("chooseEmptyBoxButton");
    chooseBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
    chooseBtn.setOnAction(e -> choose.run());

    var cancelBtn = new Button("Cancel");
    cancelBtn.setCancelButton(true);
    cancelBtn.setOnAction(e -> stage.close());

    var content = new VBox(10, new Label(empty.size() + (empty.size() == 1 ? " empty box:" : " empty boxes:")),
        list, new HBox(10, chooseBtn, cancelBtn));
    content.setPadding(new Insets(15));
    AppWindow.applyTextSize(content);

    stage.initOwner(owner);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle("Choose an Empty Box");
    stage.setScene(new Scene(content));
    AppWindow.showWithinScreen(stage);
  }

  /**
   * Returns the open boxes rented here keyed by {@link BoxNumbers#key(String)}.
   *
   * @return each holder, keyed by box number
   * @throws SQLException if the boxes can't be read
   */
  private static Map<String, Mailbox> holdersByBoxNumber() throws SQLException {
    var holders = new HashMap<String, Mailbox>();
    for (var mailbox : new MailboxRepository().findOpen()) {
      // Forwarding-only boxes don't rent the box whose number they use.
      if (!mailbox.isForwardingOnly()) {
        holders.putIfAbsent(BoxNumbers.key(mailbox.getBoxNumber()), mailbox);
      }
    }
    return holders;
  }

  /**
   * Describes how many boxes of each size are empty, such as "Large: 2 of 5
   * empty · Small: 30 of 35 empty · No size: 1 of 1 empty", with sizes in
   * alphabetical order, ignoring letter case.
   *
   * @return the description, or an empty string if no box has a size
   * @param rows every box in the inventory
   */
  static String sizeCounts(List<Row> rows) {
    if (rows.stream().allMatch(row -> row.box.getSize() == null)) {
      return "";
    }
    // Sizes that differ only in letter case count together.
    var counts = new TreeMap<String, SizeCount>();
    for (var row : rows) {
      var size = row.box.getSize();
      // "~" sorts the boxes with no size after the named sizes.
      var count = counts.computeIfAbsent(size == null ? "~" : size.toLowerCase(),
          key -> new SizeCount(size == null ? "No size" : size));
      count.total++;
      if (row.holder == null) {
        count.empty++;
      }
    }
    return counts.values().stream()
        .map(count -> count.label + ": " + count.empty + " of " + count.total + " empty")
        .collect(Collectors.joining(" · "));
  }

  /** How many boxes of a size there are, and how many of them are empty. */
  private static final class SizeCount {

    /** The size, as shown, or "No size". */
    final String label;

    /** How many boxes of the size are empty. */
    int empty;

    /** How many boxes of the size there are. */
    int total;

    /**
     * Makes a count of none.
     *
     * @param label the size, as shown
     */
    SizeCount(String label) {
      this.label = label;
    }

  }

  /**
   * Returns the boxes' numbers.
   *
   * @param rows the boxes
   * @return their numbers, in the same order
   */
  private static List<String> boxNumbers(List<Row> rows) {
    return rows.stream().map(row -> row.box.getBoxNumber()).collect(Collectors.toList());
  }

  /**
   * Describes some boxes for a message, such as "box 12" or "3 boxes".
   *
   * @param boxNumbers the boxes' numbers
   * @return the description
   */
  static String describe(List<String> boxNumbers) {
    return boxNumbers.size() == 1 ? "box " + boxNumbers.get(0) : boxNumbers.size() + " boxes";
  }

  /**
   * Lists box numbers for a message, shortening a long list, such as "1, 2, 3,
   * 4, 5, and 12 more".
   *
   * @param boxNumbers the boxes' numbers
   * @return the list
   */
  static String describeList(List<String> boxNumbers) {
    var shown = 5;
    if (boxNumbers.size() <= shown + 1) {
      return String.join(", ", boxNumbers);
    }
    return String.join(", ", boxNumbers.subList(0, shown)) + ", and " + (boxNumbers.size() - shown) + " more";
  }

  /**
   * Shows a problem in red.
   *
   * @param label where to show it
   * @param message the problem
   */
  private static void error(Label label, String message) {
    label.setStyle("-fx-text-fill: red;");
    label.setText(message);
  }

  /**
   * Returns text, or an empty string in place of {@code null}.
   *
   * @param value the text, or {@code null}
   * @return the text, never {@code null}
   */
  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  /** Not used: the screen is built with static methods. */
  private BoxInventoryView() {
  }

}
