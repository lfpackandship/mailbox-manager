package org.lfps.mailboxes.view;

import java.io.File;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.Function;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.drive.DriveBackup;
import org.lfps.mailboxes.util.Errors;
import org.lfps.mailboxes.util.Money;
import org.lfps.mailboxes.util.RentalLengths;
import org.lfps.mailboxes.util.SystemInfo;

/**
 * A separate window that lets the user adjust app settings, grouped into
 * sections, and back up or restore their data. To add a setting, declare it
 * in {@link Setting}, add a field for it to a section here (loading it in
 * {@code show} and saving it in the Save button's handler), and read it with
 * {@link SettingsRepository} where it's used.
 */
public class SettingsView {

  /**
   * Asks the user to pick a folder. Replaceable so tests can answer without
   * a real folder dialog.
   */
  static Function<Window, File> chooseFolder = owner -> new DirectoryChooser().showDialog(owner);

  /** The open settings window, or {@code null} if none is open. */
  private static Stage window;

  /** The Google Drive part of the open settings window. */
  private static GoogleDriveRow driveRow;

  /**
   * Opens the settings window and starts connecting Google Drive, for when
   * the app has been signed out of it.
   *
   * @param owner the main window
   */
  public static void showAndConnectDrive(Stage owner) {
    show(owner);
    driveRow.connect();
  }

  /**
   * Opens the settings window on top of the main window, or brings it to the
   * front if it's already open. The main window stays usable while it's open.
   *
   * @param owner the main window
   */
  public static void show(Stage owner) {
    if (window != null) {
      window.toFront();
      window.requestFocus();
      return;
    }

    var stage = new Stage();
    var repository = new SettingsRepository();
    var resultLabel = new Label();
    resultLabel.setId("resultLabel");
    resultLabel.setWrapText(true);

    var textSizeChoice = new ChoiceBox<TextSize>();
    textSizeChoice.setId("textSizeChoice");
    textSizeChoice.getItems().setAll(TextSize.values());

    var rentalLengthsField = new TextField();
    rentalLengthsField.setId("rentalLengthsField");
    rentalLengthsField.setStyle("-fx-pref-width: 12em;");

    var weekStartChoice = new ChoiceBox<DayOfWeek>();
    weekStartChoice.setId("weekStartChoice");
    weekStartChoice.getItems().setAll(DayOfWeek.SUNDAY, DayOfWeek.MONDAY);
    weekStartChoice.setConverter(new StringConverter<>() {
      @Override
      public String toString(DayOfWeek day) {
        return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.getDefault());
      }

      @Override
      public DayOfWeek fromString(String text) {
        throw new UnsupportedOperationException();
      }
    });

    var renewalWindowField = new TextField();
    renewalWindowField.setId("renewalWindowField");
    var backupsToKeepField = new TextField();
    backupsToKeepField.setId("backupsToKeepField");

    // Each whole-number setting, paired with its field, the label used in
    // error messages, and its allowed range.
    var numberFields = new LinkedHashMap<Setting, NumberField>();
    numberFields.put(Setting.RENEWAL_WINDOW_DAYS,
        new NumberField(renewalWindowField, "Renewal window", 1, 365));
    numberFields.put(Setting.BACKUPS_TO_KEEP,
        new NumberField(backupsToKeepField, "Backups to keep", 1, 365));
    for (var numberField : numberFields.values()) {
      numberField.field.setStyle("-fx-pref-width: 6em;");
    }

    var keyDepositField = new TextField();
    keyDepositField.setId("keyDepositField");
    keyDepositField.setPromptText("none");
    keyDepositField.setStyle("-fx-pref-width: 6em;");

