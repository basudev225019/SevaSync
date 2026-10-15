package com.sevasync.storage;

import com.sevasync.database.DatabaseManager;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.Admin;
import com.sevasync.model.Donor;
import com.sevasync.model.DonorType;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.LocatedUser;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.VehicleType;
import com.sevasync.model.Volunteer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * All participants in one table (single-table inheritance). {@link #map} is a small factory that turns each row
 * into the right {@link User} subclass, so callers always receive a properly typed, polymorphic object.
 */
public class SqliteUserRepository extends AbstractSqliteRepository<User> {

    public SqliteUserRepository(DatabaseManager db) {
        super(db, "users", "id", "role", "status", "name", "email", "phone", "address", "lat", "lng", "donor_type",
                "recipient_type", "need_level", "daily_capacity", "vehicle", "created_at");
    }

    @Override
    protected String orderBy() {
        return "created_at, id";
    }

    @Override
    protected User map(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        Role role = Role.valueOf(rs.getString("role"));
        AccountStatus status = AccountStatus.valueOf(rs.getString("status"));
        String name = rs.getString("name");
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        String address = rs.getString("address");
        Double lat = nullableDouble(rs, "lat");
        Double lng = nullableDouble(rs, "lng");
        GeoPoint location = lat == null || lng == null ? null : new GeoPoint(lat, lng);
        LocalDateTime created = time(rs, "created_at");
        return switch (role) {
            case ADMIN -> new Admin(id, name, email, phone, status, created);
            case DONOR -> new Donor(id, name, email, phone, address, location,
                    DonorType.valueOf(rs.getString("donor_type")), status, created);
            case RECIPIENT -> new Recipient(id, name, email, phone, address, location,
                    RecipientType.valueOf(rs.getString("recipient_type")),
                    NeedLevel.valueOf(rs.getString("need_level")), rs.getInt("daily_capacity"), status, created);
            case VOLUNTEER -> new Volunteer(id, name, email, phone, address, location,
                    VehicleType.valueOf(rs.getString("vehicle")), status, created);
        };
    }

    @Override
    protected Object[] values(User u) {
        GeoPoint loc = u instanceof LocatedUser lu ? lu.getLocation() : null;
        Object donorType = u instanceof Donor d ? d.getType() : null;
        Object recipientType = null;
        Object needLevel = null;
        Object capacity = null;
        if (u instanceof Recipient r) {
            recipientType = r.getType();
            needLevel = r.getNeedLevel();
            capacity = r.getDailyCapacity();
        }
        Object vehicle = u instanceof Volunteer v ? v.getVehicle() : null;
        return new Object[]{u.getId(), u.getRole(), u.getStatus(), u.getName(), u.getEmail(), u.getPhone(),
                u.getAddress(), loc == null ? null : loc.lat(), loc == null ? null : loc.lng(), donorType,
                recipientType, needLevel, capacity, vehicle, u.getCreatedAt()};
    }

    public Optional<User> findByEmail(String email) {
        return where("email = ?", email).stream().findFirst();
    }

    public List<User> findByRole(Role role) {
        return where("role = ?", role);
    }

    public List<User> findByRoleAndStatus(Role role, AccountStatus status) {
        return where("role = ? AND status = ?", role, status);
    }

    public long countByRole(Role role) {
        return db.queryLong("SELECT COUNT(*) FROM users WHERE role = ?", role);
    }

    public long countByRoleAndStatus(Role role, AccountStatus status) {
        return db.queryLong("SELECT COUNT(*) FROM users WHERE role = ? AND status = ?", role, status);
    }

    /** Next id for a new user of {@code role}, e.g. {@code VOL-0003}. */
    public String nextId(Role role) {
        return generateId(role.getIdPrefix());
    }
}
