package com.sevasync.api.routes;

import com.sevasync.SevaSyncSystem;
import com.sevasync.api.Access;
import com.sevasync.api.Request;
import com.sevasync.api.Response;
import com.sevasync.api.Router;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.User;
import com.sevasync.security.RegistrationRequest;
import com.sevasync.service.UserService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sign-up, sign-in, sign-out and the signed-in user's own profile. */
public class AuthRoutes implements RouteModule {
    private final SevaSyncSystem sys;

    public AuthRoutes(SevaSyncSystem sys) {
        this.sys = sys;
    }

    @Override
    public void register(Router r) {
        r.post("/api/auth/register", Access.PUBLIC,
                req -> Response.created(sys.auth().register(registration(req)).toMap()));

        r.post("/api/auth/login", Access.PUBLIC,
                req -> sys.auth().login(req.requiredStr("email"), req.requiredStr("password"), req.clientKey())
                        .toMap());

        r.post("/api/auth/logout", Access.SIGNED_IN, req -> {
            sys.auth().logout(req.bearerToken());
            return Response.noContent();
        });

        r.get("/api/auth/me", Access.SIGNED_IN, req -> me(req.user()));

        r.post("/api/auth/password", Access.SIGNED_IN, req -> {
            sys.auth().changePassword(req.user(), req.requiredStr("currentPassword"), req.requiredStr("newPassword"));
            return Response.message(200, "Password changed. Please sign in again on your other devices.");
        });

        r.patch("/api/me", Access.SIGNED_IN, req ->
                sys.users().updateOwnProfile(req.user(), profileUpdate(req)).toMap());

        r.get("/api/me/impact", Access.SIGNED_IN, req -> sys.reports().forUser(req.user()));
    }

    private Map<String, Object> me(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", u.toMap());
        m.put("impact", sys.reports().forUser(u));
        return m;
    }

    static RegistrationRequest registration(Request req) throws InvalidInputException {
        return new RegistrationRequest(req.requiredStr("role"), req.str("name"), req.str("email"), req.str("password"),
                req.str("phone"), req.str("address"), req.optDouble("lat"), req.optDouble("lng"),
                req.str("donorType"), req.str("recipientType"), req.str("needLevel"), req.optInt("dailyCapacity"),
                req.str("vehicle"));
    }

    static UserService.ProfileUpdate profileUpdate(Request req) throws InvalidInputException {
        return new UserService.ProfileUpdate(req.str("name"), req.str("phone"), req.str("address"),
                req.optDouble("lat"), req.optDouble("lng"), req.str("donorType"), req.str("needLevel"),
                req.optInt("dailyCapacity"), req.str("vehicle"));
    }
}
