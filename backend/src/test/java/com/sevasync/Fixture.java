package com.sevasync;

import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.util.TimeUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.stream.Stream;

/** Throw-away SevaSync instance in a temp directory, with a controllable clock (12:00 UTC on 2026-10-09). */
final class Fixture implements AutoCloseable {
    final Path dir;
    final TestClock clock = new TestClock(Instant.parse("2026-10-09T12:00:00Z"));
    final boolean useSqlite;
    SevaSyncSystem sys;

    Fixture() {
        this(true);
    }

    Fixture(boolean useSqlite) {
        this.useSqlite = useSqlite;
        try {
            dir = Files.createTempDirectory("sevasync-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        sys = new SevaSyncSystem(dir, clock, useSqlite);
    }

    /** Re-open the system on the same directory (simulates an application restart). */
    void restart() {
        sys.close();
        sys = new SevaSyncSystem(dir, clock, useSqlite);
    }

    LocalDateTime now() {
        return TimeUtil.now(clock);
    }

    String donor() throws SevaSyncException {
        return sys.donations().registerDonor("Test Mess", "9876543210", "", "Campus", DonorType.HOSTEL_MESS).getId();
    }

    Recipient recipient(String name, NeedLevel need, int capacity) throws SevaSyncException {
        return sys.recipients().register(name, "9812345678", "", "Town", RecipientType.SHELTER, need, capacity);
    }

    FoodBatch batch(String donorId, String food, FoodCategory cat, int servings, int preparedMinutesAgo,
                    int shelfLifeMinutes) throws SevaSyncException {
        return sys.donations().submitBatch(donorId, food, cat, true, servings,
                now().minusMinutes(preparedMinutesAgo), shelfLifeMinutes);
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
