# USPTO Integration Contract: Patent Portfolio Tracker

**Date**: 2026-03-27
**Feature**: 001-patent-tracker-app

## Overview

The application integrates with the USPTO Open Data Portal (ODP) Patent File Wrapper API to synchronize patent status data. This contract defines the integration boundary.

## Configuration

| Setting | Description | Required |
|---------|-------------|----------|
| USPTO API Key | User-provided ODP API key | Yes, for sync functionality |
| Rate Limit Delay | Milliseconds between API calls (default: 1100ms for ~55 req/min) | No |

The API key is stored locally in the application's configuration, entered via a settings dialog. It is not bundled with the application.

## Outbound Request

### Endpoint
```
GET https://data.uspto.gov/api/v1/patent/applications/{applicationNumber}
```

### Application Number Formatting
CSV stores application numbers with slashes (e.g., `15/661380`). The API requires them without slashes: `15661380`.

**Transformation**: Strip all `/` characters from the stored application number.

### Headers
```
X-API-Key: {user-provided-api-key}
Accept: application/json
```

## Expected Response Fields

The application extracts and maps the following fields from the USPTO response:

| USPTO Response Field | Maps To (Local) | Update Logic |
|---------------------|-----------------|--------------|
| applicationStatusDescription | ptoStatus | Update if different |
| patentNumber | patentNumber | Update if local is NULL or different |
| grantDate | issueGrantDate | Update if local is NULL or different |
| publicationDate | publicationDate | Update if local is NULL or different |
| publicationNumber | publicationNumber | Update if local is NULL or different |
| inventionTitle | (comparison only) | Log warning if significantly different |

## Error Handling

| HTTP Status | Behavior |
|-------------|----------|
| 200 | Parse response, compute diff, present to user |
| 401 | Invalid API key — prompt user to check/update key |
| 404 | Application not found — mark as "not found on USPTO" |
| 429 | Rate limited — pause, increase delay, retry after backoff |
| 5xx | Server error — skip patent, log error, continue batch |
| Network error | Report connection failure, no data changes |

## Sync Modes

### Single Patent Sync
- Triggered from Patent Detail View
- Queries one application number
- Shows diff to user before applying

### Bulk Sync
- Triggered from menu/toolbar
- Processes all patents that have an application number
- Sequential processing with configurable delay between requests
- Progress indicator showing current/total
- Results summary at completion (updated/unchanged/errors)
- User can cancel mid-sync

## Change Tracking

Every field update from a USPTO sync creates a StatusUpdate record:
```
{
  patentId: <id>,
  fieldName: "ptoStatus",
  previousValue: "Filed",
  newValue: "Published",
  source: "USPTO_SYNC",
  timestamp: <now>
}
```

## Offline Behavior

When no internet connection is available or no API key is configured:
- Sync buttons are disabled or show appropriate messaging
- All other application features work normally
- No background/automatic sync attempts
