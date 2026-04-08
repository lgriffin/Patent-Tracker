-- Track whether tags were applied by AI or human
ALTER TABLE patent_tag ADD COLUMN source TEXT NOT NULL DEFAULT 'HUMAN';

INSERT OR IGNORE INTO schema_version (version) VALUES (3);
