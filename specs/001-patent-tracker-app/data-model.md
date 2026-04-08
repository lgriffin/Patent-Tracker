# Data Model: Patent Portfolio Tracker

**Date**: 2026-03-27
**Feature**: 001-patent-tracker-app

## Entity Relationship Overview

```
Patent ──< PatentInventor >── Inventor
  │
  ├──< PatentTag >── Tag
  │
  ├──< StatusUpdate
  │
  └──< PatentRelationship (self-referential parent/child)
```

## Entities

### Patent

The central entity representing a patent application or granted patent.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Integer | PK, auto-increment | Internal ID |
| fileNumber | String | UNIQUE, NOT NULL | e.g., "20171047US" |
| title | String | NOT NULL | Patent title |
| filingDate | Date | NULL | YYYY-MM-DD |
| applicationNumber | String | NULL | e.g., "15/661380" |
| publicationDate | Date | NULL | |
| publicationNumber | String | NULL | |
| issueGrantDate | Date | NULL | |
| patentNumber | String | NULL | e.g., "10380367" |
| ptoStatus | String | NOT NULL | Issued, Filed, Published, Abandoned, Dropped, Allowed |
| suffix | String | NOT NULL, DEFAULT 'US' | US, US-CON1, US-DIV1 |
| classification | String | NULL | e.g., "Security", "Containers & Virt", "Quantum" |
| parentFileNumber | String | NULL | FK reference for continuations/divisionals |
| csvRowNumber | Integer | NULL | Original row in stats.csv for traceability |
| createdAt | DateTime | NOT NULL | Record creation timestamp |
| updatedAt | DateTime | NOT NULL | Last modification timestamp |

**Validation Rules**:
- fileNumber must be unique
- ptoStatus must be one of: Issued, Filed, Published, Abandoned, Dropped, Allowed
- suffix must match pattern: US(-CON\d+|-DIV\d+)?
- If suffix contains CON or DIV, parentFileNumber should be derived (e.g., "20191204US-CON1" → parent "20191204US")

**State Transitions** (ptoStatus):
```
Filed → Published → Allowed → Issued
Filed → Published → Abandoned
Filed → Abandoned
Filed → Published → Dropped
Any → (updated via USPTO sync)
```

### Inventor

A person who has contributed to one or more patents.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Integer | PK, auto-increment | |
| fullName | String | NOT NULL | e.g., "Leigh Griffin" |
| username | String | NULL, UNIQUE if present | e.g., "lgriffin" |
| createdAt | DateTime | NOT NULL | |

**Validation Rules**:
- fullName is required
- username extracted from parenthetical format: "Leigh Griffin (lgriffin)" → fullName="Leigh Griffin", username="lgriffin"
- If input is just a username (e.g., "pchibon"), store as both fullName and username until resolved
- Deduplication by username when available, otherwise by normalized fullName

### PatentInventor (Junction)

Links patents to inventors with role information.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Integer | PK, auto-increment | |
| patentId | Integer | FK → Patent, NOT NULL | |
| inventorId | Integer | FK → Inventor, NOT NULL | |
| role | String | NOT NULL | PRIMARY, SECONDARY, ADDITIONAL |
| rolePosition | Integer | NOT NULL | 1-5 mapping to CSV columns |

**Validation Rules**:
- (patentId, inventorId) must be unique
- role must be one of: PRIMARY, SECONDARY, ADDITIONAL
- Each patent must have exactly one PRIMARY inventor
- rolePosition: 1=Primary, 2=Secondary, 3-5=Additional 1-3

### Tag

User-defined metadata labels for organizing patents.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Integer | PK, auto-increment | |
| name | String | UNIQUE, NOT NULL | Case-insensitive uniqueness |
| createdAt | DateTime | NOT NULL | |

**Validation Rules**:
- name must be non-empty, trimmed, and unique (case-insensitive)
- Maximum length: 100 characters
- No special characters except hyphens and underscores

### PatentTag (Junction)

Links patents to tags (many-to-many).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Integer | PK, auto-increment | |
| patentId | Integer | FK → Patent, NOT NULL | |
| tagId | Integer | FK → Tag, NOT NULL | |
| createdAt | DateTime | NOT NULL | |

**Validation Rules**:
- (patentId, tagId) must be unique

### StatusUpdate

Audit trail for changes to patent records.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Integer | PK, auto-increment | |
| patentId | Integer | FK → Patent, NOT NULL | |
| fieldName | String | NOT NULL | e.g., "ptoStatus", "patentNumber" |
| previousValue | String | NULL | Value before change |
| newValue | String | NULL | Value after change |
| source | String | NOT NULL | CSV_IMPORT, USPTO_SYNC, MANUAL_EDIT |
| timestamp | DateTime | NOT NULL | When the change occurred |

**Validation Rules**:
- source must be one of: CSV_IMPORT, USPTO_SYNC, MANUAL_EDIT
- At least one of previousValue or newValue must be non-null

## Indexes

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| Patent | idx_patent_status | ptoStatus | Filter by status |
| Patent | idx_patent_filing_date | filingDate | Sort/filter by date |
| Patent | idx_patent_classification | classification | Filter by classification |
| Patent | idx_patent_app_number | applicationNumber | USPTO sync lookup |
| Patent | idx_patent_parent | parentFileNumber | Parent-child navigation |
| Patent | idx_patent_title_fts | title (FTS5) | Full-text search on titles |
| Inventor | idx_inventor_username | username | Deduplication lookup |
| Inventor | idx_inventor_name | fullName | Search by name |
| PatentInventor | idx_pi_patent | patentId | Join performance |
| PatentInventor | idx_pi_inventor | inventorId | Join performance |
| StatusUpdate | idx_su_patent | patentId | History lookup |
| Tag | idx_tag_name | name COLLATE NOCASE | Case-insensitive lookup |

## Derived Data (Computed at Query Time)

These are not stored but computed for display:

- **Inventor patent count**: COUNT of patents per inventor
- **Co-inventor edges**: For each pair of inventors who share patents, the count of shared patents
- **Status summary**: COUNT of patents grouped by ptoStatus
- **Filing timeline**: COUNT of patents grouped by filing year
- **Tag usage counts**: COUNT of patents per tag
