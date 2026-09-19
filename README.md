# Reader343

Reader343 is a modern, offline-first PDF reader for Android.

I created it because I could not find a modern open-source reader where I could simply add my own books. Reader343 is a calm, personal place for your PDF library: bring your books, read without an account, and make the experience your own.

## Contents

- [Why Reader343?](#why-reader343)
- [Reading your books](#reading-your-books)
- [Make the library yours](#make-the-library-yours)
- [Privacy, language, and updates](#privacy-language-and-updates)
- [Build it yourself](#build-it-yourself)
- [Release build](#release-build)

## Why Reader343?

Your library should feel like yours. Import PDFs directly, pick up where you stopped, and make notes as you read — without needing to create an account or upload your books somewhere else.

## Reading your books

- Add and read your own PDF files.
- Automatically save your place and show chapter progress.
- Use a book's table of contents, page navigation, and bookmarks to move around quickly.
- Highlight text in several colours and attach notes to passages.
- Review all notes and highlights for a book, filter them, and jump back to the original page.
- Listen to text with **Read aloud**. Choose a device voice, control speed and pitch, highlight the spoken sentence, continue to the next page automatically, and use a sleep timer.

## Make the library yours

- Fetch book details and cover art with optional metadata lookup; select the right result or edit the title yourself.
- Set a daily reading goal by time or pages, build a streak, and see reading insights and activity history.
- Get optional daily and streak reminders.
- Choose light or dark mode and adjust the page appearance for comfortable reading.
- Filter and organise the books in your library.

## Privacy, language, and updates

There are no accounts and no cloud sync. Your PDFs, progress, bookmarks, notes, highlights, and settings remain on your device.

Reader343 supports **English and Arabic**, including right-to-left Arabic layouts. Internet access is used only when you choose metadata lookup and when the app checks GitHub Releases for updates. When an update is available, Reader343 can download it, verify that it was signed by the same publisher, and hand installation to Android. Nothing from your library is uploaded.

## Build it yourself

Open the project in Android Studio, allow Gradle to finish syncing, choose a device or emulator, then press **Run**. This creates and installs a development build.

Or build a debug APK from the project folder:

```powershell
.\gradlew.bat assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Release build

To make a release APK locally, add your signing details to an untracked `keystore.properties` file in the project root, then run:

```powershell
.\gradlew.bat assembleRelease
```

The signed APK is created at `app/build/outputs/apk/release/app-release.apk`. Keep your keystore and its passwords private, and use the same signing key for every published update.

For the project release process, update the version in `app/build.gradle.kts`, create a matching Git tag such as `v1.2`, and push it. GitHub Actions builds the signed APK and prepares a draft GitHub release.
