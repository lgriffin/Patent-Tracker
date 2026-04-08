# Patent Portfolio Tracker

A desktop application for managing, analyzing, and tracking patent portfolios. Built with Java 21 and JavaFX, it provides CSV import with deduplication, live USPTO synchronization, AI-powered classification tagging, an interactive inventor collaboration graph, and a portfolio analytics dashboard.

## Features

### Patent Management

- **CSV Import with Smart Deduplication** — Import patents from CSV files using the File Number as a primary key. On re-import, only genuinely new records are added; existing patents are updated if any fields have changed, and unchanged records are skipped. A summary dialog breaks down exactly what was new, updated, or unchanged. Patent status (PTO Status) is never imported from CSV — USPTO sync is the authoritative source for status.
- **Full-Text Search** — Search patents by title using SQLite FTS5 full-text search with prefix matching.
- **Multi-Filter View** — Filter by status, inventor, classification, tags, and filing year. Filters compose together.
- **Patent Detail View** — View and edit patent fields, manage tags, see related filings (continuations/divisionals), and review the full change history audit log.

### USPTO Integration

- **USPTO Open Data Portal Sync** — Syncs patent data with the USPTO ODP API. Detects status changes, new patent numbers, grant dates, and publication details.
- **Status Lifecycle Tracking** — Tracks progression through lifecycle phases: Filed, Pending, Docketed, Examined, Published, Allowed, Patented/Issued. Movements are classified as Progressed, Regressed, Lateral, or Terminal.
- **Sync Summary Dashboard** — After bulk sync, a summary panel shows:
  - Total synced / Updated / Unchanged / Errors
  - Status movement breakdown (Progressed / Regressed / Terminal / Lateral)
  - Per-field change counts (e.g., "Status: 12, Patent #: 5, Issue Date: 3")
- **Rate Limiting** — Configurable delay between API calls (default 1100ms).
- **PDF Download** — Download patent full-text PDFs directly from USPTO.
- **Google Patents Link** — One-click link to view any patent on Google Patents.

### Classification System

- **Formal Classifications** — Define the classification categories you care about via **Tools > Manage Classifications**. Add, rename, and delete classifications with patent count visibility.
- **AI Auto-Tagging** — Automatically tag patents across 19+ technology domains using keyword pattern matching against titles. Domains include Quantum Computing, Containers, Security, Machine Learning, Edge Computing, Mesh Networking, DevOps & CI/CD, Cloud & Migration, Virtualization, Operating Systems, Functional Safety, API & Services, Data Management, Automotive, Energy & Power, Package Management, Distributed Computing, Application Lifecycle, and Networking.
- **Manual Tags** — Add and remove tags on any patent. AI-generated and human-created tags are visually distinguished.

### Analytics Dashboard

- **Portfolio Summary Cards** — Total Patents, Patented, Allowed, In Examination, Filed/Pending, Abandoned/Expired.
- **Owner Statistics** — Configurable Portfolio Owner (set in Settings with an Apply button for live preview). Shows involvement as Primary, Secondary, and Additional inventor. Changing the owner immediately rebuilds the dashboard.
- **Yearly Breakdown Chart** — Bar chart of Filed vs Issued vs Published patents by year.
- **Status Pie Chart** — Distribution grouped into lifecycle phases.
- **Classifications Table** — Ranked tags with patent counts.
- **Top 10 Collaborators** — Most frequent co-inventors with patent counts and top classifications.

### Inventor Collaboration Graph

- **Dark-Themed Interactive Network** — vis.js-powered graph on a dark canvas with glowing nodes.
- **Color-Coded by Tech Domain** — Each inventor is colored by their primary classification (e.g., Security = red, ML = purple, Quantum = cyan). A color legend shows all active domains.
- **Portfolio Owner Highlight** — The owner appears as a gold star node, immediately identifiable.
- **Smart Edge Rendering** — Edge brightness scales with collaboration strength; weak ties are faint, strong ties are bright.
- **Hover-to-Focus** — Hovering a node dims everything except that inventor's direct connections.
- **Detail Side Panel** — Click any inventor to see their patent list and top co-inventors.
- **Stats Overlay** — Shows total inventor and collaboration counts.

## Getting Started

### Prerequisites

- **Java 21** (LTS) or later
- **Maven 3.8+**
- Internet connection (for USPTO sync)

### Build and Run

```bash
# Clone the repository
git clone <repository-url>
cd patent_stats

# Build
mvn clean package

# Run
mvn javafx:run
```

### Build a Standalone JAR

```bash
mvn clean package -Pshade
java -jar target/patent-tracker-1.0-SNAPSHOT.jar
```

