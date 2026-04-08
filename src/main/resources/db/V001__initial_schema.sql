-- Patent Portfolio Tracker - Initial Schema

CREATE TABLE IF NOT EXISTS patent (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_number TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    filing_date TEXT,
    application_number TEXT,
    publication_date TEXT,
    publication_number TEXT,
    issue_grant_date TEXT,
    patent_number TEXT,
    pto_status TEXT NOT NULL,
    suffix TEXT NOT NULL DEFAULT 'US',
    classification TEXT,
    parent_file_number TEXT,
    csv_row_number INTEGER,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS inventor (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name TEXT NOT NULL,
    username TEXT UNIQUE,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS patent_inventor (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patent_id INTEGER NOT NULL REFERENCES patent(id) ON DELETE CASCADE,
    inventor_id INTEGER NOT NULL REFERENCES inventor(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    role_position INTEGER NOT NULL,
    UNIQUE(patent_id, inventor_id)
);

CREATE TABLE IF NOT EXISTS tag (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS patent_tag (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patent_id INTEGER NOT NULL REFERENCES patent(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(patent_id, tag_id)
);

CREATE TABLE IF NOT EXISTS status_update (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patent_id INTEGER NOT NULL REFERENCES patent(id) ON DELETE CASCADE,
    field_name TEXT NOT NULL,
    previous_value TEXT,
    new_value TEXT,
    source TEXT NOT NULL,
    timestamp TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_patent_status ON patent(pto_status);
CREATE INDEX IF NOT EXISTS idx_patent_filing_date ON patent(filing_date);
CREATE INDEX IF NOT EXISTS idx_patent_classification ON patent(classification);
CREATE INDEX IF NOT EXISTS idx_patent_app_number ON patent(application_number);
CREATE INDEX IF NOT EXISTS idx_patent_parent ON patent(parent_file_number);
CREATE INDEX IF NOT EXISTS idx_inventor_username ON inventor(username);
CREATE INDEX IF NOT EXISTS idx_inventor_name ON inventor(full_name);
CREATE INDEX IF NOT EXISTS idx_pi_patent ON patent_inventor(patent_id);
CREATE INDEX IF NOT EXISTS idx_pi_inventor ON patent_inventor(inventor_id);
CREATE INDEX IF NOT EXISTS idx_su_patent ON status_update(patent_id);

-- FTS5 virtual table for full-text search on patent titles
CREATE VIRTUAL TABLE IF NOT EXISTS patent_fts USING fts5(title, file_number, content=patent, content_rowid=id);

-- Triggers to keep FTS index in sync
CREATE TRIGGER IF NOT EXISTS patent_ai AFTER INSERT ON patent BEGIN
    INSERT INTO patent_fts(rowid, title, file_number) VALUES (new.id, new.title, new.file_number);
END;

CREATE TRIGGER IF NOT EXISTS patent_ad AFTER DELETE ON patent BEGIN
    INSERT INTO patent_fts(patent_fts, rowid, title, file_number) VALUES('delete', old.id, old.title, old.file_number);
END;

CREATE TRIGGER IF NOT EXISTS patent_au AFTER UPDATE OF title, file_number ON patent BEGIN
    INSERT INTO patent_fts(patent_fts, rowid, title, file_number) VALUES('delete', old.id, old.title, old.file_number);
    INSERT INTO patent_fts(rowid, title, file_number) VALUES (new.id, new.title, new.file_number);
END;

-- Schema version tracking
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO schema_version (version) VALUES (1);
