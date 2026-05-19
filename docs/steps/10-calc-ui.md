# Step 10 — Calc UI

**Status:** In progress (files-table slice).
**Prereqs:** Step 9 green.

## Scope

Frontend exposes the calculation flow. Lands in two slices.

### Slice A — Global Files table (this slice)

A second AntD `<Table>` rendered **below** the existing `FileSummarizationPanel`, listing every non-deleted `summarization_files` row across all jobs.

- Columns: `id` (truncated UUID), `fileName`, `status`, `createdAt`, `updatedAt`.
- Click anywhere on a row → expand to show prompt (`instruction`) + summary (`expandRowByClick: true`).
- Hover any cell of a row → "Run stats" Popover with `model`, `promptTokens`, `completionTokens` (100 ms enter delay; wired via AntD's `components.body.row` override).
- Server-side pagination (default page size 20), sort (`fileName`/`status`/`createdAt`/`updatedAt`), `status` filter, `name` search (debounced 300 ms, case-insensitive substring).
- Row selection (`rowSelection` checkboxes) + Delete button above the table. Delete is **soft** — `DELETE /summarize/files` body `{ids:[...]}` flips `deleted_at`; the table auto-refetches after a confirmed delete and auto-steps back if the current page empties.

Backend work this slice requires:

- Flyway `V3` adds `file_name TEXT NOT NULL DEFAULT ''` to `calc.summarization_files`; `SummarizeController` stops discarding `upload.getOriginalFilename()` and threads it through `FileInput` → `SummarizationService` → row insert.
- Flyway `V4` adds `deleted_at TIMESTAMPTZ NULL` + a partial index `WHERE deleted_at IS NULL` on `(created_at DESC)`. The `SummarizationFile` record gains `Instant deletedAt`.
- `GET /summarize/files` (paged + sortable + filterable + searchable) backed by a `FilesRepository` custom fragment using `JdbcAggregateTemplate` (Spring Data JDBC's `@Query` does not auto-append `ORDER BY` from `Pageable.getSort()`); the criteria always pin `deletedAt IS NULL`.
- The per-job query is renamed `findByJobIdAndDeletedAtIsNullOrderByCreatedAt` — `SummarizeController.get` and the `SummarizationResponseConsumer` roll-up both filter out soft-deleted rows. The Kafka pipeline's `markDone`/`markFailed` and `findByCorrelationId` deliberately do **not** filter, so background processing stays idempotent on deleted rows.
- New `DELETE /summarize/files` accepts `DeleteFilesRequest` (`@NotEmpty @Size(max=100) List<UUID> ids`), validated via `jakarta.validation`. A new `MethodArgumentNotValidException` handler returns the standard `{"error": "..."}` shape so validation failures match the existing UX. Returns `{ "deleted": N }`. The repository's `softDelete(ids, now)` is `@Modifying @Query` (`UPDATE ... SET deleted_at WHERE id IN (:ids) AND deleted_at IS NULL`) — idempotent: a re-delete returns `0`.
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
- **Hover UX** — **row-level** `Popover` via `components.body.row` (100 ms enter delay). Superseded the earlier info-icon column once user feedback was "hover anywhere on the row"; integrates fine with `expandRowByClick`.
- **Delete UX** — soft delete (`deleted_at` column, no hard DROP). Backend `DELETE /summarize/files` accepts `{ids:[...]}` so single + bulk share one endpoint; Popconfirm in the UI before firing.
