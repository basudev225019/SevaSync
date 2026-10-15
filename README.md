# SevaSync · Every meal, rescued in time

Campus kitchens cook more than they serve. **SevaSync** checks surplus food is safe, matches it to the shelter
that needs it most, and puts a student volunteer on the road before it expires. Every step is sealed in a
tamper-evident audit trail.

| | |
|---|---|
| **Backend** | Pure Java 17, no web framework. JDK `HttpServer`, SQLite via JDBC, PBKDF2 + HMAC from `javax.crypto` |
| **Frontend** | React 19 + TypeScript, Three.js via React Three Fiber, GSAP, Motion (Framer Motion), ReactBits, Tailwind v4 |
| **Tests** | 76 JUnit 5 tests: domain, security, state machine, matching, storage, simulation and real HTTP |

---

## Quick start (exhibition mode)

```bash
cd frontend && npm install && npm run build      # builds frontend/dist
cd ../backend && mvn package -DskipTests         # builds target/sevasync.jar
java -jar target/sevasync.jar --live-demo        # http://127.0.0.1:8080
```

One JAR serves both the API and the website. `--live-demo` seeds a demo campus (with two weeks of history) on an
**empty** data folder and keeps simulated participants busy, so the 3D map is always moving.

| Demo account | E-mail | Password |
|---|---|---|
| Coordinator | `admin@sevasync.demo` | `Demo@2026` |
| Donor (kitchen) | `mess@sevasync.demo` | `Demo@2026` |
| Volunteer | `arjun@sevasync.demo` | `Demo@2026` |
| Recipient | `asha@sevasync.demo` | `Demo@2026` |

The sign-in page also has one-click buttons for these accounts. Demo passwords are for demos only.

### Development mode

```bash
cd backend  && mvn test && mvn package -DskipTests && java -jar target/sevasync.jar --demo
cd frontend && npm run dev                       # http://localhost:5173, proxies /api to :8080
```

Point the dev proxy at another port with `VITE_API_TARGET=http://127.0.0.1:8090 npm run dev`.

### Command-line options

| Option | Meaning |
|---|---|
| `serve` / `cli` | Web server (default) or the coordinator console |
| `--data DIR` | Data folder (default `./data`: SQLite database, secret key, backups, exports) |
| `--host H`, `--port N` | Bind address and port (default `127.0.0.1:8080`; use `0.0.0.0` to expose on a network) |
| `--static DIR` | Built frontend to serve (auto-detects `../frontend/dist`) |
| `--cors LIST` | Comma-separated browser origins allowed to call the API (default: the Vite dev server) |
| `--demo` / `--live-demo` | Seed demo data / also run the activity simulator |

Environment: `SEVASYNC_SECRET` (base64 server key; otherwise generated in `data/secret.key`),
`SEVASYNC_ADMIN_EMAIL` and `SEVASYNC_ADMIN_PASSWORD` (first coordinator on a non-demo install; otherwise a one-time
password is printed at first start), `SEVASYNC_CORS`.

---

## What each role does

| Role | Can | Starts as |
|---|---|---|
| **Donor** (mess, canteen, fest team) | Log surplus with a live safety check, withdraw it, see where it went and its ratings | Pending verification |
| **Volunteer** | See open pickups nearest-first with ETA, claim → pick up → deliver | Active |
| **Recipient** (shelter, orphanage, NGO) | Track incoming food live, confirm and rate deliveries | Pending verification |
| **Coordinator** | Verify partners, tune and run matching, benchmark strategies, impact reports, audit, backups | Created by the system |

Permissions are checked per action (`Permission` enum), never by role name, and are narrowed by account status:
a pending organisation can only edit its profile, and a suspended account can do nothing.

---

## How it works

```
Donor logs food ─► SafetyValidator ─► Matching (Strategy) ─► Dispatch ASSIGNED
                                                               │ claim
                                             ┌─ release ─── CLAIMED
                                             ▼                 │ pickUp
                                          ASSIGNED          PICKED_UP
                                                               │ deliver (refused if food expired)
                                                            DELIVERED ─► recipient confirms + rates
   ExpiryMonitor (every 60 s): expired food → EXPIRED, undelivered dispatches → CANCELLED
   Every step → HMAC-chained audit row → live event (SSE) → every open screen updates
```

### Matching strategies (Strategy + Template Method)

