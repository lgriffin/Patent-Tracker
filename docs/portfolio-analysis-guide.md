# Portfolio Analysis Guide

The Insights tab uses Claude Code CLI to perform AI-driven analysis of your patent portfolio. There are 12 analysis types split into two categories: **single-patent** analyses that examine one patent at a time, and **cross-patent** analyses that reason across your entire portfolio.

## Single-Patent Analyses

These analyses operate on individual patent text (extracted from PDF). Each produces structured JSON results stored in the database for reuse by cross-patent analyses.

### Claims Analysis
Decomposes the patent's claim structure into a hierarchy of independent and dependent claims. Reveals the legal scope architecture — how dependent claims narrow and build upon independent claims, and which independent claim provides the broadest coverage.

### Technology Extraction
Extracts the core technical essence: the field, problem solved, specific innovations, advantages over prior art, real-world applications, and keywords. This is the foundational analysis — most cross-patent analyses depend on its output.

### Expansion Vectors
Identifies follow-on filing opportunities: continuation directions, divisional filings for separable inventions, and continuation-in-part (CIP) suggestions with new matter enhancements. Each opportunity includes prior art risk assessment and an overall expansion score.

### Prior Art Proximity
Evaluates patent robustness by assessing novelty, identifying likely prior art areas ranked by risk (HIGH/MEDIUM/LOW), rating claim strength, and flagging vulnerability areas. Useful for prosecution strategy and litigation preparedness.

## Cross-Patent Analyses

These analyses aggregate Technology Extraction results from all patents in the portfolio and reason across them. They require at least 2 patents with completed Technology Extraction.

### Whitespace Finder
Identifies technology domains adjacent to your existing patents that remain unfiled. Reveals what the portfolio *doesn't* cover but probably should, with strategic recommendations and priority rankings for each whitespace opportunity.

### Clustering
Groups patents by thematic technology clusters and evaluates portfolio health. Shows concentration vs. diversity, identifies gaps between clusters, flags redundant vs. strategic overlap, and provides portfolio balance scores.

### Adjacency Map
Maps reachable adjacent technology areas from your current portfolio, scoring each by reachability (1-10). Distinguishes low-hanging fruit (quick wins near existing patents) from frontier areas requiring significant R&D investment, and identifies stepping-stone patents for each.

### Temporal Trends
Analyzes filing patterns over time using filing dates. Tracks which technology domains are accelerating, steady, stalling, or abandoned. Reveals strategic pivots, innovation shifts, and forecasts future filing directions based on historical momentum.

### Claim Collision
Detects overlapping claim spaces within your own portfolio. Classifies overlaps as REDUNDANT (wasteful duplication), COMPLEMENTARY (strategic reinforcement), or CONTINUATION_OPPORTUNITY (basis for follow-on filings). Includes portfolio coherence scoring.

### Competitor Gaps
Benchmarks your portfolio against likely competitor coverage. Identifies where you hold unique competitive positions (moats), where you're exposed (defensive gaps), and prioritizes strategic recommendations by urgency — CRITICAL, SIGNIFICANT, or MINOR.

### Invention Prompts
Synthesizes results from Whitespace, Clustering, and Adjacency analyses into actionable R&D direction. Generates specific invention disclosure prompts with problem statements, proposed solutions, and key claims. Categorizes ideas as quick wins vs. moonshots.

### Cross-Domain Combinator
Identifies synergistic combinations of separate technology domains in your portfolio. Maps 2-domain intersections and 3-domain triple intersections, evaluating novelty and market relevance. Surfaces underexplored combinations where combining existing capabilities creates novel, unpatented opportunities.

## Analysis Dependencies

```
Technology Extraction (per patent)
    |
    +-- Whitespace --------+
    |                      |
    +-- Clustering --------+--> Invention Prompts
    |                      |
    +-- Adjacency ---------+
    |
    +-- Temporal Trends (also uses filing dates)
    +-- Claim Collision
    +-- Competitor Gaps
    +-- Cross-Domain
```

Technology Extraction is always run first. If a patent lacks it when a cross-patent analysis is triggered, it will be extracted automatically.

Invention Prompts optionally enriches its output with Whitespace and Clustering results if they exist.

## Recommended Workflow

1. **Extract All Text** — bulk-extract PDF text for all patents
2. **Run Technology Extraction** — batch-run on all patents (uses 2-second delay between calls)
3. **Clustering** — understand your portfolio structure first
4. **Whitespace** — identify gaps adjacent to your clusters
5. **Adjacency Map** — quantify which gaps are easiest to reach
6. **Temporal Trends** — see where momentum is heading
7. **Claim Collision** — check for internal redundancy
8. **Competitor Gaps** — benchmark against the competitive landscape
9. **Cross-Domain** — find novel domain combinations
10. **Invention Prompts** — generate actionable R&D ideas from all the above

## Configuration

- **Claude CLI Path** — configurable in Settings (defaults to `claude` on PATH)
- **Analysis Timeout** — configurable in Settings (default: 600 seconds). Increase for large portfolios (400+ patents). Cross-patent analyses aggregate all patent summaries into a single prompt, so larger portfolios need more processing time.
- **Results** — all analysis results are cached in the database and displayed in the Insights tab accordion. Re-running an analysis overwrites the cached result.
- **Export** — use the Export button to generate a Markdown report of all completed analyses.
