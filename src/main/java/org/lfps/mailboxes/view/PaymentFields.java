package org.lfps.mailboxes.view;

import java.util.List;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import org.lfps.mailboxes.util.Money;

/**
 * The amount paid and payment method fields, shared by {@link AddBoxView} and
 * {@link RenewBoxView}. Both are optional.
 */
final class PaymentFields {

  /** The payment methods offered; others can be typed in. */
  static final List<String> METHODS = List.of("Cash", "Check", "Card", "Other");

  /** Where the amount paid is typed, such as "60" or "$60.00". */
  final TextField amountField = new TextField();

  /** How it was paid: one of {@link #METHODS}, or typed in. */
  final ComboBox<String> methodField = new ComboBox<>();

  /**
   * Builds the two fields, empty. The caller places them in its form.
   */
  PaymentFields() {
    amountField.setId("amountField");
    amountField.setPromptText("$0.00 (optional)");
    amountField.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");

    methodField.setId("paymentMethodField");
    methodField.getItems().setAll(METHODS);
    methodField.setEditable(true);
    methodField.setPromptText("optional");
    methodField.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");
  }

  /** The last price filled in, so a later one can replace it. */
  private String suggested = "";

  /**
   * Fills in the price for the rental chosen, unless someone has typed a
   * different amount, which is kept.
   *
   * @param cents the price in cents, or {@code null} if there's no price
   */
  void suggest(Long cents) {
    var current = amountField.getText().trim();
    if (!current.isEmpty() && !current.equals(suggested)) {
      return;
    }
    suggested = cents == null ? "" : Money.format(cents);
    amountField.setText(suggested);
  }

  /**
   * Returns the amount entered.
   *
   * @return the amount in cents, or {@code null} if none was entered
   * @throws IllegalArgumentException if the amount isn't valid, with a
   *     message suitable for showing to the user
   */
  Long amountCents() {
    return Money.parse(amountField.getText());
  }

  /**
   * Returns the payment method chosen or typed.
   *
   * @return the method, or an empty string if none
   */
  String method() {
    // An editable combo box only updates its value when the text is
    // committed, so read the text directly.
    var text = methodField.getEditor().getText();
    return text == null ? "" : text.trim();
  }

}
