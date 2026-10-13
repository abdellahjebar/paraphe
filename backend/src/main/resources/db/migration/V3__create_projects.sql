-- Projects and what is configured per project: work packages (lots), participants,
-- the statuses a reviewer may give, and who is expected by default for each work package.
--
-- participant.id is the same UUID as the ReviewerId used by the reviews module.
-- The foreign keys from review_request to participant are added together with the documents
-- tables, in the next migration.

CREATE TABLE project (
    id         UUID        PRIMARY KEY,
    name       TEXT        NOT NULL CHECK (btrim(name) <> ''),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE work_package (
    project_id UUID NOT NULL REFERENCES project (id),
    code       TEXT NOT NULL CHECK (btrim(code) <> ''),
    label      TEXT NOT NULL,
    PRIMARY KEY (project_id, code)
);

CREATE TABLE participant (
    id         UUID        PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES project (id),
    name       TEXT        NOT NULL CHECK (btrim(name) <> ''),
    role       TEXT        NOT NULL
        CHECK (role IN ('ARCHITECT', 'ENGINEERING_OFFICE', 'CONTROL_OFFICE')),
    email      TEXT,
    phone      TEXT,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A link has to be sent somewhere.
    CHECK (email IS NOT NULL OR phone IS NOT NULL),
    UNIQUE (project_id, id)
);

CREATE TABLE opinion_status_definition (
    project_id UUID    NOT NULL REFERENCES project (id),
    code       TEXT    NOT NULL CHECK (btrim(code) <> ''),
    label      TEXT    NOT NULL,
    effect     TEXT    NOT NULL
        CHECK (effect IN ('APPROVING', 'APPROVING_WITH_RESERVES', 'INFORMATIVE', 'BLOCKING')),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (project_id, code)
);

-- Used to pre-select reviewers when a revision is sent; never forces anything.
-- Both foreign keys are composite so a default can only point inside its own project.
CREATE TABLE default_reviewer (
    project_id        UUID NOT NULL,
    work_package_code TEXT NOT NULL,
    participant_id    UUID NOT NULL,
    PRIMARY KEY (project_id, work_package_code, participant_id),
    FOREIGN KEY (project_id, work_package_code) REFERENCES work_package (project_id, code),
    FOREIGN KEY (project_id, participant_id) REFERENCES participant (project_id, id)
);
