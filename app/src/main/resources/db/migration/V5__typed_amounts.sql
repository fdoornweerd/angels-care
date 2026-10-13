-- Spec 004: a student's Amount is typed in, and classes no longer have a Day or Boarding fee; only
-- their Ream, which defaults to 5,000 when never set.
--
-- Nothing a term already shows may change, so the Amount each live line took from its class fee is
-- written onto the line, and a line whose Ream was 0 because no class fee was ever in force gets
-- that 0 written, so the new 5,000 default doesn't reach it. Lines whose Ream follows a class Ream
-- that was set keep following it. class_fee.amount and boarding_amount stay, unused.
--
-- Same rules as V3 and V4: rows that get a value here get a new updated_at.

-- No class fee in force for its class and term: Ream 0, as it was.
UPDATE student_term
SET ream = 0,
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE deleted_at IS NULL
  AND ream IS NULL
  AND NOT EXISTS (
        SELECT 1 FROM class_fee f
        JOIN term ft ON ft.id = f.term_id JOIN school_year fy ON fy.id = ft.school_year_id
        JOIN term lt ON lt.id = student_term.term_id
        JOIN school_year ly ON ly.id = lt.school_year_id
        WHERE f.school_class = student_term.school_class
          AND f.deleted_at IS NULL AND ft.deleted_at IS NULL AND fy.deleted_at IS NULL
          AND (fy.year < ly.year OR (fy.year = ly.year AND ft.number <= lt.number)));

-- The Day or Boarding fee in force (set in the line's term or the latest earlier one), or 0.
UPDATE student_term
SET amount = COALESCE((
        SELECT CASE student_term.boarding WHEN 'BOARDING' THEN f.boarding_amount
                                          ELSE f.amount END
        FROM class_fee f
        JOIN term ft ON ft.id = f.term_id JOIN school_year fy ON fy.id = ft.school_year_id
        JOIN term lt ON lt.id = student_term.term_id
        JOIN school_year ly ON ly.id = lt.school_year_id
        WHERE f.school_class = student_term.school_class
          AND f.deleted_at IS NULL AND ft.deleted_at IS NULL AND fy.deleted_at IS NULL
          AND (fy.year < ly.year OR (fy.year = ly.year AND ft.number <= lt.number))
        ORDER BY fy.year DESC, ft.number DESC LIMIT 1), 0),
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE deleted_at IS NULL
  AND amount IS NULL;
