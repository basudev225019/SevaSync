package com.sevasync.api.routes;

import com.sevasync.SevaSyncSystem;
import com.sevasync.api.Access;
import com.sevasync.api.Response;
import com.sevasync.api.Router;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.Permission;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.simulation.Scenario;
import com.sevasync.storage.AuditLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coordinator tools: matching, benchmark, users, reports, audit, backups. */
public class AdminRoutes implements RouteModule {
    private final SevaSyncSystem sys;

    public AdminRoutes(SevaSyncSystem sys) {
        this.sys = sys;
    }

    @Override
    public void register(Router r) {
        // ---- matching
        r.get("/api/matching/settings", Access.require(Permission.RUN_MATCHING),
                req -> sys.matching().describeSettings());
        r.put("/api/matching/settings", Access.require(Permission.MANAGE_SETTINGS), req -> {
            sys.matching().configure(req.user(), req.str("strategy"), req.optBool("autoMatch"));
            return sys.matching().describeSettings();
        });
        r.post("/api/matching/preview", Access.require(Permission.RUN_MATCHING),
                req -> sys.matching().preview(req.user(), req.str("strategy")).toMap());
        r.post("/api/matching/run", Access.require(Permission.RUN_MATCHING),
                req -> sys.matching().run(req.user(), req.str("strategy")).toMap());
        r.post("/api/matching/benchmark", Access.require(Permission.RUN_BENCHMARK), req -> {
            Scenario.Config d = Scenario.Config.defaults();
            Scenario.Config cfg = new Scenario.Config(
                    or(req.optInt("donors"), d.donors()), or(req.optInt("recipients"), d.recipients()),
                    or(req.optInt("volunteers"), d.volunteers()), or(req.optInt("batches"), d.batches()));
            int runs = or(req.optInt("runs"), 30);
            long seed = req.optInt("seed") == null ? 2026L : req.optInt("seed");
            return sys.benchmark().run(runs, seed, cfg);
        });

        // ---- users
        r.get("/api/users", Access.require(Permission.MANAGE_USERS), req -> {
            List<User> list = sys.users().list(req.user(), req.queryEnum(Role.class, "role"),
                    req.queryEnum(AccountStatus.class, "status"));
            return list.stream().map(User::toMap).toList();
        });
        r.post("/api/users", Access.require(Permission.MANAGE_USERS), req -> {
            Role role = req.requiredEnum(Role.class, "role");
            User u = sys.auth().createAccount(role, AccountStatus.ACTIVE, AuthRoutes.registration(req),
                    req.user().getId());
            return Response.created(u.toMap());
        });
        r.patch("/api/users/{id}", Access.require(Permission.MANAGE_USERS), req ->
                sys.users().updateByAdmin(req.user(), req.path("id"), AuthRoutes.profileUpdate(req)).toMap());
        r.post("/api/users/{id}/status", Access.require(Permission.MANAGE_USERS), req ->
                sys.users().setStatus(req.user(), req.path("id"), req.requiredEnum(AccountStatus.class, "status"))
                        .toMap());

        // ---- reports and audit
        r.get("/api/reports/impact", Access.require(Permission.VIEW_REPORTS), req -> sys.reports().overall());
        r.get("/api/audit", Access.require(Permission.VIEW_AUDIT), req -> {
            int limit = req.queryInt("limit", 100, 1, 1000);
            return sys.audit().tail(limit, req.query("entityType"), req.query("entityId")).stream()
                    .map(AuditLogger.AuditEntry::toMap).toList();
        });
        r.get("/api/audit/verify", Access.require(Permission.VIEW_AUDIT), req -> sys.audit().verify().toMap());

        // ---- backups
        r.get("/api/admin/backups", Access.require(Permission.MANAGE_BACKUPS), req -> sys.backups().listSnapshots());
        r.post("/api/admin/backups", Access.require(Permission.MANAGE_BACKUPS), req -> {
            String file = sys.backups().snapshot().getFileName().toString();
            sys.audit().log(req.user().getId(), "BACKUP_CREATED", "System", "database", file);
            return Response.created(Map.of("file", file));
        });
        r.post("/api/admin/exports", Access.require(Permission.MANAGE_BACKUPS), req -> {
            String folder = sys.storage().getDataDir().relativize(sys.backups().exportCsv()).toString();
            sys.audit().log(req.user().getId(), "CSV_EXPORTED", "System", "database", folder);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("folder", folder.replace('\\', '/'));
            return Response.created(m);
        });
    }

    private static int or(Integer v, int fallback) {
        return v == null ? fallback : v;
    }
}
