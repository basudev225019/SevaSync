# SevaSync – Campus Food Surplus & Waste Redistribution (pure Java backend)

Hostel messes, canteens and event teams log surplus food → the system **validates freshness** →
a **priority engine** matches the soonest-expiring food with the most urgent recipients →
volunteers deliver → every step lands in a **tamper-evident audit trail**.

* **100 % Java, JDK 17+**, zero runtime dependencies (JUnit is used for tests only).
* Two front doors on the same core: an interactive **console** (as in the project plan) and a **REST/JSON API**
  built on the JDK's own `com.sun.net.httpserver`.
* Persistence is flat CSV through `BufferedReader` / `BufferedWriter`, written atomically (temp file + move).

## Run it

```bash
./build.sh                                   # plain javac + jar, no Maven needed
java -jar target/sevasync.jar --demo         # console with sample data
java -jar target/sevasync.jar serve --demo   # REST API on http://127.0.0.1:8080/api
```
With Maven: `mvn test` (needs internet once, to fetch JUnit) and `mvn package`.

Options: `--data DIR` (default `./data`), `--port N`, `--host H` (default `127.0.0.1`; use `0.0.0.0` to expose),
`--api-key KEY` or env `SEVASYNC_API_KEY` (then every call except `/api/health` needs header `X-API-Key`).

## Project layout and who owns what (matches the presentation)

| Package | Contents | Group member |
|---|---|---|
| `model`, `contract` | `User` (abstract) → `Donor`, `Recipient`; `FoodBatch`, `Dispatch`; enums; `Entity` interface | **Member 1** – domain models & architecture |
| `service.MatchingService` | two `PriorityQueue`s + custom `Comparator`s, `LinkedList`, `HashMap` | **Member 2** – priority & matching |
| `validation.SafetyValidator`, `exception`, `util.InputSanitizer` | freshness rules, custom exceptions, input sanitising | **Member 3** – validation & error handling |
| `storage`, `service.ImpactReportService`, `cli.ConsoleUI` | CSV I/O, audit log, backup, report, menu state machine | **Member 4** – persistence & CLI |
| `api`, `service.ExpiryMonitor`, `SevaSyncSystem` | REST layer, background thread, wiring | shared / extra |

Layering: `cli` / `api` → `service` → `storage` (`CsvRepository`) → files. Services never touch files directly.

## How matching works

1. Eligible batches (`AVAILABLE`, ≥ 10 min of shelf-life left) go into a `PriorityQueue` ordered by **expiry time**, soonest first.
2. Recipients with free capacity today go into a second `PriorityQueue` ordered by **need level** (`CRITICAL` > `HIGH` > `NORMAL`), then by free capacity.
3. Take the most urgent batch, give it to the neediest recipient; if the recipient fills up, split the batch over the next one.
   Recipients with spare capacity go back into the queue.
4. Result: `Dispatch` rows (`ASSIGNED`). `preview` shows this without saving. Complexity O((B+R+A) log(B+R)).

## Safety rules (`SafetyValidator`)

* Max consumption window per category: cooked meal 4 h, beverage 8 h, bakery 12 h, fruit/veg 24 h, packaged 48 h → `UnsafeFoodException`.
* Already expired → `ExpiredBatchException`; fewer than 30 min left when logged → `UnsafeFoodException`.
* Servings 1–5000, preparation time not in the future → `InvalidInputException`.
* Delivery is refused if the batch expired meanwhile: dispatch is cancelled, servings counted as wasted.
* A background thread (`ExpiryMonitor`, every 60 s) expires overdue batches automatically.
* Recipient daily capacity is enforced → `CapacityExceededException`.

## Audit trail

`audit_log.csv` is append-only. Each row stores the SHA-256 of its content **plus the previous row's hash**, so editing,
deleting or re-ordering any row is detected by *Reports → Verify audit trail* / `GET /api/audit/verify`.
(It is tamper-*evident*, not tamper-proof – someone with file access could rebuild the whole chain.)

## REST API

| Method & path | Purpose |
|---|---|
| `GET /api/health` | liveness |
| `GET/POST /api/donors`, `GET /api/donors/{id}` | donors |
| `GET/POST /api/recipients`, `GET /api/recipients/{id}` | recipients (+ `remainingCapacityToday`) |
| `GET/POST /api/batches[?status=AVAILABLE]`, `GET /api/batches/{id}` | surplus batches |
| `POST /api/matching/preview`, `POST /api/matching/run` | matching engine |
| `GET/POST /api/dispatches[?status=ASSIGNED]` | list / manual assignment |
| `POST /api/dispatches/{id}/deliver` `{"volunteer":"Ravi"}` · `/cancel` `{"reason":"..."}` | dispatch lifecycle |
| `GET /api/reports/impact` | servings, kg saved, CO₂e (estimate), top donors/recipients |
| `GET /api/audit[?limit=50]`, `GET /api/audit/verify` | audit trail |

Errors are JSON `{"error": "...", "type": "ExpiredBatchException"}` with 400 (bad input), 401, 404, 405, 409 (capacity), 422 (unsafe/expired).

```bash
curl -X POST localhost:8080/api/donors -d '{"name":"Boys Hostel Mess","phone":"9876543210","donorType":"HOSTEL_MESS"}'
curl -X POST localhost:8080/api/batches -d '{"donorId":"DON-0001","foodName":"Veg Biryani","category":"COOKED_MEAL","vegetarian":true,"servings":80,"shelfLifeHours":3}'
curl -X POST localhost:8080/api/matching/run
```

## Data files (`data/`)

`donors.csv`, `recipients.csv`, `batches.csv`, `dispatches.csv`, `audit_log.csv` (+ optional `backup.ser`, `impact_report.csv`).
Every save rewrites the file atomically – perfectly fine for campus scale, not for millions of rows.

## Syllabus coverage

| Syllabus topic | Where |
|---|---|
| Module I – data types, operators, control flow, arrays/strings, console I/O, `PrintWriter` | everywhere; `cli.ConsoleUI` |
| Module II – classes, constructors, static/final, inner classes, command-line args, inheritance, `super`/`this`, abstract classes, overriding | `model.*`, `SevaSyncApp.main(args)`, `User.getRole()`, `describe()` overrides, nested `Slot`/records |
| Module III – packages, interfaces, exception handling (checked/unchecked, multi-catch, `finally`, `throws`), object streams & serialization, files | `contract.Entity`, `exception.*`, `storage.BackupService`, `storage.FileStorageManager` |
| Module III – multithreading (thread model, creating threads, synchronisation, stopping threads) | `service.ExpiryMonitor`, `ReentrantLock` in all services, `synchronized` repositories, API thread pool |
| Module IV – JUnit | `src/test/java` (34 tests) |
| Module IV – Swing GUI, JDBC/MySQL | **not used**: the project plan specifies CSV storage and a CLI. The repository layer is the single place to swap in JDBC; a Swing front-end would call the same services. |
| Self study – GitHub | `git init && git add . && git commit -m "SevaSync backend" && git remote add origin <url> && git push -u origin main` |

## Known limits / next steps

* No user login or roles beyond the optional shared API key – add real authentication before exposing it publicly.
* Matching is greedy (a good heuristic, not a global optimum) and ignores travel distance and dietary preferences.
* CO₂e figures use rough planning constants in `ImpactReportService`; change them to your own data.
* Single-process only: the lock is in memory, so do not run two instances on the same `data/` folder.
