package com.sevasync.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Serves the built React app ({@code frontend/dist}) so the whole platform runs from one JAR on one port.
 * Paths are normalised and confined to the root directory; unknown routes fall back to {@code index.html}
 * (single-page app routing). Hashed assets are cached for a year, the HTML never.
 */
final class StaticFiles {
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("glb", "model/gltf-binary"),
            Map.entry("hdr", "application/octet-stream"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("webmanifest", "application/manifest+json"));

    /** Content Security Policy for the app shell: only our own scripts; inline styles are needed by animations. */
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; "
            + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";

    private final Path root;

    StaticFiles(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    boolean isAvailable() {
        return Files.isRegularFile(root.resolve("index.html"));
    }

    void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }
        String raw = ex.getRequestURI().getPath();
        Path file = root.resolve(raw.replaceFirst("^/+", "")).normalize();
        if (!file.startsWith(root)) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        boolean spaFallback = false;
        if (!Files.isRegularFile(file)) {
            if (raw.contains(".") && !raw.endsWith(".html")) {
                ex.sendResponseHeaders(404, -1); // a missing asset, not a client-side route
                ex.close();
                return;
            }
            file = root.resolve("index.html");
            spaFallback = true;
        }
        String name = file.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        var h = ex.getResponseHeaders();
        h.set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        if (ext.equals("html") || spaFallback) {
            h.set("Cache-Control", "no-cache");
            h.set("Content-Security-Policy", CSP);
            h.set("X-Frame-Options", "DENY");
        } else if (raw.startsWith("/assets/")) {
            h.set("Cache-Control", "public, max-age=31536000, immutable");
        } else {
            h.set("Cache-Control", "public, max-age=3600");
        }
        byte[] bytes = Files.readAllBytes(file);
        if (method.equals("HEAD")) {
            ex.sendResponseHeaders(200, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
