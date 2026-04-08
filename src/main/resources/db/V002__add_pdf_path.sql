-- Add PDF cache path to patent table
ALTER TABLE patent ADD COLUMN pdf_path TEXT;

INSERT OR IGNORE INTO schema_version (version) VALUES (2);
