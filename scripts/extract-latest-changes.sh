#!/usr/bin/env bash
set -e

input="CHANGELOG.md"
output="CHANGELOG_LATEST.md"

latest_changes=$(awk '
  /^## \[/ {
      if (found) exit;
      found=1;
  }
  found
' "$input")

trimmed=$(printf "%s" "$latest_changes" | sed '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba}')

printf "%s\n" "$trimmed" > "$output"
