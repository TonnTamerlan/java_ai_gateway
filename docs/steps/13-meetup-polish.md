# Step 13 — Meet-up polish

**Status:** In progress — 2026-10-19: talk deck (slice A).
**Prereqs:** Step 12 green.

## Scope

Tighten the demo for a 30–45 minute talk.

- **Mine `presentation/sessions/log.md`** (curated notes, accumulated by `/save-meetup-note` across all prior sessions) and `presentation/sessions/raw/*.jsonl` (full transcripts dropped by the SessionEnd hook). These are the primary inputs to the talk; nothing in this step starts from a blank page.
- Talk outline in `docs/meetup/outline.md`.
- Architecture and sequence diagrams refined; SVG exports.
- Recorded screen flow (chat round-trip + calc round-trip).
- README rewritten with a "what to look at first" section pointing at ADRs and steps.
- Bundle splitting + lazy AntD imports if frontend bundle is unwieldy.

## First questions

1. Talk format (sit-down walkthrough vs slides + live demo)?
2. Audience assumptions (Spring vets vs broader Java)?

## Slice A — talk deck (2026-10-19)

Decisions taken with the user:

- **Theme:** "Single Responsibility Principle in AI" — LLM access is a reason to change; give it one owner (the AI Gateway).
- **Format:** one self-contained HTML deck, `presentation/meetup/deck.html` (vanilla JS + SVG, offline). Marp can't animate flows.
- **Language:** English. 40–45 min. High-level architecture only — no classes, annotations, framework versions on slides.
- **Structure:** opening · problem (4 slides) · AI gateway (3) · sync chat flow (3 animated) · async file flow (3 animated) · whole architecture (3 animated) · built with AI (3) · closing.
- **Animations loop** while the slide is on screen; global speed control (0.25×–3×, `-`/`+`, persisted).
- Speaker notes live in the deck (presenter view, `S`) and in `presentation/meetup/outline.md`.

Delivered (2026-10-19):

- `presentation/meetup/deck.html` — 23 slides (speaker intro first), 44 min planned. Speaker photo + Spin.AI logo embedded (sources in `presentation/meetup/assets/`). 15 looping animated scenes (fleet → spaghetti → gateway; sync chat request/stream/whole; async submit/batch/whole; architecture chat flow, observability, everything; cross-session AI loop). Keys: →/Space next (builds first), ← back, P pause, R restart, −/+ speed (0.25×–3×, persisted in `localStorage`), 0 reset, F fullscreen, S presenter window (notes, jokes, timer vs plan). Self-contained (no network); tested served over local HTTP.
- `presentation/meetup/outline.md` — talk script generated from the deck's `SLIDES` block by `presentation/scripts/deck-outline.js`.
- Verified in Chromium at 1440×900 and 1280×800: keyboard walk through all slides/builds, no console errors, loops restart with no leftover packets, speed persists.

Remaining in Step 13 (not started): screen recording, README "what to look at first", bundle splitting.
