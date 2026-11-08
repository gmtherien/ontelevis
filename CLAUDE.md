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
  `:core:player` (Media3), `:core:designsystem` (theme, components, fonts, icons), `:feature:*` (screens).
- Data flow: `PlaylistImporter` streams M3U / Xtream replies (`:core:parser` `ChannelImport`) into
  Room in batches of 500 inside one transaction (a failure keeps the old channels).
  `PlaylistSync` (WorkManager) refreshes daily and on "Update content".
- Guide: `EpgImporter` streams XMLTV (gzip, relaxed XML for raw "&") into `programme`, only for the
  playlist's own epg ids, from 1 day back (or the playlist's longest catch-up, max 7) to 7 days ahead. Link: user field, else Xtream `xmltv.php`,
  else the M3U header (`url-tvg` / `x-tvg-url`). Adding a playlist loads channels, then the guide in
  the background (`EpgRefreshWorker`).
- VOD (`:feature:vod`, Films / Séries in the rail): Xtream movies and series load in the background
  after the channels (`VodImporter`, `ExtrasRefreshWorker` = `EpgRefreshWorker`), episodes per series
  on demand (`get_series_info`). M3U VOD is sorted in the same pass as the channels
  (`M3uClassifier`: `/movie/` `/series/` paths, `S01E02`, video extensions, group words). Ids are
  prefixed per kind (`::m:` `::s:` `::e:`: Xtream movie and episode numbers collide). `VodPlayer`
  is separate from `LivePlayer`, which is held (`hold()` / `release()`) while a movie plays.
  Resume points in `watch_progress` (saved every 10 s and on exit; >= 95 % = finished).
  Search: `searchKey` column (`searchKeyOf`: lower case, no accents) filled on import, LIKE across
  all categories of a kind (enabled playlists, max 500); rows without a key fall back to the name.
  Sort per kind in `VodPrefs` (provider, newest/oldest year, best rated, A-Z/Z-A ignoring "4K: " / "FR| " tags,
  random with a per-screen seed), applied in `VodBrowseViewModel`.
- Catch-up (Xtream only): `tv_archive_duration` -> `channel.catchupDays`; panel time zone from
  `server_info.timezone` (`playlist.serverTimezone`, UTC if missing). Link
  `/timeshift/u/p/{minutes}/{yyyy-MM-dd:HH-mm in panel zone}/{streamId}.ts` (`XtreamUrls.timeshift`).
  The guide grows 6 h into the past when the focus nears its start on a catch-up channel
  (`LiveViewModel.extendIntoPast`, re-anchoring keeps it); past programmes in the archive get a rewind
  mark and OK plays them in `VodPlayerScreen` (`VodPlayerRoute.catchup*`, no resume point). Back lands
  on the replayed programme. BLCK archives have gaps (404 = "no recording", not our link).
- Parental PIN (`ParentalControl`): 4 digits, stored as salted SHA-256 in prefs `parental`, never in
  clear. Locks in `category_lock` (playlistId, kind live/movie/series, name; follows playlist delete).
  `closed` = locked minus opened this session; `MainActivity.onStop` calls `relock()`. Closed
  categories ask for the PIN on OK, show no content, and their channels / titles stay out of
  Favourites, History and search; a playing channel of a closed category stops. Lock/unlock from the
  category hold-OK menu (asks to create a PIN if none). PIN dialogs: `OntVerifyPinDialog` /
  `OntCreatePinDialog` (D-pad keypad + remote number keys).
