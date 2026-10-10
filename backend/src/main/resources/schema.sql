-- SevaSync SQLite Database Schema

CREATE TABLE IF NOT EXISTS donors (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT,
    address TEXT,
    donor_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS recipients (
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

CREATE TABLE IF NOT EXISTS food_batches (
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

CREATE TABLE IF NOT EXISTS dispatches (
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

CREATE TABLE IF NOT EXISTS audit_log (
    seq INTEGER PRIMARY KEY,
    timestamp TEXT NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    details TEXT,
    prev_hash TEXT NOT NULL,
    hash TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_batches_status ON food_batches(status);
CREATE INDEX IF NOT EXISTS idx_batches_donor ON food_batches(donor_id);
CREATE INDEX IF NOT EXISTS idx_dispatches_status ON dispatches(status);
CREATE INDEX IF NOT EXISTS idx_dispatches_batch ON dispatches(batch_id);
CREATE INDEX IF NOT EXISTS idx_dispatches_recipient ON dispatches(recipient_id);
