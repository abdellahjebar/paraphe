-- How long a reviewer may stay silent before the follow-up screen says so.
--
-- Two numbers per project: from how many days of waiting a reply should be chased, and from how
-- many it is late. They belong to the project because sites do not all work at the same rhythm.
-- The defaults match what was observed: a week is normal, two weeks is not.

ALTER TABLE project
    ADD COLUMN chase_from_days INTEGER NOT NULL DEFAULT 8,
    ADD COLUMN late_from_days  INTEGER NOT NULL DEFAULT 15;

ALTER TABLE project ADD CONSTRAINT project_waiting_thresholds
    CHECK (chase_from_days >= 1 AND late_from_days > chase_from_days AND late_from_days <= 365);

-- The application may change these two settings and nothing else about a project.
GRANT UPDATE (chase_from_days, late_from_days) ON project TO paraphe_app;
