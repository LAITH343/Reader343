#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <tag> <apk> [owner/repo]" >&2
  exit 2
}

[ $# -ge 2 ] || usage
TAG="$1"
APK="$2"
REPO="${3:-${GH_REPO:-$(gh repo view --json nameWithOwner --jq .nameWithOwner | tr -d '\r')}}"

[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }

find_release() {
  gh api --paginate "repos/$REPO/releases?per_page=100" \
    | jq -s --arg tag "$TAG" 'add // [] | map(select(.tag_name == $tag)) | first // empty'
}

release="$(find_release)"
if [ -z "$release" ]; then
  notes="$(mktemp)"
  cat >"$notes" <<'EOF'
## What's new
- Replace this line with one bullet per change: New|Improved|Fixed: Title — Body
EOF
  gh release create "$TAG" --repo "$REPO" --draft --verify-tag --title "Reader343 ${TAG#v}" --notes-file "$notes"
  rm -f "$notes"
  release="$(find_release)"
  echo "Created draft release $TAG."
elif [ "$(jq -r '.draft' <<<"$release")" != "true" ]; then
  echo "Release $TAG is already published. Refusing to replace its APK." >&2
  exit 1
else
  echo "Updating existing draft release $TAG."
fi

jq -r --arg keep "$(basename "$APK")" '.assets[] | select(.name | test("\\.apk$"; "i")) | select(.name != $keep) | .id' <<<"$release" \
  | tr -d '\r' \
  | while read -r asset; do
      gh api -X DELETE "repos/$REPO/releases/assets/$asset" >/dev/null
    done

gh release upload "$TAG" "$APK" --repo "$REPO" --clobber
echo "Uploaded $(basename "$APK") to draft $TAG."
