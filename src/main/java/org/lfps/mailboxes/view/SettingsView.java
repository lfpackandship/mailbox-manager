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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.util.Errors;
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
      var folder = chooseFolder.apply(stage);
      if (folder == null) {
        return;
      }
      try {
        var saved = Database.exportBackup(folder.toPath());
        showSuccess(resultLabel, "Saved a backup to " + saved);
      } catch (RuntimeException ex) {
        showError(resultLabel, "Couldn't save the backup: " + Errors.rootMessage(ex));
      }
    });

    var restoreBtn = new Button("Restore…");
    restoreBtn.setId("restoreButton");
    restoreBtn.setOnAction(e -> RestoreView.show(stage, owner));

    var dataFolderField = new TextField(Database.dataDir().toString());
    dataFolderField.setEditable(false);
    dataFolderField.setStyle("-fx-pref-width: 24em;");

    var openDataFolderBtn = new Button("Open");
    openDataFolderBtn.setId("openDataFolderButton");
    openDataFolderBtn.setDisable(!AppWindow.canOpenFolders());
    openDataFolderBtn.setOnAction(e -> AppWindow.openFolder(Database.dataDir()));

    var appearance = section("Appearance",
        row("Text size:", textSizeChoice));

    var boxes = section("Boxes",
        row("Rental lengths (months):", rentalLengthsField));

    var calendar = section("Calendar",
        row("Week starts on:", weekStartChoice));

    var renewals = section("Renewals",
        row("Show boxes due within (days):", renewalWindowField));

    var backups = section("Backups",
        row("Daily backups to keep:", backupsToKeepField),
        row("Also copy backups to:", new HBox(8, secondFolderField, chooseSecondFolderBtn, clearSecondFolderBtn)),
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
      } catch (SQLException ex) {
        showError(resultLabel, "Failed to save: " + ex.getMessage());
        return;
      }

      rentalLengthsField.setText(rentalLengths);
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

    var content = new VBox(15, appearance, boxes, calendar, renewals, backups, about,
        new HBox(10, saveBtn, closeBtn), resultLabel);
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    AppWindow.applyTextSize(scrollPane);

    stage.initOwner(owner);
    stage.setTitle("Settings");
    stage.setScene(new Scene(scrollPane));
    stage.setOnHidden(e -> window = null);
    window = stage;
    stage.show();
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
    return new Node[] { new Label(label), control };
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
