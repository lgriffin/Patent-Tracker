-- Patent Corpus: bulk download tracking, section-aware chunking, FTS5 search, training data

-- Tracks each downloaded patent document in the corpus
CREATE TABLE IF NOT EXISTS corpus_document (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patent_number TEXT NOT NULL UNIQUE,
    title TEXT,
    abstract_text TEXT,
    assignee TEXT,
    filing_date TEXT,
    grant_date TEXT,
    cpc_codes TEXT,
    source TEXT NOT NULL DEFAULT 'PATENTSVIEW',
    pdf_path TEXT,
    full_text TEXT,
    page_count INTEGER DEFAULT 0,
    word_count INTEGER DEFAULT 0,
    chunk_count INTEGER DEFAULT 0,
    download_status TEXT NOT NULL DEFAULT 'PENDING',
    extraction_status TEXT NOT NULL DEFAULT 'PENDING',
    chunking_status TEXT NOT NULL DEFAULT 'PENDING',
    domain TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_corpus_doc_status ON corpus_document(download_status);
CREATE INDEX IF NOT EXISTS idx_corpus_doc_domain ON corpus_document(domain);
CREATE INDEX IF NOT EXISTS idx_corpus_doc_cpc ON corpus_document(cpc_codes);

-- Section-aware chunks from patent documents
CREATE TABLE IF NOT EXISTS document_chunk (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id INTEGER NOT NULL REFERENCES corpus_document(id) ON DELETE CASCADE,
    patent_number TEXT NOT NULL,
    section_type TEXT NOT NULL,
    chunk_index INTEGER NOT NULL DEFAULT 0,
    chunk_text TEXT NOT NULL,
    word_count INTEGER DEFAULT 0,
    start_position INTEGER DEFAULT 0,
    end_position INTEGER DEFAULT 0,
    metadata_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_chunk_document ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_section ON document_chunk(section_type);
CREATE INDEX IF NOT EXISTS idx_chunk_patent ON document_chunk(patent_number);

-- FTS5 full-text search index on chunks
CREATE VIRTUAL TABLE IF NOT EXISTS chunk_fts USING fts5(
    chunk_text,
    section_type,
    patent_number,
    content=document_chunk,
    content_rowid=id,
    tokenize='porter unicode61'
);

-- Triggers to keep FTS5 in sync with document_chunk table
CREATE TRIGGER IF NOT EXISTS chunk_fts_insert AFTER INSERT ON document_chunk BEGIN
    INSERT INTO chunk_fts(rowid, chunk_text, section_type, patent_number)
    VALUES (new.id, new.chunk_text, new.section_type, new.patent_number);
END;

CREATE TRIGGER IF NOT EXISTS chunk_fts_delete AFTER DELETE ON document_chunk BEGIN
    INSERT INTO chunk_fts(chunk_fts, rowid, chunk_text, section_type, patent_number)
    VALUES ('delete', old.id, old.chunk_text, old.section_type, old.patent_number);
END;

CREATE TRIGGER IF NOT EXISTS chunk_fts_update AFTER UPDATE ON document_chunk BEGIN
    INSERT INTO chunk_fts(chunk_fts, rowid, chunk_text, section_type, patent_number)
    VALUES ('delete', old.id, old.chunk_text, old.section_type, old.patent_number);
    INSERT INTO chunk_fts(rowid, chunk_text, section_type, patent_number)
    VALUES (new.id, new.chunk_text, new.section_type, new.patent_number);
END;

-- Pre-configured domain profiles for bulk corpus building
CREATE TABLE IF NOT EXISTS corpus_domain (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    cpc_codes TEXT NOT NULL,
    keywords TEXT NOT NULL,
    description TEXT
);

-- Seed default CS domains
INSERT OR IGNORE INTO corpus_domain (name, display_name, cpc_codes, keywords, description) VALUES
    ('containers', 'Containers & Orchestration',
     'G06F9/455,G06F9/4856,G06F9/50',
     'container orchestration,kubernetes,docker,containerization,microservices container,pod scheduling,container runtime,service mesh',
     'Container technologies, orchestration platforms, and related resource management'),
    ('virtualization', 'Virtual Machines & Hypervisors',
     'G06F9/455,G06F9/48,G06F9/50',
     'virtual machine,hypervisor,VM migration,virtualization,hardware abstraction,live migration,memory ballooning,paravirtualization',
     'Hardware and software virtualization, hypervisors, and VM management'),
    ('ai_ml', 'Artificial Intelligence & Machine Learning',
     'G06N3,G06N20,G06F18',
     'neural network,deep learning,machine learning,transformer model,reinforcement learning,natural language processing,computer vision,generative AI',
     'AI/ML algorithms, model training, inference, and deployment'),
    ('quantum', 'Quantum Computing',
     'G06N10,H10N60',
     'quantum computing,qubit,quantum gate,quantum error correction,quantum algorithm,quantum cryptography,quantum entanglement,post-quantum',
     'Quantum computing architectures, algorithms, error correction, and quantum-safe cryptography'),
    ('software', 'General Software Engineering',
     'G06F,H04L',
     'distributed system,software architecture,API gateway,event driven,serverless,cloud computing,edge computing,DevOps automation',
     'Broad software engineering including distributed systems, cloud, and DevOps'),
    ('security', 'Cybersecurity',
     'G06F21,H04L9',
     'authentication,access control,encryption,zero trust,intrusion detection,security policy,identity management,threat detection',
     'Security protocols, access control, cryptographic systems, and threat detection');

-- Corpus-level statistics tracking
CREATE TABLE IF NOT EXISTS corpus_stats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stat_key TEXT NOT NULL UNIQUE,
    stat_value TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Training data export tracking
CREATE TABLE IF NOT EXISTS training_export (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    export_name TEXT NOT NULL,
    export_format TEXT NOT NULL,
    file_path TEXT NOT NULL,
    domain_filter TEXT,
    section_filter TEXT,
    chunk_count INTEGER DEFAULT 0,
    file_size_bytes INTEGER DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO schema_version (version) VALUES (2);
