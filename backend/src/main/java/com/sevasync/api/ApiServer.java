package com.sevasync.api;

import com.sevasync.SevaSyncSystem;
import com.sevasync.contract.Entity;
import com.sevasync.exception.CapacityExceededException;
import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.ExpiredBatchException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.exception.UnsafeFoodException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.storage.AuditLogger;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.Json;
import com.sevasync.util.TimeUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST/JSON API on top of the JDK's built-in {@code com.sun.net.httpserver} - no framework, no libraries.
 *
 * <pre>
 * GET  /api/health
 * GET|POST /api/donors            GET /api/donors/{id}
 * GET|POST /api/recipients        GET /api/recipients/{id}
 * GET|POST /api/batches[?status]  GET /api/batches/{id}
 * GET|POST /api/dispatches[?status]  GET /api/dispatches/{id}
 * POST /api/dispatches/{id}/deliver   {"volunteer": "..."}
 * POST /api/dispatches/{id}/cancel    {"reason": "..."}
 * POST /api/matching/preview | /api/matching/run
 * GET  /api/reports/impact
 * GET  /api/audit[?limit=n]       GET /api/audit/verify
 * </pre>
 */
public class ApiServer {
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private record Response(int status, Object body) {
    }

    private static final class ApiException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int status;

        private ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final SevaSyncSystem sys;
    private final HttpServer server;
    private final ExecutorService pool;
    private final String apiKey;

