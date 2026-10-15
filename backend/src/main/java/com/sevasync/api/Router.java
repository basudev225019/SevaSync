package com.sevasync.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Method + path-template routing ({@code /api/dispatches/{id}/claim}). Every route declares its {@link Access}
 * rule at registration, which makes the whole permission model readable in the route modules.
 */
public final class Router {

    /** A registered route. */
    public record Route(String method, String[] segments, Access access, Handler handler) {
        Map<String, String> match(String[] path) {
            if (path.length != segments.length) {
                return null;
            }
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < segments.length; i++) {
                String s = segments[i];
                if (s.startsWith("{") && s.endsWith("}")) {
                    params.put(s.substring(1, s.length() - 1), path[i]);
                } else if (!s.equals(path[i])) {
                    return null;
                }
            }
            return params;
        }
    }

    /** Result of a lookup: the route and its path parameters, or the allowed methods for a 405. */
    public record Match(Route route, Map<String, String> params, Set<String> allowedMethods) {
        public boolean found() {
            return route != null;
        }
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String path, Access access, Handler h) {
        return add("GET", path, access, h);
    }

    public Router post(String path, Access access, Handler h) {
        return add("POST", path, access, h);
    }

    public Router put(String path, Access access, Handler h) {
        return add("PUT", path, access, h);
    }

    public Router patch(String path, Access access, Handler h) {
        return add("PATCH", path, access, h);
    }

    public Router add(String method, String path, Access access, Handler h) {
        routes.add(new Route(method, split(path), access, h));
        return this;
    }

    public Match find(String method, String path) {
        String[] parts = split(path);
        Set<String> allowed = new LinkedHashSet<>();
        // literal segments win over parameters: /dispatches/open must not be read as /dispatches/{id}
        Route best = null;
        Map<String, String> bestParams = null;
        int bestLiterals = -1;
        for (Route r : routes) {
            Map<String, String> params = r.match(parts);
            if (params == null) {
                continue;
            }
            allowed.add(r.method());
            if (r.method().equals(method)) {
                int literals = r.segments().length - params.size();
                if (literals > bestLiterals) {
                    best = r;
                    bestParams = params;
                    bestLiterals = literals;
                }
            }
        }
        return new Match(best, bestParams, allowed);
    }

    public List<Route> routes() {
        return List.copyOf(routes);
    }

    private static String[] split(String path) {
        return java.util.Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
}
