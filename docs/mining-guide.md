# Patent Mining Guide

## Overview

Patent Mining searches external patents on Google Patents and uses Claude AI analysis to generate new patent ideas informed by both your existing portfolio and the competitive landscape. There are two distinct mining modes:

1. **General Area Mining** -- explore broad technology areas derived from portfolio analyses
2. **Invention Prompt Mining** -- validate specific invention ideas against the competitive landscape

## Getting Started

Prerequisites:

- Patent portfolio imported (CSV import)
- PDF text extracted and Technology Extraction completed (at least some patents)
- Claude CLI configured in Settings
- For General Area Mining: at least one cross-patent analysis completed (Clustering, Whitespace, Adjacency, etc.)
- For Invention Prompt Mining: Invention Prompts analysis completed on the Insights tab

## General Area Mining

### How Areas Are Extracted

When you click "Refresh Areas", the system scans your completed portfolio analyses and extracts technology areas:

- **Clustering**: cluster themes and key technologies
- **Cross-Domain**: domain inventory and core capabilities
- **Whitespace**: whitespace opportunity areas
- **Temporal Trends**: domain trends and emerging themes
- **Adjacency**: anchor domains and target areas
- **Competitor Gaps**: portfolio strengths and competitor areas
- **Technology Extraction**: technical fields and keywords from individual patents

Areas are ranked by the number of source analyses that mention them. You can also type a custom area in the search box.

### Mining Flow

1. Select an area from the dropdown (or type a custom area)
2. Click "Mine Patents"
3. The system searches Google Patents for recent US patents (last 2 years, max 100 results)
4. Found patents are stored locally and displayed in the search results table
5. Claude analyzes the external patents against your portfolio to generate:
   - **Landscape Summary**: overview of the competitive landscape, key trends, portfolio overlap
   - **Patent Ideas**: novel invention ideas with problem statements, descriptions, novelty angles
   - **Defensive Opportunities**: urgent filings to protect existing portfolio positions
   - **Portfolio Blind Spots**: technology areas you may be missing

### Results

Results are stored with type `PATENT_MINING:{area}` and appear in the Mining History accordion.

## Invention Prompt Mining

### How Prompts Are Extracted

Invention prompts come from the "Invention Prompts" analysis on the Insights tab. When you click "Refresh", the system loads:

- **Invention prompts** -- main ideas with categories
- **Quick wins** -- low-hanging fruit ideas (category: QUICK_WIN)
- **Moonshots** -- ambitious frontier ideas (category: MOONSHOT)

Each prompt includes: title, problem statement, description, technical domain, category, and source patents.

### Mining Flow

1. Switch to "Invention Prompt Mining" mode
2. Select an invention prompt from the list
3. Review the detail panel (problem, description, domain, source patents)
4. Click "Mine Patents"
5. The system derives search keywords from the prompt's title and domain
6. Google Patents search runs for those keywords
7. Claude validates the invention idea against external patents, producing:
   - **Idea Validation**: landscape support level (STRONG/MODERATE/WEAK), differentiation assessment, closest external patents, recommended refinements, risk factors
   - Plus the same sections as general mining (landscape summary, patent ideas, defensive opportunities, blind spots)

### Landscape Support Levels

- **STRONG** (green) -- the competitive landscape strongly supports this invention direction; clear differentiation exists
- **MODERATE** (amber) -- some overlap with existing patents; refinements recommended
- **WEAK** (red) -- significant overlap with existing patents; idea may need substantial pivoting

### Results

Results are stored with type `PATENT_MINING_IP:{title}` and appear in the Mining History accordion.

## Bulk Mining (Mine All Prompts)

In Invention Prompt mode, the "Mine All Prompts" button runs mining for ALL invention prompts sequentially:

1. Click "Mine All Prompts"
2. Progress shows: "Mining 1/N: {prompt title}", "Mining 2/N: {prompt title}", etc.
3. Each prompt goes through the full search + analysis cycle
4. Failed prompts are logged but don't stop the batch
5. On completion: summary shows "X/Y succeeded" with total cost
6. All results appear in the Mining History accordion

This is useful for comprehensive portfolio exploration -- run it overnight and review all results the next day.

## Mining History

All mining results (general and IP) are persisted and displayed in the Mining History accordion at the bottom of the Mining tab.

### History Display

- Results are sorted by timestamp (most recent first)
- Each entry shows: `[General/IP] {area/title} ({timestamp})`
- Expanding an entry reveals a nested accordion with all analysis sections
- Each entry has its own "Export" button for individual export

### Persistence

Mining results survive application restarts. When you return to the Mining tab, all past results are automatically loaded.

## Exporting Results

### Individual Export

Click the "Export" button on any history entry or the top-level "Export Results" button. This creates a Markdown file with the complete analysis for that mining run.

### Export All

Click "Export All" to create a single Markdown document containing ALL mining results (both general and IP), ordered by timestamp.

### Export Format

Exported Markdown includes:

- Result type and area/title
- Timestamp
- Idea Validation (IP mining only): landscape support, differentiation, closest patents, refinements, risks
- Landscape Summary: search area, patents analyzed, overlap, density, key trends
- Patent Ideas: numbered list with problem statements, descriptions, novelty angles, feasibility, strategic value
- Defensive Opportunities: titles, urgency levels, threat patents
- Portfolio Blind Spots: bullet list

## Google Patents Search

The mining feature searches Google Patents using an XHR JSON API:

- **Scope**: US patents only
- **Time range**: Last 2 years (from filing/priority date)
- **Maximum results**: 100 patents per search
- **Data returned**: Patent number, title, snippet/abstract, grant date
- **Rate limiting**: One search per mining run

The search uses the area name plus any extracted keywords as the query. For IP mining, keywords are derived from the invention prompt's title and technical domain.

## Tips

- Run cross-patent analyses (especially Clustering and Whitespace) before mining -- they produce better area suggestions
- Use General Area Mining for broad landscape exploration
- Use IP Mining to validate specific invention ideas before investing in disclosure writing
- Use "Mine All Prompts" for comprehensive exploration of all generated ideas
- Export and share results with your patent attorney for filing prioritization
- Re-mining the same area or prompt overwrites the previous result (but you can export first)
