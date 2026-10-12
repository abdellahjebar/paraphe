-- Who must answer on a revision, and what they answered.
--
-- revision_id and reviewer_id are plain UUIDs for now: the documents and projects modules that
-- own them do not exist yet. Foreign keys to them are added when those tables arrive.

CREATE TABLE review_request (
    revision_id  UUID        NOT NULL,
    reviewer_id  UUID        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (revision_id, reviewer_id)
);

-- An opinion keeps a snapshot of the status it was given with (code, label, effect), so it stays
-- meaningful and immutable even if a project later renames or reconfigures its statuses.
CREATE TABLE opinion (
    seq          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id           UUID        NOT NULL UNIQUE,
    revision_id  UUID        NOT NULL,
    reviewer_id  UUID        NOT NULL,
    status_code  TEXT        NOT NULL,
    status_label TEXT        NOT NULL,
    effect       TEXT        NOT NULL
        CHECK (effect IN ('APPROVING', 'APPROVING_WITH_RESERVES', 'INFORMATIVE', 'BLOCKING')),
    comment      TEXT        NOT NULL DEFAULT '',
    given_at     TIMESTAMPTZ NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- The database itself refuses an opinion from someone the revision was not sent to.
    FOREIGN KEY (revision_id, reviewer_id) REFERENCES review_request (revision_id, reviewer_id)
);

CREATE INDEX opinion_revision_idx ON opinion (revision_id, seq);

-- Generic guard, reusable for any append-only table.
CREATE FUNCTION forbid_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER opinion_no_update_delete
    BEFORE UPDATE OR DELETE ON opinion
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER opinion_no_truncate
    BEFORE TRUNCATE ON opinion
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_change();
