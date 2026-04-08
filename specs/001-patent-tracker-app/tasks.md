# Tasks: Patent Portfolio Tracker

**Input**: Design documents from `/specs/001-patent-tracker-app/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Not explicitly requested in the feature specification. Test tasks are omitted.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven project initialization, dependencies, and module system configuration

- [ ] T001 Create Maven project with pom.xml including OpenJFX 21, sqlite-jdbc, OpenCSV, Jackson, JUnit 5 dependencies and javafx-maven-plugin configuration in pom.xml
- [ ] T002 Create Java module descriptor with requires for javafx.controls, javafx.fxml, javafx.web, java.sql, java.net.http in src/main/java/module-info.java
- [ ] T003 [P] Create application entry point with primary stage setup and tab-based navigation (List, Graph, Dashboard) in src/main/java/com/patenttracker/App.java
- [ ] T004 [P] Create main window FXML layout with TabPane for List/Graph/Dashboard views and menu bar (File > Import CSV, Settings) in src/main/resources/fxml/main.fxml
- [ ] T005 [P] Create application stylesheet with base styles for tables, buttons, filters, and tag chips in src/main/resources/css/app.css

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database infrastructure, core models, and DAOs that ALL user stories depend on

**Warning**: No user story work can begin until this phase is complete

- [ ] T006 Create initial SQLite schema with Patent, Inventor, PatentInventor, Tag, PatentTag, StatusUpdate tables and indexes including FTS5 virtual table for patent title search in src/main/resources/db/V001__initial_schema.sql
- [ ] T007 Implement DatabaseManager with SQLite connection management, automatic schema migration on startup, and app data directory creation (~/.patenttracker/) in src/main/java/com/patenttracker/dao/DatabaseManager.java
- [ ] T008 [P] Create Patent model class with all fields (id, fileNumber, title, filingDate, applicationNumber, publicationDate, publicationNumber, issueGrantDate, patentNumber, ptoStatus, suffix, classification, parentFileNumber, csvRowNumber, createdAt, updatedAt) in src/main/java/com/patenttracker/model/Patent.java
- [ ] T009 [P] Create Inventor model class with fields (id, fullName, username, createdAt) in src/main/java/com/patenttracker/model/Inventor.java
- [ ] T010 [P] Create PatentInventor model class with fields (id, patentId, inventorId, role, rolePosition) in src/main/java/com/patenttracker/model/PatentInventor.java
- [ ] T011 [P] Create StatusUpdate model class with fields (id, patentId, fieldName, previousValue, newValue, source, timestamp) in src/main/java/com/patenttracker/model/StatusUpdate.java
- [ ] T012 [P] Create Tag model class with fields (id, name, createdAt) in src/main/java/com/patenttracker/model/Tag.java
- [ ] T013 [P] Create PatentTag model class with fields (id, patentId, tagId, createdAt) in src/main/java/com/patenttracker/model/PatentTag.java
- [ ] T014 [P] Implement NameParser utility to extract fullName and username from formats like "Leigh Griffin (lgriffin)" and handle bare usernames like "pchibon" in src/main/java/com/patenttracker/util/NameParser.java
- [ ] T015 [P] Implement FileNumberParser utility to extract parent file number and relationship type (CON/DIV) from suffixed file numbers like "20191204US-CON1" in src/main/java/com/patenttracker/util/FileNumberParser.java
- [ ] T016 Implement PatentDao with CRUD operations, FTS5 title search, filtering by status/classification/filingYear/inventorId/tagId, sorting, and parent-child queries in src/main/java/com/patenttracker/dao/PatentDao.java
- [ ] T017 [P] Implement InventorDao with CRUD operations, find-or-create by username/name, and co-inventor query (pairs of inventors with shared patent counts) in src/main/java/com/patenttracker/dao/InventorDao.java
- [ ] T018 [P] Implement StatusUpdateDao with create and findByPatentId operations in src/main/java/com/patenttracker/dao/StatusUpdateDao.java

**Checkpoint**: Foundation ready — database, models, DAOs, and utilities are in place. User story implementation can now begin.

---

## Phase 3: User Story 1 - Import and Browse Patent Portfolio (Priority: P1) MVP

**Goal**: Import stats.csv into the local database and browse all 426 patents in a sortable table with a detail view.

**Independent Test**: Import stats.csv, verify 426 patents appear in the table. Click a patent row and verify all fields display correctly in the detail view.

### Implementation for User Story 1

- [ ] T019 [US1] Implement CsvImportService using OpenCSV to parse stats.csv, normalize inventor names via NameParser, resolve parent file numbers via FileNumberParser, deduplicate inventors, and persist all records (Patent, Inventor, PatentInventor) via DAOs in src/main/java/com/patenttracker/service/CsvImportService.java
- [ ] T020 [US1] Create patent list FXML layout with TableView (columns: Title, File Number, Filing Date, Application #, Patent #, Status, Primary Inventor, Classification), search bar placeholder, and filter panel placeholder in src/main/resources/fxml/patent-list.fxml
- [ ] T021 [US1] Implement MainController to load patents from PatentDao into the TableView, handle column sorting, row selection to open detail view, and File > Import CSV menu action with file chooser dialog in src/main/java/com/patenttracker/controller/MainController.java
- [ ] T022 [US1] Create patent detail FXML layout showing all patent fields, inventor list with roles, related filings section (continuations/divisionals), tags section placeholder, and change history section placeholder in src/main/resources/fxml/patent-detail.fxml
- [ ] T023 [US1] Implement PatentDetailController to load and display full patent details, inventor list with roles, related filings as clickable links, and back navigation preserving list state in src/main/java/com/patenttracker/controller/PatentDetailController.java

**Checkpoint**: Application can import stats.csv and browse/sort all 426 patents with full detail views. This is the MVP.

---

## Phase 4: User Story 2 - Search and Filter Patents (Priority: P1)

**Goal**: Enable full-text search on patent titles and filtering by status, inventor, classification, filing year, and tags.

**Independent Test**: Search for "quantum" and verify correct results. Filter by status "Issued" and verify count. Combine search + filter and verify AND logic. Clear filters and verify full list restored.

### Implementation for User Story 2

- [ ] T024 [US2] Implement SearchService to combine full-text title search (FTS5) with filters (status, inventor, classification, filing year, tag) using AND logic, returning filtered patent results with counts in src/main/java/com/patenttracker/service/SearchService.java
- [ ] T025 [US2] Update MainController to wire search bar to SearchService for real-time title search, add filter dropdowns/checkboxes for status, inventor, classification, and year range, display active filters as removable chips, and show result count (e.g., "127 of 426 patents") in src/main/java/com/patenttracker/controller/MainController.java
- [ ] T026 [US2] Update patent-list.fxml to include filter panel with status checkboxes, inventor combo box, classification combo box, year range selector, and active filter chip bar in src/main/resources/fxml/patent-list.fxml

**Checkpoint**: Full search and filter capability working. Users can locate any patent in under 10 seconds.

---

## Phase 5: User Story 3 - Metadata Tagging (Priority: P2)

**Goal**: Allow users to create, apply, remove, and search by custom metadata tags on patents.

**Independent Test**: Add tags "quantum-crypto" and "2022-batch" to a patent. Filter by tag "quantum-crypto" and verify correct results. Remove a tag and verify it's gone. Bulk-tag 5 selected patents.

### Implementation for User Story 3

- [ ] T027 [P] [US3] Implement TagDao with CRUD operations, find-or-create by name (case-insensitive), findByPatentId, findAllWithCounts, add/remove patent-tag associations, and bulk-add tag to multiple patents in src/main/java/com/patenttracker/dao/TagDao.java
- [ ] T028 [US3] Update PatentDetailController to display tags as editable chips, support adding new tags via text input with autocomplete from existing tags, and removing tags by clicking X on a chip in src/main/java/com/patenttracker/controller/PatentDetailController.java
- [ ] T029 [US3] Update MainController to add tag filter dropdown populated from TagDao.findAllWithCounts, support multi-select in patent list with checkboxes, and add bulk tag action for selected patents in src/main/java/com/patenttracker/controller/MainController.java
- [ ] T030 [US3] Update SearchService to include tag filtering in combined search queries in src/main/java/com/patenttracker/service/SearchService.java

**Checkpoint**: Full tagging system working — create, apply, remove, search/filter by tags, and bulk operations.

---

## Phase 6: User Story 4 - Inventor Relationship Graph (Priority: P2)

**Goal**: Display an interactive network graph showing inventor co-invention relationships with weighted edges.

**Independent Test**: Open graph view and verify all unique inventors appear as nodes. Verify edges exist between co-inventors with correct shared patent counts. Click an inventor node and verify detail panel shows correct data. Apply a "quantum" filter and verify graph updates to show only quantum-related inventors.

### Implementation for User Story 4

- [ ] T031 [US4] Implement GraphDataService to compute inventor graph data: extract unique inventors as nodes (sized by patent count), compute co-inventor pairs as edges (weighted by shared patent count), support filtering by current search/filter criteria, and output as JSON-serializable structure in src/main/java/com/patenttracker/service/GraphDataService.java
- [ ] T032 [US4] Create graph.html template with vis-network initialization, force-directed layout configuration (Barnes-Hut), node hover (tooltip with name + count), edge hover (tooltip with shared patents), node click (posts event to Java bridge), zoom/pan/drag support, and a receiveGraphData(json) function callable from Java in src/main/resources/graph/graph.html
- [ ] T033 [P] [US4] Download and bundle vis-network.min.js and vis-network.min.css as application resources in src/main/resources/graph/
- [ ] T034 [US4] Create graph FXML layout with WebView for the graph and a side panel for inventor details (patent list, top co-inventors) in src/main/resources/fxml/graph.fxml
- [ ] T035 [US4] Implement GraphController to load graph.html into WebView, pass graph JSON from GraphDataService via executeScript, handle node click callbacks from JavaScript to populate side panel, and re-render graph when search/filter criteria change in src/main/java/com/patenttracker/controller/GraphController.java

**Checkpoint**: Interactive inventor graph fully functional with hover tooltips, click details, and filter synchronization.

---

## Phase 7: User Story 5 - USPTO Status Synchronization (Priority: P3)

**Goal**: Query the USPTO Open Data Portal API to update patent status, issue date, and patent number. Preserve change history.

**Independent Test**: Configure API key in settings. Trigger sync on a known patent (e.g., application 15/661380). Verify updated fields and change history record. Test bulk sync on 5 patents. Test with invalid API key and verify error handling.

### Implementation for User Story 5

- [ ] T036 [US5] Create settings FXML layout with fields for USPTO API key input (masked), rate limit delay slider (default 1100ms), and database file path display in src/main/resources/fxml/settings.fxml
- [ ] T037 [US5] Implement SettingsController to load/save configuration from ~/.patenttracker/config.properties, validate API key format, and expose settings to other services in src/main/java/com/patenttracker/controller/SettingsController.java
- [ ] T038 [US5] Implement UsptoSyncService using java.net.http.HttpClient to query USPTO ODP application-data endpoint, strip slashes from application numbers, parse JSON response with Jackson to extract status/patentNumber/grantDate/publicationDate, compute diff against local values, create StatusUpdate records for changes, and handle HTTP 401/404/429/5xx errors per the integration contract in src/main/java/com/patenttracker/service/UsptoSyncService.java
- [ ] T039 [US5] Create sync FXML layout with progress bar, per-patent result table (application #, field, old value, new value, status), accept/reject buttons per row, accept-all button, and cancel button in src/main/resources/fxml/sync.fxml
- [ ] T040 [US5] Implement SyncController to trigger single-patent sync from PatentDetailController, trigger bulk sync from menu, display progress and diffs, handle accept/reject per patent, and run sync on background thread with UI progress updates in src/main/java/com/patenttracker/controller/SyncController.java
- [ ] T041 [US5] Update PatentDetailController to show change history from StatusUpdateDao, add "Sync with USPTO" button that triggers single-patent sync, and refresh detail view after sync completes in src/main/java/com/patenttracker/controller/PatentDetailController.java

**Checkpoint**: USPTO sync working for single and bulk operations with change history tracking and error handling.

---

## Phase 8: User Story 6 - Dashboard and Portfolio Analytics (Priority: P3)

**Goal**: Display portfolio summary statistics with charts showing status breakdown, filing timeline, and classification distribution.

**Independent Test**: Open dashboard and verify total patent count matches database. Verify status breakdown chart sums to total. Verify filing timeline chart covers all years from 2017-2026. Click a chart segment and verify navigation to filtered list view.

### Implementation for User Story 6

- [ ] T042 [US6] Implement StatsService to compute portfolio analytics: total count, count by status, count by classification, count by filing year, top inventors by patent count in src/main/java/com/patenttracker/service/StatsService.java
- [ ] T043 [US6] Create dashboard FXML layout with summary cards (total patents, issued count, filed count), PieChart for status breakdown, BarChart for filing timeline by year, BarChart for classification distribution, and BarChart for top 10 inventors in src/main/resources/fxml/dashboard.fxml
- [ ] T044 [US6] Implement DashboardController to load stats from StatsService, populate JavaFX charts, handle click on chart segments to navigate to patent list view with corresponding filter applied, and refresh data when returning to dashboard tab in src/main/java/com/patenttracker/controller/DashboardController.java

**Checkpoint**: Dashboard fully functional with interactive charts and drill-down navigation.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Final integration, UX improvements, and packaging

- [ ] T045 Add application icon and window title in src/main/java/com/patenttracker/App.java
- [ ] T046 Implement keyboard shortcuts (Ctrl+F for search, Ctrl+I for import, Escape to clear filters) in src/main/java/com/patenttracker/controller/MainController.java
- [ ] T047 Add confirmation dialog before CSV re-import (warns about duplicate handling) in src/main/java/com/patenttracker/controller/MainController.java
- [ ] T048 [P] Create Maven profile for fat JAR packaging with maven-shade-plugin in pom.xml
- [ ] T049 [P] Create Maven profile for native packaging with jpackage (Windows .msi, Linux .deb, macOS .dmg) in pom.xml
- [ ] T050 Run quickstart.md validation: verify build, import, search, graph, and sync workflows end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US1 Import & Browse (Phase 3)**: Depends on Foundational — this is the MVP
- **US2 Search & Filter (Phase 4)**: Depends on US1 (extends MainController and patent-list.fxml)
- **US3 Tagging (Phase 5)**: Depends on Foundational only — can run in parallel with US1/US2
- **US4 Inventor Graph (Phase 6)**: Depends on Foundational only — can run in parallel with US1/US2/US3
- **US5 USPTO Sync (Phase 7)**: Depends on Foundational only — can run in parallel with other stories
- **US6 Dashboard (Phase 8)**: Depends on Foundational only — can run in parallel with other stories
- **Polish (Phase 9)**: Depends on all user stories being complete

### User Story Dependencies

```
Phase 1: Setup
    ↓
