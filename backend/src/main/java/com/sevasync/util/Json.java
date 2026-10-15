package com.sevasync.util;

import com.sevasync.contract.Entity;
import com.sevasync.exception.InvalidInputException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tiny dependency-free JSON writer and parser (enough for the SevaSync API).
 *
 * <p>{@link LocalDateTime} values are written with the server's UTC offset ({@code 2026-10-15T17:30:00+05:30}),
 * so a browser in any time zone reads the correct instant.
 */
public final class Json {
    private static volatile ZoneId zone = ZoneId.systemDefault();

    private Json() {
    }

    /** Zone used to attach an offset to {@link LocalDateTime} values (set once from the application clock). */
    public static void setZone(ZoneId z) {
        zone = z;
    }

    // ---------------------------------------------------------------- writer

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(256);
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Double d) {
            sb.append(d.isNaN() || d.isInfinite() ? "null" : String.valueOf(d));
        } else if (o instanceof Float f) {
            sb.append(f.isNaN() || f.isInfinite() ? "null" : String.valueOf(f));
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Entity e) {
            write(e.toMap(), sb);
        } else if (o instanceof Optional<?> opt) {
            write(opt.orElse(null), sb);
        } else if (o instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object x : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(x, sb);
            }
            sb.append(']');
        } else if (o instanceof LocalDateTime t) {
            quote(t.atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), sb);
        } else if (o instanceof TemporalAccessor) {
            quote(o.toString(), sb);
        } else {
            quote(o.toString(), sb);
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                // keeps "</script>" and friends inert if JSON is ever embedded in HTML
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case ' ' -> sb.append("\\u2028");
                case ' ' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- parser

    /** Parses any JSON document (objects become Maps, arrays Lists, numbers Long or Double). */
    public static Object parse(String text) throws InvalidInputException {
        return new Parser(text).parseDocument();
    }

    /** Parses a JSON object into a Map. */
    public static Map<String, Object> parseObject(String text) throws InvalidInputException {
        Object o = parse(text);
        if (o instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        throw new InvalidInputException("Request body must be a JSON object");
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 20;
        private final String s;
        private int pos;
        private int depth;

        private Parser(String s) {
            this.s = s == null ? "" : s;
        }

        Object parseDocument() throws InvalidInputException {
            skipWs();
            Object v = value();
            skipWs();
            if (pos != s.length()) {
                throw error("unexpected trailing characters");
            }
            return v;
        }

        private Object value() throws InvalidInputException {
            if (pos >= s.length()) {
                throw error("unexpected end of JSON");
            }
            char c = s.charAt(pos);
            if (c == '{') {
                return object();
            } else if (c == '[') {
                return array();
            } else if (c == '"') {
                return string();
            } else if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            } else if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return number();
        }

        private Map<String, Object> object() throws InvalidInputException {
            enter();
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (peek() == '}') {
                pos++;
                depth--;
                return m;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw error("object key expected");
                }
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                m.put(key, value());
                skipWs();
                if (peek() == ',') {
                    pos++;
                } else {
                    expect('}');
                    depth--;
                    return m;
                }
            }
        }

        private List<Object> array() throws InvalidInputException {
            enter();
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (peek() == ']') {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                skipWs();
                list.add(value());
                skipWs();
                if (peek() == ',') {
                    pos++;
                } else {
                    expect(']');
                    depth--;
                    return list;
                }
            }
        }

        private String string() throws InvalidInputException {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= s.length()) {
                        break;
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > s.length()) {
                                throw error("bad unicode escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("bad unicode escape");
                            }
                            pos += 4;
                        }
                        default -> throw error("bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("unterminated string");
        }

        private Object number() throws InvalidInputException {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String num = s.substring(start, pos);
            if (num.isEmpty()) {
                throw error("unexpected character '" + s.charAt(start) + "'");
            }
            try {
                if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                    return Double.parseDouble(num);
                }
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw error("bad number " + num);
            }
        }

        private void enter() throws InvalidInputException {
            if (++depth > MAX_DEPTH) {
                throw error("JSON nested too deeply");
            }
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private void expect(char c) throws InvalidInputException {
            if (peek() != c) {
                throw error("'" + c + "' expected");
            }
            pos++;
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private InvalidInputException error(String msg) {
            return new InvalidInputException("Invalid JSON at position " + pos + ": " + msg);
        }
    }
}
