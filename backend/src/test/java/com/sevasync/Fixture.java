package com.sevasync;

import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.Admin;
import com.sevasync.model.Dispatch;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.VehicleType;
import com.sevasync.model.Volunteer;
import com.sevasync.security.RegistrationRequest;
import com.sevasync.service.DonationService;
import com.sevasync.storage.AuditLogger;
import com.sevasync.util.TimeUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Throw-away SevaSync in a temp directory with a controllable clock (10:00 UTC on 2026-10-09), a fast password
 * work factor, a fixed server secret, a coordinator account and auto-matching switched off (tests run matching
 * explicitly, so they stay deterministic).
 */
final class Fixture implements AutoCloseable {
    static final String PASSWORD = "Passw0rd!";
    static final String SECRET = Base64.getEncoder()
            .encodeToString("sevasync-test-secret-0123456789ab".getBytes(StandardCharsets.UTF_8));

    final Path dir;
    final TestClock clock = new TestClock(Instant.parse("2026-10-09T10:00:00Z"));
    SevaSyncSystem sys;
    final Admin admin;
    private int counter;

    Fixture() {
        try {
            dir = Files.createTempDirectory("sevasync-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        sys = open();
        try {
            admin = (Admin) create(Role.ADMIN, "Test Coordinator", null, null, null, null, null, null);
            sys.matching().configure(admin, null, false);
        } catch (SevaSyncException e) {
            throw new IllegalStateException(e);
        }
    }

    private SevaSyncSystem open() {
        return new SevaSyncSystem(new SevaSyncSystem.Options(dir, clock, 1000, SECRET));
    }

    /** Re-open the system on the same directory (simulates an application restart). */
    void restart() {
        sys.close();
        sys = open();
    }

    LocalDateTime now() {
        return TimeUtil.now(clock);
    }

    Donor donor(String name, double northKm, double eastKm) throws SevaSyncException {
        return (Donor) create(Role.DONOR, name, GeoPoint.CAMPUS.offsetKm(northKm, eastKm), "HOSTEL_MESS", null, null,
                null, null);
    }

    Donor donor() throws SevaSyncException {
        return donor("Test Mess", 0, 0);
    }

    Recipient recipient(String name, NeedLevel need, int capacity, double northKm, double eastKm)
            throws SevaSyncException {
        return (Recipient) create(Role.RECIPIENT, name, GeoPoint.CAMPUS.offsetKm(northKm, eastKm), null, "SHELTER",
                need.name(), capacity, null);
    }

    Recipient recipient(String name, NeedLevel need, int capacity) throws SevaSyncException {
        return recipient(name, need, capacity, 1, 1);
    }

    Volunteer volunteer(String name, VehicleType vehicle) throws SevaSyncException {
        return (Volunteer) create(Role.VOLUNTEER, name, GeoPoint.CAMPUS, null, null, null, null, vehicle.name());
    }

    User create(Role role, String name, GeoPoint at, String donorType, String recipientType, String need,
                Integer capacity, String vehicle) throws SevaSyncException {
        counter++;
        RegistrationRequest r = new RegistrationRequest(role.name(), name, "user" + counter + "@test.campus", PASSWORD,
                String.format("98%08d", counter), "Campus", at == null ? null : at.lat(), at == null ? null : at.lng(),
                donorType, recipientType, need, capacity, vehicle);
        return sys.auth().createAccount(role, AccountStatus.ACTIVE, r, AuditLogger.SYSTEM);
    }

    FoodBatch batch(Donor donor, String food, FoodCategory cat, int servings, int preparedMinutesAgo,
                    int shelfLifeMinutes) throws SevaSyncException {
        return sys.donations().logBatch(donor, new DonationService.NewBatch(food, cat, true, servings,
                now().minusMinutes(preparedMinutesAgo), shelfLifeMinutes, ""));
    }

    /** Runs matching as the coordinator and returns the dispatches it created. */
    List<Dispatch> match() throws SevaSyncException {
        List<String> ids = sys.matching().run(admin, null).items().stream().map(m -> (String) m.get("id")).toList();
        return ids.stream().map(id -> sys.dispatchRepo().findById(id).orElseThrow()).toList();
    }

    Dispatch reload(Dispatch d) {
        return sys.dispatchRepo().findById(d.getId()).orElseThrow();
    }

    FoodBatch reload(FoodBatch b) {
        return sys.batchRepo().findById(b.getId()).orElseThrow();
    }

    @Override
    public void close() {
        sys.close();
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort clean-up
        }
    }
}
