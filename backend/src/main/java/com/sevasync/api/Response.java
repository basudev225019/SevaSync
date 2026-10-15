package com.sevasync.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP status plus JSON body (and optional extra headers) returned by a route handler. */
public record Response(int status, Object body, Map<String, String> headers) {

    public static Response ok(Object body) {
        return new Response(200, body, Map.of());
    }

    public static Response created(Object body) {
        return new Response(201, body, Map.of());
    }

    public static Response noContent() {
        return new Response(204, null, Map.of());
    }

    public static Response message(int status, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", text);
        return new Response(status, m, Map.of());
    }
}