    // The price sheet's wording, each paired with its setting.
    var shopNameField = new TextField();
    shopNameField.setId("shopNameField");
    shopNameField.setStyle("-fx-pref-width: 24em;");
    var sheetFields = new LinkedHashMap<Setting, TextInputControl>();
    sheetFields.put(Setting.SHOP_NAME, shopNameField);
    sheetFields.put(Setting.SHOP_DETAILS, textArea("shopDetailsField", 3));
    sheetFields.put(Setting.PRICE_SHEET_INTRO, textArea("priceSheetIntroField", 7));
    sheetFields.put(Setting.PRICE_SHEET_NOTE, textArea("priceSheetNoteField", 2));
    sheetFields.put(Setting.REMINDER_MESSAGE, textArea("reminderMessageField", 3));

    var secondFolderField = new TextField();
    secondFolderField.setId("secondBackupFolderField");
    secondFolderField.setEditable(false);
    secondFolderField.setPromptText("None");
    secondFolderField.setStyle("-fx-pref-width: 24em;");

    try {
      textSizeChoice.setValue(TextSize.fromName(repository.get(Setting.TEXT_SIZE)));
      rentalLengthsField.setText(currentRentalLengths(repository));
      weekStartChoice.setValue(DayOfWeek.MONDAY.name().equals(repository.get(Setting.WEEK_START))
          ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY);
      for (var entry : numberFields.entrySet()) {
        entry.getValue().field.setText(String.valueOf(repository.getInt(entry.getKey())));
      }
      secondFolderField.setText(repository.get(Setting.SECOND_BACKUP_FOLDER));
      keyDepositField.setText(currentKeyDeposit(repository));
      for (var entry : sheetFields.entrySet()) {
        entry.getValue().setText(repository.get(entry.getKey()));
      }
    } catch (SQLException e) {
      showError(resultLabel, "Failed to load settings: " + e.getMessage());
    }

    var chooseSecondFolderBtn = new Button("Choose…");
    chooseSecondFolderBtn.setId("chooseSecondFolderButton");
    chooseSecondFolderBtn.setOnAction(e -> {
      var folder = chooseFolder.apply(stage);
      if (folder != null) {
        secondFolderField.setText(folder.getAbsolutePath());
      }
    });

    var clearSecondFolderBtn = new Button("Don't Copy");
    clearSecondFolderBtn.setId("clearSecondFolderButton");
    clearSecondFolderBtn.setOnAction(e -> secondFolderField.clear());

    var backUpNowBtn = new Button("Back Up Now…");
    backUpNowBtn.setId("backUpNowButton");
    backUpNowBtn.setOnAction(e -> {
      try {
        var message = backUpNow(stage);
        if (message != null) {
          showSuccess(resultLabel, message);
        }
      } catch (IllegalStateException ex) {
        showError(resultLabel, ex.getMessage());
      }
    });

    var restoreBtn = new Button("Restore…");
    restoreBtn.setId("restoreButton");
    restoreBtn.setOnAction(e -> RestoreView.show(stage, owner));

    var drive = new GoogleDriveRow(stage);

    var dataFolderField = new TextField(Database.dataDir().toString());
    dataFolderField.setEditable(false);
    dataFolderField.setStyle("-fx-pref-width: 24em;");

    var openDataFolderBtn = new Button("Open");
    openDataFolderBtn.setId("openDataFolderButton");
    openDataFolderBtn.setDisable(!AppWindow.canOpenFolders());
    openDataFolderBtn.setOnAction(e -> AppWindow.openFolder(Database.dataDir()));

    var appearance = section("Appearance",
        row("Text size:", textSizeChoice));

    var pricesBtn = new Button("Prices…");
    pricesBtn.setId("pricesButton");
    pricesBtn.setOnAction(e -> PricesView.show(owner));

    var boxes = section("Boxes",
        row("Rental lengths (months):", rentalLengthsField),
        row("Prices for each size:", pricesBtn),
        row("Key deposit per key:", keyDepositField));

    var printSheetBtn = new Button("Print Price Sheet…");
    printSheetBtn.setId("settingsPrintPriceSheetButton");
    printSheetBtn.setOnAction(e -> PriceSheetView.showPriceSheet(owner));

