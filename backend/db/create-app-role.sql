-- The role the application connects with.
--
-- It is NOT the owner of the schema, it is not a superuser and it cannot create anything: it only
-- gets the privileges migration V10 grants it. Whoever owns the database (the "migrator") creates
-- the schema; this role only uses it. That is what keeps the evidence tables safe even if the
-- application were compromised.
--
-- Run once, as the database owner, BEFORE the application starts:
--   * docker compose does it automatically on the first start of an empty database;
--   * elsewhere: psql -f db/create-app-role.sql
--
-- The password below is a DEVELOPMENT value. In any other environment change it, and give the
-- application the new one through DATABASE_PASSWORD:
--   ALTER ROLE paraphe_app PASSWORD '...';

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'paraphe_app') THEN
        CREATE ROLE paraphe_app LOGIN PASSWORD 'paraphe_app_dev'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
END
$$;
