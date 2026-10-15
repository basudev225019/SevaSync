package com.sevasync.util;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Every "now" in the system comes from an injected {@link Clock}, which keeps time-based rules testable. */
public final class TimeUtil {
    private TimeUtil() {
    }

    /** Current local date-time from the injected clock, truncated to whole seconds. */
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Whole minutes from {@code from} to {@code to}; negative when {@code to} lies in the past. */
    public static long minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toMinutes();
    }
}
