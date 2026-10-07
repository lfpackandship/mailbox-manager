package org.lfps.mailboxes.view;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Control;

/**
 * Outlines the fields on a form that have a mistake in red, tints them pink,
 * and puts the cursor in the first, so they're easy to find on a long form
 * such as Add New Box. The message saying what's wrong is shown next to Save;
 * this only points at where.
 */
final class FieldProblems {

  /** Added to a field's own style to outline it in red and tint it pink. */
  private static final String OUTLINE = " -fx-text-box-border: #c62828; -fx-focus-color: #c62828;"
      + " -fx-outer-border: #c62828; -fx-control-inner-background: #ffebee;";

  /** The fields outlined now, first problem first. */
  private final List<Control> marked = new ArrayList<>();

  /** The style each outlined field had before, to put back. */
  private final List<String> styles = new ArrayList<>();

  /** Starts with no fields outlined. */
  FieldProblems() {
  }

  /**
   * Outlines a field with a mistake.
   *
   * @param field the field
   */
  void mark(Control field) {
    if (marked.contains(field)) {
      return;
    }
    marked.add(field);
    styles.add(field.getStyle());
    field.setStyle(field.getStyle() + OUTLINE);
  }

  /** Removes every outline, before checking the form again. */
  void clear() {
    for (var i = 0; i < marked.size(); i++) {
      marked.get(i).setStyle(styles.get(i));
    }
    marked.clear();
    styles.clear();
  }

  /** Puts the cursor in the first field with a mistake, if there is one. */
  void focusFirst() {
    if (!marked.isEmpty()) {
      marked.get(0).requestFocus();
    }
  }

}
