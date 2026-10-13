-- Two engineering offices share the same role but not the same speciality (structure, technical
-- trades...). The speciality tells them apart. OTHER is a safe fallback for a party the fixed
-- list does not name yet; new roles are added only when their need is confirmed.

ALTER TABLE participant ADD COLUMN specialty TEXT;

ALTER TABLE participant DROP CONSTRAINT participant_role_check;
ALTER TABLE participant ADD CONSTRAINT participant_role_check
    CHECK (role IN ('ARCHITECT', 'ENGINEERING_OFFICE', 'CONTROL_OFFICE', 'OTHER'));