| Strategy | Batches in order of | Each batch goes to |
|---|---|---|
| `fifo` (baseline) | logging time | recipients in registration order |
| `expiry-first` (original engine) | earliest expiry | highest need, then most free capacity |
| `smart` (default) | earliest expiry | best score: need − distance − fairness penalty + whole-batch fit, **only if reachable before expiry** |

`AbstractGreedyStrategy` holds the shared dual-`PriorityQueue` algorithm (O(B log B + B·R log R)). Each strategy
only supplies orderings and, optionally, feasibility and score.

### Benchmark: is "smart" actually better?

The Matching Lab replays seeded synthetic campus days; every strategy faces exactly the same food, recipients and
volunteers. With the default scenario (6 kitchens, 8 recipients, **2 volunteers**, 12 batches/day, 40 days):

| | Smart | Expiry first | FIFO |
|---|---|---|---|
| Food wasted | **19.6 %** | 21.1 % | 21.1 % |
| Average trip | **3.78 km** | 4.05 km | 4.05 km |
| Meals to high/critical-need recipients | **58.8 %** | 58.5 % | 54.2 % |

These are the honest limits. When supply exceeds recipient capacity, every strategy wastes the same surplus,
because no algorithm can create room that doesn't exist; the waste-cause chart shows this. The strategies differ
where volunteers are scarce, and that is where distance-aware matching helps.

### Food-safety rules (`SafetyValidator`)

Cooked meal 4 h · beverage 8 h · bakery 12 h · fruit & veg 24 h · packaged 48 h after preparation. A batch needs at
least 30 minutes left when logged. Matching ignores food with under 10 minutes left. A hand-over of expired food is
refused, and the dispatch is cancelled and audited.

---

## OOP concepts you can point at

| Concept | Where |
|---|---|
| Abstraction & inheritance | `User` (abstract) → `LocatedUser` (abstract) → `Donor`, `Recipient`, `Volunteer`; `Admin` |
| Polymorphism | `User.toMap()` / `describe()` / `toPublicMap()`; exceptions override `httpStatus()` and `code()` |
| Interfaces | `Entity`, `Locatable`, `Repository<T>`, `MatchingStrategy`, `EventListener`, `RouteModule` |
| Encapsulation | Validating setters; `Dispatch` mutators are package-private, reachable only by its states |
| **State** pattern | `DispatchState` (sealed interface, one class per state, illegal moves rejected) |
| **Strategy** pattern | `MatchingStrategy` + `StrategyRegistry`, switchable at runtime |
| **Template Method** | `AbstractGreedyStrategy`, `AbstractSqliteRepository` |
| **Observer** | `EventBus` → SSE stream; events fire only after the transaction commits |
| **Factory** | `UserFactory` (form → right subclass), `SqliteUserRepository.map` (row → right subclass) |
| **Adapter** | `RoleRepository<T>`: a typed `Repository<Donor>` over the shared users table |
| Generics | `Repository<T extends Entity>`, `DatabaseManager.Work<T, E extends Exception>` |
| Collections | `PriorityQueue`, `EnumMap`, `EnumSet`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, `ArrayDeque` |
| Exceptions | Checked hierarchy under `SevaSyncException`; unchecked `StorageException` for infrastructure |
| Concurrency | Fair `ReentrantLock` transactions, `ScheduledExecutorService`, thread pool HTTP server; a test proves 8 simultaneous claims → exactly one wins |
| File I/O | `FileStorageManager` (BufferedReader/Writer, atomic temp-file moves), `CsvRepository`, database snapshots |

---

## Security

- **Passwords:** PBKDF2-HMAC-SHA256, 210,000 iterations, per-user salt, constant-time comparison. Hashes live in
  their own table and are never part of a `User` object.
- **Sessions:** 256-bit random bearer tokens. Only their SHA-256 is stored, they expire after 12 h, and sign-out,
  suspension and password changes revoke them server-side.
- **Brute force:** 5 failed sign-ins per account and client in 10 minutes → HTTP 429 with `Retry-After`. Unknown
  e-mails take the same time and give the same message as wrong passwords.
- **Audit trail:** HMAC-SHA-256 chain keyed with a server secret, so a database editor cannot recompute it.
  Verification names the first altered or missing row.
- **HTTP:** CORS only for configured origins, `Content-Type: application/json` required for request bodies (blocks
  cross-site form posts), 64 KB body limit, strict Content-Security-Policy on the web app, `nosniff`,
  `no-referrer`, `X-Frame-Options: DENY`, and no stack traces in responses (`X-Request-Id` for log correlation).
