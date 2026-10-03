package org.lfps.mailboxes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

  @Test
  void parsesTheKindsOfValueGoogleSends() {
    var parsed = Json.parseObject("{ \"files\": [ {\"id\": \"a1\", \"name\": \"x.db\"} ],"
        + " \"size\": 12.5, \"count\": -3, \"ok\": true, \"no\": false, \"none\": null, \"empty\": {} }");

    assertEquals(List.of(Map.of("id", "a1", "name", "x.db")), parsed.get("files"));
    assertEquals(12.5, parsed.get("size"));
    assertEquals(-3.0, parsed.get("count"));
    assertEquals(true, parsed.get("ok"));
    assertEquals(false, parsed.get("no"));
    assertNull(parsed.get("none"));
    assertEquals(Map.of(), parsed.get("empty"));
  }

  @Test
  void unescapesStrings() {
    assertEquals("a\"b\\c/d\ne\tf é", Json.parse("\"a\\\"b\\\\c\\/d\\ne\\tf \\u00e9\""));
  }

  @Test
  void quotedStringsParseBackToTheSameText() {
    var text = "Box \"12\" \\ back\nslash \u0001 é";
    assertEquals(text, Json.parse(Json.quote(text)));
  }

  @Test
  void rejectsBrokenJson() {
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2] extra"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("\"unterminated"));
    assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[]"));
  }

}
