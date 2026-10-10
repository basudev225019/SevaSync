# SevaSync – Campus Food Surplus & Waste Redistribution Platform

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Database](https://img.shields.io/badge/Database-SQLite-003B57?logo=sqlite&logoColor=white)](https://www.sqlite.org/)
[![Frontend](https://img.shields.io/badge/Frontend-React%20%2B%20Vite-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![Tests](https://img.shields.io/badge/Tests-34%20Passing-10b981)](https://junit.org/junit5/)

> **Zero Campus Food Waste Initiative**: Hostel messes, cafeterias, and event committees log surplus food → the system validates freshness rules → a double `PriorityQueue` matching engine pairs soonest-expiring food with the highest-urgency recipient shelters → volunteers deliver meals → every action is recorded in a tamper-evident cryptographic SHA-256 audit log.

---

## Architecture Overview

SevaSync is structured as a clean full-stack monorepo:

```
SevaSync/
├── backend/                             # Pure Java 17+ Backend with SQLite
│   ├── pom.xml                          # Maven build with SQLite JDBC & shaded JAR
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/sevasync/
│   │   │   │   ├── api/                 # REST API server (CORS-enabled, zero framework)
│   │   │   │   ├── cli/                 # Interactive terminal console UI
│   │   │   │   ├── contract/            # Domain entity contracts
│   │   │   │   ├── database/            # SQLite DatabaseManager, schema migrator
│   │   │   │   ├── exception/           # Domain exceptions
│   │   │   │   ├── model/               # Donor, Recipient, FoodBatch, Dispatch, Enums
│   │   │   │   ├── service/             # Matching, Donation, Dispatch, Impact, ExpiryMonitor
│   │   │   │   ├── storage/             # SQLite Repositories, AuditLogger, BackupService
│   │   │   │   ├── util/                # Json, TimeUtil, InputSanitizer
│   │   │   │   ├── validation/          # Freshness & safety rules
│   │   │   │   ├── DemoData.java        # Campus sample data seeder
│   │   │   │   ├── SevaSyncApp.java     # Application entrypoint (serve or cli)
│   │   │   │   └── SevaSyncSystem.java  # Dependency wiring & lifecycle management
│   │   │   └── resources/
│   │   │       └── schema.sql           # SQLite DDL database schema
│   │   └── test/
│   │       └── java/com/sevasync/       # 34 comprehensive JUnit 5 tests
│   └── data/                            # SQLite database storage (sevasync.db)
│
├── frontend/                            # Modern React + Vite Application
│   ├── package.json
│   ├── vite.config.ts                   # Proxies /api -> http://127.0.0.1:8080
│   ├── index.html
│   ├── src/
│   │   ├── components/                  # Navbar, StatCard, Modals
│   │   ├── pages/                       # Dashboard, Batches, Matching, Dispatches, Partners, Impact, Audit
│   │   ├── services/                    # Typed API client
│   │   ├── types/                       # TypeScript models
│   │   ├── index.css                    # Rich dark-mode glassmorphism design system
│   │   ├── App.tsx                      # Top-level state and routing
│   │   └── main.tsx
│
└── README.md
```

---

## Getting Started

### Prerequisites
- **JDK 17 or higher** (OpenJDK 17, 21, 26+)
- **Node.js 18+** and **npm**

---

### 1. Run the Java Backend (SQLite Database)

Navigate to the `backend/` directory:

```bash
cd backend
```

#### Run All 34 Tests:
```bash
mvn test
```

#### Build the Standalone Executable JAR:
```bash
mvn package -DskipTests
```

#### Start the REST API Server with Demo Data:
```bash
java -jar target/sevasync.jar serve --demo
```
* **Host & Port**: Listening on `http://127.0.0.1:8080/api`
* **Database**: Automatically provisions SQLite at `backend/data/sevasync.db` with WAL mode enabled.
* **CLI Options**:
  - `serve`: Starts HTTP API server
  - `cli`: Starts interactive terminal UI
  - `--data DIR`: Database directory (default: `./data`)
  - `--port N`: HTTP port (default: `8080`)
  - `--demo`: Seeds sample campus donors, shelters, and food batches

---

### 2. Run the React + Vite Frontend

In a separate terminal, navigate to the `frontend/` directory:

```bash
cd frontend
npm install
npm run dev
```

Open **`http://localhost:5173`** in your browser.

The frontend includes:
- **Interactive Dashboard**: Live KPIs, freshness countdown timers, and quick action controls.
- **Surplus Food Batches**: Real-time batch table with category limits, remaining shelf-life bars, and intake modal.
- **Smart Matching Engine**: Visual double `PriorityQueue` simulation (dry run) and one-click execution.
- **Dispatch Tracker**: Lifecycle tracking (`ASSIGNED` → `DELIVERED` or `CANCELLED`).
- **Partner Registry**: Donors and recipient shelters with live capacity utilization meters.
- **Impact & ESG Report**: Food waste saved (kg), CO₂e avoided (kg), and mess leaderboards.
- **Tamper-Evident Audit Trail**: Live SHA-256 hash-chain viewer with single-click cryptographic verification.

---

## SQLite Database Schema

The backend uses SQLite (`sevasync.db`) configured with foreign keys and Write-Ahead Logging (`WAL`):

```sql
-- Food Donors
CREATE TABLE donors (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT,
    address TEXT,
    donor_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

-- Recipient Organizations
CREATE TABLE recipients (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT,
    address TEXT,
    recipient_type TEXT NOT NULL,
    need_level TEXT NOT NULL,
    daily_capacity INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

-- Food Surplus Batches
CREATE TABLE food_batches (
    id TEXT PRIMARY KEY,
    donor_id TEXT NOT NULL,
    food_name TEXT NOT NULL,
    category TEXT NOT NULL,
    vegetarian INTEGER NOT NULL DEFAULT 0,
    servings INTEGER NOT NULL,
    remaining_servings INTEGER NOT NULL,
    prepared_at TEXT NOT NULL,
    shelf_life_minutes INTEGER NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (donor_id) REFERENCES donors(id) ON DELETE CASCADE
);

-- Volunteer Dispatches
CREATE TABLE dispatches (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    recipient_id TEXT NOT NULL,
    servings INTEGER NOT NULL,
    status TEXT NOT NULL,
    assigned_at TEXT NOT NULL,
    delivered_at TEXT,
    volunteer_name TEXT,
    note TEXT,
    FOREIGN KEY (batch_id) REFERENCES food_batches(id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES recipients(id) ON DELETE CASCADE
);

-- Tamper-Evident SHA-256 Audit Trail
CREATE TABLE audit_log (
    seq INTEGER PRIMARY KEY,
    timestamp TEXT NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    details TEXT,
    prev_hash TEXT NOT NULL,
    hash TEXT NOT NULL
);
```

---

## How Smart Matching Works

1. **Eligible Batches Queue**: Surplus batches (`AVAILABLE` with $\ge 10$ minutes remaining shelf life) enter a `PriorityQueue` sorted by **earliest expiry first**.
2. **Recipient Slots Queue**: Registered beneficiaries with remaining daily capacity enter a second `PriorityQueue` sorted by **need urgency** (`CRITICAL` > `HIGH` > `NORMAL`), then by remaining capacity.
3. **Greedy Allocation**: The engine assigns the soonest-expiring batch to the highest-urgency recipient. If the recipient fills up, the batch is split over the next recipient.
4. **Algorithmic Complexity**: $\mathcal{O}((B + R + A) \log (B + R))$ where $B$ = batches, $R$ = recipients, $A$ = allocations.

---

## Safety & Freshness Rules (`SafetyValidator`)

* **Category Shelf-Life Thresholds**:
  - `COOKED_MEAL`: Max 4 hours
  - `BEVERAGE`: Max 8 hours
  - `BAKERY`: Max 12 hours
  - `FRUIT_VEGETABLE`: Max 24 hours
  - `PACKAGED`: Max 48 hours
* **Minimum Window**: Food batches submitted with $< 30$ minutes of remaining shelf life are rejected.
* **Background Expiry Daemon**: `ExpiryMonitor` runs every 60s to expire overdue batches and cancel undelivered dispatches.
* **Delivery Guard**: Delivery hand-over is refused if the batch expired in transit.

---

## REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Service health status |
| `GET` | `/api/donors` | List all food donors |
| `POST` | `/api/donors` | Register a new food donor |
| `GET` | `/api/donors/{id}` | Get donor details |
| `GET` | `/api/recipients` | List recipients (+ remaining daily capacity) |
| `POST` | `/api/recipients` | Register a new recipient beneficiary |
| `GET` | `/api/recipients/{id}` | Get recipient details |
| `GET` | `/api/batches[?status=AVAILABLE]` | List surplus batches |
| `POST` | `/api/batches` | Log a surplus food batch |
| `GET` | `/api/batches/{id}` | Get batch details |
| `POST` | `/api/matching/preview` | Dry-run matching simulation |
| `POST` | `/api/matching/run` | Execute priority matching & persist dispatches |
| `GET` | `/api/dispatches[?status=ASSIGNED]` | List dispatches |
| `POST` | `/api/dispatches/{id}/deliver` | Confirm volunteer delivery (`{"volunteer": "..."}`) |
| `POST` | `/api/dispatches/{id}/cancel` | Cancel dispatch (`{"reason": "..."}`) |
| `GET` | `/api/reports/impact` | Aggregate environmental & social figures |
| `GET` | `/api/audit[?limit=50]` | Read cryptographic audit log rows |
| `GET` | `/api/audit/verify` | Verify cryptographic hash chain integrity |
| `POST` | `/api/demo` | Seed realistic campus demo data |

---

## License & Attribution
Developed for campus food surplus management and sustainability accountability.
