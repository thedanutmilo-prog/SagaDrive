# SagaDrive AA (MVP, stages 1-2 + Spotify launcher)

**Status: source only. Never compiled or run.** The build sandbox had no network, so no Gradle sync, APK or test run was possible. Expect to fix a few compile errors on first sync. Not tested on a Proton Saga MC2, ZLink or any real head unit.

## Hardware verification checklist
1. Phone model, Android version (min supported: 8.0 / API 26), Google Play services present.
2. Android Auto app installed and up to date on the phone.
3. Does ZLink run real Android Auto? Many aftermarket units emulate it. Unconfirmed.
4. Does ZLink accept sideloaded media apps? (see Discovery below)
5. Head-unit Android version, screen resolution, wired vs wireless link.

## Build & install
1. Open this folder in Android Studio (current stable). Let it sync; accept the Gradle 8.13 download.
2. Versions in `gradle/libs.versions.toml` are conservative pins; upgrade after the first successful build.
3. Run on a device (USB debugging) or `./gradlew assembleDebug` after Studio generates the wrapper (or run `gradle wrapper` once).
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.
5. Unit tests: `./gradlew testDebugUnitTest` (LRC parser, lyrics sync, Spotify link validation).

## Testing with Android Auto
- **DHU first:** install "Android Auto Desktop Head Unit Emulator" via SDK Manager, enable Android Auto developer mode (tap Version 10 times), start head unit server, then `adb forward tcp:5277 tcp:5277` and run `desktop-head-unit`.
- **Discovery:** Android Auto > Settings > Developer settings > enable **Unknown sources** (needed for sideloaded APKs). Check the manifest has `automotive_app_desc.xml` with `<uses name="media"/>` and the service exposes `androidx.media3.session.MediaLibraryService` + `android.media.browse.MediaBrowserService`. Grant music permission in the phone app first, otherwise browsing returns a permission error.
- **ZLink:** try only after DHU works. Whether ZLink exposes sideloaded apps is unknown.

## Implemented (untested until you build)
- Media3 `MediaLibraryService` with browse tree (Songs / Albums / Artists), play/pause/skip, metadata, notification, audio focus, noisy-audio handling
- MediaStore library; only the audio permission is requested
- Phone dashboard: now playing, controls, local song list, Spotify card, Maps/Waze launchers, dark navy theme, landscape layout
- Spotify: detect, open, Play Store fallback, validated link opening (`SpotifyLauncher`)
- Navigation intents (`NavigationLauncher`); LRC parser + position-based sync logic (no UI yet)

## Not yet built
Search, playlists/queue UI, recent tracks, playback-state restore, Room/DataStore, lyrics screen and file import, voice control, Home/Work destinations, YouTube/browser launchers, Passenger mode, settings/diagnostics, Malay strings, voice-command tests.

## Spotify: what it does and does not do
- Spotify runs as its own Android Auto media app. Browse its catalogue and playlists on the car screen via Android Auto's app list. SagaDrive cannot switch Android Auto's displayed app for you.
- SagaDrive only detects Spotify and launches it. No login, no tokens, no passwords, no audio access.
- "Open Spotify" pauses SagaDrive first. Both apps use normal audio focus; when Spotify starts, SagaDrive's player pauses and stays paused.
- The media-key buttons go to whichever app holds media-button focus (usually the last active player), not specifically Spotify.
- **Not implemented, by design:** Spotify account integration. Official options are Spotify's Android App Remote SDK (the .aar is manually downloaded, needs a registered client ID, and playback control needs Premium) and the Web API (OAuth with PKCE, Premium for playback control, and per reports I found, development-mode apps are limited to a handful of users since 2025-2026). Verify current terms at developer.spotify.com before building on them. Neither lets SagaDrive browse Spotify's catalogue on the car display.

## Other limits
- Android Auto shows only its own templated media UI. Synced lyrics, browser and video cannot be drawn on it; lyrics will be phone-side (parked use).
- Album art in Android Auto browse lists may be blank: MediaStore `content://` artwork may not be readable by the Android Auto process. A small ContentProvider would fix it (todo).
- Selecting one song from Android Auto queues only that song.
- Voice: the media session supports Assistant's standard media commands; custom phrases are not guaranteed.

## Troubleshooting
- App not listed in Android Auto: Unknown sources off, DHU/phone Android Auto outdated, or restart Android Auto after install.
- Empty browse list: music permission not granted, or no music indexed by MediaStore.
- Spotify button disabled/"not installed": Spotify missing, or work-profile/other user.
- Playback stops after switching apps: another app took audio focus permanently; resume manually.

## Building without a PC (GitHub Actions)
Upload this project to a free GitHub repository (keep the `.github` folder). Open the repo's **Actions** tab, run **Build APK**, then download `SagaDriveAA-debug-apk` from the finished run and unzip it to get `app-debug.apk`. If the build fails, open the failed step and send me the red error text. Not yet run.
