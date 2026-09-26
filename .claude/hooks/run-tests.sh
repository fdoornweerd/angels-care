#!/usr/bin/env bash
# Stop hook: run the test suite whenever Claude finishes a turn. On failure it exits 2, which hands
# the failures back to Claude so it keeps working instead of stopping on a broken build.
input=$(cat)

# Already continuing because of this hook: let it stop, rather than loop forever on a failure that
# needs Finn's input.
if printf '%s' "$input" | grep -q '"stop_hook_active": *true'; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# During the red phase of /tdd the tests are *meant* to fail and Claude is meant to stop for review.
if [ -f .claude/state/red-phase ]; then
    exit 0
fi

if out=$(./gradlew test --console=plain 2>&1); then
    exit 0
fi

echo "Stop hook: ./gradlew test failed. Fix this before finishing (or explain why you can't):" >&2
printf '%s\n' "$out" | grep -vE '^[[:space:]]+at |^> Task|^$' | tail -40 >&2
exit 2
