package org.lfps.mailboxes.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for talking to Google's web services: parsing replies
 * into maps, lists, strings, numbers, and booleans, and quoting strings for
 * requests.
 */
public final class Json {

  private final String text;
  private int pos;

  private Json(String text) {
    this.text = text;
  }

  /**
   * Parses a JSON document.
   *
   * @param text the JSON text
   * @return a {@code Map<String, Object>} for an object, a
   *     {@code List<Object>} for an array, a {@code String}, a {@code Double},
   *     a {@code Boolean}, or {@code null}
   * @throws IllegalArgumentException if the text isn't valid JSON
   */
  public static Object parse(String text) {
    var parser = new Json(text);
    var value = parser.value();
    parser.skipWhitespace();
    if (parser.pos != text.length()) {
      throw parser.error("Unexpected text after the end");
    }
    return value;
  }

  /**
   * Parses a JSON document that must be an object.
   *
   * @param text the JSON text
   * @return the object's members
   * @throws IllegalArgumentException if the text isn't a JSON object
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseObject(String text) {
    var value = parse(text);
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Expected a JSON object");
    }
    return (Map<String, Object>) value;
  }

  /**
   * Returns a string as a quoted JSON string.
   *
   * @param value the string
   * @return the string in double quotes, with special characters escaped
   */
  public static String quote(String value) {
    var out = new StringBuilder("\"");
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      switch (c) {
        case '"': out.append("\\\""); break;
        case '\\': out.append("\\\\"); break;
        case '\n': out.append("\\n"); break;
        case '\r': out.append("\\r"); break;
        case '\t': out.append("\\t"); break;
        default:
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
      }
    }
    return out.append('"').toString();
  }

  private Object value() {
    skipWhitespace();
    if (pos >= text.length()) {
      throw error("Unexpected end");
    }
    var c = text.charAt(pos);
    if (c == '{') {
      return object();
    }
    if (c == '[') {
      return array();
    }
    if (c == '"') {
      return string();
    }
    if (text.startsWith("true", pos)) {
      pos += 4;
      return Boolean.TRUE;
    }
    if (text.startsWith("false", pos)) {
      pos += 5;
      return Boolean.FALSE;
    }
    if (text.startsWith("null", pos)) {
      pos += 4;
      return null;
    }
    return number();
  }

  private Map<String, Object> object() {
    var members = new LinkedHashMap<String, Object>();
    pos++;
    skipWhitespace();
    if (peek('}')) {
      pos++;
      return members;
    }
    while (true) {
      skipWhitespace();
      if (!peek('"')) {
        throw error("Expected a member name");
      }
      var name = string();
      skipWhitespace();
      expect(':');
      members.put(name, value());
      skipWhitespace();
      if (peek(',')) {
        pos++;
      } else {
        expect('}');
        return members;
      }
    }
  }

  private List<Object> array() {
    var items = new ArrayList<Object>();
    pos++;
    skipWhitespace();
    if (peek(']')) {
      pos++;
      return items;
    }
    while (true) {
      items.add(value());
      skipWhitespace();
      if (peek(',')) {
        pos++;
      } else {
        expect(']');
        return items;
      }
    }
  }

  private String string() {
    pos++;
    var out = new StringBuilder();
    while (pos < text.length()) {
      var c = text.charAt(pos++);
      if (c == '"') {
        return out.toString();
      }
      if (c != '\\') {
        out.append(c);
        continue;
      }
      if (pos >= text.length()) {
        break;
      }
      var escaped = text.charAt(pos++);
      switch (escaped) {
        case '"': case '\\': case '/': out.append(escaped); break;
        case 'b': out.append('\b'); break;
        case 'f': out.append('\f'); break;
        case 'n': out.append('\n'); break;
        case 'r': out.append('\r'); break;
        case 't': out.append('\t'); break;
        case 'u':
          if (pos + 4 > text.length()) {
            throw error("Incomplete \\u escape");
          }
          try {
            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
          } catch (NumberFormatException e) {
            throw error("Invalid \\u escape");
          }
          pos += 4;
          break;
        default:
          throw error("Invalid escape \\" + escaped);
      }
    }
    throw error("Unterminated string");
  }

  private Double number() {
    var start = pos;
    while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
      pos++;
    }
    try {
      return Double.valueOf(text.substring(start, pos));
    } catch (NumberFormatException e) {
      pos = start;
      throw error("Unexpected character");
    }
  }

  private void skipWhitespace() {
    while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
      pos++;
    }
  }

  private boolean peek(char c) {
    return pos < text.length() && text.charAt(pos) == c;
  }

  private void expect(char c) {
    if (!peek(c)) {
      throw error("Expected '" + c + "'");
    }
    pos++;
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at position " + pos + " of JSON");
  }

}
