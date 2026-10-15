package com.sevasync.api.routes;

import com.sevasync.SevaSyncSystem;
import com.sevasync.api.Access;
import com.sevasync.api.Response;
import com.sevasync.api.Router;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Permission;
import com.sevasync.service.DonationService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Surplus food batches. */
public class BatchRoutes implements RouteModule {
    private final SevaSyncSystem sys;

    public BatchRoutes(SevaSyncSystem sys) {
        this.sys = sys;
    }

    @Override
    public void register(Router r) {
        r.get("/api/batches", Access.SIGNED_IN, req -> sys.views().batches(
                sys.donations().list(req.user(), req.queryEnum(BatchStatus.class, "status"))));

        r.post("/api/batches", Access.require(Permission.LOG_BATCH), req -> {
            int minutes;
            if (req.has("shelfLifeMinutes")) {
                minutes = req.requiredInt("shelfLifeMinutes");
            } else if (req.optDouble("shelfLifeHours") != null) {
                minutes = (int) Math.round(req.optDouble("shelfLifeHours") * 60);
            } else {
                throw new InvalidInputException("shelfLifeMinutes (or shelfLifeHours) is required");
            }
            DonationService.NewBatch form = new DonationService.NewBatch(req.requiredStr("foodName"),
                    req.requiredEnum(FoodCategory.class, "category"), Boolean.TRUE.equals(req.optBool("vegetarian")),
                    req.requiredInt("servings"), req.optDateTime("preparedAt"), minutes, req.str("notes"));
            FoodBatch b = sys.donations().logBatch(req.user(), form);
            return Response.created(sys.views().batch(b));
        });

        r.get("/api/batches/{id}", Access.SIGNED_IN, req -> {
            FoodBatch b = sys.donations().get(req.user(), req.path("id"));
            Map<String, Object> m = new LinkedHashMap<>(sys.views().batch(b));
            m.put("dispatches", sys.views().dispatches(sys.dispatchRepo().findByBatch(b.getId()), null));
            m.put("history", sys.audit().tail(50, "FoodBatch", b.getId()).stream()
                    .map(e -> e.toMap()).toList());
            return m;
        });

        r.post("/api/batches/{id}/withdraw", Access.require(Permission.WITHDRAW_BATCH), req ->
                sys.views().batch(sys.donations().withdraw(req.user(), req.path("id"), req.str("reason"))));
    }
}
