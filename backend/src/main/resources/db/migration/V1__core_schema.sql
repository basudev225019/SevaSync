-- SevaSync v2 core schema (SQLite)

-- Every participant. Single-table inheritance: the role column decides which Java subclass a row becomes,
-- and the role-specific columns are NULL for the other roles.
CREATE TABLE users (
    id              TEXT PRIMARY KEY,
    role            TEXT NOT NULL CHECK (role IN ('ADMIN', 'DONOR', 'RECIPIENT', 'VOLUNTEER')),
    status          TEXT NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED')),
    name            TEXT NOT NULL,
    email           TEXT NOT NULL UNIQUE COLLATE NOCASE,
    phone           TEXT NOT NULL DEFAULT '',
    address         TEXT NOT NULL DEFAULT '',
    lat             REAL,
    lng             REAL,
    donor_type      TEXT,
    recipient_type  TEXT,
    need_level      TEXT,
    daily_capacity  INTEGER CHECK (daily_capacity IS NULL OR daily_capacity > 0),
    vehicle         TEXT,
    created_at      TEXT NOT NULL
);

-- Password hashes live apart from the users table so a user query can never select them by accident.
CREATE TABLE credentials (
    user_id     TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    hash        TEXT NOT NULL,
    salt        TEXT NOT NULL,
    iterations  INTEGER NOT NULL,
    updated_at  TEXT NOT NULL
);

-- Server-side sessions. Only the SHA-256 of the bearer token is stored, never the token itself.
CREATE TABLE sessions (
    token_hash    TEXT PRIMARY KEY,
    user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TEXT NOT NULL,
    expires_at    TEXT NOT NULL,
    last_seen_at  TEXT NOT NULL
);

CREATE TABLE food_batches (
    id                  TEXT PRIMARY KEY,
    donor_id            TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    food_name           TEXT NOT NULL,
    category            TEXT NOT NULL,
    vegetarian          INTEGER NOT NULL DEFAULT 0,
    servings            INTEGER NOT NULL CHECK (servings > 0),
    remaining_servings  INTEGER NOT NULL CHECK (remaining_servings >= 0 AND remaining_servings <= servings),
    prepared_at         TEXT NOT NULL,
    shelf_life_minutes  INTEGER NOT NULL CHECK (shelf_life_minutes > 0),
    expires_at          TEXT NOT NULL,
    status              TEXT NOT NULL,
    notes               TEXT NOT NULL DEFAULT '',
    created_at          TEXT NOT NULL
);

CREATE TABLE dispatches (
    id            TEXT PRIMARY KEY,
    batch_id      TEXT NOT NULL REFERENCES food_batches(id) ON DELETE CASCADE,
    recipient_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    volunteer_id  TEXT REFERENCES users(id) ON DELETE SET NULL,
    servings      INTEGER NOT NULL CHECK (servings > 0),
    status        TEXT NOT NULL,
    strategy      TEXT NOT NULL DEFAULT '',
    distance_km   REAL NOT NULL DEFAULT 0,
    assigned_at   TEXT NOT NULL,
    claimed_at    TEXT,
    picked_up_at  TEXT,
    delivered_at  TEXT,
    confirmed_at  TEXT,
    rating        INTEGER NOT NULL DEFAULT 0 CHECK (rating BETWEEN 0 AND 5),
    feedback      TEXT NOT NULL DEFAULT '',
    note          TEXT NOT NULL DEFAULT ''
);

-- Append-only, HMAC-chained audit trail.
CREATE TABLE audit_log (
    seq          INTEGER PRIMARY KEY,
    timestamp    TEXT NOT NULL,
    actor_id     TEXT NOT NULL,
    action       TEXT NOT NULL,
    entity_type  TEXT NOT NULL,
    entity_id    TEXT NOT NULL,
    details      TEXT NOT NULL DEFAULT '',
    prev_hash    TEXT NOT NULL,
    hash         TEXT NOT NULL
);

-- Human-readable sequential ids per prefix (DON-0001, BAT-0001 ...), allocated inside the writing transaction.
CREATE TABLE sequences (
    name   TEXT PRIMARY KEY,
    value  INTEGER NOT NULL
);

-- Runtime settings an administrator can change (active matching strategy, auto-matching ...).
CREATE TABLE settings (
    key    TEXT PRIMARY KEY,
    value  TEXT NOT NULL
);
