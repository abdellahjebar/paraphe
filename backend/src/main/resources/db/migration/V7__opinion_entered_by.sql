-- An opinion can be recorded by the contractor on behalf of a reviewer (answer received by phone or
-- by message). The proof must say so: entered_by names who typed it, and stays empty when the
-- reviewer answered through their own link.

ALTER TABLE opinion ADD COLUMN entered_by TEXT;

ALTER TABLE opinion ADD CONSTRAINT opinion_entered_by_not_blank
    CHECK (entered_by IS NULL OR btrim(entered_by) <> '');
