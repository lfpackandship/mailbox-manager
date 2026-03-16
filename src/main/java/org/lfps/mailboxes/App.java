package org.lfps.mailboxes;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX App
 */
public class App extends Application {

  @Override
  public void start(Stage stage) {
    var javaVersion = SystemInfo.javaVersion();
    var javafxVersion = SystemInfo.javafxVersion();

    var firstNameField = new TextField();
    firstNameField.setPromptText("John");

    var lastNameField = new TextField();
    lastNameField.setPromptText("Doe");

    var boxNumber = new TextField();
    boxNumber.setPromptText("310");

    var emailField = new TextField();
    emailField.setPromptText("Enter your email");

    var submitBtn = new Button("Submit");
    var resultLabel = new Label();

    submitBtn.setOnAction(e -> {
      resultLabel.setText("Submitted");
    });

    var layout = new VBox(10,
        new Label("First Name:"), firstNameField,
        new Label("Last Name:"), lastNameField,
        new Label("Box Number:"), boxNumber,
        new Label("Email:"), emailField,
        submitBtn,
        resultLabel);
    layout.setPadding(new Insets(20));

    var scene = new Scene(layout, 640, 480);
    stage.setScene(scene);
    stage.show();
  }

  public static void main(String[] args) {
    launch();
  }

}
