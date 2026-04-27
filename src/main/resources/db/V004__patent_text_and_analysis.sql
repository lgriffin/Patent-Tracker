CREATE TABLE IF NOT EXISTS patent_text (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patent_id INTEGER NOT NULL UNIQUE REFERENCES patent(id) ON DELETE CASCADE,
    full_text TEXT NOT NULL,
    page_count INTEGER,
    extracted_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS patent_analysis (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patent_id INTEGER NOT NULL REFERENCES patent(id) ON DELETE CASCADE,
    analysis_type TEXT NOT NULL,
    result_json TEXT NOT NULL,
    model_used TEXT,
    analyzed_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(patent_id, analysis_type)
);

CREATE INDEX IF NOT EXISTS idx_pt_patent ON patent_text(patent_id);
CREATE INDEX IF NOT EXISTS idx_pa_patent ON patent_analysis(patent_id);
CREATE INDEX IF NOT EXISTS idx_pa_type ON patent_analysis(analysis_type);

INSERT OR IGNORE INTO schema_version (version) VALUES (4);
