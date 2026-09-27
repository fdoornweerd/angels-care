-- Spec 002: term accounts. Categories and items belong to a school year; quantity × rate is
-- recorded per item per month of a term; each category has an expected/budgeted amount per term;
-- class fees carry forward from the term they are set in; each student has a line per term.
--
-- Same rules as V2: UUID text ids from Java, UTC ISO-8601 created_at/updated_at, soft deletes via
-- deleted_at (so unique indexes are partial), enums as TEXT with a CHECK, whole UGX as INTEGER.
-- The V2 tables student_group, group_membership, fee_assignment and expense_budget are no longer
-- used; they stay because committed migrations are never edited.

-- Categories now belong to a school year. Categories from before V3 join the latest school year,
-- so they stay visible (on a database with no school year they keep none and are not shown).
-- The changed rows get a new updated_at, like any other write.
ALTER TABLE income_category ADD COLUMN school_year_id TEXT REFERENCES school_year (id);
ALTER TABLE expense_category ADD COLUMN school_year_id TEXT REFERENCES school_year (id);
UPDATE income_category
SET school_year_id = (SELECT id FROM school_year WHERE deleted_at IS NULL ORDER BY year DESC LIMIT 1),
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE school_year_id IS NULL
  AND EXISTS (SELECT 1 FROM school_year WHERE deleted_at IS NULL);
UPDATE expense_category
SET school_year_id = (SELECT id FROM school_year WHERE deleted_at IS NULL ORDER BY year DESC LIMIT 1),
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE school_year_id IS NULL
  AND EXISTS (SELECT 1 FROM school_year WHERE deleted_at IS NULL);
DROP INDEX income_category_unique_name;
DROP INDEX expense_category_unique_name;
CREATE UNIQUE INDEX income_category_unique_name ON income_category (school_year_id, lower(name))
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX expense_category_unique_name ON expense_category (school_year_id, lower(name))
    WHERE deleted_at IS NULL;

-- What an item is counted in: "kg", "bags", "months".
ALTER TABLE income_item ADD COLUMN unit TEXT NOT NULL DEFAULT '';
ALTER TABLE expense_item ADD COLUMN unit TEXT NOT NULL DEFAULT '';

-- quantity × rate for one item in one month (1-3) of a term. Either may still be blank.
-- Quantities are stored in hundredths (12.5 kg = 1250), never as floating point.
CREATE TABLE income_entry (
    id                  TEXT PRIMARY KEY,
    item_id             TEXT NOT NULL REFERENCES income_item (id),
    term_id             TEXT NOT NULL REFERENCES term (id),
    month               INTEGER NOT NULL CHECK (month BETWEEN 1 AND 3),
    quantity_hundredths INTEGER CHECK (quantity_hundredths >= 0),
    rate                INTEGER CHECK (rate >= 0),
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    deleted_at          TEXT
);
CREATE UNIQUE INDEX income_entry_unique ON income_entry (item_id, term_id, month)
    WHERE deleted_at IS NULL;

CREATE TABLE expense_entry (
    id                  TEXT PRIMARY KEY,
    item_id             TEXT NOT NULL REFERENCES expense_item (id),
    term_id             TEXT NOT NULL REFERENCES term (id),
    month               INTEGER NOT NULL CHECK (month BETWEEN 1 AND 3),
    quantity_hundredths INTEGER CHECK (quantity_hundredths >= 0),
    rate                INTEGER CHECK (rate >= 0),
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    deleted_at          TEXT
);
CREATE UNIQUE INDEX expense_entry_unique ON expense_entry (item_id, term_id, month)
    WHERE deleted_at IS NULL;

-- The expected (income) or budgeted (expense) amount for a category in a term.
CREATE TABLE income_plan (
    id          TEXT PRIMARY KEY,
    category_id TEXT NOT NULL REFERENCES income_category (id),
    term_id     TEXT NOT NULL REFERENCES term (id),
    amount      INTEGER NOT NULL CHECK (amount >= 0),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    deleted_at  TEXT
);
CREATE UNIQUE INDEX income_plan_unique ON income_plan (category_id, term_id) WHERE deleted_at IS NULL;

CREATE TABLE expense_plan (
    id          TEXT PRIMARY KEY,
    category_id TEXT NOT NULL REFERENCES expense_category (id),
    term_id     TEXT NOT NULL REFERENCES term (id),
    amount      INTEGER NOT NULL CHECK (amount >= 0),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    deleted_at  TEXT
);
CREATE UNIQUE INDEX expense_plan_unique ON expense_plan (category_id, term_id) WHERE deleted_at IS NULL;

-- A class's fee and ream charge from term_id onwards, until a later term has its own row.
CREATE TABLE class_fee (
    id           TEXT PRIMARY KEY,
    school_class TEXT NOT NULL
        CHECK (school_class IN ('BABY', 'MIDDLE', 'TOP', 'P1', 'P2', 'P3', 'P4', 'P5', 'P6', 'P7')),
    term_id      TEXT NOT NULL REFERENCES term (id),
    amount       INTEGER NOT NULL CHECK (amount >= 0),
    ream         INTEGER NOT NULL CHECK (ream >= 0),
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL,
    deleted_at   TEXT
);
CREATE UNIQUE INDEX class_fee_unique ON class_fee (school_class, term_id) WHERE deleted_at IS NULL;

-- A student's line on a term. school_class is recorded when they are put on the term, so moving
-- class later doesn't move them in past terms. amount, ream and debt are null while the class fee
-- (or carried balance) applies. debt may be negative: a credit.
CREATE TABLE student_term (
    id           TEXT PRIMARY KEY,
    student_id   TEXT NOT NULL REFERENCES student (id),
    term_id      TEXT NOT NULL REFERENCES term (id),
    school_class TEXT NOT NULL
        CHECK (school_class IN ('BABY', 'MIDDLE', 'TOP', 'P1', 'P2', 'P3', 'P4', 'P5', 'P6', 'P7')),
    amount       INTEGER CHECK (amount >= 0),
    ream         INTEGER CHECK (ream >= 0),
    debt         INTEGER,
    paid_1       INTEGER CHECK (paid_1 >= 0),
    paid_2       INTEGER CHECK (paid_2 >= 0),
    paid_3       INTEGER CHECK (paid_3 >= 0),
    remarks      TEXT,
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL,
    deleted_at   TEXT
);
CREATE UNIQUE INDEX student_term_unique ON student_term (student_id, term_id) WHERE deleted_at IS NULL;
CREATE INDEX student_term_by_term ON student_term (term_id);
