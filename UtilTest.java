package com.sevasync;

import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.NeedLevel;
import com.sevasync.util.CsvUtil;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilTest {

    @Test
    void csvRoundTripsCommasQuotesAndEmptyFields() {
        String[] fields = {"Annapurna, \"Main\" Mess", "", "plain", "a,b,c"};
        assertEquals(List.of(fields), List.of(CsvUtil.split(CsvUtil.join(fields))));
    }

    @Test
    void csvFlattensLineBreaks() {
        assertEquals("a b", CsvUtil.split(CsvUtil.join(new String[]{"a\nb"}))[0]);
    }

    @Test
    void phoneNumbersAreNormalised() throws Exception {
        assertEquals("9876543210", InputSanitizer.phone("+91 98765-43210", "Phone"));
        assertEquals("9876543210", InputSanitizer.phone("09876543210", "Phone"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.phone("12345", "Phone"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.phone("5876543210", "Phone"));
    }

    @Test
    void textIsCleanedAndLimited() throws Exception {
        assertEquals("Hello World", InputSanitizer.text("  =Hello\t<b>World</b>\n", "Name", 50, true));
        assertEquals("cmd", InputSanitizer.text("=+@cmd", "Name", 50, true));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.text("   ", "Name", 50, true));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.text("x".repeat(51), "Name", 50, true));
    }

    @Test
    void enumsAndNumbersAreParsedStrictly() throws Exception {
        assertEquals(NeedLevel.CRITICAL, InputSanitizer.enumValue(NeedLevel.class, " critical ", "need"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.enumValue(NeedLevel.class, "urgent", "need"));
        assertEquals(7, InputSanitizer.integer(" 7 ", "n", 1, 10));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.integer("11", "n", 1, 10));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.integer("abc", "n", 1, 10));
        assertTrue(InputSanitizer.yesNo("Y", "v"));
        assertFalse(InputSanitizer.yesNo("no", "v"));
    }

    @Test
    void jsonParsesAndWrites() throws Exception {
        Map<String, Object> m = Json.parseObject(
                "{\"a\": 1, \"b\": [true, null, \"x\\ny\\u0041\"], \"c\": {\"d\": 2.5}}");
        assertEquals(1L, m.get("a"));
        List<?> b = (List<?>) m.get("b");
        assertEquals(3, b.size());
        assertEquals("x\nyA", b.get(2));
        assertEquals(2.5, ((Map<?, ?>) m.get("c")).get("d"));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("k", "q\"uote");
        out.put("n", List.of(1, 2));
        assertEquals("{\"k\":\"q\\\"uote\",\"n\":[1,2]}", Json.write(out));
    }

    @Test
    void jsonRejectsGarbage() {
        assertThrows(InvalidInputException.class, () -> Json.parseObject("{\"a\": }"));
        assertThrows(InvalidInputException.class, () -> Json.parseObject("[1,2]"));
        assertThrows(InvalidInputException.class, () -> Json.parseObject("{\"a\":1} trailing"));
        assertThrows(InvalidInputException.class, () -> Json.parseObject("[".repeat(50)));
    }
}
