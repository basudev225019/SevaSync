package com.sevasync.service;

import com.sevasync.database.DatabaseManager;
import com.sevasync.event.Audience;
import com.sevasync.event.EventBus;
import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.DonorType;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.LocatedUser;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Permission;
import com.sevasync.model.Recipient;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.VehicleType;
import com.sevasync.model.Volunteer;
import com.sevasync.security.AuthService;
import com.sevasync.security.Guard;
import com.sevasync.security.UserFactory;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.SqliteUserRepository;
import com.sevasync.util.InputSanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Account administration (verification, suspension) and self-service profile updates. */
public class UserService {

    /** Partial profile update: {@code null} means "leave unchanged". */
    public record ProfileUpdate(String name, String phone, String address, Double lat, Double lng, String donorType,
                                String needLevel, Integer dailyCapacity, String vehicle) {
    }

    private final DatabaseManager db;
    private final SqliteUserRepository users;
    private final AuthService auth;
    private final AuditLogger audit;
    private final EventBus events;

    public UserService(DatabaseManager db, SqliteUserRepository users, AuthService auth, AuditLogger audit,
                       EventBus events) {
        this.db = db;
        this.users = users;
        this.auth = auth;
        this.audit = audit;
        this.events = events;
    }

    public User get(String id) throws EntityNotFoundException {
        return users.findById(id).orElseThrow(() -> new EntityNotFoundException("User", id));
    }

    public List<User> list(User admin, Role role, AccountStatus status) throws SevaSyncException {
        Guard.require(admin, Permission.MANAGE_USERS);
        List<User> all = role == null ? users.findAll() : users.findByRole(role);
        if (status == null) {
            return all;
        }
        List<User> out = new ArrayList<>();
        for (User u : all) {
            if (u.getStatus() == status) {
                out.add(u);
            }
        }
        return out;
    }

    /** Verify (ACTIVE), suspend (SUSPENDED) or reset to PENDING. Suspension ends all of the user's sessions. */
    public User setStatus(User admin, String userId, AccountStatus status) throws SevaSyncException {
        Guard.require(admin, Permission.MANAGE_USERS);
        if (admin.getId().equals(userId)) {
            throw new AccessDeniedException("You cannot change the status of your own account");
        }
        return db.inTransaction(() -> {
            User u = get(userId);
            AccountStatus before = u.getStatus();
            u.setStatus(status);
            users.save(u);
            if (status == AccountStatus.SUSPENDED) {
                auth.logoutEverywhere(userId);
            }
            String verb = status == AccountStatus.ACTIVE ? (before == AccountStatus.PENDING ? "verified" : "reactivated")
                    : status == AccountStatus.SUSPENDED ? "suspended" : "set to pending";
            audit.log(admin.getId(), "USER_STATUS_CHANGED", "User", userId, before + " -> " + status);
            events.publish("USER_STATUS_CHANGED", "User", userId, u.getName() + " was " + verb,
                    Map.of("status", status), Audience.users(userId).and(Audience.admins()));
            return u;
        });
    }

    /** A user editing their own profile. Role-specific fields only apply to the matching role. */
    public User updateOwnProfile(User self, ProfileUpdate p) throws SevaSyncException {
        Guard.require(self, Permission.UPDATE_OWN_PROFILE);
        return db.inTransaction(() -> {
            User u = get(self.getId());
            apply(u, p, false);
            users.save(u);
            audit.log(self.getId(), "PROFILE_UPDATED", "User", u.getId(), "");
            return u;
        });
    }

    /** An administrator adjusting a recipient's need level or capacity (e.g. after a field visit). */
    public User updateByAdmin(User admin, String userId, ProfileUpdate p) throws SevaSyncException {
        Guard.require(admin, Permission.MANAGE_USERS);
        return db.inTransaction(() -> {
            User u = get(userId);
            apply(u, p, true);
            users.save(u);
            audit.log(admin.getId(), "PROFILE_UPDATED", "User", u.getId(), "by administrator");
            events.publish("PROFILE_UPDATED", "User", userId, u.getName() + "'s profile was updated", Map.of(),
                    Audience.users(userId).and(Audience.admins()));
            return u;
        });
    }

    private static void apply(User u, ProfileUpdate p, boolean byAdmin) throws InvalidInputException {
        if (p.name() != null) {
            u.setName(InputSanitizer.text(p.name(), "Name", 80, true));
        }
        if (p.phone() != null) {
            u.setPhone(InputSanitizer.phone(p.phone(), "Phone"));
        }
        if (p.address() != null) {
            u.setAddress(InputSanitizer.text(p.address(), "Address", 160, false));
        }
        if ((p.lat() != null || p.lng() != null) && u instanceof LocatedUser lu) {
            GeoPoint g = UserFactory.location(p.lat(), p.lng());
            lu.setLocation(g);
        }
        if (u instanceof Donor d && p.donorType() != null) {
            d.setType(InputSanitizer.enumValue(DonorType.class, p.donorType(), "donorType"));
        }
        if (u instanceof Volunteer v && p.vehicle() != null) {
            v.setVehicle(InputSanitizer.enumValue(VehicleType.class, p.vehicle(), "vehicle"));
        }
        if (u instanceof Recipient r) {
            if (p.dailyCapacity() != null) {
                r.setDailyCapacity(InputSanitizer.range(p.dailyCapacity(), "Daily capacity", 1,
                        Recipient.MAX_DAILY_CAPACITY));
            }
            if (p.needLevel() != null) {
                // a recipient may lower its own urgency, but only a coordinator can raise it (prevents gaming)
                NeedLevel requested = InputSanitizer.enumValue(NeedLevel.class, p.needLevel(), "needLevel");
                if (!byAdmin && requested.getWeight() > r.getNeedLevel().getWeight()) {
                    throw new InvalidInputException("Ask a coordinator to raise your need level");
                }
                r.setNeedLevel(requested);
            }
        }
    }
}
