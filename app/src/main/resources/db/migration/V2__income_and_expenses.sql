-- Spec 001: the school calendar, students and groups, income and expense catalogues, fee
-- assignments and expense budgets.
--
-- Every table follows the sync rules in CLAUDE.md: a UUID text id generated in Java, UTC ISO-8601
-- created_at/updated_at, and a deleted_at that marks a soft delete. Uniqueness therefore only
-- applies among rows that are not deleted, which is why the unique indexes are partial. Enums are
-- stored as their Java name() with a CHECK listing the allowed values. Amounts are whole UGX.

CREATE TABLE school_year (
    id         TEXT PRIMARY KEY,
    year       INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);
CREATE UNIQUE INDEX school_year_unique_year ON school_year (year) WHERE deleted_at IS NULL;

CREATE TABLE term (
    id             TEXT PRIMARY KEY,
    school_year_id TEXT NOT NULL REFERENCES school_year (id),
    number         INTEGER NOT NULL CHECK (number BETWEEN 1 AND 3),
    start_date     TEXT NOT NULL,
    end_date       TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL,
    deleted_at     TEXT
);
CREATE UNIQUE INDEX term_unique_number ON term (school_year_id, number) WHERE deleted_at IS NULL;

CREATE TABLE student (
    id           TEXT PRIMARY KEY,
    first_name   TEXT NOT NULL,
    last_name    TEXT NOT NULL,
    admission_no TEXT,
    school_class TEXT NOT NULL
        CHECK (school_class IN ('BABY', 'MIDDLE', 'TOP', 'P1', 'P2', 'P3', 'P4', 'P5', 'P6', 'P7')),
    residency    TEXT NOT NULL CHECK (residency IN ('NATIONAL', 'REFUGEE')),
    status       TEXT NOT NULL CHECK (status IN ('ACTIVE', 'LEFT')),
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL,
    deleted_at   TEXT
);
CREATE UNIQUE INDEX student_unique_admission_no ON student (lower(admission_no))
    WHERE deleted_at IS NULL AND admission_no IS NOT NULL;

CREATE TABLE student_group (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    deleted_at  TEXT
);
CREATE UNIQUE INDEX student_group_unique_name ON student_group (lower(name)) WHERE deleted_at IS NULL;

-- end_term_id NULL means "still a member".
CREATE TABLE group_membership (
    id            TEXT PRIMARY KEY,
    group_id      TEXT NOT NULL REFERENCES student_group (id),
    student_id    TEXT NOT NULL REFERENCES student (id),
    start_term_id TEXT NOT NULL REFERENCES term (id),
    end_term_id   TEXT REFERENCES term (id),
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL,
    deleted_at    TEXT
);
CREATE INDEX group_membership_by_student ON group_membership (student_id);

CREATE TABLE income_category (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);
CREATE UNIQUE INDEX income_category_unique_name ON income_category (lower(name))
    WHERE deleted_at IS NULL;

CREATE TABLE income_item (
    id          TEXT PRIMARY KEY,
    category_id TEXT NOT NULL REFERENCES income_category (id),
    name        TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    deleted_at  TEXT
);
CREATE UNIQUE INDEX income_item_unique_name ON income_item (category_id, lower(name))
    WHERE deleted_at IS NULL;

-- Exactly one of student_id, group_id and school_class is set, matching target_type.
-- end_term_id NULL means "until further notice".
CREATE TABLE fee_assignment (
    id             TEXT PRIMARY KEY,
    income_item_id TEXT NOT NULL REFERENCES income_item (id),
    target_type    TEXT NOT NULL CHECK (target_type IN ('STUDENT', 'GROUP', 'CLASS')),
    student_id     TEXT REFERENCES student (id),
    group_id       TEXT REFERENCES student_group (id),
    school_class   TEXT
        CHECK (school_class IN ('BABY', 'MIDDLE', 'TOP', 'P1', 'P2', 'P3', 'P4', 'P5', 'P6', 'P7')),
    amount         INTEGER NOT NULL CHECK (amount > 0),
    frequency      TEXT NOT NULL CHECK (frequency IN ('PER_TERM', 'PER_MONTH')),
    start_term_id  TEXT NOT NULL REFERENCES term (id),
    end_term_id    TEXT REFERENCES term (id),
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL,
    deleted_at     TEXT,
    CHECK (
        (target_type = 'STUDENT' AND student_id IS NOT NULL AND group_id IS NULL AND school_class IS NULL)
        OR (target_type = 'GROUP' AND group_id IS NOT NULL AND student_id IS NULL AND school_class IS NULL)
        OR (target_type = 'CLASS' AND school_class IS NOT NULL AND student_id IS NULL AND group_id IS NULL)
    )
);
CREATE INDEX fee_assignment_by_item ON fee_assignment (income_item_id);

CREATE TABLE expense_category (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);
CREATE UNIQUE INDEX expense_category_unique_name ON expense_category (lower(name))
    WHERE deleted_at IS NULL;

CREATE TABLE expense_item (
    id          TEXT PRIMARY KEY,
    category_id TEXT NOT NULL REFERENCES expense_category (id),
    name        TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    deleted_at  TEXT
);
CREATE UNIQUE INDEX expense_item_unique_name ON expense_item (category_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE TABLE expense_budget (
    id              TEXT PRIMARY KEY,
    expense_item_id TEXT NOT NULL REFERENCES expense_item (id),
    school_year_id  TEXT NOT NULL REFERENCES school_year (id),
    monthly_amount  INTEGER NOT NULL CHECK (monthly_amount >= 0),
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT
);
CREATE UNIQUE INDEX expense_budget_unique_item_year ON expense_budget (expense_item_id, school_year_id)
    WHERE deleted_at IS NULL;