## Usage

### 1. Import Your Patents

**File > Import CSV...** and select your CSV file.

**Expected CSV format** (columns by index):

| Column | Field |
|--------|-------|
| 0 | (ignored) |
| 1 | File Number (unique key for dedup) |
| 2 | Title |
| 3 | Filing Date (YYYY-MM-DD) |
| 4 | Application Number |
| 5 | Publication Date |
| 6 | Publication Number |
| 7 | Issue/Grant Date |
| 8 | Patent Number |
| 9 | PTO Status (ignored on import — USPTO sync is authoritative) |
| 10 | Suffix (defaults to "US") |
| 11 | Primary Inventor(s) |
| 12 | Secondary Inventor (optional) |
| 13-15 | Additional Inventors (optional) |
| 16 | Classification (optional) |

Re-importing is safe and idempotent. Inventor names support `Full Name (username)` format and comma-separated names.

### 2. Configure Settings

**Settings > Preferences...**

- **Portfolio Owner** — Select from a dropdown of known inventors or type a new name. Click **Apply** to immediately update the dashboard with that person's stats.
- **USPTO API Key** — Enter your ODP API key from [data.uspto.gov](https://data.uspto.gov/myodp/landing).
- **Rate Limit Delay** — Milliseconds between USPTO API calls.

### 3. Sync with USPTO

**Tools > Sync All with USPTO...** fetches the latest data from the USPTO Open Data Portal, compares it against your local records, and shows a real-time results table followed by a comprehensive summary panel.

### 4. Manage Classifications

**Tools > Manage Classifications...** to define, rename, or remove the formal classification categories for your portfolio. Patent counts per classification are shown.

### 5. Auto-Tag Patents

**Tools > Auto-Tag Patents (AI)...** classifies patents by technology domain using keyword matching. Tags are marked as AI-generated and can be manually overridden.

### 6. Dashboard & Graph

- **Dashboard tab** — Portfolio analytics driven by the configured Portfolio Owner.
- **Inventor Graph tab** — Interactive collaboration network with domain coloring and hover-to-focus.

## Data Storage

All data is stored locally in `~/.patenttracker/`:

| File | Contents |
|------|----------|
| `patents.db` | SQLite database (WAL mode) with patents, inventors, tags, and audit log |
| `config.properties` | Settings: owner name, API key, rate limit, classifications |

The database includes FTS5 full-text search, parent/child relationship tracking, and a complete field-level change audit log.

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 (LTS) |
| UI Framework | JavaFX 21 (OpenJFX) |
| Database | SQLite 3.45 (sqlite-jdbc) |
| CSV Parsing | OpenCSV 5.9 |
| JSON | Jackson Databind 2.16 |
| Graph Visualization | vis-network (via WebView) |
| Build Tool | Maven |

## Project Structure

```
src/main/java/com/patenttracker/
  App.java                              Application entry point
  controller/
    MainController.java                 Main window, menus, tab management
    PatentListController.java           Patent table with search & filters
    PatentDetailController.java         Patent detail/edit dialog
    DashboardController.java            Analytics dashboard
    GraphController.java                Inventor collaboration graph
    SyncController.java                 USPTO sync with summary panel
    SettingsController.java             Preferences with live owner apply
    ClassificationController.java       Classification management
  service/
    CsvImportService.java               CSV import with dedup & update
    UsptoSyncService.java               USPTO ODP API integration
    AutoTagService.java                 AI keyword-based tagging
    StatsService.java                   Dashboard analytics queries
    GraphDataService.java               Graph data with domain coloring
  dao/
    DatabaseManager.java                SQLite connection & migrations
    PatentDao.java                      Patent CRUD & search
    InventorDao.java                    Inventor CRUD & relationships
    TagDao.java                         Tag CRUD & patent associations
    StatusUpdateDao.java                Change audit log
  model/
    Patent.java, Inventor.java, PatentInventor.java, Tag.java, StatusUpdate.java
  util/
    FileNumberParser.java               CON/DIV suffix parsing
    NameParser.java                      "Name (username)" parsing

src/main/resources/
  fxml/           JavaFX layout files
  css/app.css     Application styles (dark graph theme)
  db/             Database migration scripts
  graph/          vis-network graph HTML & JS
```

## Security

- Patent data (CSV files, SQLite database) is stored locally and never committed to version control.
- USPTO API keys are stored in `~/.patenttracker/config.properties` (local, gitignored) and are never hardcoded in source.
- The `.gitignore` excludes `*.csv`, `*.db`, `config.properties`, `.env*`, and local tool configs.

## License

See LICENSE file for details.
