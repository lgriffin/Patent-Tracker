# Research: Patent Portfolio Tracker

**Date**: 2026-03-27
**Feature**: 001-patent-tracker-app

## R1: USPTO API for Patent Status Synchronization

### Decision: Use USPTO Open Data Portal (ODP) Patent File Wrapper API

### Rationale
The original PEDS API was retired on March 14, 2025. The replacement is the USPTO Open Data Portal (ODP) at `data.uspto.gov`, which provides equivalent functionality through the Patent File Wrapper (PFW) API endpoints.

### API Details

- **Base URL**: `https://data.uspto.gov`
- **Application Data Endpoint**: `/apis/patent-file-wrapper/application-data` — retrieves bibliographic data for a specific application number
- **Search Endpoint**: `/apis/patent-file-wrapper/search` — search across multiple patents/applications with filters
- **Authentication**: API key required. Obtained via USPTO.gov account verified with ID.me at `https://data.uspto.gov/myodp/landing`
- **Rate Limits**: ~60 requests per API key per minute; 429 HTTP status on exceeded limits
- **Response Format**: JSON with bibliographic fields including application status, patent number, issue/grant date, publication date, filing date, title, and inventor information
- **Query Format**: Supports both GET with query parameters and POST with structured JSON body. Application numbers are queried without slashes (e.g., `15661380` not `15/661380`)

### Key Fields Mapping (CSV to ODP)

| CSV Field | ODP Response Field |
|-----------|-------------------|
| PTO Status_Filing | applicationStatusCode / applicationStatusDescription |
| Patent # | patentNumber |
| Issue/Grant Date | grantDate |
| Publication Date | publicationDate |
| Publication # | publicationNumber |

### Implementation Notes

- API key must be stored securely (user-provided, not bundled)
- Application must handle HTTP 429 responses with backoff/retry
- Bulk sync should process sequentially with delays to stay within rate limits
- Application numbers in CSV contain slashes (e.g., "15/661380") — must strip slashes for API queries

### Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| PEDS API | Retired March 2025 |
| PatentsView API | Focuses on granted patents only; lacks pre-grant application status data |
| USPTO Bulk Data | Download-only, not suitable for real-time single-patent queries |
| Screen scraping Patent Center | Fragile, violates Terms of Service |

