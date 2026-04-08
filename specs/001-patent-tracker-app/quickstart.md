# Quickstart: Patent Portfolio Tracker

**Date**: 2026-03-27
**Feature**: 001-patent-tracker-app

## Prerequisites

- **JDK 21** (LTS) — Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
- **Maven 3.9+** — Download from [Apache Maven](https://maven.apache.org/download.cgi)
- **Git** — For version control

Optional:
- **USPTO ODP API Key** — Required only for USPTO sync feature. Register at [USPTO My ODP](https://data.uspto.gov/myodp/landing)

## Project Setup

```bash
# Clone and enter project
git clone <repository-url>
cd patent_stats

# Build the project
mvn clean install

# Run the application
mvn javafx:run
```

## Project Structure

```
patent_stats/
├── pom.xml                          # Maven build configuration
├── stats.csv                        # Source patent data
├── src/
│   └── main/
│       ├── java/
│       │   └── com/patenttracker/
│       │       ├── App.java                  # Application entry point
│       │       ├── model/                    # Data model (Patent, Inventor, Tag, etc.)
│       │       ├── dao/                      # Data access layer (SQLite operations)
│       │       ├── service/                  # Business logic (import, sync, search, graph)
│       │       ├── controller/               # JavaFX UI controllers
│       │       └── util/                     # CSV parsing, name normalization, etc.
│       ├── resources/
│       │   ├── fxml/                         # JavaFX view layouts
│       │   ├── css/                          # Application styles
│       │   ├── graph/                        # vis.js bundle and graph HTML template
│       │   └── db/                           # SQL migration scripts
│       └── module-info.java                  # JPMS module descriptor
│   └── test/
│       └── java/
│           └── com/patenttracker/
│               ├── dao/                      # DAO unit tests
│               ├── service/                  # Service unit tests
│               └── integration/              # Integration tests (import, sync)
├── specs/                            # Feature specifications (this directory)
└── .specify/                         # Speckit configuration
```

## Key Dependencies

| Dependency | Purpose | Maven Artifact |
|------------|---------|----------------|
| OpenJFX 21 | Desktop UI framework | `org.openjfx:javafx-controls`, `javafx-fxml`, `javafx-web` |
| sqlite-jdbc | Embedded database | `org.xerial:sqlite-jdbc` |
| OpenCSV | CSV parsing | `com.opencsv:opencsv` |
| vis-network | Graph visualization (JS) | Bundled as resource (not Maven) |
| Jackson | JSON parsing (USPTO API responses) | `com.fasterxml.jackson.core:jackson-databind` |
| JUnit 5 | Testing | `org.junit.jupiter:junit-jupiter` |
| java.net.http | HTTP client (USPTO API) | Built into JDK 11+ |

## Database

- **Engine**: SQLite via sqlite-jdbc
- **Location**: `~/.patenttracker/patents.db` (user home directory)
- **Schema migrations**: Applied automatically on startup via versioned SQL scripts in `src/main/resources/db/`

## Configuration

Application settings stored in `~/.patenttracker/config.properties`:

```properties
# USPTO Open Data Portal API Key (optional, required for sync)
uspto.api.key=

# Rate limit delay between USPTO API calls (milliseconds)
uspto.rate.delay=1100

# Database file path (default: ~/.patenttracker/patents.db)
db.path=
```

## Development Workflow

```bash
# Run tests
mvn test

# Run application in dev mode
mvn javafx:run

# Package as native installer
mvn javafx:jlink jpackage:jpackage

# Build fat JAR (alternative distribution)
mvn package -Pfat-jar
```

## First Launch

1. Start the application (`mvn javafx:run`)
2. Use File > Import CSV to load `stats.csv`
3. Browse patents in the main table view
4. Use the search bar and filters to explore
5. Switch to the Graph tab to see inventor relationships
6. (Optional) Enter your USPTO API key in Settings to enable sync
