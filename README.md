# Reader343

An offline PDF reader for Android with reading stats, daily goals, highlights and notes. There is no account and no cloud sync. The only network call is the update check against this repository's GitHub Releases.

## Building

```sh
./gradlew assembleDebug
./gradlew lint testDebugUnitTest
```

## Releasing

The app updates itself from the latest stable release of this repository (`/releases/latest`, so drafts and prereleases are never offered). It reads everything it needs from the tag, the APK asset name and the release notes. There is no extra metadata file.

### Contract

1. **Version.** Bump `versionCode` and `versionName` in `app/build.gradle.kts`. The `versionCode` must be higher than the previous stable release's.
2. **Tag.** `v{versionName}`, for example `v1.1` or `v1.1.2`.
3. **Asset.** Exactly one APK, named `reader343-{versionName}-{versionCode}.apk`, for example `reader343-1.1-118.apk`. The app takes the version name and code from this file name only. The APK must be signed with the same key as the installed app, or the app will refuse to install it.
4. **Release notes.** A `## What's new` heading followed by one bullet per change:

   ```markdown
   ## What's new
   - New: Weekly goals — Set a weekly target alongside the daily one.
   - Improved: Faster page rendering — Large PDFs open about 40% quicker.
   - Fixed: Streak rollover at midnight — Sessions crossing midnight no longer count twice.
   ```

   Each bullet is `- {New|Improved|Fixed}: {title} — {body}`, with an em dash between title and body. The app shows each one as a card tagged New, Improved or Fixed. Anything outside that section is ignored by the app, so you can add other notes below it under their own heading.

### Checking a release

The `Release check` workflow (`.github/workflows/release-check.yml`) runs when a release is published or edited, and can be started by hand with a tag. It fails with one error line per problem and writes a job summary. It never changes or deletes the release.

The same checks run locally with the GitHub CLI and `jq`:

```sh
.github/scripts/check-release.sh v1.1
```

It verifies the tag format, that there is exactly one correctly named APK, that its version name matches the tag, that its version code is higher than the previous stable release's, and that every bullet under `## What's new` follows the format above. To check notes before publishing, create the release as a draft first and run the script against its tag.
