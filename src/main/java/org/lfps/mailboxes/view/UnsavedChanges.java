package org.lfps.mailboxes.view;

import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.stage.Window;

/**
 * Notices when something on a form such as Add New Box or Edit Box has been
 * changed, so leaving the form can ask before throwing the changes away.
 * The form watches each of its fields once they're filled in, and tells
 * {@link AppWindow#setLeaveCheck} to call {@link #confirmLeave} before Back,
 * Cancel, the Go menu, or closing the window leaves it.
 */
final class UnsavedChanges {

  /** Whether anything has been changed since the form opened or was last saved. */
  private boolean changed;

  /** Starts with nothing changed and nothing watched. */
  UnsavedChanges() {
  }

  /**
   * Watches values on the form, such as fields' text, for changes.
   *
   * @param values the values to watch
   * @return this, to watch more
   */
  UnsavedChanges watch(ObservableValue<?>... values) {
    for (var value : values) {
      value.addListener((obs, oldValue, newValue) -> changed = true);
    }
    return this;
  }

  /**
   * Watches a list on the form, such as the forwarding addresses added, for
   * entries being added or removed.
   *
   * @param list the list to watch
   * @return this, to watch more
   */
  UnsavedChanges watchList(ObservableList<?> list) {
    list.addListener((ListChangeListener<Object>) change -> changed = true);
    return this;
  }

  /**
   * Returns whether anything has been changed since the form opened or was
   * last saved.
   *
   * @return {@code true} if there's something unsaved
   */
  boolean isChanged() {
    return changed;
  }

  /** Records that the form has been saved, so there's nothing to lose. */
  void saved() {
    changed = false;
  }

  /**
   * Asks whether to throw away the changes, if there are any.
   *
   * @param owner the window the question belongs to
   * @return {@code true} if nothing has changed or the answer was to throw
   *     the changes away; {@code false} to stay on the form
   */
  boolean confirmLeave(Window owner) {
    return !changed || Dialogs.confirm.ask(owner, "Leave without saving?",
        "The changes you've made here haven't been saved, and will be lost.");
  }

}
