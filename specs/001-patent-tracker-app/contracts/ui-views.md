# UI View Contracts: Patent Portfolio Tracker

**Date**: 2026-03-27
**Feature**: 001-patent-tracker-app

## Overview

The application exposes the following views to the user. These contracts define what each view displays and what actions it supports, without prescribing layout or visual design.

---

## V1: Patent List View (Main View)

**Purpose**: Browse, search, and filter the full patent portfolio.

### Displayed Data
- Sortable table with columns: Title, File Number, Filing Date, Application #, Patent #, Status, Primary Inventor, Classification
- Column visibility is user-configurable
- Result count showing filtered/total (e.g., "127 of 426 patents")

### User Actions
| Action | Input | Result |
|--------|-------|--------|
| Search | Text in search box | Table filtered to patents matching title keywords |
| Filter by status | Status dropdown/checkboxes | Table shows only matching statuses |
| Filter by inventor | Inventor name selector | Table shows patents involving that inventor |
| Filter by classification | Classification selector | Table shows matching classification |
| Filter by tag | Tag selector | Table shows patents with that tag |
| Filter by year | Year range selector | Table shows patents filed in that range |
| Sort | Click column header | Table sorted by that column (asc/desc toggle) |
| Select patent | Click row | Opens Patent Detail View (V2) |
| Multi-select | Checkbox per row | Enables bulk tag operations |
| Bulk tag | Select patents + choose tag | Adds/removes tag from selected patents |
| Import CSV | Menu/button | Opens file picker, imports CSV into database |
| Navigate to Graph | Tab/button | Opens Inventor Graph View (V4) |
| Navigate to Dashboard | Tab/button | Opens Dashboard View (V5) |

### Combined Filters
- All filters are combinable (AND logic)
- Active filters are visible as removable chips/badges
- "Clear all filters" action resets to full list

---

## V2: Patent Detail View

**Purpose**: View and edit all details for a single patent.

### Displayed Data
- All patent fields (title, file number, dates, numbers, status, classification)
- Inventor list with roles (Primary, Secondary, Additional)
- Tags (editable)
- Related patents (continuations/divisionals) with navigation links
- Change history (from StatusUpdate records)

### User Actions
| Action | Input | Result |
|--------|-------|--------|
| Add tag | Type tag name | Tag created (if new) and associated with patent |
| Remove tag | Click X on tag | Tag association removed |
| Sync with USPTO | Button | Triggers USPTO query for this patent, shows diff, user confirms |
| Navigate to related patent | Click related patent link | Opens that patent's detail view |
| Navigate to inventor | Click inventor name | Filters main list to that inventor's patents |
| Back to list | Back button | Returns to Patent List View with previous filters preserved |

---

## V3: USPTO Sync View

**Purpose**: Show results of a USPTO status synchronization operation.

### Displayed Data
- Per-patent sync result: application number, current local values vs USPTO values, diff highlighting
- Overall progress (X of Y patents processed)
- Error/warning messages for failed lookups

### User Actions
| Action | Input | Result |
|--------|-------|--------|
| Sync single patent | From Patent Detail View | Queries USPTO for one patent |
| Sync all patents | Bulk sync button | Sequentially queries USPTO for all patents with application numbers |
| Accept update | Per-patent confirm | Updates local DB with USPTO values, creates StatusUpdate record |
| Reject update | Per-patent reject | Keeps local values unchanged |
| Accept all | Button | Accepts all retrieved updates at once |

---

## V4: Inventor Graph View

**Purpose**: Visualize inventor co-invention relationships as an interactive network graph.

### Displayed Data
- Nodes: Each unique inventor (sized by patent count)
- Edges: Co-invention relationships (weighted by number of shared patents)
- Node labels: Inventor name
- Edge labels (on hover): Number of shared patents

### User Actions
| Action | Input | Result |
|--------|-------|--------|
| Zoom | Scroll wheel / pinch | Graph zooms in/out |
| Pan | Click + drag background | Graph viewport moves |
| Move node | Click + drag node | Node repositions, layout adjusts |
| Hover node | Mouse over node | Tooltip shows inventor name and total patent count |
| Hover edge | Mouse over edge | Tooltip shows shared patent count and titles |
| Click node | Click on node | Side panel shows inventor details: patents, top co-inventors |
| Filter sync | Apply filters in list view | Graph updates to show only inventors from filtered patent set |
| Reset layout | Button | Re-applies force-directed layout |

---

## V5: Dashboard View

**Purpose**: Show portfolio summary statistics and visualizations.

### Displayed Data
- Total patent count
- Status breakdown (pie/bar chart): Issued, Filed, Published, Abandoned, Dropped, Allowed
- Filing timeline (bar/line chart): Patents filed per year
- Classification breakdown (bar chart): Count per classification
- Top inventors (bar chart): Most prolific inventors by patent count

### User Actions
| Action | Input | Result |
|--------|-------|--------|
| View details | Click on chart segment | Navigates to list view with corresponding filter applied |

---

## V6: Tag Management View

**Purpose**: View and manage all tags across the portfolio.

### Displayed Data
- List of all tags with patent count per tag
- Search/filter within tags

### User Actions
| Action | Input | Result |
|--------|-------|--------|
| Rename tag | Edit tag name | All patent associations updated |
| Delete tag | Delete button | Tag removed from all patents |
| Click tag | Select tag | Navigates to list view filtered by that tag |
