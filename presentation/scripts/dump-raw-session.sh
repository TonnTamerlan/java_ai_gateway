#!/usr/bin/env bash
# SessionEnd hook target. Copies the JSONL transcript of the just-ended session
# into presentation/sessions/raw/<UTC-ts>-<short-sid>.jsonl and writes a sibling
# markdown extract beside it. Best-effort: never crashes the hook pipeline.
#
# Triggered on /clear and /exit (matcher: "clear|prompt_input_exit|other").
# Stdin is JSON: { session_id, cwd, reason, hook_event_name, ... }
set -euo pipefail

input=$(cat)

# Tolerant JSON parsing — if jq missing or input malformed, fall back to "".
get() {
    if command -v jq >/dev/null 2>&1; then
        printf '%s' "$input" | jq -r ".$1 // empty" 2>/dev/null || true
    fi
}

session_id=$(get session_id)
cwd=$(get cwd)
reason=$(get reason)
[[ -n "$reason" ]] || reason=unknown

if [[ -z "$session_id" || -z "$cwd" ]]; then
    echo "save-meetup raw dump: missing session_id or cwd; skipping" >&2
    exit 0
fi

# Claude Code encodes the cwd by replacing every non-alphanumeric character
# (slashes, underscores, dots, …) with a dash. So:
#   /Users/akopylov/IdeaProjects/java_ai_gateway
#     -> -Users-akopylov-IdeaProjects-java-ai-gateway
encoded=$(printf '%s' "$cwd" | tr -c '[:alnum:]' '-')
transcript="${HOME}/.claude/projects/${encoded}/${session_id}.jsonl"

if [[ ! -f "$transcript" ]]; then
    echo "save-meetup raw dump: transcript not found at $transcript; skipping" >&2
    exit 0
fi

ts=$(date -u +%Y-%m-%dT%H-%M-%SZ)
short_sid="${session_id:0:8}"
out_dir="${cwd}/presentation/sessions/raw"
mkdir -p "$out_dir"

raw_out="${out_dir}/${ts}-${short_sid}.jsonl"
md_out="${out_dir}/${ts}-${short_sid}.md"

# Source of truth: verbatim transcript copy.
cp "$transcript" "$raw_out"

# Best-effort markdown extract. If jq fails, the .jsonl is still on disk.
{
    echo "# Raw session ${short_sid}"
    echo
    echo "- session_id: \`${session_id}\`"
    echo "- ended_via: \`${reason}\`"
    echo "- timestamp_utc: ${ts}"
    echo "- cwd: ${cwd}"
    echo
    echo "---"
    echo

    if command -v jq >/dev/null 2>&1; then
        jq -r '
          select(.type == "user" or .type == "assistant") |
          if .type == "user" then
            "\n## User\n\n" +
            (.message.content
              | if type == "string" then .
                elif type == "array" then (map(select(.type=="text") | .text) | join("\n"))
                else (tojson) end)
          else
            "\n## Assistant\n\n" +
            (.message.content
              | if type == "array" then
                  (map(
                    if .type == "text" then .text
                    elif .type == "tool_use" then ("_Tool: " + (.name // "?") + "_")
                    elif .type == "thinking" then empty
                    else empty end
                  ) | join("\n\n"))
                else (.message.content // "") end)
          end
        ' "$transcript" 2>/dev/null \
            || echo "_(jq extraction failed; see ${raw_out##*/} for the raw JSONL)_"
    else
        echo "_(jq not installed on PATH; see ${raw_out##*/} for the raw JSONL)_"
    fi
} > "$md_out"

echo "save-meetup raw dump → ${raw_out#$cwd/}" >&2
exit 0
