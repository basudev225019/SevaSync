package com.sevasync.api.routes;

import com.sevasync.api.Router;

/** A group of related endpoints that registers itself with the router. */
public interface RouteModule {
    void register(Router router);
}