    var sheetExplanation = new Label("Printed at the top of the price sheet and renewal reminders. In the text "
        + "above the prices, start a line with - to make it a bullet point. Save before printing to use your changes.");
    sheetExplanation.setWrapText(true);
    sheetExplanation.setStyle("-fx-max-width: 32em;");

    var priceSheet = section("Price Sheet and Reminders",
        row("", sheetExplanation),
        row("Shop name:", shopNameField),
        row("Address and phone:", sheetFields.get(Setting.SHOP_DETAILS)),
        row("Text above the prices:", sheetFields.get(Setting.PRICE_SHEET_INTRO)),
        row("Text below the prices:", sheetFields.get(Setting.PRICE_SHEET_NOTE)),
        row("Reminder message:", sheetFields.get(Setting.REMINDER_MESSAGE)),
        row("", printSheetBtn));

    var calendar = section("Calendar",
        row("Week starts on:", weekStartChoice));

    var renewals = section("Renewals",
        row("Show boxes due within (days):", renewalWindowField));

    var backups = section("Backups",
        row("Daily backups to keep:", backupsToKeepField),
        row("Also copy backups to:", new HBox(8, secondFolderField, chooseSecondFolderBtn, clearSecondFolderBtn)),
        row("Google Drive:", drive),
        row("", new HBox(8, backUpNowBtn, restoreBtn)),
        row("Data folder:", new HBox(8, dataFolderField, openDataFolderBtn)));

    var about = section("About",
        row("Java version:", new Label(SystemInfo.javaVersion())),
        row("JavaFX version:", new Label(SystemInfo.javafxVersion())));

    var saveBtn = new Button("Save");
    saveBtn.setId("saveButton");
    saveBtn.setDefaultButton(true);
    saveBtn.setOnAction(e -> {
      var errors = new StringBuilder();
      var numbers = new LinkedHashMap<Setting, Integer>();

      for (var entry : numberFields.entrySet()) {
        var numberField = entry.getValue();
        var value = parseInRange(numberField.field.getText(), numberField.min, numberField.max);
        if (value != null) {
          numbers.put(entry.getKey(), value);
        } else {
          errors.append(numberField.label + " must be a whole number from "
              + numberField.min + " to " + numberField.max + ".\n");
        }
      }

      String rentalLengths = null;
      try {
        rentalLengths = RentalLengths.format(RentalLengths.parse(rentalLengthsField.getText()));
      } catch (IllegalArgumentException ex) {
        errors.append(ex.getMessage()).append('\n');
      }

      String keyDeposit = "";
      try {
        var cents = Money.parse(keyDepositField.getText());
        keyDeposit = cents == null ? "" : Money.format(cents);
      } catch (IllegalArgumentException ex) {
        errors.append("Key deposit per key must be an amount in dollars and cents, like 10 or 10.00.\n");
      }

      if (errors.length() > 0) {
        showError(resultLabel, errors.toString().trim());
        return;
      }

      try {
        for (var entry : numbers.entrySet()) {
          repository.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        repository.put(Setting.TEXT_SIZE, textSizeChoice.getValue().name());
        repository.put(Setting.RENTAL_LENGTHS, rentalLengths);
        repository.put(Setting.WEEK_START, weekStartChoice.getValue().name());
        repository.put(Setting.SECOND_BACKUP_FOLDER, secondFolderField.getText());
        repository.put(Setting.KEY_DEPOSIT, keyDeposit);
        for (var entry : sheetFields.entrySet()) {
          repository.put(entry.getKey(), entry.getValue().getText().strip());
        }
      } catch (SQLException ex) {
        showError(resultLabel, "Failed to save: " + ex.getMessage());
        return;
      }

      rentalLengthsField.setText(rentalLengths);
      keyDepositField.setText(keyDeposit);
      AppWindow.applyTextSizeToOpenWindows();

      // Copy today's backup to a newly chosen folder straight away, so a
      // folder that can't be written to is noticed now rather than tomorrow.
      try {
        Database.copyToSecondBackupFolder();
        showSuccess(resultLabel, "Saved");
      } catch (RuntimeException ex) {
        showError(resultLabel, "Saved, but today's backup couldn't be copied: " + Errors.rootMessage(ex));
      }
    });

    var closeBtn = new Button("Close");
    closeBtn.setId("closeButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var content = new VBox(15, appearance, boxes, priceSheet, calendar, renewals, backups, about,
        new HBox(10, saveBtn, closeBtn), resultLabel);
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    AppWindow.applyTextSize(scrollPane);

    stage.initOwner(owner);
    stage.setTitle("Settings");
    stage.setScene(new Scene(scrollPane));
    stage.setOnHidden(e -> {
      window = null;
      driveRow = null;
      DriveBackup.cancelConnect();
    });
    window = stage;
    driveRow = drive;
    stage.show();
  }

