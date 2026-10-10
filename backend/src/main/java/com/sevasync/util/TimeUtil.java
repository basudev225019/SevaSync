package com.sevasync.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class TimeUtil {
    private TimeUtil() {
    }

    /** Current local date-time from the injected clock, truncated to whole seconds. */
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