### Sources
- [USPTO PEDS Retirement Notice](https://www.uspto.gov/system-status/20250212-patent-examination-data-system-peds-retirement)
- [USPTO ODP Getting Started](https://data.uspto.gov/apis/getting-started)
- [ODP API Rate Limits](https://data.uspto.gov/apis/api-rate-limits)
- [ODP Patent File Wrapper Search](https://data.uspto.gov/apis/patent-file-wrapper/search)
- [ODP Patent File Wrapper Application Data](https://data.uspto.gov/apis/patent-file-wrapper/application-data)
- [PEDS to ODP Transition Guide](https://data.uspto.gov/apis/transition-guide)

---

## R2: UI Framework for Cross-Platform Desktop Application

### Decision: JavaFX (OpenJFX) with WebView for graph visualization

### Rationale
The user specified Java. JavaFX is the standard modern UI toolkit for Java desktop applications, replacing Swing. OpenJFX (the open-source version) supports Windows, Linux, and macOS. JavaFX includes a WebView component backed by WebKit, which enables embedding rich JavaScript-based graph visualizations.

### Key Details

- **JavaFX Version**: OpenJFX 21+ (LTS aligned with JDK 21)
- **JDK**: Java 21 LTS (latest long-term support)
- **Distribution**: OpenJFX is a separate module from JDK; included via Maven/Gradle dependency
- **WebView**: JavaFX WebView can host vis.js or D3.js for the inventor relationship graph — these are mature, well-documented JavaScript graph visualization libraries that provide interactive force-directed layouts ideal for co-inventor networks
- **Platform Support**: Windows (x64), Linux (x64), macOS (x64, aarch64) via platform-specific OpenJFX artifacts automatically resolved by build tools

### Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Swing | Legacy, no modern graph support, dated look and feel |
| Eclipse SWT/RCP | Heavy framework, complex build/deployment |
| Compose Multiplatform (Kotlin) | User specified Java; adds Kotlin dependency |
| Electron + Java backend | Over-engineered for the use case; dual-process architecture unnecessary |
| Pure JavaFX graph libraries (SmartGraph, JUNG) | Limited interactivity and visual polish compared to web-based vis.js; SmartGraph is small/unmaintained; JUNG is dated |

---

## R3: Embedded Database

### Decision: SQLite via sqlite-jdbc

### Rationale
SQLite is the gold standard for local embedded databases. It requires no server, stores data in a single file, is cross-platform, and handles the expected scale (~1,000 records) trivially. The `sqlite-jdbc` library bundles native SQLite binaries for all target platforms.

### Key Details

- **Library**: `org.xerial:sqlite-jdbc` (latest stable)
- **Storage**: Single `.db` file in the application's data directory
- **Performance**: More than sufficient for ~1,000 records with basic indexes
- **Full-text search**: SQLite FTS5 extension supports full-text search on patent titles — enables fast keyword search without external libraries
- **Migration**: Schema versioning via simple version table and SQL migration scripts

### Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| H2 Database | Good option, but SQLite has broader ecosystem and simpler single-file model |
| Apache Derby | Heavier, no meaningful advantage at this scale |
| Flat files / JSON | No query capability, would require custom search implementation |

---

## R4: Graph Visualization Library

### Decision: vis.js Network via JavaFX WebView

### Rationale
vis.js Network provides an excellent force-directed graph layout with built-in interactivity (zoom, pan, drag, hover tooltips, click events). Embedding it in a JavaFX WebView gives the richness of web-based visualization within a native desktop app. Communication between Java and JavaScript is bidirectional via WebView's `executeScript()` and `WebEngine` callbacks.

### Key Details

- **Library**: vis-network (standalone JS, ~300KB)
- **Layout**: Force-directed (Barnes-Hut algorithm) — ideal for social/co-authorship networks
- **Interactivity**: Built-in zoom, pan, node drag, hover events, click events, selection
- **Data flow**: Java builds graph data (nodes/edges JSON) → passes to WebView → vis.js renders → click events callback to Java for detail panels
- **Bundling**: vis-network JS/CSS bundled as application resources, loaded from local files (no internet needed)

### Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| D3.js | More powerful but lower-level — requires more custom code for basic graph features |
| SmartGraph (JavaFX native) | Limited features, small community, less polished |
| JUNG | Dated (last major update ~2015), Swing-based rendering |
| GraphStream | Java-based but Swing rendering, limited interactivity |
| JGraphX | Focuses on diagrams/flowcharts, not network graphs |

---

## R5: Build Tool and Packaging

### Decision: Maven with jpackage for distribution

### Rationale
Maven is the most widely used Java build tool with excellent IDE support. The `javafx-maven-plugin` handles OpenJFX module configuration. `jpackage` (bundled with JDK 14+) creates native installers/packages for each target platform.

### Key Details

- **Build**: Maven 3.9+ with `javafx-maven-plugin`
- **Packaging**: `jpackage` creates platform-native packages (`.msi`/`.exe` on Windows, `.deb`/`.rpm` on Linux, `.dmg`/`.pkg` on macOS)
- **Alternative distribution**: Fat JAR with `maven-shade-plugin` for simpler distribution (requires user to have JDK installed)
- **Module system**: Use Java Platform Module System (JPMS) `module-info.java` as required by JavaFX

### Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Gradle | Both are viable; Maven chosen for broader familiarity and simpler XML configuration for this project size |

---

## R6: CSV Parsing

### Decision: OpenCSV library

### Rationale
The stats.csv file contains quoted fields with commas (e.g., titles like `"SECURE, PLATFORM-INDEPENDENT CODE SIGNING"`). A robust CSV parser is needed to handle RFC 4180 compliant CSV correctly. OpenCSV is the standard Java CSV library.

### Key Details

- **Library**: `com.opencsv:opencsv`
- **Features**: Handles quoted fields, escaped quotes, multi-line fields, custom separators
- **Column mapping**: Can map columns by header name or position
- **Error handling**: Reports parsing errors per-row without aborting entire import
