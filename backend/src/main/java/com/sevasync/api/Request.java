package com.sevasync.api;

import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.User;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.Json;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only view of one HTTP request with typed accessors for path parameters, query parameters and the JSON body.
 * Validation errors surface as {@link InvalidInputException} (HTTP 400) with the field name in the message.
 */
public final class Request {
    static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private final ZoneId zone;
    private User user;
    private Map<String, Object> body;

    Request(HttpExchange exchange, Map<String, String> pathParams, ZoneId zone) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
        this.zone = zone;
    }

    // ------------------------------------------------------------------ identity

    void setUser(User user) {
        this.user = user;
    }

    /** The signed-in user (never null on routes that require sign-in). */
    public User user() throws AccessDeniedException {
        if (user == null) {
            throw new AccessDeniedException("Sign in required");
        }
        return user;
    }

    /** The signed-in user cast to a role-specific subclass, or 403 when the caller has a different role. */
    public <T extends User> T user(Class<T> type) throws AccessDeniedException {
        User u = user();
        if (!type.isInstance(u)) {
            throw new AccessDeniedException("Only " + type.getSimpleName().toLowerCase(Locale.ROOT)
                    + "s can do this");
        }
        return type.cast(u);
    }

    public User userOrNull() {
        return user;
    }

    public String bearerToken() {
        return bearerToken(exchange);
    }

    static String bearerToken(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return h.substring(7).trim();
        }
        return null;
    }

    /** Client address used for rate limiting. */
    public String clientKey() {
        return exchange.getRemoteAddress() == null ? "unknown"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    // ------------------------------------------------------------------ path and query

    public String path(String name) {
        return pathParams.get(name);
    }

    public String query(String name) {
        return query.get(name);
    }

    public int queryInt(String name, int fallback, int min, int max) throws InvalidInputException {
        String v = query.get(name);
        return v == null || v.isBlank() ? fallback : InputSanitizer.integer(v, name, min, max);
    }

    public <E extends Enum<E>> E queryEnum(Class<E> type, String name) throws InvalidInputException {
        String v = query.get(name);
        return v == null || v.isBlank() ? null : InputSanitizer.enumValue(type, v, name);
    }

    // ------------------------------------------------------------------ JSON body

    public Map<String, Object> body() throws InvalidInputException {
        if (body == null) {
            body = readBody();
        }
        return body;
    }

    public boolean has(String key) throws InvalidInputException {
        return body().get(key) != null;
    }

    public String str(String key) throws InvalidInputException {
        Object v = body().get(key);
        return v == null ? null : v.toString();
    }

    public String requiredStr(String key) throws InvalidInputException {
        String v = str(key);
        if (v == null || v.isBlank()) {
            throw new InvalidInputException(key + " is required");
        }
        return v;
    }

    public Integer optInt(String key) throws InvalidInputException {
        Object v = body().get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE) {
            return d.intValue();
        }
        throw new InvalidInputException(key + " must be a whole number");
    }

    public int requiredInt(String key) throws InvalidInputException {
        Integer v = optInt(key);
        if (v == null) {
            throw new InvalidInputException(key + " is required");
        }
        return v;
    }

    public Double optDouble(String key) throws InvalidInputException {
        Object v = body().get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        throw new InvalidInputException(key + " must be a number");
    }

    public Boolean optBool(String key) throws InvalidInputException {
        Object v = body().get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        throw new InvalidInputException(key + " must be true or false");
    }

    public <E extends Enum<E>> E requiredEnum(Class<E> type, String key) throws InvalidInputException {
        return InputSanitizer.enumValue(type, requiredStr(key), key);
    }

    public LocalDateTime optDateTime(String key) throws InvalidInputException {
        String v = str(key);
        return v == null || v.isBlank() ? null : InputSanitizer.dateTime(v, key, zone);
    }

    private Map<String, Object> readBody() throws InvalidInputException {
        byte[] raw;
        try (InputStream in = exchange.getRequestBody()) {
            raw = in.readNBytes(MAX_BODY_BYTES + 1);
        } catch (IOException e) {
            throw new InvalidInputException("Cannot read request body");
        }
        if (raw.length > MAX_BODY_BYTES) {
            throw new InvalidInputException("Request body is larger than " + (MAX_BODY_BYTES / 1024) + " KB");
        }
        if (raw.length == 0) {
            return new HashMap<>();
        }
        String type = header("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            // also blocks cross-site "simple" form posts, which cannot send this content type without a preflight
            throw new InvalidInputException("Content-Type must be application/json");
        }
        return Json.parseObject(new String(raw, StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return q;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            q.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return q;
    }
}