  /**
   * Asks for a folder, such as a USB drive, and saves a backup there.
   *
   * @param owner the window the folder dialog belongs to
   * @return a message saying where it was saved, or {@code null} if cancelled
   * @throws IllegalStateException if it couldn't be saved, with a message
   *     suitable for showing to the user
   */
  static String backUpNow(Window owner) {
    var folder = chooseFolder.apply(owner);
    if (folder == null) {
      return null;
    }
    try {
      return "Saved a backup to " + Database.exportBackup(folder.toPath());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Couldn't save the backup: " + Errors.rootMessage(e), e);
    }
  }

  /**
   * Closes the settings window if it's open, for example after a restore
   * replaces the settings it shows.
   */
  static void close() {
    if (window != null) {
      window.close();
    }
  }

  /**
   * Parses a whole number typed into a settings field, ignoring surrounding
   * whitespace.
   *
   * @return the number, or {@code null} if the text is not a whole number
   *     from {@code min} to {@code max} inclusive
   */
  static Integer parseInRange(String text, int min, int max) {
    if (text == null) {
      return null;
    }
    try {
      var value = Integer.parseInt(text.trim());
      return value >= min && value <= max ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String currentRentalLengths(SettingsRepository repository) throws SQLException {
    try {
      return RentalLengths.format(RentalLengths.parse(repository.get(Setting.RENTAL_LENGTHS)));
    } catch (IllegalArgumentException e) {
      return RentalLengths.format(RentalLengths.parse(Setting.RENTAL_LENGTHS.defaultValue()));
    }
  }

  private static String currentKeyDeposit(SettingsRepository repository) throws SQLException {
    try {
      var cents = Money.parse(repository.get(Setting.KEY_DEPOSIT));
      return cents == null ? "" : Money.format(cents);
    } catch (IllegalArgumentException e) {
      return "";
    }
  }

  private static TextArea textArea(String id, int rows) {
    var area = new TextArea();
    area.setId(id);
    area.setPrefRowCount(rows);
    area.setWrapText(true);
    area.setStyle("-fx-pref-width: 24em;");
    return area;
  }

  private static void showError(Label label, String message) {
    label.setStyle("-fx-text-fill: red;");
    label.setText(message);
  }

  private static void showSuccess(Label label, String message) {
    label.setStyle("-fx-text-fill: green;");
    label.setText(message);
  }

  private static VBox section(String title, Node[]... rows) {
    var header = new Label(title);
    header.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    for (var i = 0; i < rows.length; i++) {
      grid.addRow(i, rows[i]);
      // Keep each control at its own width rather than the widest row's.
      GridPane.setFillWidth(rows[i][1], false);
    }

    return new VBox(8, header, grid);
  }

  private static Node[] row(String label, Node control) {
    var text = new Label(label);
    // Never shorten a label to "…" to make room for a wide control.
    text.setMinWidth(Region.USE_PREF_SIZE);
    return new Node[] { text, control };
  }

  private static final class NumberField {
    private final TextField field;
    private final String label;
    private final int min;
    private final int max;

    NumberField(TextField field, String label, int min, int max) {
      this.field = field;
      this.label = label;
      this.min = min;
      this.max = max;
    }
  }

  private SettingsView() {
  }

}
