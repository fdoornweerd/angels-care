-- Spec 003: students are Day or Boarding (recorded per term, like class), each class has a Boarding
-- fee next to its Day fee, and every student records the term they joined and, once Left, their
-- last term, so that an earlier term only ever lists the students who were there.
--
-- Same rules as V2 and V3: enums as TEXT with a CHECK, whole UGX as INTEGER, and rows that get a
-- value here (rather than just a column default, which every device gets alike) get a new
-- updated_at.

ALTER TABLE student ADD COLUMN boarding TEXT NOT NULL DEFAULT 'DAY'
    CHECK (boarding IN ('DAY', 'BOARDING'));
-- Null only for students on no term yet: they join the first term whose page is opened.
ALTER TABLE student ADD COLUMN joined_term_id TEXT REFERENCES term (id);
-- Set exactly when status is LEFT.
ALTER TABLE student ADD COLUMN left_term_id TEXT REFERENCES term (id);

ALTER TABLE student_term ADD COLUMN boarding TEXT NOT NULL DEFAULT 'DAY'
    CHECK (boarding IN ('DAY', 'BOARDING'));

-- The class fee's existing amount is the Day fee.
ALTER TABLE class_fee ADD COLUMN boarding_amount INTEGER NOT NULL DEFAULT 0
    CHECK (boarding_amount >= 0);

-- A student joined in the earliest term they are on.
UPDATE student
SET joined_term_id = (
        SELECT s.term_id FROM student_term s
        JOIN term t ON t.id = s.term_id JOIN school_year y ON y.id = t.school_year_id
        WHERE s.student_id = student.id
          AND s.deleted_at IS NULL AND t.deleted_at IS NULL AND y.deleted_at IS NULL
        ORDER BY y.year, t.number LIMIT 1),
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE EXISTS (
        SELECT 1 FROM student_term s
        JOIN term t ON t.id = s.term_id JOIN school_year y ON y.id = t.school_year_id
        WHERE s.student_id = student.id
          AND s.deleted_at IS NULL AND t.deleted_at IS NULL AND y.deleted_at IS NULL);

-- A student already Left left after the latest term they are on.
UPDATE student
SET left_term_id = (
        SELECT s.term_id FROM student_term s
        JOIN term t ON t.id = s.term_id JOIN school_year y ON y.id = t.school_year_id
        WHERE s.student_id = student.id
          AND s.deleted_at IS NULL AND t.deleted_at IS NULL AND y.deleted_at IS NULL
        ORDER BY y.year DESC, t.number DESC LIMIT 1),
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE status = 'LEFT'
  AND EXISTS (
        SELECT 1 FROM student_term s
        JOIN term t ON t.id = s.term_id JOIN school_year y ON y.id = t.school_year_id
        WHERE s.student_id = student.id
          AND s.deleted_at IS NULL AND t.deleted_at IS NULL AND y.deleted_at IS NULL);
