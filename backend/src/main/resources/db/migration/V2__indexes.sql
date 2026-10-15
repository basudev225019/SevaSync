-- Indexes for the hot paths: matching (open batches by expiry), volunteer boards and recipient capacity checks.
CREATE INDEX idx_users_role_status       ON users(role, status);
CREATE INDEX idx_sessions_user           ON sessions(user_id);
CREATE INDEX idx_sessions_expiry         ON sessions(expires_at);
CREATE INDEX idx_batches_status_expiry   ON food_batches(status, expires_at);
CREATE INDEX idx_batches_donor           ON food_batches(donor_id);
CREATE INDEX idx_dispatches_status       ON dispatches(status);
CREATE INDEX idx_dispatches_batch        ON dispatches(batch_id);
CREATE INDEX idx_dispatches_recipient    ON dispatches(recipient_id, assigned_at);
CREATE INDEX idx_dispatches_volunteer    ON dispatches(volunteer_id, status);
CREATE INDEX idx_audit_entity            ON audit_log(entity_type, entity_id);
