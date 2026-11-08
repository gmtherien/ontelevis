# Ontelevis

Free, open-source IPTV player for **Android TV, Google TV, Fire TV and Android phones**.
No ads, no tracking, no subscription.

> Ontelevis is only a player. It ships **no channels, no playlists and no content**.
> You bring your own source (an M3U link or an Xtream Codes account from your provider).

*Lecteur IPTV libre et gratuit pour Android TV, Google TV, Fire TV et téléphones Android.
Aucun contenu n'est fourni : vous ajoutez votre propre liste de lecture.*

## Status

Early development. The project skeleton builds and starts; features are being built for V1:
M3U and Xtream playlists, XMLTV guide, channel list with now/next, grid guide, favourites,
channel zapping, Xtream catch-up, parental PIN, backup/restore, English and French.

## Build

Requirements: JDK 17 and the Android SDK (platform 37).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # unit tests
./gradlew spotlessCheck lintDebug
```

## Architecture

Kotlin, Jetpack Compose (Compose for TV on TV, Material 3 on phones), Media3/ExoPlayer with the
Jellyfin FFmpeg audio decoder, Room, Hilt, WorkManager. Multi-module Gradle build:

| Module | Role |
| --- | --- |
| `:app` | Activity, navigation, TV vs phone entry |
| `:core:model` | Plain Kotlin data types |
| `:core:parser` | M3U, Xtream Codes and XMLTV readers (pure JVM, streaming) |
| `:core:data` | Room database and repositories |
| `:core:player` | Media3 player setup |
| `:core:designsystem` | Themes and shared UI |
| `:feature:live`, `:feature:guide`, `:feature:settings` | Screens |

Shared Gradle configuration lives in `build-logic/` (convention plugins); versions in
`gradle/libs.versions.toml`.

## Contributing

Translations and bug reports are welcome. Every user-visible string goes in `strings.xml`
(English in `values/`, French in `values-fr/`).

## License

[GPL-3.0](LICENSE). Ontelevis uses the [Jellyfin Media3 FFmpeg decoder](https://github.com/jellyfin/jellyfin-androidx-media),
which is also GPL-3.0.
