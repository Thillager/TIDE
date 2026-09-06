package lsp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parsesObjectWithAllBasicJsonTypes() {
        Object value = Json.parse("{\"name\":\"ThIDE\",\"enabled\":true,\"count\":5,\"nothing\":null}");

        assertInstanceOf(Map.class, value);
        Map<?, ?> map = (Map<?, ?>) value;

        assertEquals("ThIDE", map.get("name"));
        assertEquals(Boolean.TRUE, map.get("enabled"));
        assertEquals(5.0, map.get("count"));
        assertNull(map.get("nothing"));
    }

    @Test
    void parsesNestedObjectsAndArrays() {
        Object value = Json.parse("{\"items\":[1,2,3],\"nested\":{\"ok\":false}}");
        Map<?, ?> map = (Map<?, ?>) value;

        assertEquals(List.of(1.0, 2.0, 3.0), map.get("items"));
        assertEquals(false, ((Map<?, ?>) map.get("nested")).get("ok"));
    }

    @Test
    void parsesEscapedStringsAndUnicode() {
        Object value = Json.parse("{\"text\":\"a\\n\\t\\\"\\\\\\u00e4\"}");
        assertEquals("a\n\t\"\\ä", ((Map<?, ?>) value).get("text"));
    }

    @Test
    void parsesNumbers() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"integer\":42,\"negative\":-3.5,\"exp\":1.2e3}");

        assertEquals(42.0, map.get("integer"));
        assertEquals(-3.5, map.get("negative"));
        assertEquals(1200.0, map.get("exp"));
    }

    @Test
    void writesAndReadsBackNestedData() {
        Map<String, Object> original = Map.of(
                "name", "ThIDE",
                "enabled", true,
                "values", List.of(1.0, 2.0, 3.0),
                "empty", List.of()
        );

        String json = Json.write(original);
        Object parsed = Json.parse(json);

        assertEquals(original, parsed);
    }

    @Test
    void writesEscapedStringsCorrectly() {
        String original = "Anführungszeichen: \" und Backslash: \\ und Zeilenumbruch: \n";
        String json = Json.write(original);

        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\\\"));
        assertTrue(json.contains("\\n"));
        assertEquals(original, Json.parse(json));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("tru"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"x\" 1}"));
    }
}
