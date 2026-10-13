-- People who can sign in. There is no public sign-up: accounts are created by an administrator.
-- Reviewers never have an account; they use their personal link.

CREATE TABLE app_user (
    id                   UUID        PRIMARY KEY,
    email                TEXT        NOT NULL,
    display_name         TEXT        NOT NULL CHECK (btrim(display_name) <> ''),
    password_hash        TEXT        NOT NULL CHECK (password_hash <> ''),
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL,
    last_login_at        TIMESTAMPTZ,
    -- Emails are stored normalised, so "A@x.org" and "a@x.org" can never be two accounts.
    CONSTRAINT app_user_email_normalised CHECK (email = lower(btrim(email)) AND email <> ''),
    CONSTRAINT app_user_email_unique UNIQUE (email)
);
