-- What the application role may do. Everything else is refused by PostgreSQL itself.
--
-- The application connects as paraphe_app (see db/create-app-role.sql). It does not own any table,
-- so it cannot drop a trigger, disable one, alter a table or truncate it, and on the evidence
-- tables it has no UPDATE or DELETE privilege at all: the append-only guarantee no longer rests on
-- the triggers alone.
--
-- Every new table needs its grants decided here or in a later migration. DatabaseRolesIntegrationTest
-- fails when a table has no explicit decision.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'paraphe_app') THEN
        RAISE EXCEPTION
            'role paraphe_app does not exist: run backend/db/create-app-role.sql as the database owner first';
    END IF;
END
$$;

-- Start from nothing, so this migration states the complete list of what is allowed.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM paraphe_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO paraphe_app;

-- Evidence: written once, then only read. No UPDATE, no DELETE, no TRUNCATE.
GRANT SELECT, INSERT ON audit_event, opinion, revision TO paraphe_app;

-- A review link records two facts about itself, once each: first opened, revoked.
GRANT SELECT, INSERT ON review_link TO paraphe_app;
GRANT UPDATE (first_opened_at, revoked_at) ON review_link TO paraphe_app;

-- Who must answer a revision: written when the review is opened, never changed.
GRANT SELECT, INSERT ON review_request TO paraphe_app;

-- Structure that is only ever added to.
GRANT SELECT, INSERT ON project, work_package, document, project_member TO paraphe_app;

-- Configuration that can be switched off, never deleted.
GRANT SELECT, INSERT ON participant, opinion_status_definition TO paraphe_app;
GRANT UPDATE (active) ON participant, opinion_status_definition TO paraphe_app;

-- The default reviewers of a work package are replaced as a set.
GRANT SELECT, INSERT, DELETE ON default_reviewer TO paraphe_app;

-- Accounts: sign-in bookkeeping, password change, deactivation. Never the email or the identity.
GRANT SELECT, INSERT ON app_user TO paraphe_app;
GRANT UPDATE (password_hash, must_change_password, last_login_at, active) ON app_user TO paraphe_app;