    /**
     * @param host   interface to bind, e.g. "127.0.0.1" (local only) or "0.0.0.0"
     * @param port   port, 0 picks a free one
     * @param apiKey optional shared secret required in the {@code X-API-Key} header (null/blank = no auth)
     */
    public ApiServer(SevaSyncSystem sys, String host, int port, String apiKey) throws IOException {
        this.sys = sys;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.pool = Executors.newFixedThreadPool(8);
        this.server.setExecutor(pool);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        pool.shutdownNow();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    // ------------------------------------------------------------------ dispatching

    private void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-API-Key, Authorization");

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        int status;
        Object body;
        try {
            Response r = route(ex);
            status = r.status();
            body = r.body();
        } catch (ApiException e) {
            status = e.status;
            body = error("ApiError", e.getMessage());
        } catch (SevaSyncException e) {
            status = statusFor(e);
            body = error(e.getClass().getSimpleName(), e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            status = 500;
            body = error("InternalError", "Unexpected server error");
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static int statusFor(SevaSyncException e) {
        if (e instanceof InvalidInputException) {
            return 400;
        } else if (e instanceof EntityNotFoundException) {
            return 404;
        } else if (e instanceof CapacityExceededException) {
            return 409;
        } else if (e instanceof ExpiredBatchException || e instanceof UnsafeFoodException) {
            return 422;
        }
        return 400;
    }

    private static Map<String, Object> error(String type, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        m.put("type", type);
        return m;
    }

    private Response route(HttpExchange ex) throws ApiException, SevaSyncException {
        URI uri = ex.getRequestURI();
        String[] seg = Arrays.stream(uri.getPath().split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (seg.length < 2 || !seg[0].equals("api")) {
            throw new ApiException(404, "Unknown path " + uri.getPath());
        }
        String method = ex.getRequestMethod();
        Map<String, String> query = query(uri.getRawQuery());

        if (!seg[1].equals("health") && !seg[1].equals("demo")) {
            authenticate(ex);
        }
        return switch (seg[1]) {
            case "health" -> {
                requireMethod(method, "GET");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("status", "UP");
                m.put("time", TimeUtil.now(sys.clock()));
                yield ok(m);
            }
            case "demo" -> {
                requireMethod(method, "POST");
                boolean loaded = com.sevasync.DemoData.load(sys);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("loaded", loaded);
                m.put("message", loaded ? "Sample campus data loaded successfully" : "System already contains data");
                yield ok(m);
            }
            case "donors" -> donors(method, seg, ex);
            case "recipients" -> recipients(method, seg, ex);
            case "batches" -> batches(method, seg, query, ex);
            case "dispatches" -> dispatches(method, seg, query, ex);
            case "matching" -> matching(method, seg);
            case "reports" -> {
                requireMethod(method, "GET");
                if (seg.length == 3 && seg[2].equals("impact")) {
                    yield ok(sys.reports().generate().toMap());
                }
                throw new ApiException(404, "Unknown report");
            }
            case "audit" -> audit(method, seg, query);
            default -> throw new ApiException(404, "Unknown resource " + seg[1]);
        };
    }

    // ------------------------------------------------------------------ resources

    private Response donors(String method, String[] seg, HttpExchange ex) throws ApiException, SevaSyncException {
        if (seg.length == 2) {
            if (method.equals("GET")) {
                return ok(maps(sys.donations().listDonors()));
            }
            requireMethod(method, "POST");
            Map<String, Object> b = body(ex);
            return new Response(201, sys.donations().registerDonor(str(b, "name"), str(b, "phone"), str(b, "email"),
                    str(b, "address"), InputSanitizer.enumValue(DonorType.class, str(b, "donorType"), "donorType")).toMap());
        }
        if (seg.length == 3) {
            requireMethod(method, "GET");
            return ok(sys.donations().getDonor(seg[2]).toMap());
        }
        throw new ApiException(404, "Unknown path");
    }

    private Response recipients(String method, String[] seg, HttpExchange ex) throws ApiException, SevaSyncException {
        if (seg.length == 2) {
            if (method.equals("GET")) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Recipient r : sys.recipients().list()) {
                    out.add(withCapacity(r));
                }
                return ok(out);
            }
            requireMethod(method, "POST");
            Map<String, Object> b = body(ex);
            Recipient r = sys.recipients().register(str(b, "name"), str(b, "phone"), str(b, "email"), str(b, "address"),
                    InputSanitizer.enumValue(RecipientType.class, str(b, "recipientType"), "recipientType"),
                    InputSanitizer.enumValue(NeedLevel.class, str(b, "needLevel"), "needLevel"),
                    intOf(b, "dailyCapacity"));
            return new Response(201, withCapacity(r));
        }
        if (seg.length == 3) {
            requireMethod(method, "GET");
            return ok(withCapacity(sys.recipients().get(seg[2])));
        }
        throw new ApiException(404, "Unknown path");
    }

    private Map<String, Object> withCapacity(Recipient r) {
        Map<String, Object> m = r.toMap();
        m.put("remainingCapacityToday", sys.recipients().remainingCapacityToday(r));
        return m;
    }

    private Response batches(String method, String[] seg, Map<String, String> query, HttpExchange ex)
            throws ApiException, SevaSyncException {
        if (seg.length == 2) {
            if (method.equals("GET")) {
                BatchStatus status = query.containsKey("status")
                        ? InputSanitizer.enumValue(BatchStatus.class, query.get("status"), "status") : null;
                return ok(maps(sys.donations().listBatches(status)));
            }
            requireMethod(method, "POST");
            Map<String, Object> b = body(ex);
            LocalDateTime prepared = b.get("preparedAt") == null ? TimeUtil.now(sys.clock())
                    : InputSanitizer.dateTime(str(b, "preparedAt"), "preparedAt");
            int minutes;
            if (b.get("shelfLifeMinutes") != null) {
                minutes = intOf(b, "shelfLifeMinutes");
            } else if (b.get("shelfLifeHours") instanceof Number hours) {
                minutes = (int) Math.round(hours.doubleValue() * 60);
            } else {
                throw new InvalidInputException("shelfLifeHours (or shelfLifeMinutes) is required");
            }
            FoodBatch batch = sys.donations().submitBatch(str(b, "donorId"), str(b, "foodName"),
                    InputSanitizer.enumValue(FoodCategory.class, str(b, "category"), "category"),
                    Boolean.TRUE.equals(b.get("vegetarian")), intOf(b, "servings"), prepared, minutes);
            return new Response(201, batch.toMap());
        }
        if (seg.length == 3) {
            requireMethod(method, "GET");
            return ok(sys.donations().getBatch(seg[2]).toMap());
        }
        throw new ApiException(404, "Unknown path");
    }

    private Response dispatches(String method, String[] seg, Map<String, String> query, HttpExchange ex)
            throws ApiException, SevaSyncException {
        if (seg.length == 2) {
            if (method.equals("GET")) {
                DispatchStatus status = query.containsKey("status")
                        ? InputSanitizer.enumValue(DispatchStatus.class, query.get("status"), "status") : null;
                return ok(maps(sys.dispatches().list(status)));
            }
            requireMethod(method, "POST");
            Map<String, Object> b = body(ex);
            Dispatch d = sys.dispatches().assignManually(str(b, "batchId"), str(b, "recipientId"), intOf(b, "servings"));
            return new Response(201, d.toMap());
        }
        if (seg.length == 3) {
            requireMethod(method, "GET");
            return ok(sys.dispatches().get(seg[2]).toMap());
        }
        if (seg.length == 4 && seg[3].equals("deliver")) {
            requireMethod(method, "POST");
            return ok(sys.dispatches().markDelivered(seg[2], str(body(ex), "volunteer")).toMap());
        }
        if (seg.length == 4 && seg[3].equals("cancel")) {
            requireMethod(method, "POST");
            return ok(sys.dispatches().cancel(seg[2], str(body(ex), "reason")).toMap());
        }
        throw new ApiException(404, "Unknown path");
    }

    private Response matching(String method, String[] seg) throws ApiException {
        requireMethod(method, "POST");
        if (seg.length == 3 && seg[2].equals("preview")) {
            return ok(sys.matching().preview().toMap());
        }
        if (seg.length == 3 && seg[2].equals("run")) {
            return ok(sys.matching().runMatching().toMap());
        }
        throw new ApiException(404, "Unknown path");
    }

    private Response audit(String method, String[] seg, Map<String, String> query)
            throws ApiException, InvalidInputException {
        requireMethod(method, "GET");
        if (seg.length == 3 && seg[2].equals("verify")) {
            AuditLogger.Verification v = sys.audit().verify();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("valid", v.valid());
            m.put("entries", v.entries());
            m.put("firstBadSeq", v.firstBadSeq());
            return ok(m);
        }
        if (seg.length == 2) {
            int limit = query.containsKey("limit") ? InputSanitizer.integer(query.get("limit"), "limit", 1, 1000) : 50;
            List<Map<String, Object>> out = new ArrayList<>();
            for (AuditLogger.AuditEntry e : sys.audit().tail(limit)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("seq", e.seq());
                m.put("timestamp", e.timestamp());
                m.put("action", e.action());
                m.put("entityType", e.entityType());
                m.put("entityId", e.entityId());
                m.put("details", e.details());
                m.put("hash", e.hash());
                out.add(m);
            }
            return ok(out);
        }
        throw new ApiException(404, "Unknown path");
    }

    // ------------------------------------------------------------------ helpers

    private void authenticate(HttpExchange ex) throws ApiException {
        if (apiKey == null) {
            return;
        }
        String given = ex.getRequestHeaders().getFirst("X-API-Key");
        if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(401, "Missing or invalid X-API-Key header");
        }
    }

    private static void requireMethod(String actual, String expected) throws ApiException {
        if (!actual.equals(expected)) {
            throw new ApiException(405, "Method " + actual + " not allowed here (use " + expected + ")");
        }
    }

    private static Response ok(Object body) {
        return new Response(200, body);
    }

    private static List<Map<String, Object>> maps(List<? extends Entity> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Entity e : list) {
            out.add(e.toMap());
        }
        return out;
    }

    private static Map<String, Object> body(HttpExchange ex) throws ApiException, InvalidInputException {
        try {
            byte[] raw = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (raw.length > MAX_BODY_BYTES) {
                throw new ApiException(413, "Request body too large");
            }
            return Json.parseObject(new String(raw, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(400, "Cannot read request body");
        }
    }

    private static String str(Map<String, Object> b, String key) {
        Object v = b.get(key);
        return v == null ? null : v.toString();
    }

    private static int intOf(Map<String, Object> b, String key) throws InvalidInputException {
        Object v = b.get(key);
        if (v instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        throw new InvalidInputException(key + " must be a whole number");
    }

    private static Map<String, String> query(String raw) {
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
