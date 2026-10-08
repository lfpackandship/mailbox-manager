package org.lfps.mailboxes.view;

import java.sql.SQLException;
import java.util.List;

import javafx.scene.control.TextField;

import org.lfps.mailboxes.data.Setting;
import org.lfps.mailboxes.data.SettingsRepository;
import org.lfps.mailboxes.model.DepositOutcome;
import org.lfps.mailboxes.util.Money;

/**
 * The number of keys given out and the refundable key deposit paid for them,
 * shared by {@link AddBoxView} and {@link EditBoxView}. Both are optional.
 * Entering the number of keys fills in the deposit, at the amount per key set
 * in Settings.
 */
final class KeyFields {

  /**
   * The choices for what happened to a closed box's key deposit, asked when
   * closing a box and on Edit Box: given back, kept, or not recorded yet, in
   * that order (see {@link #outcomeAt} and {@link #indexOf}).
   */
  static final List<String> OUTCOME_CHOICES = List.of(
      DepositOutcome.RETURNED.getLabel(),
      DepositOutcome.KEPT.getLabel() + " (for example, the keys weren't returned)",
      "Not recorded yet");

  /** The index in {@link #OUTCOME_CHOICES} of "Not recorded yet". */
  static final int NOT_RECORDED = 2;

  /** The most keys a box can have. */
  static final int MAX_KEYS = 20;

  /** Where the number of keys is typed. */
  final TextField countField = new TextField();

  /** Where the deposit is typed, such as "20" or "$20.00". */
  final TextField depositField = new TextField();

  /** The last deposit filled in, so a later one can replace it. */
  private String suggested = "";

  /**
   * Builds the two fields, filled in with what's recorded. The caller places
   * them in its form.
   *
   * @param count the number of keys recorded, or {@code null} if none
   * @param depositCents the deposit recorded, or {@code null} if none
   */
  KeyFields(Integer count, Long depositCents) {
    countField.setId("keyCountField");
    countField.setPromptText("optional");
    countField.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");
    depositField.setId("keyDepositAmountField");
    depositField.setPromptText("$0.00 (optional)");
    depositField.setStyle("-fx-pref-width: 15em; -fx-max-width: 15em;");

    countField.setText(count == null ? "" : String.valueOf(count));
    depositField.setText(depositCents == null ? "" : Money.format(depositCents));
    // A recorded deposit that matches the number of keys follows it if the
    // number changes; one entered by hand is kept.
    if (depositField.getText().equals(depositFor(countField.getText()))) {
      suggested = depositField.getText();
    }

    countField.textProperty().addListener((obs, oldCount, newCount) -> {
      var current = depositField.getText().trim();
      if (current.isEmpty() || current.equals(suggested)) {
        suggested = depositFor(newCount);
        depositField.setText(suggested);
      }
    });
  }

  /**
   * Returns the number of keys entered.
   *
   * @return the number, or {@code null} if none was entered
   * @throws IllegalArgumentException if it isn't a whole number from 0 to
   *     {@link #MAX_KEYS}, with a message suitable for showing to the user
   */
  Integer count() {
    var text = countField.getText().trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      var count = Integer.parseInt(text);
      if (count >= 0 && count <= MAX_KEYS) {
        return count;
      }
    } catch (NumberFormatException e) {
      // Explained below.
    }
    throw new IllegalArgumentException("Keys must be a whole number from 0 to " + MAX_KEYS + ".");
  }

  /**
   * Returns the deposit entered.
   *
   * @return the deposit in cents, or {@code null} if none was entered
   * @throws IllegalArgumentException if it isn't an amount, with a message
   *     suitable for showing to the user
   */
  Long depositCents() {
    try {
      return Money.parse(depositField.getText());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Enter the key deposit in dollars and cents, like 20 or 20.00.");
    }
  }

  /**
   * Returns the deposit for a number of keys at the amount per key set in
   * Settings, formatted, or an empty string if there's no deposit for it.
   *
   * @param countText the number of keys, as typed
   * @return the deposit, such as "$20.00", or an empty string
   */
  private static String depositFor(String countText) {
    try {
      var count = Integer.parseInt(countText.trim());
      var perKey = Money.parse(new SettingsRepository().get(Setting.KEY_DEPOSIT));
      if (count < 0 || count > MAX_KEYS || perKey == null) {
        return "";
      }
      return Money.format(count * perKey);
    } catch (IllegalArgumentException | SQLException notANumberOrBadSetting) {
      return "";
    }
  }

  /**
   * Returns the outcome for a choice in {@link #OUTCOME_CHOICES}.
   *
   * @param index the index of the choice
   * @return the outcome, or {@code null} for "Not recorded yet"
   */
  static DepositOutcome outcomeAt(int index) {
    if (index == 0) {
      return DepositOutcome.RETURNED;
    }
    return index == 1 ? DepositOutcome.KEPT : null;
  }

  /**
   * Returns where an outcome is in {@link #OUTCOME_CHOICES}.
   *
   * @param outcome the outcome, or {@code null} if not recorded
   * @return its index
   */
  static int indexOf(DepositOutcome outcome) {
    if (outcome == null) {
      return NOT_RECORDED;
    }
    return outcome == DepositOutcome.RETURNED ? 0 : 1;
  }

  /**
   * Describes the keys and deposit recorded for a box, and what happened to
   * the deposit if the box is closed, such as "2 keys, $20.00 deposit, given
   * back".
   *
   * @param count the number of keys, or {@code null} if not recorded
   * @param depositCents the deposit, or {@code null} if not recorded
   * @param outcome what happened to the deposit, or {@code null} if not
   *     recorded
   * @return the description, or an empty string if nothing is recorded
   */
  static String describe(Integer count, Long depositCents, DepositOutcome outcome) {
    var text = describe(count, depositCents);
    if (outcome == null) {
      return text;
    }
    var what = outcome.getLabel().toLowerCase();
    return text.isEmpty() ? "Deposit " + what : text + ", " + what;
  }

  /**
   * Describes the keys and deposit recorded for a box, such as
   * "2 keys, $20.00 deposit".
   *
   * @param count the number of keys, or {@code null} if not recorded
   * @param depositCents the deposit, or {@code null} if not recorded
   * @return the description, or an empty string if neither is recorded
   */
  static String describe(Integer count, Long depositCents) {
    var keys = count == null ? "" : count + (count == 1 ? " key" : " keys");
    var deposit = depositCents == null ? "" : Money.format(depositCents) + " deposit";
    if (keys.isEmpty() || deposit.isEmpty()) {
      return keys + deposit;
    }
    return keys + ", " + deposit;
  }

}
