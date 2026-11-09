# Ontelevis

Free, open-source IPTV player for **Android TV, Google TV, Fire TV and Android phones**.
No ads, no tracking, no subscription.

> Ontelevis is only a player. It ships **no channels, no playlists and no content**.
> You bring your own source (an M3U link or an Xtream Codes account from your provider).

*Lecteur IPTV libre et gratuit pour Android TV, Google TV, Fire TV et téléphones Android.
Aucun contenu n'est fourni : vous ajoutez votre propre liste de lecture.*

## Install

Ontelevis is not on the Play Store or the Amazon Appstore yet: you install the APK yourself.
On a TV, the easiest way is the free **Downloader** app by AFTVnews.

1. Install **Downloader** (by AFTVnews) from the Play Store (Android TV / Google TV) or the
   Amazon Appstore (Fire TV).
2. Allow Downloader to install apps:
   - Fire TV: Settings › My Fire TV › Developer options › Install unknown apps › Downloader: On.
   - Android TV / Google TV: Settings › Apps › Security & restrictions › Unknown sources ›
     Downloader: On (on some models: Settings › Privacy › Security › Install unknown apps).
3. Open Downloader, type the code **`6258556`** in the address box and press Go
   (or enter `aftv.news/6258556`).
4. When the download ends, choose **Install**, then **Open**.

Other ways:

- Direct link to the newest stable APK (phones, browsers, any other downloader app):
  `https://github.com/gmtherien/ontelevis/releases/latest/download/ontelevis.apk`
- All versions, including betas: the [Releases](https://github.com/gmtherien/ontelevis/releases) page.

Once installed, Ontelevis checks for updates by itself at each start, so you only do this once.

**"Unknown developer" or "unsafe app" warning:** Google Play Protect shows it for apps installed
outside the Play Store whose developer is not yet registered with Google. It is not a sign of a
problem in the app; choose **Install anyway** (sometimes under **More details**). Every release is
built from this repository by GitHub Actions and signed with the same key (certificate SHA-256
`8e33258116c731a880b83980da857e9fa5e9994cce40b02c3f4066599140449b`).

### Installation (français)

1. Installez **Downloader** (par AFTVnews) depuis le Play Store (Android TV / Google TV) ou
   l'Amazon Appstore (Fire TV).
2. Autorisez Downloader à installer des applications (Fire TV : Paramètres › Ma Fire TV ›
   Options pour les développeurs › Installer des applications inconnues ; Android TV / Google TV :
   Paramètres › Applications › Sécurité et restrictions › Sources inconnues).
3. Dans Downloader, tapez le code **`6258556`** (ou `aftv.news/6258556`), puis Go.
4. À la fin du téléchargement, choisissez **Installer**, puis **Ouvrir**.

Lien direct vers le dernier APK stable :
`https://github.com/gmtherien/ontelevis/releases/latest/download/ontelevis.apk`.
Si la télé avertit que le développeur est inconnu, c'est Play Protect qui réagit à une app installée
hors du Play Store : choisissez **Installer quand même**.

## Status

Version 1.0.0, with: M3U and Xtream playlists, XMLTV grid guide, movies and
series (search, sort, resume), favourites and history, category order / hide / lock, channel
zapping, Xtream catch-up, parental PIN, backup and restore, English and French.

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
| `:feature:live`, `:feature:vod`, `:feature:settings` | Screens |

Shared Gradle configuration lives in `build-logic/` (convention plugins); versions in
`gradle/libs.versions.toml`.

## Releases

Pushing a tag `v1.2.3` (or `v1.2.3-beta.1` for a pre-release) runs `.github/workflows/release.yml`:
tests, signed release build (R8), signature check, and a GitHub release with `ontelevis.apk`.
Android app id: `com.ontelevis.app` (1.0.0-beta.1 to beta.5 used `app.ontelevis`: uninstall that one
after installing a newer version). The newest stable APK is always at
`https://github.com/gmtherien/ontelevis/releases/latest/download/ontelevis.apk`.

Installed release builds check GitHub at each start and offer the newest release (stable only,
unless "Receive beta versions" is on in Settings); they download it, check it is signed with the
same key, and open the system installer. Development builds never update.

The signing key is never in the repository. One-time setup by the maintainer:

1. Create the key once, outside the project, and keep copies (password manager + external drive):
   losing it means existing installs can never be updated.
   ```bash
   keytool -genkeypair -v -keystore ~/keystores/ontelevis-release.jks -alias ontelevis \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add four repository secrets (Settings › Secrets and variables › Actions):
   `ONTELEVIS_KEYSTORE_BASE64` (output of `base64 -i ~/keystores/ontelevis-release.jks`),
   `ONTELEVIS_KEYSTORE_PASSWORD`, `ONTELEVIS_KEY_ALIAS` (`ontelevis`), `ONTELEVIS_KEY_PASSWORD`.
   With `gh`: `base64 -i ~/keystores/ontelevis-release.jks | gh secret set ONTELEVIS_KEYSTORE_BASE64`,
   then `gh secret set ONTELEVIS_KEYSTORE_PASSWORD` (it asks for the value), and so on.
3. Release: `git tag v1.0.0 && git push origin v1.0.0`.

Local release builds use the same environment variables (`ONTELEVIS_KEYSTORE_FILE` = path of the
`.jks`); without them `assembleRelease` gives an unsigned APK.

## Contributing

Translations and bug reports are welcome. Every user-visible string goes in `strings.xml`
(English in `values/`, French in `values-fr/`).

## License

[GPL-3.0](LICENSE). Ontelevis uses the [Jellyfin Media3 FFmpeg decoder](https://github.com/jellyfin/jellyfin-androidx-media),
which is also GPL-3.0.

Third-party assets: [Inter and Inter Tight](https://github.com/rsms/inter) fonts (SIL Open Font License 1.1,
`third_party/fonts`) and [Phosphor](https://phosphoricons.com) icons (MIT, `third_party/icons`).