- **Data:** parameterised SQL everywhere, input sanitised centrally (`InputSanitizer`), CSV exports neutralise
  spreadsheet formulas, volunteers appear to other users by first name only, and the public network view never
  includes volunteers.
- **Transactions:** every business operation is one transaction (audit row included). A failure leaves no trace.

---

## API (selection)

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/health`, `/api/meta`, `/api/public/stats`, `/api/public/network` | anyone |
| `POST` | `/api/auth/register`, `/api/auth/login` | anyone |
| `POST` | `/api/auth/logout`, `/api/auth/password` · `GET /api/auth/me` · `PATCH /api/me` | signed in |
| `GET` | `/api/events` (Server-Sent Events, resumable with `Last-Event-ID`) | signed in |
| `GET` | `/api/network` | verified users |
| `GET/POST` | `/api/batches`, `/api/batches/{id}`, `/api/batches/{id}/withdraw` | donors, coordinators |
| `GET` | `/api/dispatches`, `/api/dispatches/open`, `/api/dispatches/{id}` | role-scoped |
| `POST` | `/api/dispatches/{id}/claim · release · pickup · deliver` | volunteers |
| `POST` | `/api/dispatches/{id}/confirm` · `/cancel` · `POST /api/dispatches` (manual) | recipients · coordinators |
| `GET/PUT` | `/api/matching/settings` · `POST /api/matching/preview · run · benchmark` | coordinators |
| `GET/POST/PATCH` | `/api/users`, `/api/users/{id}`, `/api/users/{id}/status` | coordinators |
| `GET` | `/api/reports/impact`, `/api/audit`, `/api/audit/verify` · `POST /api/admin/backups · exports` | coordinators |

Errors are always `{"error": "...", "code": "..."}` with a meaningful status (400, 401, 403, 404, 409, 422, 429).

---

## Project layout

```
backend/src/main/java/com/sevasync/
  model/        User hierarchy, Role, Permission, AccountStatus, FoodBatch, Dispatch + DispatchState, GeoPoint
  validation/   SafetyValidator
  matching/     MatchingStrategy, AbstractGreedyStrategy, Smart / ExpiryFirst / Fifo, StrategyRegistry
  service/      Donation, Dispatch, Matching, User, ImpactReport, Network, ExpiryMonitor, ViewAssembler
  security/     AuthService, PasswordHasher, LoginThrottle, Guard, UserFactory, SecretStore
  storage/      Repositories (Template Method base), AuditLogger, BackupService, CsvRepository, FileStorageManager
  database/     DatabaseManager (transactions), Migrator  ·  resources/db/migration/V*.sql
  event/        EventBus, DomainEvent, Audience
  simulation/   Scenario, Simulator, BenchmarkService
  api/          ApiServer, Router, Request, EventStream (SSE), StaticFiles, routes/*
  cli/          ConsoleUI (coordinator console)
frontend/src/
  pages/        Landing, Login, Register, Dashboard (per role), Pickups, Batches, Dispatches, Network,
                Matching lab, Partners, Impact, Audit, Profile
  components/   three/ (CampusScene, nodes, routes), reactbits/ (vendored), ui/, charts/, layout/, modals/
  context/      AuthContext, LiveContext (SSE → cache invalidation + toasts)
  services/     api.ts (typed client), events.ts (SSE over fetch)
```

---

## Troubleshooting

- **Windows: "Unable to establish loopback connection" on start.** JDK 16+ builds its internal selector on an
  AF_UNIX socket in `%TEMP%`, and some machines block sockets there. SevaSync points
  `jdk.net.unixdomain.tmpdir` at `<data>/sockets` automatically (and Maven tests use `target/`), so this
  should not happen. If you run the classes another way, pass `-Djdk.net.unixdomain.tmpdir=<some folder>`.
- **Old database.** A database from SevaSync v1 is moved aside to `data/sevasync-v1-legacy-*.db`, never deleted,
  and a fresh v2 database is created.
- **Demo data did not load.** `--demo` only seeds an empty database. Use a fresh `--data` folder.

## Credits

UI effects from [ReactBits](https://reactbits.dev), copied into `frontend/src/components/reactbits` as their
registry intends (check its licence before any commercial use). 3D with [three.js](https://threejs.org) and
[React Three Fiber](https://r3f.docs.pmnd.rs). Animations by [GSAP](https://gsap.com) and
[Motion](https://motion.dev).
