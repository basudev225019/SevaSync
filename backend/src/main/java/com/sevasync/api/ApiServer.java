package com.sevasync.api;

import com.sevasync.SevaSyncSystem;
import com.sevasync.api.routes.AdminRoutes;
import com.sevasync.api.routes.AuthRoutes;
import com.sevasync.api.routes.BatchRoutes;
import com.sevasync.api.routes.DispatchRoutes;
import com.sevasync.api.routes.NetworkRoutes;
import com.sevasync.api.routes.PublicRoutes;
import com.sevasync.api.routes.RouteModule;
import com.sevasync.exception.AuthenticationException;
import com.sevasync.exception.RateLimitedException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.security.Guard;
import com.sevasync.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST/JSON API on the JDK's built-in {@code com.sun.net.httpserver} - no web framework.
 *
 * <p>Cross-cutting concerns live here, once: CORS for an explicit list of origins, authentication and permission
 * checks driven by each route's {@link Access}, error-to-status mapping, security headers, request ids and access
 * logging. Business code never touches HTTP.
 */
public class ApiServer {
    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);
    private static final int THREADS = 96;

    private final SevaSyncSystem sys;
    private final HttpServer server;
    private final ExecutorService pool;
    private final Router router = new Router();
    private final Set<String> allowedOrigins;
    private final EventStream events;
    private final StaticFiles staticFiles;

    /**
     * @param host           interface to bind, e.g. "127.0.0.1" (local only) or "0.0.0.0"
     * @param port           port, 0 picks a free one
     * @param allowedOrigins browser origins allowed to call the API cross-origin (the Vite dev server)
     * @param staticDir      built frontend to serve, or {@code null}
     */
    public ApiServer(SevaSyncSystem sys, String host, int port, Set<String> allowedOrigins, Path staticDir)
            throws IOException {
        this.sys = sys;
        this.allowedOrigins = Set.copyOf(allowedOrigins);
        this.server = HttpServer.create(new InetSocketAddress(host, port), 128);
        AtomicInteger n = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "sevasync-http-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.server.setExecutor(pool);
        this.events = new EventStream(sys.events(), sys.auth());
        StaticFiles sf = staticDir == null ? null : new StaticFiles(staticDir);
        this.staticFiles = sf != null && sf.isAvailable() ? sf : null;

        for (RouteModule m : List.of(new PublicRoutes(sys), new AuthRoutes(sys), new NetworkRoutes(sys),
                new BatchRoutes(sys), new DispatchRoutes(sys), new AdminRoutes(sys))) {
            m.register(router);
        }
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleRoot);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        events.stop();
        server.stop(1);
        pool.shutdownNow();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public boolean servesFrontend() {
        return staticFiles != null;
    }

    public Router router() {
        return router;
    }

    // ------------------------------------------------------------------ dispatch

    private void handleRoot(HttpExchange ex) throws IOException {
        if (staticFiles == null) {
            sendJson(ex, 404, error("The frontend is not bundled with this server; run it with `npm run dev`",
                    "NOT_FOUND"));
            return;
        }
        staticFiles.handle(ex);
    }

    private void handleApi(HttpExchange ex) throws IOException {
        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        applyCors(ex);
        ex.getResponseHeaders().set("X-Request-Id", requestId);

        if (method.equals("OPTIONS")) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        if (method.equals("GET") && path.equals("/api/events")) {
            events.handle(ex);
            return;
        }

        int status;
        Object body;
        Map<String, String> extra = Map.of();
        try {
            Router.Match match = router.find(method, path);
            if (!match.found()) {
                if (match.allowedMethods().isEmpty()) {
                    throw new HttpError(404, "No endpoint " + path, "NOT_FOUND");
                }
                ex.getResponseHeaders().set("Allow", String.join(", ", match.allowedMethods()));
                throw new HttpError(405, method + " is not allowed here (use "
                        + String.join(" or ", match.allowedMethods()) + ")", "METHOD_NOT_ALLOWED");
            }
            Request req = new Request(ex, match.params(), sys.clock().getZone());
            Access access = match.route().access();
            if (access.signedIn()) {
                req.setUser(sys.auth().authenticate(req.bearerToken()));
                if (access.permission() != null) {
                    Guard.require(req.userOrNull(), access.permission());
                }
            }
            Object result = match.route().handler().handle(req);
            if (result instanceof Response r) {
                status = r.status();
                body = r.body();
                extra = r.headers();
            } else {
                status = 200;
                body = result;
            }
        } catch (HttpError e) {
            status = e.status;
            body = error(e.getMessage(), e.code);
        } catch (RateLimitedException e) {
            status = e.httpStatus();
            body = error(e.getMessage(), e.code());
            extra = Map.of("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        } catch (AuthenticationException e) {
            status = e.httpStatus();
            body = error(e.getMessage(), e.code());
            extra = Map.of("WWW-Authenticate", "Bearer");
        } catch (SevaSyncException e) {
            status = e.httpStatus();
            body = error(e.getMessage(), e.code());
        } catch (Exception e) {
            LOG.error("[{}] {} {} failed", requestId, method, path, e);
            status = 500;
            body = error("Something went wrong on our side (request " + requestId + ")", "INTERNAL");
        }
        extra.forEach((k, v) -> ex.getResponseHeaders().set(k, v));
        send(ex, status, body);
        long ms = (System.nanoTime() - started) / 1_000_000;
        if (status >= 500) {
            LOG.warn("[{}] {} {} -> {} ({} ms)", requestId, method, path, status, ms);
        } else {
            LOG.info("[{}] {} {} -> {} ({} ms)", requestId, method, path, status, ms);
        }
    }

    private void applyCors(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            var h = ex.getResponseHeaders();
            h.set("Access-Control-Allow-Origin", origin);
            h.set("Vary", "Origin");
            h.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, OPTIONS");
            h.set("Access-Control-Allow-Headers", "Authorization, Content-Type, Last-Event-ID");
            h.set("Access-Control-Expose-Headers", "X-Request-Id, Retry-After");
            h.set("Access-Control-Max-Age", "600");
        }
    }

    // ------------------------------------------------------------------ output

    private static void send(HttpExchange ex, int status, Object body) throws IOException {
        var h = ex.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", "no-store");
        h.set("Referrer-Policy", "no-referrer");
        if (status == 204 || body == null) {
            ex.sendResponseHeaders(status == 200 ? 204 : status, -1);
            ex.close();
            return;
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        h.set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, body);
    }

    static Map<String, Object> error(String message, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        m.put("code", code);
        return m;
    }

    /** Routing-level failure (404, 405) that is not a business exception. */
    private static final class HttpError extends Exception {
        private static final long serialVersionUID = 1L;
        private final int status;
        private final String code;

        HttpError(int status, String message, String code) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
