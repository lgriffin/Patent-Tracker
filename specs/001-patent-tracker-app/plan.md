# Implementation Plan: Patent Portfolio Tracker

**Branch**: `001-patent-tracker-app` | **Date**: 2026-03-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-patent-tracker-app/spec.md`

## Summary

A cross-platform Java desktop application for managing a portfolio of 400+ US patent applications. Built with JavaFX for the UI, SQLite for local persistence, and vis.js (via WebView) for interactive inventor relationship graph visualization. Integrates with the USPTO Open Data Portal API to synchronize patent status, issue dates, and patent numbers. Supports CSV import, full-text search, custom metadata tagging, and portfolio analytics.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: OpenJFX 21 (UI), sqlite-jdbc (database), OpenCSV (CSV parsing), Jackson (JSON), vis-network (graph visualization via WebView)
**Storage**: SQLite — single embedded file at `~/.patenttracker/patents.db`
**Testing**: JUnit 5
**Target Platform**: Windows (x64), Linux (x64), macOS (x64, aarch64)
**Project Type**: Desktop application
**Performance Goals**: Instant response for search/filter across ~1,000 records; graph renders in under 2 seconds
**Constraints**: Offline-capable (except USPTO sync); single-user; single-file database
**Scale/Scope**: ~1,000 patent records; 6 views; 1 external API integration

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitution principles are defined (template only). No gates to enforce. **PASS**.

**Post-Phase 1 re-check**: Design is a single Maven project with clear layer separation (model → dao → service → controller). No unnecessary abstractions. **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/001-patent-tracker-app/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: technology research & decisions
├── data-model.md        # Phase 1: entity definitions & relationships
├── quickstart.md        # Phase 1: setup & development guide
├── contracts/
│   ├── ui-views.md      # Phase 1: UI view contracts
│   └── uspto-integration.md  # Phase 1: USPTO API integration contract
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── com/patenttracker/
│   │       ├── App.java                  # Application entry point + primary stage setup
│   │       ├── model/
│   │       │   ├── Patent.java           # Patent entity
│   │       │   ├── Inventor.java         # Inventor entity
│   │       │   ├── PatentInventor.java   # Junction: patent-inventor with role
│   │       │   ├── Tag.java              # Tag entity
│   │       │   ├── PatentTag.java        # Junction: patent-tag
│   │       │   └── StatusUpdate.java     # Change history record
│   │       ├── dao/
│   │       │   ├── DatabaseManager.java  # Connection pool, schema migration
│   │       │   ├── PatentDao.java        # Patent CRUD + search + filter
│   │       │   ├── InventorDao.java      # Inventor CRUD + co-inventor queries
│   │       │   ├── TagDao.java           # Tag CRUD + patent association
│   │       │   └── StatusUpdateDao.java  # Change history CRUD
│   │       ├── service/
│   │       │   ├── CsvImportService.java # CSV parsing + import logic
│   │       │   ├── UsptoSyncService.java # USPTO ODP API integration
│   │       │   ├── SearchService.java    # Full-text search + filter combination
│   │       │   ├── GraphDataService.java # Compute inventor graph nodes/edges
│   │       │   └── StatsService.java     # Portfolio analytics computation
│   │       ├── controller/
│   │       │   ├── MainController.java       # Patent list view + search/filter
│   │       │   ├── PatentDetailController.java # Patent detail + tags + history
│   │       │   ├── GraphController.java      # Inventor graph (WebView bridge)
│   │       │   ├── DashboardController.java  # Analytics dashboard
│   │       │   ├── SyncController.java       # USPTO sync progress/results
│   │       │   └── SettingsController.java   # API key + preferences
│   │       └── util/
│   │           ├── NameParser.java       # "Leigh Griffin (lgriffin)" → name + username
│   │           └── FileNumberParser.java # "20191204US-CON1" → parent + relationship type
│   └── resources/
│       ├── fxml/
│       │   ├── main.fxml                 # Main window layout with tab bar
│       │   ├── patent-list.fxml          # Patent table view
│       │   ├── patent-detail.fxml        # Patent detail view
│       │   ├── graph.fxml                # Graph view (WebView container)
│       │   ├── dashboard.fxml            # Analytics dashboard
│       │   ├── sync.fxml                 # USPTO sync view
│       │   └── settings.fxml             # Settings dialog
│       ├── css/
│       │   └── app.css                   # Application stylesheet
│       ├── graph/
│       │   ├── vis-network.min.js        # vis.js network bundle
│       │   ├── vis-network.min.css       # vis.js styles
│       │   └── graph.html                # Graph HTML template with JS bridge
│       └── db/
│           └── V001__initial_schema.sql  # Initial database schema
└── test/
    └── java/
        └── com/patenttracker/
            ├── dao/
            │   ├── PatentDaoTest.java
            │   └── InventorDaoTest.java
            ├── service/
            │   ├── CsvImportServiceTest.java
            │   ├── SearchServiceTest.java
            │   └── GraphDataServiceTest.java
            ├── util/
            │   ├── NameParserTest.java
            │   └── FileNumberParserTest.java
            └── integration/
                └── ImportAndSearchIntegrationTest.java
```

**Structure Decision**: Single Maven project (no multi-module). JavaFX desktop app with standard layered architecture: model → dao → service → controller. The vis.js library is bundled as a static resource loaded into JavaFX WebView. This keeps the build simple and avoids the complexity of a separate frontend build pipeline.

## Complexity Tracking

No constitution violations to justify. The design is intentionally simple:
- Single project, single build tool
- Direct DAO pattern (no ORM, no repository abstraction) — appropriate for SQLite at this scale
- WebView bridge for graph visualization — avoids adding a complex native graph library dependency
