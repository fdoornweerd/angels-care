-- The pre-scaffold app created a throwaway `students` table holding only 'Test Student' rows.
-- Drop it so real student data later starts from a clean, properly designed table.
DROP TABLE IF EXISTS students;

-- Facts about this database file itself (device_id, when it was created). Not school data, and
-- never synced, so it deliberately has none of the id/created_at/updated_at/deleted_at columns.
CREATE TABLE app_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO app_meta (key, value)
VALUES ('schema_created_at', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));
