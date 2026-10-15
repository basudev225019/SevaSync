package com.sevasync.api.routes;

import com.sevasync.SevaSyncSystem;
import com.sevasync.api.Access;
import com.sevasync.api.Router;
import com.sevasync.model.Permission;

/** The full live network (with volunteers) for signed-in participants. */
public class NetworkRoutes implements RouteModule {
    private final SevaSyncSystem sys;

    public NetworkRoutes(SevaSyncSystem sys) {
        this.sys = sys;
    }

    @Override
    public void register(Router r) {
        r.get("/api/network", Access.require(Permission.VIEW_NETWORK), req -> sys.network().snapshot(req.user()));
    }
}
