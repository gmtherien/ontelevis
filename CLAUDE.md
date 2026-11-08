# Ontelevis — notes for Claude Code

Free, open-source (GPL-3.0) native Android IPTV player: Android TV / Google TV / Fire TV + phones.
Pure IPTV client (M3U, Xtream, XMLTV). No desktop, no iOS, no VOD/media hub in V1. Ships no content.
Owner: gmtherien. Replies in French. Confirm before anything outward-facing (push, release, store).

## Commands
- JDK 17 + Android SDK (`~/Library/Android/sdk`, platform 37). Env vars are in `~/.zshrc`.
- `./gradlew assembleDebug` — debug APK (`app/build/outputs/apk/debug/`), id `app.ontelevis.debug`.
- `./gradlew test` — JVM unit tests (JUnit 6, parsers).
- `./gradlew spotlessCheck` / `spotlessApply` — ktlint formatting.
- `./gradlew lintDebug` — Android lint (HardcodedText and MissingTranslation are errors).
- Before a commit: `./gradlew spotlessCheck test lintDebug assembleDebug` must pass (same as CI).
- Emulator: `emulator -avd tv34 &`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Architecture
- `:app` (Activity, navigation, TV vs phone via `isTelevision()`), `:core:model` (pure Kotlin),
  `:core:parser` (pure JVM, streaming), `:core:data` (Room + repositories, Hilt modules),
  `:core:player` (Media3), `:core:designsystem` (themes), `:feature:*` (screens).
- Convention plugins in `build-logic/`: `ontelevis.android.application|library|compose|feature`,
  `ontelevis.hilt`, `ontelevis.jvm.library`. Versions only in `gradle/libs.versions.toml`.
- build-logic compiles against AGP only; other plugins are applied by id (keeps it independent
  of their Kotlin metadata version). AGP 9 built-in Kotlin: no `kotlin-android` plugin.
- TV UI = `androidx.tv:tv-material`; phone UI = Compose Material 3. Feature screens must work in both.

## Rules
- No hard-coded user-visible text: `strings.xml` in `values/` (EN) and `values-fr/` (FR).
  Lint only checks XML; in Compose always use `stringResource`.
- The app never ships a playlist, channel or default source (Google Play policy).
- Big inputs are streamed: M3U line by line, XMLTV with a pull parser and batched DB inserts.
  Never load a whole playlist or guide in memory.
- Never log URLs that contain credentials (Xtream user/password are in the path).
- `minSdk 24` (current AndroidX floor; Fire OS 6 = API 25). `compileSdk 37`, `targetSdk 36`.
- Media3 stays on the 1.9.x line until Jellyfin ships an FFmpeg decoder for a newer Media3.
- Commit only when asked. Never push without asking.

## Lessons carried over from VesperTV (reference only, no code copied)
- Audio: FFmpeg decoder with `EXTENSION_RENDERER_MODE_ON` (PREFER software-decodes plain AAC).
  AC3/E-AC3 5.1 is silent on boxes without a Dolby licence otherwise. Decoder fallback on.
- Low-RAM boxes (< 2.5 GB, e.g. Fire Stick): cap ExoPlayer's buffer in bytes (~64 MB), or a 4K
  stream fills the heap and the app is killed.
- A live stream that ENDS should reconnect, not stop. Cap restart loops (e.g. > 6 restarts in
  60 s → stop and show an error); give up at once on HTTP 404/410.
- A `.ts` URL can redirect to HLS: on "container unsupported", retry once as HLS.
- D-pad: something must always hold focus; explicit focus targets when content swaps; no
  `animateTo` on big lists (`scrollToItem`); test on real boxes, emulators hide most bugs.
- Fire OS on-screen keyboard swallows Back and sends a synthetic Enter: keep text fields read-only
  until OK, never reopen the keyboard on Enter.
- EPG lives in the database (Room/SQLite), queried by time window; never the whole guide in memory.
- Channel ids: unique per playlist (`playlistId::rawId`); several copies can share one EPG id.
