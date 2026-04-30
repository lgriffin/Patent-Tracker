# Sequence Diagrams

This document provides detailed sequence diagrams for the major workflows in the Patent Stats application. Each diagram illustrates the interaction between UI controllers, services, data access objects, and external systems.

---

## 1. CSV Import Flow

The CSV import flow handles bulk ingestion of patent data from exported spreadsheets. It performs deduplication by file number, detects continuation/divisional parent relationships, normalizes inventor names, and produces a summary of what changed.

```mermaid
sequenceDiagram
    participant U as User
    participant MC as MainController
    participant FC as FileChooser
    participant CIS as CsvImportService
    participant FNP as FileNumberParser
    participant NP as NameParser
    participant PD as PatentDao
    participant ID as InventorDao
    participant SUD as StatusUpdateDao

    U->>MC: File > Import CSV
    MC->>FC: showOpenDialog()
    FC-->>U: File selection dialog
    U->>FC: Select CSV file
    FC-->>MC: filePath

    Note over MC: Spawns background thread

    MC->>CIS: importCsv(filePath)
    CIS->>CIS: Read CSV, skip header row

    loop For each CSV row
        CIS->>CIS: Parse patent fields from row
        CIS->>FNP: detectParent(fileNumber)
        FNP-->>CIS: parentFileNumber (CON/DIV) or null
        CIS->>PD: findByFileNumber(fileNumber)

        alt Patent exists
            PD-->>CIS: existingPatent
            CIS->>CIS: Merge changes into existing record
            alt Fields changed
                CIS->>PD: update(patent)
                CIS->>SUD: insert(statusUpdate)
                Note right of CIS: Count: updated++
            else No changes
                Note right of CIS: Count: unchanged++
            end
        else New patent
            PD-->>CIS: null
            CIS->>PD: insert(patent)
            Note right of CIS: Count: imported++
        end

        loop For each inventor column
            CIS->>NP: parse(rawName)
            NP-->>CIS: ParsedName(first, last)
            CIS->>ID: findOrCreate(parsedName)
            ID-->>CIS: inventor
            CIS->>CIS: Create PatentInventor junction
        end
    end

    CIS-->>MC: ImportResult(imported, updated, unchanged, errors)
    MC->>U: Show summary Alert
```

---

## 2. USPTO Sync Flow

The USPTO sync flow keeps local patent records up to date by querying the USPTO API for each tracked application. It detects status changes, classifies the type of movement (progression, regression, terminal, or lateral), and logs an audit trail of every change.

```mermaid
sequenceDiagram
    participant U as User
    participant SC as SyncController
    participant USS as UsptoSyncService
    participant API as USPTO_API
    participant PD as PatentDao
    participant SUD as StatusUpdateDao

    U->>SC: Tools > Sync All
    SC->>U: Open modal progress dialog

    Note over SC: Spawns background thread

    loop For each patent with application number
        SC->>USS: syncPatent(patent)
        USS->>API: GET https://api.uspto.gov/...
        API-->>USS: Patent status response

        USS->>USS: Compare response fields against local patent

        alt Changes found
            USS->>PD: update(patent)
            USS->>SUD: insert(statusUpdate)
            Note over USS: Classify change type
            USS->>USS: Classify: Progressed / Regressed / Terminal / Lateral
            USS-->>SC: SyncResult(updated, changeType)
        else No changes
            USS-->>SC: SyncResult(unchanged)
        end

        USS->>SC: Report progress (current/total)
    end

    SC->>U: Show summary: updated/unchanged/errors
    Note over SC,U: Includes movement breakdown:<br/>Progressed, Regressed, Terminal, Lateral
```

---

## 3. Single-Patent Analysis Flow

Single-patent analysis runs AI-powered analysis on individual patents using the Claude CLI. It extracts text from cached PDFs when needed, substitutes patent metadata into prompt templates, and stores structured JSON results along with token usage and cost tracking.

```mermaid
sequenceDiagram
    participant U as User
    participant IC as InsightsController
    participant IS as InsightService
    participant PES as PdfExtractorService
    participant CCS as ClaudeCliService
    participant CLI as Claude_CLI
    participant PAD as PatentAnalysisDao

    U->>IC: Click analysis button (e.g. "Run Technology Extraction")

    Note over IC: Spawns background thread

    IC->>IS: analyzeAll(analysisType)

    loop For each patent
        IS->>IS: Check PatentTextDao for extracted text

        opt Text not cached
            IS->>PES: extractText(cachedPdf)
            PES-->>IS: extractedText
        end

        IS->>IS: Load prompt template from resources
        IS->>IS: Substitute variables (patent_title, patent_number, patent_text)

        IS->>CCS: analyze(prompt)
        CCS->>CLI: Spawn Claude CLI subprocess
        CLI-->>CCS: JSON result + token counts + cost
        CCS->>CCS: Extract and validate JSON
        CCS-->>IS: AnalysisResult(json, tokens, cost)

        IS->>PAD: insertOrUpdate(analysisResult)

        Note over IS: Wait 2 seconds (rate limiting)

        IS->>IC: Report progress (current/total)
    end

    IC->>U: Update UI with counts and results
```

---

## 4. Cross-Patent Analysis (Chunked Map-Reduce)

Cross-patent analyses such as whitespace finding and portfolio gap analysis operate across the entire patent portfolio. When the portfolio exceeds the batch size, it is split into chunks and processed in a map-reduce pattern: each chunk is analyzed independently, then chunk results are merged hierarchically in groups of three until a single consolidated result remains.

