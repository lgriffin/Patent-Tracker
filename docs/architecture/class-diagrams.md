# Class Diagrams

This document provides Mermaid class diagrams for the patent tracking application, organized by architectural layer: domain models, data access, services, and controllers.

## Domain Models

The core domain model centers on the `Patent` entity, which connects to inventors through a join table supporting roles, to tags for classification, and to various content and analysis records. `StatusUpdate` provides an audit trail of changes, while `MinedPatent` captures externally discovered patents from search operations.

```mermaid
classDiagram
    class Patent {
        int id
        String fileNumber
        String title
        LocalDate filingDate
        String applicationNumber
        LocalDate publicationDate
        String publicationNumber
        LocalDate issueGrantDate
        String patentNumber
        String ptoStatus
        String suffix
        String classification
        String parentFileNumber
        String pdfPath
    }

    class Inventor {
        int id
        String fullName
        String username
    }

    class PatentInventor {
        int id
        int patentId
        int inventorId
        String role
        int rolePosition
    }

    class Tag {
        int id
        String name
    }

    class PatentTag {
        int patentId
        int tagId
        String source
    }

    class StatusUpdate {
        int id
        int patentId
        String fieldName
        String previousValue
        String newValue
        String source
        LocalDateTime timestamp
    }

    class PatentText {
        int id
        int patentId
        String fullText
        int pageCount
        LocalDateTime extractedAt
    }

    class PatentAnalysis {
        int id
        int patentId
        String analysisType
        String resultJson
        String modelUsed
        LocalDateTime analyzedAt
    }

    class MinedPatent {
        int id
        String patentNumber
        String title
        String abstractText
        LocalDate grantDate
        String searchArea
        String searchQuery
    }

    Patent "1" --> "*" PatentInventor
    Inventor "1" --> "*" PatentInventor
    Patent "1" --> "*" PatentTag
    Tag "1" --> "*" PatentTag
    Patent "1" --> "*" StatusUpdate
    Patent "1" --> "0..1" PatentText
    Patent "1" --> "*" PatentAnalysis
```

## Data Access Layer

The DAO layer follows a singleton `DatabaseManager` pattern. Each DAO encapsulates all SQL operations for its corresponding domain entity. The `DatabaseManager` owns the SQLite connection lifecycle, and every DAO obtains connections through it.

```mermaid
classDiagram
    class DatabaseManager {
        +getInstance() DatabaseManager
        +initialize() void
        +getConnection() Connection
    }

    class PatentDao {
        +insert() void
        +update() void
        +findAll() List
        +findByFileNumber() Patent
        +search() List
        +findById() Patent
    }

    class InventorDao {
        +findOrCreate() Inventor
        +findAll() List
        +findCoInventorEdges() List
    }

    class TagDao {
        +findOrCreate() Tag
        +findAll() List
        +findByPatentId() List
    }

    class PatentAnalysisDao {
        +insertOrUpdate() void
        +findByPatentId() List
        +findByPatentIdAndType() PatentAnalysis
        +findByPatentIdAndTypePrefix() List
        +countByType() int
    }

    class PatentTextDao {
        +save() void
        +findByPatentId() PatentText
        +exists() boolean
    }

    class MinedPatentDao {
        +insertOrIgnore() void
        +findBySearchArea() List
        +deleteBySearchArea() void
    }

    class StatusUpdateDao {
        +insert() void
        +findByPatentId() List
    }

    PatentDao -- DatabaseManager
    InventorDao -- DatabaseManager
    TagDao -- DatabaseManager
    PatentAnalysisDao -- DatabaseManager
    PatentTextDao -- DatabaseManager
    MinedPatentDao -- DatabaseManager
    StatusUpdateDao -- DatabaseManager
```

## Service Layer

Services implement the application's business logic. Several services produce typed result records to communicate outcomes back to callers. `ClaudeCliService` integrates with the Claude CLI for AI-powered patent analysis and exposes a `StreamingCallback` interface for real-time output. `InsightService` and `PatentMiningService` orchestrate multi-step workflows that combine AI analysis with data persistence.

