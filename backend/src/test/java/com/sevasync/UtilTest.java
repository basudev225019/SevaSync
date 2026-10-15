package com.sevasync;

import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.GeoPoint;
import com.sevasync.util.CsvUtil;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.Json;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilTest {

    @Test
    void jsonRoundTripsNestedStructuresAndEscapes() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Asha \"Bhavan\"\n<script>");
        m.put("n", 42);
        m.put("list", List.of(1, true, "x"));
        String text = Json.write(m);
        assertTrue(text.contains("\\u003cscript\\u003e"), "angle brackets must be escaped");
        Map<String, Object> back = Json.parseObject(text);
        assertEquals("Asha \"Bhavan\"\n<script>", back.get("name"));
        assertEquals(42L, back.get("n"));
        assertEquals(List.of(1L, true, "x"), back.get("list"));
    }

    @Test
    void jsonRejectsMalformedAndDeeplyNestedInput() {
        assertThrows(InvalidInputException.class, () -> Json.parseObject("{\"a\":}"));
        assertThrows(InvalidInputException.class, () -> Json.parseObject("[1,2]"));
        assertThrows(InvalidInputException.class, () -> Json.parse("[".repeat(50) + "]".repeat(50)));
    }

    @Test
    void jsonWritesLocalTimesWithTheServerOffset() {
        Json.setZone(ZoneId.of("Asia/Kolkata"));
        try {
            assertEquals("\"2026-10-09T12:30:00+05:30\"", Json.write(LocalDateTime.of(2026, 10, 9, 12, 30)));
            assertEquals("null", Json.write(Double.NaN));
        } finally {
            Json.setZone(ZoneOffset.UTC);
        }
    }

    @Test
    void sanitizerCleansTextAndValidatesContactDetails() throws Exception {
        assertEquals("Hello world", InputSanitizer.text("  <b>Hello</b>\t  world ", "x", 50, true));
        assertEquals("SUM(A1)", InputSanitizer.text("=SUM(A1)", "x", 50, true));
        assertEquals("9876543210", InputSanitizer.phone("+91 98765-43210", "Phone"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.phone("12345", "Phone"));
        assertEquals("asha@campus.edu", InputSanitizer.email(" Asha@Campus.edu ", "E-mail"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.email("not-an-email", "E-mail"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.text("", "Name", 10, true));
    }

    @Test
    void passwordPolicyNeedsLengthLetterAndDigit() throws Exception {
        assertEquals("Passw0rd!", InputSanitizer.password("Passw0rd!"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.password("short1"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.password("onlyletters"));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.password("1234567890"));
    }

    @Test
    void dateTimeAcceptsLocalAndOffsetForms() throws Exception {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDateTime expected = LocalDateTime.of(2026, 10, 9, 12, 30);
        assertEquals(expected, InputSanitizer.dateTime("2026-10-09T12:30", "t", ist));
        assertEquals(expected, InputSanitizer.dateTime("2026-10-09T07:00:00Z", "t", ist));
        assertEquals(expected, InputSanitizer.dateTime("2026-10-09T12:30:00+05:30", "t", ist));
        assertThrows(InvalidInputException.class, () -> InputSanitizer.dateTime("tomorrow", "t", ist));
    }

    @Test
    void csvQuotesAndNeutralisesFormulas() {
        String line = CsvUtil.join(new String[]{"a,b", "say \"hi\"", "=CMD()", "-12.5"});
        assertEquals("\"a,b\",\"say \"\"hi\"\"\",'=CMD(),-12.5", line);
        assertArrayEquals(new String[]{"a,b", "say \"hi\"", "'=CMD()", "-12.5"}, CsvUtil.split(line));
    }

    @Test
    void geoDistanceIsHaversine() {
        GeoPoint a = new GeoPoint(12.9716, 77.5946);
        GeoPoint b = a.offsetKm(3, 4);
        assertEquals(5.0, a.distanceKm(b), 0.05);
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(91, 0));
    }
}
