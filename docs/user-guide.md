# User Guide

## Overview

Patent Portfolio Tracker is a desktop application for managing, analyzing, and mining patent portfolios. It combines patent lifecycle management with AI-powered analysis.

## Getting Started

### Prerequisites

- Java 21 (LTS) or later
- Maven 3.8+
- Claude CLI installed (for AI analysis features)
- USPTO Open Data Portal API key (for patent sync)

### Build and Run

```bash
mvn clean package
mvn javafx:run
```

### First Launch

On first launch, the app creates `~/.patenttracker/` with `patents.db` and `config.properties`. The window opens with five tabs: Patents, Graph, Dashboard, Insights, Mining.

## Workflow Overview

Recommended order:

1. Import patents from CSV
2. Configure settings (owner, API keys)
3. Sync with USPTO for latest status
4. Download PDFs and extract text
5. Run AI analyses on the Insights tab
6. Mine for new patent opportunities
7. Export results for review

## Tab Guide

### Patents Tab

- **Table View**: shows all patents with columns for title, file number, dates, status, inventors, tags
- **Search**: full-text search on patent titles using the search bar
- **Filters**: filter by status, inventor, classification, tag, or filing year using dropdowns. Filters compose together.
- **Filter Pills**: active filters shown as pills below the filter bar; click X to remove
- **Patent Detail**: click any patent row to open the detail editor. Edit fields, manage tags, sync with USPTO, view change history.
- **PDF Column**: shows icon for PDF status (cached/available/none). Click to download or open.

### Dashboard Tab

- **Summary Cards**: Total Patents, Patented, Allowed, In Examination, Filed/Pending, Abandoned/Expired, Continuances
- **Owner Statistics**: select portfolio owner in Settings. Shows count as Primary, Secondary, Additional inventor.
- **Status Pie Chart**: distribution across lifecycle phases
- **Yearly Breakdown**: bar chart of Filed vs Issued vs Published by year
- **Classifications Table**: ranked list of formal classifications with patent counts
- **Top 10 Collaborators**: most frequent co-inventors with counts broken down by status and top classifications

### Graph Tab

- Interactive inventor collaboration network using vis-network
- Nodes colored by primary technology domain
- Portfolio owner shown as gold star node
- Edge brightness scales with collaboration strength
- Hover any node to focus on that inventor's connections
- Click a node to see patent list and top co-inventors in the side panel

### Insights Tab

See [Portfolio Analysis Guide](portfolio-analysis-guide.md) for detailed coverage.

Brief summary:

- 12 analysis types: 4 single-patent (Claims, Technology, Expansion, Prior Art) + 8 cross-patent (Whitespace, Clustering, Adjacency, Temporal Trends, Claim Collision, Competitor Gaps, Invention Prompts, Cross-Domain)
- Batch operations: Extract All Text, Run Technology Extraction, Run Claims/Expansion/Prior Art
- Progress tracking with cancel support
- Results displayed in accordion, exportable to Markdown
- Cost tracking: tokens and USD per analysis

### Mining Tab

See [Mining Guide](mining-guide.md) for detailed coverage.

Brief summary:

- Two modes: General Area Mining and Invention Prompt Mining
- Searches Google Patents for external competitive patents
- Claude AI generates new patent ideas, defensive opportunities, and validates invention ideas
- Mining history with browseable past results
- Bulk "Mine All Prompts" for comprehensive exploration
- Export individual or all results to Markdown

## Settings Reference

Access via the Settings tab or menu.

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| Portfolio Owner | (first inventor) | Any name | Controls dashboard owner statistics |
| USPTO API Key | (empty) | String | API key from data.uspto.gov |
| Rate Limit Delay | 1100ms | 100-5000ms | Delay between USPTO API calls |
| Claude CLI Path | "claude" | File path | Path to the Claude CLI binary |
| Analysis Timeout | 600s | 120-1200s | Max time for a single AI analysis call |
| Idle Timeout | 120s | 30-600s | Max idle time before killing a stalled analysis |
| Batch Size | 30 | 5-100 | Patents per chunk for cross-patent analyses |

## Data Storage

All data stored locally in `~/.patenttracker/`:

| Path | Contents |
|------|----------|
| `patents.db` | SQLite database (WAL mode) -- patents, inventors, tags, analyses, mined patents, audit log |
| `config.properties` | Settings: owner, API keys, Claude CLI path, timeouts, classifications |
| `pdfs/` | Cached patent PDF files |
| `logs/` | Analysis operation logs (insight-analysis.log) |

### Backup

To back up your data, copy the entire `~/.patenttracker/` directory. The SQLite database is self-contained.

## Menu Reference

### File Menu

- **Import CSV...** -- Import patents from a CSV file (see [CSV Format](csv-format.md))

### Tools Menu

- **Sync All with USPTO...** -- Bulk sync all patents with USPTO ODP API
- **Auto-Tag Patents (AI)...** -- Run keyword-based auto-tagging across 19+ technology domains
- **Manage Classifications...** -- Add, rename, or remove formal classification categories

## Troubleshooting

### "No patents found" when mining

- Google Patents search returns US patents from the last 2 years only
- Try broader search terms or a different area
- Check internet connectivity

### Claude CLI analysis fails

- Verify Claude CLI is installed and on PATH (or set the full path in Settings)
- Increase Analysis Timeout for large portfolios (400+ patents may need 900-1200s)
- Increase Idle Timeout if analyses stall (streaming analyses may have long pauses)
- Check `~/.patenttracker/logs/insight-analysis.log` for detailed error messages

### USPTO sync errors

- Ensure your API key is set in Settings
- Check that patents have Application Number populated
- Some older patents may not have records in the ODP API
- Rate limit errors: increase the Rate Limit Delay in Settings

### CSV import errors

- See [CSV Format](csv-format.md) for column specification
- Minimum 11 columns required per row
- Dates must be YYYY-MM-DD format
- File Number is required and must be unique

### Cross-patent analysis produces incomplete results

- Ensure Technology Extraction is complete for all patents (auto-extracted if missing)
- For large portfolios (100+ patents), reduce Batch Size to avoid prompt size limits
- If merge phase fails, the system falls back to the largest chunk result

## Related Documentation

- [Architecture Overview](architecture/overview.md)
- [Class Diagrams](architecture/class-diagrams.md)
- [Sequence Diagrams](architecture/sequence-diagrams.md)
- [CSV Import Format](csv-format.md)
- [Mining Guide](mining-guide.md)
- [Portfolio Analysis Guide](portfolio-analysis-guide.md)
