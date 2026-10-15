package com.sevasync.api.routes;

import com.sevasync.SevaSyncSystem;
import com.sevasync.api.Access;
import com.sevasync.api.Router;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.RecipientType;
import com.sevasync.model.Role;
import com.sevasync.model.VehicleType;
import com.sevasync.util.TimeUtil;
import com.sevasync.validation.SafetyValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Endpoints anyone may call: health, landing-page figures, the anonymised network, and form metadata. */
public class PublicRoutes implements RouteModule {
    private final SevaSyncSystem sys;

    public PublicRoutes(SevaSyncSystem sys) {
        this.sys = sys;
    }

    @Override
    public void register(Router r) {
        r.get("/api/health", Access.PUBLIC, req -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", "UP");
            m.put("version", SevaSyncSystem.VERSION);
            m.put("time", TimeUtil.now(sys.clock()));
            return m;
        });
        r.get("/api/public/stats", Access.PUBLIC, req -> sys.reports().publicStats());
        r.get("/api/public/network", Access.PUBLIC, req -> sys.network().snapshot(null));
        r.get("/api/meta", Access.PUBLIC, req -> meta());
    }

    private static Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (FoodCategory c : FoodCategory.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", c);
            x.put("maxShelfLifeMinutes", c.getMaxShelfLifeMinutes());
            x.put("kgPerServing", c.getKgPerServing());
            categories.add(x);
        }
        List<Map<String, Object>> vehicles = new ArrayList<>();
        for (VehicleType v : VehicleType.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", v);
            x.put("speedKmh", v.getSpeedKmh());
            x.put("maxServings", v.getMaxServings());
            vehicles.add(x);
        }
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Role role : Role.values()) {
            if (role.isSelfRegistrable()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", role);
                x.put("name", role.getDisplayName());
                x.put("needsVerification", role.getInitialStatus() != com.sevasync.model.AccountStatus.ACTIVE);
                roles.add(x);
            }
        }
        m.put("roles", roles);
        m.put("categories", categories);
        m.put("vehicles", vehicles);
        m.put("donorTypes", DonorType.values());
        m.put("recipientTypes", RecipientType.values());
        m.put("needLevels", NeedLevel.values());
        m.put("minRemainingMinutes", SafetyValidator.MIN_REMAINING_MINUTES);
        m.put("maxServings", SafetyValidator.MAX_SERVINGS);
        return m;
    }
}
