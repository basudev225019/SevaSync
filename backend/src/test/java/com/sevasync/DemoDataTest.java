package com.sevasync;

import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Role;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataTest {

    @Test
    void demoCampusHasEveryRoleHistoryAndLiveWork() throws Exception {
        Path dir = Files.createTempDirectory("sevasync-demo");
        try (SevaSyncSystem sys = new SevaSyncSystem(new SevaSyncSystem.Options(dir,
                Clock.system(ZoneId.of("Asia/Kolkata")), 1000, Fixture.SECRET))) {
            assertTrue(DemoData.load(sys));
            assertFalse(DemoData.load(sys), "loading twice must not duplicate anything");

            assertEquals(1, sys.userRepo().countByRole(Role.ADMIN));
            assertEquals(5, sys.userRepo().countByRole(Role.DONOR));
            assertEquals(5, sys.userRepo().countByRole(Role.RECIPIENT));
            assertEquals(4, sys.userRepo().countByRole(Role.VOLUNTEER));

            assertTrue(sys.dispatchRepo().findByStatus(DispatchStatus.DELIVERED).size() > 20, "history");
            assertFalse(sys.dispatchRepo().findByStatus(DispatchStatus.PICKED_UP).isEmpty(), "someone en route");
            assertFalse(sys.dispatchRepo().findByStatus(DispatchStatus.CLAIMED).isEmpty(), "someone heading out");
            assertTrue(sys.audit().verify().valid());

            Map<String, Object> stats = sys.reports().publicStats();
            assertTrue(((Number) stats.get("mealsServed")).intValue() > 500);
            sys.auth().login(DemoData.ADMIN_EMAIL, DemoData.PASSWORD, "test");
        }
    }
}