```mermaid
classDiagram
    class CsvImportService {
        +importCsv() ImportResult
    }

    class ImportResult {
        int imported
        int updated
        int unchanged
        List errors
    }

    class UsptoSyncService {
        +syncPatent() SyncResult
        +syncAll() SyncResult
    }

    class SyncResult {
    }

    class ClaudeCliService {
        +analyze() AnalysisResult
        +analyzeStreaming() AnalysisResult
        +loadPromptTemplate() String
    }

    class AnalysisResult {
        boolean success
        String resultJson
        String error
        String modelUsed
        long durationMs
        int inputTokens
        int outputTokens
        double costUsd
    }

    class StreamingCallback {
        <<interface>>
    }

    class InsightService {
        +analyzeTechnology() InsightResult
        +analyzeClaims() InsightResult
        +analyzeExpansion() InsightResult
        +analyzePriorArt() InsightResult
        +analyzeWhitespace() InsightResult
        +analyzeClustering() InsightResult
        +analyzeAdjacency() InsightResult
        +analyzeTemporalTrends() InsightResult
        +analyzeClaimCollision() InsightResult
        +analyzeCompetitorGaps() InsightResult
        +analyzeInventionPrompts() InsightResult
        +analyzeCrossDomain() InsightResult
        +exportMarkdown() void
    }

    class InsightResult {
    }

    class CrossPatentProgressCallback {
        <<interface>>
    }

    class PatentMiningService {
        +extractAreasOfInterest() List
        +extractInventionPrompts() List
        +mineArea() MiningResult
        +mineInventionPrompt() MiningResult
        +mineAllInventionPrompts() List
        +getAllMiningResults() List
        +exportAllMiningMarkdown() void
    }

    class MiningResult {
    }

    class MiningHistoryItem {
    }

    class AreaOfInterest {
    }

    class InventionPromptItem {
    }

    class MiningProgressCallback {
        <<interface>>
    }

    class BulkMiningProgressCallback {
        <<interface>>
    }

    class GooglePatentsSearchService {
        +search() SearchResult
    }

    class SearchResult {
    }

    class SearchProgressCallback {
        <<interface>>
    }

    class PdfDownloadService {
        +downloadPdf() void
    }

    class PdfExtractorService {
        +extractText() String
    }

    class GraphDataService {
        +buildGraphJson() String
    }

    class StatsService {
        +getTotalCount() int
        +getStatusBreakdown() Map
        +getYearlyBreakdown() Map
        +getOwnerRoleBreakdown() Map
        +getTopCollaborators() List
    }

    class AutoTagService {
        +autoTag() void
    }

    CsvImportService *-- ImportResult
    ClaudeCliService *-- AnalysisResult
    ClaudeCliService -- StreamingCallback
    InsightService *-- InsightResult
    InsightService -- CrossPatentProgressCallback
    PatentMiningService *-- MiningResult
    PatentMiningService *-- MiningHistoryItem
    PatentMiningService *-- AreaOfInterest
    PatentMiningService *-- InventionPromptItem
    PatentMiningService -- MiningProgressCallback
    PatentMiningService -- BulkMiningProgressCallback
    GooglePatentsSearchService *-- SearchResult
    GooglePatentsSearchService -- SearchProgressCallback
    UsptoSyncService *-- SyncResult

    InsightService -- ClaudeCliService
    InsightService -- PatentAnalysisDao
    InsightService -- PatentTextDao
    InsightService -- PdfExtractorService
    PatentMiningService -- ClaudeCliService
    PatentMiningService -- GooglePatentsSearchService
    PatentMiningService -- PatentAnalysisDao
    PatentMiningService -- MinedPatentDao
    PatentMiningService -- PatentDao
    CsvImportService -- PatentDao
    CsvImportService -- InventorDao
    CsvImportService -- StatusUpdateDao
```

## Controllers

Controllers wire the JavaFX UI to the service layer. `MainController` owns the top-level `TabPane` and coordinates CSV import, USPTO sync, and auto-tagging. Each tab has a dedicated controller that manages its own view lifecycle and user interactions. `SyncController` and `PdfDownloadController` present progress modals for long-running operations.

```mermaid
classDiagram
    class MainController {
        +handleImportCsv() void
        +handleSyncAll() void
        +handleAutoTag() void
    }

    class PatentListController {
        +refreshData() void
        +handleSearch() void
    }

    class PatentDetailController {
        +handleSave() void
        +handleDelete() void
        +handleSync() void
    }

    class DashboardController {
        +refresh() void
    }

    class GraphController {
        +refresh() void
    }

    class InsightsController {
        +handleExtractAll() void
        +handleAnalyzeAll() void
        +handleWhitespace() void
        +handleClustering() void
        +handleExportAll() void
        +refresh() void
    }

    class MiningController {
        +handleMine() void
        +handleMineAllPrompts() void
        +handleExport() void
        +handleExportAll() void
        +loadMiningHistory() void
        +refresh() void
    }

    class SettingsController {
        +handleSave() void
        +getOwnerName() String
        +getClaudeCliPath() String
        +getIdleTimeout() int
        +getBatchSize() int
    }

    class SyncController {
        +startSync() void
    }

    class PdfDownloadController {
        +startDownload() void
    }

    class ClassificationController {
        +handleAdd() void
        +handleDelete() void
    }

    MainController -- CsvImportService
    MainController -- UsptoSyncService
    MainController -- AutoTagService
    PatentListController -- PatentDao
    PatentDetailController -- PatentDao
    DashboardController -- StatsService
    GraphController -- GraphDataService
    InsightsController -- InsightService
    MiningController -- PatentMiningService
    SyncController -- UsptoSyncService
    PdfDownloadController -- PdfDownloadService
```
