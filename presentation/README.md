# Goose meet-up artifacts

Raw material for the meet-up talk (Step 13). **Append-only.** Two channels feed this folder:

| Channel | What | When | Trigger |
| --- | --- | --- | --- |
| `sessions/log.md` | Curated notes — headlines, decisions, snippets to show on stage | The moment something demo-worthy happens | `/save-meetup-note [headline]` (manual) |
| `sessions/raw/<ts>-<sid>.{jsonl,md}` | Full transcript of every ended session | On `/clear` and `/exit` | SessionEnd hook (automatic) |

The curated log is the primary input to the talk. The raw transcripts are the safety net — searchable, foolproof, harder to read.

Step 13 will mine both.

## Subfolders

- `scripts/` — `dump-raw-session.sh` is the hook target. Don't edit lightly; it runs on every session end.
- `sessions/log.md` — the file to re-read when writing slides. Append entries via the slash command.
- `sessions/raw/` — autosaved JSONL + markdown extracts.

## Don't commit secrets here

The raw transcripts can quote anything that appeared in chat — including environment values, API keys you may have pasted. Skim before pushing.
