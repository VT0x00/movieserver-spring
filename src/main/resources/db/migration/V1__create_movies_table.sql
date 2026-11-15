CREATE TABLE IF NOT EXISTS movies (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    title             TEXT NOT NULL,
    year              INTEGER NOT NULL,
    genre             TEXT,
    duration_seconds  INTEGER NOT NULL,
    file_name         TEXT NOT NULL,
    file_path         TEXT NOT NULL,
    file_size         INTEGER NOT NULL,
    created_at        TEXT NOT NULL
);
