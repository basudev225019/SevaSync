package com.sevasync.security;

import com.sevasync.database.DatabaseManager;
import com.sevasync.event.Audience;
import com.sevasync.event.EventBus;
import com.sevasync.exception.AccessDeniedException;
import com.sevasync.exception.AuthenticationException;
import com.sevasync.exception.ConflictException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.CredentialRepository;
import com.sevasync.storage.SessionRepository;
import com.sevasync.storage.SqliteUserRepository;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.TimeUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sign-up, sign-in and session handling.
 *
 * <p>Tokens are 256-bit random values handed to the client once; the server stores only their SHA-256, so a leaked
 * database does not leak usable sessions. Sign-out deletes the session server-side (real revocation).
 */
public class AuthService {
    public static final Duration SESSION_TTL = Duration.ofHours(12);

    public record AuthResult(String token, User user, LocalDateTime expiresAt) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("token", token);
            m.put("expiresAt", expiresAt);
            m.put("user", user.toMap());
            return m;
        }
    }

    private final DatabaseManager db;
    private final SqliteUserRepository users;
    private final CredentialRepository credentials;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final AuditLogger audit;
    private final EventBus events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** Hash of a random password, verified against when the e-mail is unknown so timing does not reveal accounts. */
    private final PasswordHasher.Hash decoy;

    public AuthService(DatabaseManager db, SqliteUserRepository users, CredentialRepository credentials,
                       SessionRepository sessions, PasswordHasher hasher, LoginThrottle throttle, AuditLogger audit,
                       EventBus events, Clock clock) {
        this.db = db;
        this.users = users;
        this.credentials = credentials;
        this.sessions = sessions;
        this.hasher = hasher;
        this.throttle = throttle;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.decoy = hasher.hash(Long.toHexString(random.nextLong()) + "x1");
    }

    /** Public self-registration (donor, recipient or volunteer). Returns a signed-in session. */
    public AuthResult register(RegistrationRequest request) throws SevaSyncException {
        Role role = UserFactory.parseRole(request.role());
        if (!role.isSelfRegistrable()) {
            throw new AccessDeniedException(role + " accounts cannot be created through sign-up");
        }
        User user = createAccount(role, role.getInitialStatus(), request, null);
        return openSession(user);
    }

    /** Creates an account of any role (used by administrators, demo data and the bootstrap admin). */
    public User createAccount(Role role, AccountStatus status, RegistrationRequest request, String actorId)
            throws SevaSyncException {
        String password = InputSanitizer.password(request.password());
        String email = InputSanitizer.email(request.email(), "E-mail");
        PasswordHasher.Hash hash = hasher.hash(password); // slow on purpose, so done outside the DB lock
        return db.inTransaction(() -> {
            if (users.findByEmail(email).isPresent()) {
                throw new ConflictException("An account with this e-mail already exists");
            }
            LocalDateTime now = TimeUtil.now(clock);
            User user = UserFactory.create(users.nextId(role), role, status, request, now);
            users.save(user);
            credentials.save(user.getId(), hash, now);
            audit.log(actorId == null ? user.getId() : actorId, "USER_REGISTERED", "User", user.getId(),
                    user.describe() + " (" + status + ")");
            events.publish("USER_REGISTERED", "User", user.getId(), user.getName() + " joined as "
                    + role.getDisplayName(), Map.of("role", role, "status", status), Audience.admins());
            return user;
        });
    }

    public AuthResult login(String rawEmail, String password, String clientKey) throws SevaSyncException {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || password == null || password.isEmpty()) {
            throw new InvalidInputException("E-mail and password are required");
        }
        String throttleKey = email + "|" + clientKey;
        throttle.check(throttleKey);

        User user = users.findByEmail(email).orElse(null);
        PasswordHasher.Hash stored = user == null ? null : credentials.find(user.getId()).orElse(null);
        boolean ok = hasher.verify(password, stored == null ? decoy : stored) && stored != null;
        if (!ok) {
            throttle.recordFailure(throttleKey);
            throw new AuthenticationException("E-mail or password is incorrect");
        }
        if (!user.getStatus().canSignIn()) {
            throw new AccessDeniedException("This account has been suspended. Contact the coordinator.");
        }
        throttle.recordSuccess(throttleKey);
        if (hasher.needsRehash(stored)) {
            credentials.save(user.getId(), hasher.hash(password), TimeUtil.now(clock));
        }
        audit.log(user.getId(), "USER_SIGNED_IN", "User", user.getId(), "");
        return openSession(user);
    }

    /** Resolves a bearer token to its user, or fails with 401. */
    public User authenticate(String token) throws AuthenticationException {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("Sign in required");
        }
        String tokenHash = sha256(token);
        SessionRepository.Session s = sessions.find(tokenHash)
                .orElseThrow(() -> new AuthenticationException("Session expired or signed out"));
        LocalDateTime now = TimeUtil.now(clock);
        if (!s.expiresAt().isAfter(now)) {
            sessions.delete(tokenHash);
            throw new AuthenticationException("Session expired");
        }
        User user = users.findById(s.userId())
                .orElseThrow(() -> new AuthenticationException("Account no longer exists"));
        if (!user.getStatus().canSignIn()) {
            sessions.delete(tokenHash);
            throw new AuthenticationException("Account suspended");
        }
        return user;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.delete(sha256(token));
        }
    }

    /** Ends every session of a user (after suspension or a password change). */
    public int logoutEverywhere(String userId) {
        return sessions.deleteAllForUser(userId);
    }

    public void changePassword(User user, String current, String next) throws SevaSyncException {
        PasswordHasher.Hash stored = credentials.find(user.getId())
                .orElseThrow(() -> new AuthenticationException("No password set"));
        if (!hasher.verify(current == null ? "" : current, stored)) {
            throw new AuthenticationException("Current password is incorrect");
        }
        String clean = InputSanitizer.password(next);
        PasswordHasher.Hash hash = hasher.hash(clean);
        db.runInTransaction(() -> {
            credentials.save(user.getId(), hash, TimeUtil.now(clock));
            sessions.deleteAllForUser(user.getId());
            audit.log(user.getId(), "PASSWORD_CHANGED", "User", user.getId(), "all sessions ended");
        });
    }

    /** Creates the first administrator if none exists yet. @return true when one was created */
    public boolean ensureAdmin(String email, String password, String name) throws SevaSyncException {
        if (users.countByRole(Role.ADMIN) > 0) {
            return false;
        }
        createAccount(Role.ADMIN, AccountStatus.ACTIVE, new RegistrationRequest("ADMIN", name, email, password,
                "9000000000", "", null, null, null, null, null, null, null), AuditLogger.SYSTEM);
        return true;
    }

    public int purgeExpiredSessions() {
        return sessions.deleteExpired(TimeUtil.now(clock));
    }

    private AuthResult openSession(User user) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime now = TimeUtil.now(clock);
        LocalDateTime expires = now.plus(SESSION_TTL);
        sessions.create(new SessionRepository.Session(sha256(token), user.getId(), now, expires));
        return new AuthResult(token, user, expires);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
