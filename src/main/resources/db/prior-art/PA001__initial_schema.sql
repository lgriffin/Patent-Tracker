-- Prior Art Search schema (separate database: prior_art.db)

CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS search_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    idea_text TEXT NOT NULL,
    decomposed_json TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS discovered_patent (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL REFERENCES search_session(id) ON DELETE CASCADE,
    patent_number TEXT NOT NULL,
    title TEXT,
    abstract_text TEXT,
    assignee TEXT,
    filing_date TEXT,
    grant_date TEXT,
    cpc_codes TEXT,
    source TEXT NOT NULL,
    relevance_score REAL DEFAULT 0.0,
    matched_concepts_json TEXT,
    fetched_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_discovered_patent_session
    ON discovered_patent(session_id);

CREATE INDEX IF NOT EXISTS idx_discovered_patent_number
    ON discovered_patent(patent_number);

CREATE TABLE IF NOT EXISTS search_analysis (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL REFERENCES search_session(id) ON DELETE CASCADE,
    phase TEXT NOT NULL,
    result_json TEXT NOT NULL,
    model_used TEXT,
    duration_ms INTEGER DEFAULT 0,
    cost_usd REAL DEFAULT 0.0,
    analyzed_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(session_id, phase)
);

CREATE INDEX IF NOT EXISTS idx_search_analysis_session
    ON search_analysis(session_id);

INSERT INTO schema_version (version) VALUES (1);
