# Feature Specification: Patent Portfolio Tracker

**Feature Branch**: `001-patent-tracker-app`
**Created**: 2026-03-27
**Status**: Draft
**Input**: User description: "I want to create a Java application that takes the content in stats.csv and models out an environment that I can use to track my patent applications. Of particular interest is the relationship between inventors which I would like to have a graph based view into. The end application should be a UI driven approach, runnable on Windows / Linux / Mac, allowing me to search by topic and associate metadata tags. I want a local small DB created. Each patent has a US patent number associated that I want to be able to real time pull the information from the official USPTO and update things like issue date and patent number as well as the status. In that sense the data set is lagging behind reality"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import and Browse Patent Portfolio (Priority: P1)

As a patent portfolio owner, I want to import my existing patent data from a CSV file and browse all patents in an organized view so that I can see my full portfolio at a glance.

**Why this priority**: Without data import and browsing, no other features are meaningful. This is the foundational capability that everything else builds on.

**Independent Test**: Can be fully tested by importing stats.csv and verifying all 426 patents appear in a browsable list with correct fields (title, filing date, application number, status, inventors, classification).

**Acceptance Scenarios**:

1. **Given** the application is launched for the first time, **When** the user selects stats.csv for import, **Then** all patent records are parsed and stored in the local database with all fields preserved.
2. **Given** patents have been imported, **When** the user opens the application, **Then** a sortable, scrollable list of all patents is displayed showing key columns (title, filing date, status, primary inventor, patent number).
3. **Given** a patent list is displayed, **When** the user clicks on a patent row, **Then** a detail view shows all fields for that patent including all inventors, dates, application number, publication info, and classification.
4. **Given** a CSV contains malformed rows or missing fields, **When** import is attempted, **Then** the system imports valid records and reports which rows could not be parsed.

---

### User Story 2 - Search and Filter Patents (Priority: P1)

As a patent portfolio owner, I want to search patents by topic, title keywords, inventor name, status, and classification so that I can quickly find relevant patents.

**Why this priority**: With 400+ patents, discovery without search is impractical. This is essential for daily use alongside browsing.

**Independent Test**: Can be tested by performing searches against imported data and verifying correct result sets.

**Acceptance Scenarios**:

1. **Given** patents are loaded, **When** the user types "quantum" in the search box, **Then** all patents with "quantum" in the title are displayed.
2. **Given** patents are loaded, **When** the user filters by status "Issued", **Then** only patents with PTO status "Issued" are shown.
3. **Given** patents are loaded, **When** the user filters by inventor "Stephen Coady", **Then** all patents where Stephen Coady appears as any inventor (primary, secondary, or additional) are shown.
4. **Given** patents are loaded, **When** the user filters by classification "Security", **Then** only patents classified as Security are shown.
5. **Given** search results are displayed, **When** the user clears the search, **Then** the full patent list is restored.

---

### User Story 3 - Metadata Tagging (Priority: P2)

As a patent portfolio owner, I want to associate custom metadata tags with patents so that I can categorize and organize them beyond the existing classification field.

**Why this priority**: The existing Classification column is sparse (many blanks) and coarse. Custom tags allow flexible organization by technology area, project, business unit, or any user-defined taxonomy.

**Independent Test**: Can be tested by adding tags to patents and then filtering/searching by those tags.

**Acceptance Scenarios**:

1. **Given** a patent detail view is open, **When** the user adds tags (e.g., "cloud", "enterprise", "2022-portfolio"), **Then** those tags are saved and visible on the patent.
2. **Given** multiple patents have tags, **When** the user searches or filters by a tag, **Then** all patents with that tag are shown.
3. **Given** a patent has tags, **When** the user removes a tag, **Then** the tag is removed from that patent.
4. **Given** tags exist across patents, **When** the user views the tag list, **Then** all unique tags are shown with counts of associated patents.
5. **Given** multiple patents are selected, **When** the user applies a tag in bulk, **Then** the tag is added to all selected patents.

---

### User Story 4 - Inventor Relationship Graph (Priority: P2)

As a patent portfolio owner, I want to see a visual graph of inventor relationships showing who has co-invented with whom and how frequently, so that I can understand collaboration patterns.

