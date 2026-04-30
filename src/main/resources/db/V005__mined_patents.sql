CREATE TABLE IF NOT EXISTS mined_patent (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patent_number TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    abstract_text TEXT,
    grant_date TEXT,
    search_area TEXT NOT NULL,
    search_query TEXT,
    fetched_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_mp_search_area ON mined_patent(search_area);

INSERT OR IGNORE INTO schema_version (version) VALUES (5);
