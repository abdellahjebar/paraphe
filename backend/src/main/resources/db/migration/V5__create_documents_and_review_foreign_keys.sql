-- Documents and their revisions, then the foreign keys the review tables were waiting for.

CREATE TABLE document (
    id                UUID        PRIMARY KEY,
    project_id        UUID        NOT NULL REFERENCES project (id),
    work_package_code TEXT,
    title             TEXT        NOT NULL CHECK (btrim(title) <> ''),
    document_type     TEXT,
    created_at        TIMESTAMPTZ NOT NULL,
    -- A document can only sit in a work package of its own project (NULL means none).
    CONSTRAINT document_work_package_fk
        FOREIGN KEY (project_id, work_package_code) REFERENCES work_package (project_id, code)
);

CREATE INDEX document_project_idx ON document (project_id);

-- A revision is a fact: once submitted it never changes (its file hash is part of the proof).
CREATE TABLE revision (
    id            UUID        PRIMARY KEY,
    document_id   UUID        NOT NULL REFERENCES document (id),
    sequence      INTEGER     NOT NULL CHECK (sequence >= 1),
    label         TEXT        NOT NULL CHECK (btrim(label) <> ''),
    submitted_at  TIMESTAMPTZ NOT NULL,
    file_key      TEXT        NOT NULL,
    file_name     TEXT        NOT NULL,
    file_size     BIGINT      NOT NULL CHECK (file_size > 0),
    file_sha256   TEXT        NOT NULL CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    external_link TEXT,
    CONSTRAINT revision_sequence_unique UNIQUE (document_id, sequence)
);

-- The index label is free text ("A", "REV1"), but the same label twice on a document is an error.
CREATE UNIQUE INDEX revision_label_unique ON revision (document_id, lower(label));

CREATE TRIGGER revision_no_update_delete
    BEFORE UPDATE OR DELETE ON revision
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER revision_no_truncate
    BEFORE TRUNCATE ON revision
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_change();

-- Foreign keys postponed from the review tables (see V2).
ALTER TABLE review_request
    ADD CONSTRAINT review_request_revision_fk
        FOREIGN KEY (revision_id) REFERENCES revision (id);

ALTER TABLE review_request
    ADD CONSTRAINT review_request_participant_fk
        FOREIGN KEY (reviewer_id) REFERENCES participant (id);

-- The reviewer must belong to the same project as the revision's document.
CREATE FUNCTION review_request_same_project() RETURNS trigger AS $$
DECLARE
    document_project UUID;
    participant_project UUID;
BEGIN
    SELECT d.project_id INTO document_project
    FROM revision r JOIN document d ON d.id = r.document_id
    WHERE r.id = NEW.revision_id;

    SELECT project_id INTO participant_project
    FROM participant WHERE id = NEW.reviewer_id;

    -- A missing revision or participant is the foreign keys' business and gets their precise error.
    IF document_project IS NOT NULL AND participant_project IS NOT NULL
       AND document_project <> participant_project THEN
        RAISE EXCEPTION 'reviewer % does not belong to the project of revision %',
            NEW.reviewer_id, NEW.revision_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER review_request_same_project_check
    BEFORE INSERT ON review_request
    FOR EACH ROW EXECUTE FUNCTION review_request_same_project();
