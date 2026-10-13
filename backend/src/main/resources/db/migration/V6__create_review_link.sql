-- Personal links that let a reviewer answer without an account.
--
-- Only the SHA-256 of the token is stored: the token itself is shown once, at issue time, and
-- cannot be recovered. A link is scoped to one revision and one reviewer.

CREATE TABLE review_link (
    id              UUID        PRIMARY KEY,
    revision_id     UUID        NOT NULL,
    reviewer_id     UUID        NOT NULL,
    token_hash      TEXT        NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    first_opened_at TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT review_link_expiry_after_issue CHECK (expires_at > issued_at),
    CONSTRAINT review_link_review_request_fk
        FOREIGN KEY (revision_id, reviewer_id) REFERENCES review_request (revision_id, reviewer_id)
);

-- One active (not revoked) link per reviewer and revision: issuing a new one revokes the old one.
CREATE UNIQUE INDEX review_link_one_active
    ON review_link (revision_id, reviewer_id)
    WHERE revoked_at IS NULL;

CREATE INDEX review_link_revision_idx ON review_link (revision_id);

-- A link is evidence: it is never deleted, and only two facts about it may be recorded once,
-- when it was first opened and when it was revoked.
CREATE FUNCTION review_link_guard() RETURNS trigger AS $$
BEGIN
    IF NEW.id <> OLD.id
       OR NEW.revision_id <> OLD.revision_id
       OR NEW.reviewer_id <> OLD.reviewer_id
       OR NEW.token_hash <> OLD.token_hash
       OR NEW.issued_at <> OLD.issued_at
       OR NEW.expires_at <> OLD.expires_at THEN
        RAISE EXCEPTION 'review_link identity is immutable' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.first_opened_at IS NOT NULL AND NEW.first_opened_at IS DISTINCT FROM OLD.first_opened_at THEN
        RAISE EXCEPTION 'review_link first_opened_at is write-once' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at THEN
        RAISE EXCEPTION 'review_link revoked_at is write-once' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER review_link_guard_update
    BEFORE UPDATE ON review_link
    FOR EACH ROW EXECUTE FUNCTION review_link_guard();

CREATE TRIGGER review_link_no_delete
    BEFORE DELETE ON review_link
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER review_link_no_truncate
    BEFORE TRUNCATE ON review_link
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_change();