- Backup (`BackupManager`, readable JSON, owner's choice): playlists with logins, favourites, VOD
  favourites, category order / hidden / locks, PIN hash, sorts, language. App ids start with the
  playlist id, which changes on restore: the file stores the playlist's position + the rest of the id.
  Restore replaces everything in one transaction, then `PlaylistSync.refreshNow`; asks the PIN first
  if one is set. Files (`BackupFiles`): phone = system picker; TV (no picker, Android TV ships only
  `com.android.tv.frameworkpackagestubs`) = Download/Ontelevis via MediaStore (storage permission
  before Android 10). Android 10+ only lists files this install wrote (not after a reinstall).
- Releases (owner chose GitHub Actions signing): tag `vX.Y.Z` -> `release.yml` builds `assembleRelease`
  signed from secrets `ONTELEVIS_KEYSTORE_BASE64/_PASSWORD`, `ONTELEVIS_KEY_ALIAS/_PASSWORD`,
  versionName from the tag, versionCode = 100 + run number, checks with apksigner, publishes the
  single asset `ontelevis.apk` (stable name for `releases/latest/download`). Never add a second APK
  asset. Never put the key or its passwords in the repo. Release certificate (owner's key, kept in
  ~/keystores/ontelevis-release.jks + secrets): SHA-256
  `8e33258116c731a880b83980da857e9fa5e9994cce40b02c3f4066599140449b`; any release APK must show it
  (`apksigner verify --print-certs`). First release: v1.0.0-beta.1 (pre-release, versionCode 101). Release APK ~10 MB (R8), tested on the
  emulator with a public iptv-org M3U (2026-11-08).
- Playback: one `LivePlayer` (`:core:player`, singleton) shared by the preview and full screen, so
  going full screen never reconnects. `RecoveryPolicy` (unit-tested) decides reconnects; `MainActivity`
  suspends it in `onStop` and resumes in `onStart`. The last channel is saved in `PlaybackPrefs`.
- Convention plugins in `build-logic/`: `ontelevis.android.application|library|compose|feature`,
  `ontelevis.hilt`, `ontelevis.jvm.library`. Versions only in `gradle/libs.versions.toml`.
- build-logic compiles against AGP only; other plugins are applied by id (keeps it independent
  of their Kotlin metadata version). AGP 9 built-in Kotlin: no `kotlin-android` plugin.
- TV UI = `androidx.tv:tv-material`; phone UI = Compose Material 3. Feature screens must work in both
  (`LocalIsTv`). TV text input goes through `OntTextField` (gated: read-only until OK).

## Design (docs/design/DESIGN_HANDOFF.md, mockups in docs/design/mockups)
- Claude Design handoff, TV only. Tokens: `OntTheme.colors` / `OntTheme.type`, `OntDimens`, `OntMotion`.
  Mockups are 1920x1080 px = 960x540 dp: divide px by 2.
- Focus = light surface `focusSurface` + dark text + slight scale. Selection = amber. Red = live only.
- Build focusable things from `OntComponents.kt` (or copy their focus treatment). No emoji; provider
  names go through `parseChannelName` (keep the raw name for search).
- Fonts Inter / Inter Tight bundled in `:core:designsystem` res/font (OFL, third_party/fonts).
  Icons: Phosphor regular as vector drawables `ont_ic_*` (MIT, third_party/icons).
- Owner choices that differ from the handoff: category names are shown raw (emoji and prefixes
  kept, provider order); no "Full screen" button (2nd OK on the playing channel, or OK on the
  preview); OK on a category moves focus into the guide; no channel numbers anywhere (guide,
  player, phone list); guide mode: while the guide has focus, rail and categories slide away and
  the info panel follows the focused programme (TiviMate-like).
- Rail shows V1 destinations only (TV, Movies, Series, Update content, Settings); Home, Search,
  Multiview, Sports from the mockup are out of V1.

## Rules
- No hard-coded user-visible text: `strings.xml` in `values/` (EN) and `values-fr/` (FR).
  Lint only checks XML; in Compose always use `stringResource`.
- The app never ships a playlist, channel or default source (Google Play policy).
- Big inputs are streamed: M3U line by line, XMLTV with a pull parser and batched DB inserts.
  Never load a whole playlist or guide in memory.
- Never log URLs that contain credentials (Xtream user/password are in the path).
- `minSdk 24` (current AndroidX floor; Fire OS 6 = API 25). `compileSdk 37`, `targetSdk 36`.
- Media3 stays on the 1.9.x line until Jellyfin ships an FFmpeg decoder for a newer Media3.
- Navigation: never `restoreState` the start destination (TV); use `goTo()` in `TvApp.kt`.
  Restoring it brought the saved Settings stack back on top.
- `usesCleartextTraffic="true"` is required: most IPTV panels and guides are plain http.
- Room migrations are required from version 3 on (`autoMigrations` in `OntelevisDatabase`; new NOT
  NULL columns need `@ColumnInfo(defaultValue = ...)`). Users' playlists must never be wiped. Never
  type the owner's real provider credentials in the app yourself: keep the data across builds instead.
- In a `LaunchedEffect`, never change one of its own keys before its suspend work is done: the
  effect restarts and cancels that work.
- `java.time` needs core library desugaring (on in the convention plugins).
- Hilt "Cannot create an instance of ...ViewModel" = a module's KSP output went missing. KSP2 bug: when
  only a dependency module changes, `kspDebugKotlin` re-runs, generates nothing and wipes its output.
  `HiltConventionPlugin` clears the KSP caches before each run (always a full run). Do not remove it.
- Compose keys: an `onKeyEvent` must come before `focusable()` in the modifier chain, or it never
  receives the keys. A lazy item off screen cannot take focus: `scrollToItem` first.
- Commit only when asked. Never push without asking.
- Commit messages and PR descriptions never mention Claude or Claude Code: no `Co-Authored-By` trailer,
  no "Generated with" line (owner, 2026-11-08).

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