```mermaid
sequenceDiagram
    participant U as User
    participant IC as InsightsController
    participant IS as InsightService
    participant CCS as ClaudeCliService
    participant CLI as Claude_CLI
    participant PAD as PatentAnalysisDao

    U->>IC: Click cross-patent analysis (e.g. "Whitespace Finder")

    Note over IC: Spawns background thread

    IC->>IS: analyzeCrossPatent(analysisType)
    IS->>IS: Build patent-tech pairs

    opt Technology analysis missing
        IS->>IS: Auto-run Technology Extraction first
    end

    alt Portfolio > batch size
        IS->>IS: Split into chunks

        Note over IS,CLI: MAP PHASE

        loop For each chunk
            IS->>IS: Build summary text from chunk patents
            IS->>CCS: analyzeStreaming(chunkPrompt, callback)
            CCS->>CLI: Spawn Claude CLI subprocess

            par Streaming and watchdog
                CLI->>CCS: Stream JSON events (system, assistant, result)
                CCS->>IC: Streaming progress callback
            and
                Note over CCS: Watchdog thread monitors idle timeout
            end

            CCS-->>IS: Chunk result JSON
        end

        Note over IS,CLI: MERGE PHASE (hierarchical)

        loop Until single result remains
            IS->>IS: Group chunk results in sets of 3
            loop For each group
                IS->>IS: Load merge template
                IS->>CCS: analyzeStreaming(mergePrompt, 3x idle timeout)
                CCS->>CLI: Spawn Claude CLI subprocess
                CLI-->>CCS: Merged result JSON

                alt Merge fails
                    Note over IS: Fallback: use largest chunk result
                end

                CCS-->>IS: Merged group result
            end
        end
    else Portfolio fits in single batch
        IS->>CCS: analyzeStreaming(fullPrompt, callback)
        CCS->>CLI: Spawn Claude CLI subprocess
        CLI-->>CCS: Result JSON
        CCS-->>IS: Analysis result
    end

    IS->>PAD: insertOrUpdate(finalResult)
    IS-->>IC: Complete
    IC->>U: Display result in accordion
```

---

## 5. General Area Patent Mining

General area mining searches external patent databases to understand the competitive landscape around a technology area. It queries Google Patents for prior art, combines external findings with the user's own portfolio summary, and uses Claude to produce a landscape analysis including patent ideas and defensive filing opportunities.

```mermaid
sequenceDiagram
    participant U as User
    participant MnC as MiningController
    participant PMS as PatentMiningService
    participant GPS as GooglePatentsSearchService
    participant GP as Google_Patents
    participant CCS as ClaudeCliService
    participant CLI as Claude_CLI
    participant MPD as MinedPatentDao
    participant PAD as PatentAnalysisDao

    U->>MnC: Select area from ComboBox, click "Mine Patents"

    Note over MnC: Spawns background thread

    MnC->>PMS: mineArea(selectedArea)

    PMS->>GPS: search(areaKeywords)
    GPS->>GPS: Build XHR query URL
    GPS->>GP: HTTP GET (Google Patents XHR API)
    GP-->>GPS: JSON response
    GPS->>GPS: Parse: cluster[].result[].patent
    GPS-->>PMS: List<MinedPatent>

    PMS->>MPD: storeAll(minedPatents)

    PMS->>PMS: Build portfolio summary (all TECHNOLOGY analyses)
    PMS->>PMS: Build external patents text from MinedPatentDao

    PMS->>PMS: Load patent-mining.txt template
    PMS->>CCS: analyzeStreaming(miningPrompt)
    CCS->>CLI: Spawn Claude CLI subprocess

    CLI->>CCS: Stream: landscape summary
    CLI->>CCS: Stream: patent ideas
    CLI->>CCS: Stream: defensive opportunities

    CCS-->>PMS: MiningResult(landscape, ideas, defensive)

    PMS->>PAD: insertOrUpdate(type="PATENT_MINING:{area}", result)
    PMS-->>MnC: Complete

    MnC->>MnC: Refresh mining history accordion
    MnC->>U: Display results
```

---

## 6. Bulk IP Mining

Bulk IP mining automates the patent mining workflow across all invention prompts previously generated by the invention prompt analysis. For each prompt, it derives search keywords, queries Google Patents, and runs a specialized IP mining analysis. This enables rapid coverage of the full invention space with cost tracking and progress reporting.

```mermaid
sequenceDiagram
    participant U as User
    participant MnC as MiningController
    participant PMS as PatentMiningService
    participant GPS as GooglePatentsSearchService
    participant CCS as ClaudeCliService
    participant CLI as Claude_CLI
    participant PAD as PatentAnalysisDao

    U->>MnC: Click "Mine All Prompts" (IP mode)

    Note over MnC: Spawns background thread

    MnC->>PMS: mineAllInventionPrompts()
    PMS->>PMS: extractInventionPrompts from INVENTION_PROMPTS analysis

    loop For each invention prompt
        PMS->>MnC: Report progress (current/total, prompt title)

        PMS->>PMS: Derive search keywords from title + domain

        PMS->>GPS: search(keywords)
        GPS-->>PMS: List<MinedPatent>

        PMS->>PMS: Build portfolio summary + external patents text
        PMS->>PMS: Load patent-mining-ip.txt template

        PMS->>CCS: analyzeStreaming(ipMiningPrompt)
        CCS->>CLI: Spawn Claude CLI subprocess

        CLI-->>CCS: IP mining result JSON
        CCS-->>PMS: MiningResult

        PMS->>PAD: insertOrUpdate(type="PATENT_MINING_IP:{title}", result)

        alt Success
            PMS->>MnC: Report completion (success)
        else Failure
            PMS->>MnC: Report completion (failure + error)
        end
    end

    PMS-->>MnC: BulkResult(succeeded, failed, totalCost)
    MnC->>U: Show total: X/Y succeeded, total cost
    MnC->>MnC: Refresh mining history accordion
```