**Why this priority**: Understanding inventor networks is a stated primary interest. It provides unique insight not available from tabular data alone.

**Independent Test**: Can be tested by loading patent data and verifying the graph correctly shows inventors as nodes and co-invention relationships as edges with correct frequency counts.

**Acceptance Scenarios**:

1. **Given** patents are loaded, **When** the user opens the inventor graph view, **Then** a network graph is displayed with each unique inventor as a node and co-invention relationships as edges.
2. **Given** the graph is displayed, **When** the user hovers over an edge between two inventors, **Then** the number of shared patents and a list of those patent titles is shown.
3. **Given** the graph is displayed, **When** the user clicks on an inventor node, **Then** a panel shows that inventor's patent count, list of patents, and their most frequent co-inventors.
4. **Given** the graph is displayed, **When** the user zooms, pans, or rearranges nodes, **Then** the graph responds interactively.
5. **Given** a filtered patent set (e.g., only "quantum" patents), **When** the user opens the graph view, **Then** the graph reflects only the filtered subset of patents.

---

### User Story 5 - USPTO Status Synchronization (Priority: P3)

As a patent portfolio owner, I want to pull real-time status information from the USPTO for my patents so that I can keep my records up to date with current issue dates, patent numbers, and statuses.

**Why this priority**: The dataset is acknowledged as lagging reality. This closes the gap, but requires external API access and is built on top of the core data model.

**Independent Test**: Can be tested by triggering a sync for a known patent application number and verifying that updated fields (status, patent number, issue date) are reflected in the local database.

**Acceptance Scenarios**:

1. **Given** a patent with an application number, **When** the user triggers a status check for that patent, **Then** the system queries the USPTO and displays the current status, issue date, and patent number if available.
2. **Given** updated information is retrieved from USPTO, **When** the user confirms the update, **Then** the local database is updated with the new values and the previous values are preserved in a change history.
3. **Given** the user wants to update multiple patents, **When** the user triggers a bulk sync, **Then** the system processes patents sequentially (respecting rate limits) and reports results for each.
4. **Given** the USPTO service is unavailable or returns an error, **When** a sync is attempted, **Then** the user is informed of the failure and existing data is unchanged.
5. **Given** a patent's local data matches the USPTO data, **When** a sync is performed, **Then** the system indicates "no changes" for that patent.

---

### User Story 6 - Dashboard and Portfolio Analytics (Priority: P3)

As a patent portfolio owner, I want to see summary statistics and visualizations of my portfolio so that I can understand trends and composition.

**Why this priority**: Adds analytical value on top of the core data, but is not required for basic operation.

**Independent Test**: Can be tested by loading data and verifying dashboard metrics match manual calculations.

**Acceptance Scenarios**:

1. **Given** patents are loaded, **When** the user opens the dashboard, **Then** summary statistics are shown including: total patents, count by status (Issued, Filed, Published, Abandoned, Dropped, Allowed), count by classification, and count by year filed.
2. **Given** the dashboard is displayed, **When** the user views the filing timeline, **Then** a chart shows patents filed per year.
3. **Given** the dashboard is displayed, **When** the user views the status breakdown, **Then** a chart shows the distribution of patent statuses.

---

### Edge Cases

