## Prompt: Fix `/api/v1/public/search/typeahead` Reliability Bug

You are fixing a blocking API reliability issue.

### Problem

`GET /api/v1/public/search/typeahead?q=<prefix>` returns `500` for valid query inputs (length >= 2).

Current likely cause: PostgreSQL query uses `SELECT DISTINCT title ... ORDER BY similarity(title, :prefix)` which is invalid in PostgreSQL for `DISTINCT` ordering.

### Required Behavior

For `/api/v1/public/search/typeahead`:

- If `q` is missing/blank/length < 2: return `200` with `[]`
- If `q` is valid (length >= 2): return `200` with up to 10 title suggestions ranked by trigram similarity
- Never return `500` for normal valid input
- Preserve existing auth contract (public endpoint, no auth required)

### Implementation Requirements

1. Fix the repository query in `SearchDocumentRepository` to be PostgreSQL-valid and ranking-preserving.
2. Keep `pg_trgm` usage (`%` and/or `similarity`) for fuzzy matching.
3. Ensure deterministic ordering (e.g., similarity desc, then title asc).
4. Do not degrade behavior of other search endpoints.

### Suggested SQL Approach (choose one)

- `DISTINCT ON` pattern, e.g.:
  - inner query computes `similarity(title, :prefix)`
  - dedupe by title
  - order by score desc, title asc
  - limit 10

or

- subquery with grouped max similarity per title, then ordered outer select.

### Test Requirements

Update/add integration tests in `SearchApiIT`:

1. `publicTypeahead_withShortQuery_returnsEmpty200`
2. `publicTypeahead_withLongerQuery_returns200AndSuggestions`
3. `publicTypeahead_returnsAtMost10`
4. `publicTypeahead_resultsAreRankedDeterministically`

Also remove/replace any test that currently expects `500` for valid input.

### Output Required

Return:

1. Files changed
2. Exact query before/after
3. Test cases added/updated
4. Test command run and summarized result
5. Confirmation that endpoint now returns `200` for valid input

### Acceptance Criteria

- No valid `/api/v1/public/search/typeahead` request returns `500`
- Endpoint returns `200` + JSON array for valid queries
- Integration tests pass and assert correct non-error behavior
- No regressions in existing search API tests
