-- Append-only journal of everything that happens in the system.
-- Rows are inserted and read, never changed or removed: the database itself refuses it.

CREATE TABLE audit_event (
    id           UUID        PRIMARY KEY,
    event_type   TEXT        NOT NULL,
    actor        TEXT        NOT NULL,
    target_type  TEXT        NOT NULL,
    target_id    UUID        NOT NULL,
    payload      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at  TIMESTAMPTZ NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_event_target_idx
    ON audit_event (target_type, target_id, occurred_at);

CREATE FUNCTION audit_event_forbid_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_forbid_change();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_forbid_change();
