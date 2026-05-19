# Step 10 — Calc UI

**Status:** In progress (files-table slice).
**Prereqs:** Step 9 green.

## Scope

Frontend exposes the calculation flow. Lands in two slices.

### Slice A — Global Files table (this slice)

A second AntD `<Table>` rendered **below** the existing `FileSummarizationPanel`, listing every `summarization_files` row across all jobs.

- Columns: `id` (truncated UUID), `fileName`, `status`, `createdAt`, `updatedAt`.
- Expandable row → prompt (`instruction`) + summary.
- Rightmost info-icon column → Popover (placement `bottomLeft`) with `model`, `promptTokens`, `completionTokens`.
- Server-side pagination (default page size 20), sort (`fileName`/`status`/`createdAt`/`updatedAt`), `status` filter, `name` search (debounced 300 ms, case-insensitive substring).

Backend work this slice requires:

- Flyway `V3` adds `file_name TEXT NOT NULL DEFAULT ''` to `calc.summarization_files`; `SummarizeController` stops discarding `upload.getOriginalFilename()` and threads it through `FileInput` → `SummarizationService` → row insert.
- `GET /summarize/files` (paged + sortable + filterable + searchable) backed by a `FilesRepository` custom fragment using `JdbcAggregateTemplate` (Spring Data JDBC's `@Query` does not auto-append `ORDER BY` from `Pageable.getSort()`).
- New `FileSummaryView` DTO omits `originalText` to keep payloads small.
- Sort field whitelist (server-side): `fileName` / `status` / `createdAt` / `updatedAt`; anything else → HTTP 400.

### Slice B — Submission form + jobs detail polish (deferred)

- Submission form: react-hook-form + zod, AntD Form components.
- Jobs list table (separate from files): AntD Table with status Tag.
- Job detail: polled with TanStack Query `refetchInterval`, shows steps + final result.
- Vitest + MSW for the new components.

## Resolved decisions

- **Table placement** — below the upload panel on the same page, no routing.
- **`id` column** — `summarization_files.id` (UUID), not `correlation_id`.
- **Scope** — all files across all jobs (global table), not per-job.
- **Default page size** — 20.
- **Hover UX** — info-icon column + `Popover`, not full-row wrap, to avoid AntD expandable-row interaction issues.
