package com.sevasync.api.routes;

import com.sevasync.SevaSyncSystem;
import com.sevasync.api.Access;
import com.sevasync.api.Request;
import com.sevasync.api.Response;
import com.sevasync.api.Router;
import com.sevasync.exception.AccessDeniedException;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Permission;
import com.sevasync.model.User;
import com.sevasync.model.Volunteer;
import com.sevasync.service.DispatchService;

import java.util.List;
import java.util.Map;

/** Dispatch life-cycle endpoints for volunteers, recipients and coordinators. */
public class DispatchRoutes implements RouteModule {
    private final SevaSyncSystem sys;

    public DispatchRoutes(SevaSyncSystem sys) {
        this.sys = sys;
    }

    @Override
    public void register(Router r) {
        r.get("/api/dispatches", Access.SIGNED_IN, req -> {
            User u = req.user();
            List<Dispatch> list = sys.dispatches().listFor(u, req.queryEnum(DispatchStatus.class, "status"));
            return sys.views().dispatches(list, u instanceof Volunteer v ? v : null);
        });

        r.get("/api/dispatches/open", Access.require(Permission.VIEW_OPEN_PICKUPS), req -> {
            Volunteer v = req.user(Volunteer.class);
            List<Map<String, Object>> views = sys.views().dispatches(sys.dispatches().openPickups(v), v);
            // nearest pickup first, but anything this volunteer cannot carry goes to the end
            return views.stream().sorted((a, b) -> {
                int fit = Boolean.compare(!(Boolean) a.get("fitsVehicle"), !(Boolean) b.get("fitsVehicle"));
                return fit != 0 ? fit : Double.compare((Double) a.get("distanceToPickupKm"),
                        (Double) b.get("distanceToPickupKm"));
            }).toList();
        });

        r.get("/api/dispatches/{id}", Access.SIGNED_IN, req -> {
            User u = req.user();
            Dispatch d = sys.dispatches().get(u, req.path("id"));
            Map<String, Object> view = sys.views().dispatches(List.of(d), u instanceof Volunteer v ? v : null).get(0);
            view.put("history", sys.audit().tail(30, "Dispatch", d.getId()).stream().map(e -> e.toMap()).toList());
            return view;
        });

        r.post("/api/dispatches", Access.require(Permission.ASSIGN_MANUALLY), req -> Response.created(view(req,
                sys.dispatches().assignManually(req.user(), req.requiredStr("batchId"),
                        req.requiredStr("recipientId"), req.requiredInt("servings")))));

        r.post("/api/dispatches/{id}/claim", Access.require(Permission.CLAIM_DISPATCH), req ->
                view(req, sys.dispatches().claim(req.user(Volunteer.class), req.path("id"))));

        r.post("/api/dispatches/{id}/release", Access.require(Permission.PROGRESS_DISPATCH), req ->
                view(req, sys.dispatches().release(req.user(Volunteer.class), req.path("id"))));

        r.post("/api/dispatches/{id}/pickup", Access.require(Permission.PROGRESS_DISPATCH), req ->
                view(req, sys.dispatches().pickUp(req.user(Volunteer.class), req.path("id"))));

        r.post("/api/dispatches/{id}/deliver", Access.require(Permission.PROGRESS_DISPATCH), req ->
                view(req, sys.dispatches().deliver(req.user(Volunteer.class), req.path("id"))));

        r.post("/api/dispatches/{id}/confirm", Access.require(Permission.CONFIRM_RECEIPT), req ->
                view(req, sys.dispatches().confirmReceipt(req.user(), req.path("id"),
                        DispatchService.parseRating(req.body().get("rating")), req.str("feedback"))));

        r.post("/api/dispatches/{id}/cancel", Access.require(Permission.CANCEL_ANY_DISPATCH), req ->
                view(req, sys.dispatches().cancel(req.user(), req.path("id"), req.str("reason"))));
    }

    private Map<String, Object> view(Request req, Dispatch d) throws AccessDeniedException {
        User u = req.user();
        return sys.views().dispatches(List.of(d), u instanceof Volunteer v ? v : null).get(0);
    }
}
