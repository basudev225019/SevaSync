package com.sevasync;

import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.AuthenticationException;
import com.sevasync.exception.ConflictException;
import com.sevasync.exception.RateLimitedException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.Permission;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.security.AuthService;
import com.sevasync.security.PasswordHasher;
import com.sevasync.security.RegistrationRequest;
import com.sevasync.service.DonationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityTest {
    private final Fixture f = new Fixture();

    @AfterEach
    void tearDown() {
        f.close();
    }

    private static RegistrationRequest volunteerForm(String email) {
        return new RegistrationRequest("VOLUNTEER", "Arjun Mehta", email, "Cycl1ngFast", "9900000001", "Hostel",
                12.97, 77.59, null, null, null, null, "BICYCLE");
    }

    @Test
    void passwordsAreSaltedAndVerifiedInConstantTime() {
        PasswordHasher h = new PasswordHasher(1000);
        PasswordHasher.Hash a = h.hash("Secr3tPass");
        PasswordHasher.Hash b = h.hash("Secr3tPass");
        assertNotEquals(a.hash(), b.hash(), "same password, different salt, different hash");
        assertTrue(h.verify("Secr3tPass", a));
        assertFalse(h.verify("secr3tpass", a));
        assertTrue(new PasswordHasher(5000).needsRehash(a));
    }

    @Test
    void volunteersAreActiveImmediatelyButOrganisationsWaitForVerification() throws Exception {
        AuthService.AuthResult v = f.sys.auth().register(volunteerForm("arjun@test.campus"));
        assertEquals(AccountStatus.ACTIVE, v.user().getStatus());
        assertTrue(v.user().can(Permission.CLAIM_DISPATCH));

        AuthService.AuthResult d = f.sys.auth().register(new RegistrationRequest("DONOR", "Night Canteen",
                "canteen@test.campus", "Canteen123", "9876500000", "", null, null, "CANTEEN", null, null, null, null));
        assertEquals(AccountStatus.PENDING, d.user().getStatus());
        assertFalse(d.user().can(Permission.LOG_BATCH));
        assertThrows(AccessDeniedException.class, () -> f.sys.donations().logBatch(d.user(),
                new DonationService.NewBatch("Rice", FoodCategory.COOKED_MEAL, true, 10, null, 120, "")));

        f.sys.users().setStatus(f.admin, d.user().getId(), AccountStatus.ACTIVE);
        User verified = f.sys.auth().authenticate(d.token());
        assertTrue(verified.can(Permission.LOG_BATCH));
    }

    @Test
    void administratorsCannotBeCreatedThroughSignUp() {
        RegistrationRequest r = new RegistrationRequest("ADMIN", "Mallory", "m@test.campus", "Passw0rd!",
                "9876511111", "", null, null, null, null, null, null, null);
        assertThrows(AccessDeniedException.class, () -> f.sys.auth().register(r));
    }

    @Test
    void duplicateEmailsAreRejectedCaseInsensitively() throws Exception {
        f.sys.auth().register(volunteerForm("arjun@test.campus"));
        assertThrows(ConflictException.class, () -> f.sys.auth().register(volunteerForm("ARJUN@test.campus")));
    }

    @Test
    void loginIssuesARevocableSession() throws Exception {
        f.sys.auth().register(volunteerForm("arjun@test.campus"));
        AuthService.AuthResult s = f.sys.auth().login("Arjun@Test.Campus", "Cycl1ngFast", "10.0.0.1");
        assertEquals("arjun@test.campus", f.sys.auth().authenticate(s.token()).getEmail());
        f.sys.auth().logout(s.token());
        assertThrows(AuthenticationException.class, () -> f.sys.auth().authenticate(s.token()));
    }

    @Test
    void sessionsExpire() throws Exception {
        AuthService.AuthResult s = f.sys.auth().register(volunteerForm("arjun@test.campus"));
        f.clock.advance(AuthService.SESSION_TTL.plusMinutes(1));
        assertThrows(AuthenticationException.class, () -> f.sys.auth().authenticate(s.token()));
    }

    @Test
    void wrongPasswordsAreThrottledPerAccountAndClient() throws Exception {
        f.sys.auth().register(volunteerForm("arjun@test.campus"));
        for (int i = 0; i < 5; i++) {
            assertThrows(AuthenticationException.class,
                    () -> f.sys.auth().login("arjun@test.campus", "wrong-password1", "10.0.0.9"));
        }
        assertThrows(RateLimitedException.class,
                () -> f.sys.auth().login("arjun@test.campus", "Cycl1ngFast", "10.0.0.9"));
        // a different client is not locked out by someone else's guessing
        f.sys.auth().login("arjun@test.campus", "Cycl1ngFast", "10.0.0.10");
        // and the window slides
        f.clock.advance(Duration.ofMinutes(11));
        f.sys.auth().login("arjun@test.campus", "Cycl1ngFast", "10.0.0.9");
    }

    @Test
    void unknownEmailGivesTheSameAnswerAsAWrongPassword() {
        AuthenticationException e = assertThrows(AuthenticationException.class,
                () -> f.sys.auth().login("nobody@test.campus", "Whatever1", "x"));
        assertEquals("E-mail or password is incorrect", e.getMessage());
    }

    @Test
    void suspensionEndsSessionsAndBlocksSignIn() throws Exception {
        AuthService.AuthResult s = f.sys.auth().register(volunteerForm("arjun@test.campus"));
        f.sys.users().setStatus(f.admin, s.user().getId(), AccountStatus.SUSPENDED);
        assertThrows(AuthenticationException.class, () -> f.sys.auth().authenticate(s.token()));
        assertThrows(AccessDeniedException.class,
                () -> f.sys.auth().login("arjun@test.campus", "Cycl1ngFast", "x"));
    }

    @Test
    void passwordChangeRevokesEverySession() throws Exception {
        AuthService.AuthResult s = f.sys.auth().register(volunteerForm("arjun@test.campus"));
        assertThrows(AuthenticationException.class,
                () -> f.sys.auth().changePassword(s.user(), "wrong", "N3wPassword"));
        f.sys.auth().changePassword(s.user(), "Cycl1ngFast", "N3wPassword");
        assertThrows(AuthenticationException.class, () -> f.sys.auth().authenticate(s.token()));
        f.sys.auth().login("arjun@test.campus", "N3wPassword", "x");
    }

    @Test
    void rolePermissionMatrixIsDisjointWhereItMatters() {
        assertTrue(Role.DONOR.getPermissions().contains(Permission.LOG_BATCH));
        assertFalse(Role.VOLUNTEER.getPermissions().contains(Permission.LOG_BATCH));
        assertFalse(Role.DONOR.getPermissions().contains(Permission.CLAIM_DISPATCH));
        assertFalse(Role.RECIPIENT.getPermissions().contains(Permission.RUN_MATCHING));
        assertTrue(Role.ADMIN.getPermissions().containsAll(EnumSet.of(Permission.RUN_MATCHING,
                Permission.MANAGE_USERS, Permission.VIEW_AUDIT)));
        assertTrue(AccountStatus.SUSPENDED.filter(Role.ADMIN.getPermissions()).isEmpty());
        assertEquals(EnumSet.of(Permission.UPDATE_OWN_PROFILE),
                AccountStatus.PENDING.filter(Role.DONOR.getPermissions()));
    }

    @Test
    void coordinatorsCannotSuspendThemselves() {
        assertThrows(AccessDeniedException.class,
                () -> f.sys.users().setStatus(f.admin, f.admin.getId(), AccountStatus.SUSPENDED));
    }
}
