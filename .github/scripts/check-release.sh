#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <tag> [owner/repo]" >&2
  exit 2
}

[ $# -ge 1 ] || usage
TAG="$1"
REPO="${2:-${GH_REPO:-$(gh repo view --json nameWithOwner --jq .nameWithOwner | tr -d '\r')}}"

TAG_PATTERN='^v[0-9]+\.[0-9]+(\.[0-9]+)?$'
ASSET_PATTERN='^reader343-([0-9]+\.[0-9]+(\.[0-9]+)?)-([0-9]+)\.apk$'
BULLET_PATTERN='^[-*+] (New|Improved|Fixed): .+ — .+$'
HEADING="## What's new"

errors=()
fail() { errors+=("$1"); }
jqr() { jq -r "$@" | tr -d '\r'; }

releases="$(gh api --paginate "repos/$REPO/releases?per_page=100" | jq -s 'add // []')"
release="$(jq --arg tag "$TAG" 'map(select(.tag_name == $tag)) | first // empty' <<<"$releases")"
if [ -z "$release" ]; then
  echo "No release found for tag $TAG in $REPO" >&2
  exit 1
fi

if [[ ! "$TAG" =~ $TAG_PATTERN ]]; then
  fail "Tag '$TAG' must look like v1.2 or v1.2.3."
fi
version_from_tag="${TAG#v}"

mapfile -t apks < <(jqr '.assets[].name | select(test("\\.apk$"; "i"))' <<<"$release")
asset_code=""
if [ "${#apks[@]}" -ne 1 ]; then
  fail "Expected exactly one .apk asset, found ${#apks[@]}${apks[*]:+: ${apks[*]}}."
else
  apk="${apks[0]}"
  if [[ "$apk" =~ $ASSET_PATTERN ]]; then
    asset_name="${BASH_REMATCH[1]}"
    asset_code="${BASH_REMATCH[3]}"
    if [ "$asset_name" != "$version_from_tag" ]; then
      fail "Asset versionName '$asset_name' does not match tag version '$version_from_tag'."
    fi
  else
    fail "Asset '$apk' must be named reader343-{versionName}-{versionCode}.apk."
  fi
fi

if [ -n "$asset_code" ]; then
  published="$(jqr '.published_at // ""' <<<"$release")"
  previous_code="$(jqr --arg tag "$TAG" --arg published "$published" '
    map(select(.draft | not) | select(.prerelease | not) | select(.tag_name != $tag)
        | select($published == "" or (.published_at // "") < $published))
    | map(.assets[].name | capture("^reader343-[0-9]+\\.[0-9]+(\\.[0-9]+)?-(?<code>[0-9]+)\\.apk$")? | .code | tonumber)
    | max // empty' <<<"$releases")"
  if [ -n "$previous_code" ] && [ "$asset_code" -le "$previous_code" ]; then
    fail "versionCode $asset_code must be higher than the previous stable release's $previous_code."
  fi
fi

body="$(jqr '.body // ""' <<<"$release")"
in_section=0
found_heading=0
bullets=0
while IFS= read -r line; do
  if [[ "$line" =~ ^#{1,6}[[:space:]] ]]; then
    if [ "$line" = "$HEADING" ]; then
      in_section=1
      found_heading=1
    else
      in_section=0
    fi
    continue
  fi
  [ "$in_section" -eq 1 ] || continue
  if [[ "$line" =~ ^[[:space:]]*[-*+][[:space:]] ]]; then
    bullets=$((bullets + 1))
    if [[ ! "$line" =~ $BULLET_PATTERN ]]; then
      fail "Changelog bullet must read '- New|Improved|Fixed: Title — Body': $line"
    fi
  fi
done <<<"$body"

if [ "$found_heading" -eq 0 ]; then
  fail "Release body must contain a '$HEADING' heading."
elif [ "$bullets" -eq 0 ]; then
  fail "The '$HEADING' section must list at least one bullet."
fi

summary="${GITHUB_STEP_SUMMARY:-}"
if [ "${#errors[@]}" -eq 0 ]; then
  echo "Release $TAG is valid."
  [ -z "$summary" ] || printf '### Release %s\n\nAll checks passed.\n' "$TAG" >>"$summary"
  exit 0
fi

for error in "${errors[@]}"; do
  if [ -n "${GITHUB_ACTIONS:-}" ]; then
    echo "::error title=Release $TAG::$error"
  else
    echo "error: $error" >&2
  fi
done
if [ -n "$summary" ]; then
  {
    printf '### Release %s failed %d check(s)\n\n' "$TAG" "${#errors[@]}"
    for error in "${errors[@]}"; do printf -- '- %s\n' "$error"; done
  } >>"$summary"
fi
exit 1
