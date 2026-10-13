-- Who may see and change a project. A user who is not a member must not even learn that the
-- project exists: the application answers "not found", never "forbidden".

CREATE TABLE project_member (
    project_id UUID        NOT NULL REFERENCES project (id),
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);

CREATE INDEX project_member_user_idx ON project_member (user_id);