Phase 2: Foundational
    ↓
    ├── Phase 3: US1 Import & Browse (MVP) ─→ Phase 4: US2 Search & Filter
    ├── Phase 5: US3 Tagging (independent)
    ├── Phase 6: US4 Inventor Graph (independent)
    ├── Phase 7: US5 USPTO Sync (independent)
    └── Phase 8: US6 Dashboard (independent)
    ↓
Phase 9: Polish
```

### Within Each User Story

- Models/DAOs before Services
- Services before Controllers
- FXML layouts before Controllers that reference them
- Core implementation before integration with other views

### Parallel Opportunities

- **Phase 1**: T003, T004, T005 can run in parallel (after T001, T002)
- **Phase 2**: T008-T015 (all models + utilities) can run in parallel; T016-T018 (DAOs) can run in parallel after models
- **Phase 3-8**: US3, US4, US5, US6 are independent of each other and can be developed in parallel after Foundational
- **Phase 5**: T027 (TagDao) can run in parallel with other US3 tasks
- **Phase 6**: T033 (bundle vis.js) can run in parallel with T031, T032

---

## Parallel Example: After Foundational Phase

```bash
# These can all run in parallel after Phase 2 is complete:
Task: "US1 - Implement CsvImportService" (T019)
Task: "US3 - Implement TagDao" (T027)
Task: "US4 - Implement GraphDataService" (T031)
Task: "US5 - Implement UsptoSyncService" (T038)
Task: "US6 - Implement StatsService" (T042)
```

## Parallel Example: Phase 2 Models

```bash
# All model classes can be created simultaneously:
Task: "Create Patent model" (T008)
Task: "Create Inventor model" (T009)
Task: "Create PatentInventor model" (T010)
Task: "Create StatusUpdate model" (T011)
Task: "Create Tag model" (T012)
Task: "Create PatentTag model" (T013)
Task: "Implement NameParser" (T014)
Task: "Implement FileNumberParser" (T015)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T005)
2. Complete Phase 2: Foundational (T006-T018)
3. Complete Phase 3: US1 Import & Browse (T019-T023)
4. **STOP and VALIDATE**: Import stats.csv, verify 426 patents, browse and view details
5. This is a fully functional, useful application

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 Import & Browse → **MVP** (can use immediately)
3. US2 Search & Filter → Enhanced discovery
4. US3 Tagging → Custom organization
5. US4 Inventor Graph → Relationship insights
6. US5 USPTO Sync → Live data updates
7. US6 Dashboard → Portfolio analytics
8. Polish → Production-ready packaging

### Suggested Approach

Given this is a single-developer project, implement sequentially in priority order (P1 → P2 → P3). Each phase produces a usable increment. The MVP (Phase 1-3) delivers the core value and can be validated before investing in secondary features.

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- The vis-network JS bundle (T033) should be downloaded once and committed as a resource
- USPTO API key is user-provided, never hardcoded or committed
