package org.lfps.mailboxes.view;

import javafx.scene.text.Font;

/**
 * The text sizes offered in Settings, as multiples of the system's normal
 * text size. Stored in {@link org.lfps.mailboxes.data.Setting#TEXT_SIZE} by
 * name.
 */
public enum TextSize {

  /** The system's normal text size. */
  NORMAL("Normal", 1.0),

  /** A quarter larger than normal. */
  LARGE("Large", 1.25),

  /** Half again as large as normal. */
  EXTRA_LARGE("Extra large", 1.5);

  private final String label;
  private final double scale;

  TextSize(String label, double scale) {
    this.label = label;
    this.scale = scale;
  }

  /**
   * Returns the size stored under a setting value.
   *
   * @param name the stored name, such as {@code "LARGE"}
   * @return the matching size, or {@link #NORMAL} if the name is unknown
   */
  public static TextSize fromName(String name) {
    for (var size : values()) {
      if (size.name().equals(name)) {
        return size;
      }
    }
    return NORMAL;
  }

  /**
   * Returns how many times larger than normal this size is.
   *
   * @return the scale factor, such as 1.25
   */
  public double scale() {
    return scale;
  }

  /**
   * Returns the inline CSS that applies this size to a window's contents.
   * Everything else in the app is sized in {@code em}, so it scales with it.
   *
   * @return a {@code -fx-font-size} declaration in pixels
   */
  public String style() {
    return String.format(java.util.Locale.ROOT, "-fx-font-size: %.1fpx;", Font.getDefault().getSize() * scale);
  }

  @Override
  public String toString() {
    return label;
  }

}
