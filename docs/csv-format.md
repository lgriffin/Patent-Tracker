# CSV Import Format

## Overview

The application imports patent data from CSV files exported from internal patent tracking systems. Each row represents a single patent filing. **File Number** is the unique key used for deduplication: re-importing the same CSV will update changed records and skip unchanged ones without creating duplicates.

## Column Specification

| Index | Column Name | Type | Required | Notes |
|-------|-------------|------|----------|-------|
| 0 | (Row Number) | Any | No | Ignored by the importer |
| 1 | File Number | String | **YES** | Unique identifier for deduplication (e.g., `"20171047US"`) |
| 2 | Title | String | **YES** | Defaults to `"Untitled"` if blank |
| 3 | Filing Date | Date (`YYYY-MM-DD`) | No | Also handles `"YYYY-MM-DD 0:00:00"` format |
| 4 | Application Number | String | No | Used for USPTO sync lookups (e.g., `"15/661380"`) |
| 5 | Publication Date | Date (`YYYY-MM-DD`) | No | |
| 6 | Publication Number | String | No | |
| 7 | Issue/Grant Date | Date (`YYYY-MM-DD`) | No | |
| 8 | Patent Number | String | No | e.g., `"10380367"` |
| 9 | PTO Status | String | No | Defaults to `"Unknown"`. **Note:** this field is never updated from CSV on re-import -- USPTO sync is the authoritative source |
| 10 | Suffix | String | No | Defaults to `"US"`. Examples: `"US"`, `"US-CON1"`, `"US-DIV1"`, `"US-CIP1"` |
| 11 | Primary Inventor(s) | String | No | Supports comma-separated names and `"Full Name (username)"` format |
| 12 | Secondary Inventor | String | No | Optional |
| 13 | Additional Inventor 1 | String | No | Optional |
| 14 | Additional Inventor 2 | String | No | Optional |
| 15 | Additional Inventor 3 | String | No | Optional |
| 16 | Classification | String | No | Optional formal classification tag |

**Minimum required columns:** 11 (indices 0--10). Columns 11--16 are optional.

## Example CSV

```csv
Row,File Number,Title,Filing Date,Application Number,Publication Date,Publication Number,Issue/Grant Date,Patent Number,PTO Status,Suffix,Primary Inventor,Secondary Inventor,Additional Inventor 1,,Classification
1,20171047US,Container Security Framework,2017-03-15,15/661380,2018-09-20,US20180267123A1,2019-08-13,10380367,Patented,US,"Leigh Griffin (lgriffin)","Stephen Coady (scoady)",,,Security
2,20191204US,Quantum Key Distribution Method,2019-06-01,16/428901,2020-12-03,US20200389456A1,,,,US,"Leigh Griffin (lgriffin)","Paul Browne (pbrowne)","John Smith",,Quantum Computing
3,20191204US-CON1,Quantum Key Distribution Continuation,2020-01-15,16/742338,,,,Allowed,US-CON1,"Leigh Griffin (lgriffin)",,,,Quantum Computing
```

## Inventor Name Formats

The importer recognizes three name formats:

1. **`"Full Name (username)"`** -- e.g., `"Leigh Griffin (lgriffin)"` results in `fullName="Leigh Griffin"`, `username="lgriffin"`.
2. **`"Full Name"`** (contains a space, no parentheses) -- e.g., `"Stephen Coady"` results in `fullName="Stephen Coady"`, `username=null`.
3. **`"username"`** (no spaces, no parentheses) -- e.g., `"pchibon"` results in `fullName="pchibon"`, `username="pchibon"`.

Comma-separated names in a single column are split and processed individually.

## Deduplication Behavior

- **File Number** is the unique key.
- On re-import, existing patents are compared field-by-field.
- If any field changed: the record is updated, counted as **"updated"**.
- If all fields match: the record is skipped, counted as **"unchanged"**.
- New file numbers: inserted, counted as **"imported"**.
- Re-importing is safe and idempotent.

## Merge Logic

On re-import, certain fields are updated while others are preserved:

**Updated on re-import:** `title`, `filingDate`, `applicationNumber`, `publicationDate`, `publicationNumber`, `issueGrantDate`, `patentNumber`, `classification`, `parentFileNumber`.

**Never updated from CSV:** `ptoStatus` (USPTO sync is the authoritative source for patent status).

## Parent/Child Detection

The `FileNumberParser` automatically detects parent-child relationships from the file number suffix:

- Suffix patterns like `"US-CON1"`, `"US-DIV1"`, `"US-CIP1"` indicate continuations, divisionals, and continuation-in-part (CIP) filings respectively.
- The parent file number is derived by removing the suffix: `"20191204US-CON1"` indicates a parent of `"20191204US"`.
- This creates parent-child relationships automatically in the database.

## Common Issues

1. **Date format:** Must be `YYYY-MM-DD` (ISO format). The parser also handles `"YYYY-MM-DD 0:00:00"`.
2. **Missing columns:** Rows with fewer than 11 columns are skipped with an error logged.
3. **Duplicate file numbers in CSV:** The second occurrence overwrites the first (last-write-wins).
4. **Encoding:** CSV should be UTF-8.
5. **Header row:** The first row is always skipped (treated as header).