- What happens when the CSV file has a different column order or extra/missing columns than expected?
- How does the system handle patents with a suffix like US-CON1 or US-DIV1 (continuation/divisional filings)?
- What happens when a patent has no classification value (many rows have blank classification)?
- How does the system handle inventor names in different formats (e.g., "Leigh Griffin (lgriffin)" vs just a username like "pchibon")?
- What happens when the same patent title appears multiple times with different file numbers (e.g., continuations)?
- How does the system handle the quoted CSV fields (titles containing commas)?
- What happens when USPTO rate limits are exceeded during bulk sync?
- How does the system handle patents that have been abandoned or dropped — should they still be synced?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST import patent data from CSV files matching the stats.csv column structure (Count, File Number, Title, Filing Date, Application #, Publication Date, Publication #, Issue/Grant Date, Patent #, PTO Status_Filing, Suffix, Primary Inventor, Secondary, Additional, Additional 2, Additional 3, Classification).
- **FR-002**: System MUST store all imported patent data in a local embedded database that persists between application sessions.
- **FR-003**: System MUST provide a tabular view of all patents with sorting and column visibility controls.
- **FR-004**: System MUST provide full-text search across patent titles and keyword search across all fields.
- **FR-005**: System MUST support filtering patents by status, inventor, classification, filing year, and custom tags.
- **FR-006**: System MUST allow users to add, remove, and manage custom metadata tags on individual patents and in bulk.
- **FR-007**: System MUST display an interactive network graph showing inventor co-invention relationships with edge weights representing number of shared patents.
- **FR-008**: System MUST allow the inventor graph to be filtered in sync with the current patent filter/search criteria.
- **FR-009**: System MUST query the USPTO Patent Examination Data System (PEDS) API to retrieve current status, issue date, and patent number for patents by application number.
- **FR-010**: System MUST preserve a change history when patent records are updated via USPTO sync, recording previous values and timestamps.
- **FR-011**: System MUST run as a desktop application on Windows, Linux, and macOS without requiring additional software installation beyond a standard runtime.
- **FR-012**: System MUST handle continuation (CON) and divisional (DIV) patent filings, linking them to their parent applications.
- **FR-013**: System MUST parse inventor names that include username identifiers in parentheses (e.g., "Leigh Griffin (lgriffin)") and normalize them for relationship tracking.
- **FR-014**: System MUST provide a patent detail view showing all fields, associated tags, change history, and links to related filings (continuations/divisionals).
- **FR-015**: System MUST provide portfolio summary statistics including counts by status, classification, year, and inventor.

### Key Entities

- **Patent**: A patent application or granted patent. Key attributes: file number, title, filing date, application number, publication date, publication number, issue/grant date, patent number, PTO status, suffix (US, US-CON1, US-DIV1), classification. A patent has one primary inventor and zero or more co-inventors.
- **Inventor**: A person who has invented or co-invented patents. Key attributes: full name, username identifier. An inventor can be associated with many patents in various roles (primary, secondary, additional).
- **Tag**: A user-defined metadata label. Key attributes: name. A tag can be associated with many patents, and a patent can have many tags.
- **StatusUpdate**: A historical record of a change to a patent's data. Key attributes: field changed, previous value, new value, source of change (CSV import, USPTO sync, manual edit), timestamp.
- **PatentRelationship**: A link between related patents (e.g., a continuation filing to its parent). Key attributes: parent file number, child file number, relationship type (continuation, divisional).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 426 records from stats.csv are successfully imported with zero data loss on first import.
- **SC-002**: Users can locate any specific patent from the full portfolio in under 10 seconds using search or filters.
- **SC-003**: The inventor relationship graph correctly displays all unique inventors and their co-invention connections, with accurate patent counts on each edge.
- **SC-004**: USPTO sync successfully retrieves and updates status information for patents with valid application numbers, with at least 95% success rate when the USPTO service is available.
- **SC-005**: The application launches and is usable on all three target platforms (Windows, Linux, macOS) without platform-specific installation steps beyond extracting/running the application.
- **SC-006**: Custom tags can be created, applied, and used as search/filter criteria within a single user session.
- **SC-007**: Portfolio summary statistics (total count, status breakdown, filing timeline) are accurate and update in real time as data changes.

## Assumptions

- The user is the primary portfolio owner (Leigh Griffin) and is the sole user of the application — no multi-user or authentication features are needed.
- The stats.csv column structure is stable and representative of all data that needs to be imported; future CSV imports will follow the same schema.
- The USPTO Patent Examination Data System (PEDS) API is publicly accessible and does not require an API key for basic status queries by application number.
- The application is intended for personal/professional portfolio management, not for legal or compliance purposes — no audit trail or access control requirements beyond basic change history.
- The local database does not need to support more than ~1,000 patent records for performance purposes.
- Internet connectivity is required only for USPTO sync; all other features work offline.
- Patents with blank classification fields should be importable and displayable; classification is not a required field.
- The suffix field (US, US-CON1, US-DIV1) indicates the relationship type to a parent filing and can be parsed to establish parent-child links.
