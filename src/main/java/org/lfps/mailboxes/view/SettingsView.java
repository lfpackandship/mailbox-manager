package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.util.LinkedHashMap;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.lfps.mailboxes.data.Database;
import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.util.SystemInfo;

/**
 * A separate window that lets the user adjust app settings, grouped into
 * sections. To add a setting, declare it in {@link Setting}, add a field for
 * it to a section here, and read it with {@link SettingsRepository} where
 * it's used.
 */
public class SettingsView {

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

    var repository = new SettingsRepository();
    var resultLabel = new Label();
    resultLabel.setId("resultLabel");

    var renewalWindowField = new TextField();
    renewalWindowField.setId("renewalWindowField");
    var backupsToKeepField = new TextField();
    backupsToKeepField.setId("backupsToKeepField");

    // Each editable setting, paired with its field, the label used in error
    // messages, and its allowed range.
    var numberFields = new LinkedHashMap<Setting, NumberField>();
    numberFields.put(Setting.RENEWAL_WINDOW_DAYS,
        new NumberField(renewalWindowField, "Renewal window", 1, 365));
    numberFields.put(Setting.BACKUPS_TO_KEEP,
        new NumberField(backupsToKeepField, "Backups to keep", 1, 365));

    try {
      for (var entry : numberFields.entrySet()) {
        entry.getValue().field.setText(String.valueOf(repository.getInt(entry.getKey())));
      }
    } catch (SQLException e) {
      resultLabel.setStyle("-fx-text-fill: red;");
      resultLabel.setText("Failed to load settings: " + e.getMessage());
    }

    for (var numberField : numberFields.values()) {
      numberField.field.setPrefWidth(80);
    }

    var dataFolderField = new TextField(Database.dataDir().toString());
    dataFolderField.setEditable(false);
    dataFolderField.setPrefWidth(400);

    var renewals = section("Renewals",
        row("Show boxes due within (days):", renewalWindowField));

    var backups = section("Backups",
        row("Daily backups to keep:", backupsToKeepField),
        row("Data folder:", dataFolderField));

    var about = section("About",
        row("Java version:", new Label(SystemInfo.javaVersion())),
        row("JavaFX version:", new Label(SystemInfo.javafxVersion())));

    var saveBtn = new Button("Save");
    saveBtn.setId("saveButton");
    saveBtn.setDefaultButton(true);
    saveBtn.setOnAction(e -> {
      var errors = new StringBuilder();
      var values = new LinkedHashMap<Setting, Integer>();

      for (var entry : numberFields.entrySet()) {
        var numberField = entry.getValue();
        var value = parseInRange(numberField.field.getText(), numberField.min, numberField.max);
        if (value != null) {
          values.put(entry.getKey(), value);
        } else {
          errors.append(numberField.label + " must be a whole number from "
              + numberField.min + " to " + numberField.max + ".\n");
        }
      }

      if (errors.length() > 0) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText(errors.toString().trim());
        return;
      }

      try {
        for (var entry : values.entrySet()) {
          repository.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        resultLabel.setStyle("-fx-text-fill: green;");
        resultLabel.setText("Saved");
      } catch (SQLException ex) {
        resultLabel.setStyle("-fx-text-fill: red;");
        resultLabel.setText("Failed to save: " + ex.getMessage());
      }
    });

    var stage = new Stage();

    var closeBtn = new Button("Close");
    closeBtn.setId("closeButton");
    closeBtn.setCancelButton(true);
    closeBtn.setOnAction(e -> stage.close());

    var content = new VBox(15, renewals, backups, about,
        new HBox(10, saveBtn, closeBtn), resultLabel);
    content.setPadding(new Insets(20));

    var scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);

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

  private static VBox section(String title, Node[]... rows) {
    var header = new Label(title);
    header.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    for (var i = 0; i < rows.length; i++) {
      grid.addRow(i, rows[i]);
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
