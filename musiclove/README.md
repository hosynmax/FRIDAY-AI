# MUSIC LOVE

Separate native Android music-player module in this repository.

## Platform
- Android 13 (API 33) and newer
- Kotlin, Jetpack Compose, Material 3
- AndroidX Media3 / ExoPlayer / MediaSession

## Features
- Local audio library scan via MediaStore
- Background playback with MediaSessionService and media notification / lock-screen controls
- Favorites, recently played tracks and persistent playlists
- Online search and streaming of MP3 recordings whose Internet Archive metadata declares CC0, CC BY or CC BY-SA
- Shuffle, repeat, seek, volume and sleep timer
- Persian-first dark interface and 3D-inspired animated vinyl player

## Online catalogue limitation
The online search filters for tracks that declare a reusable licence. It cannot provide unauthorized access to commercial catalogues or bypass other services' protections. Availability depends on the Internet Archive catalogue and MP3 encodings.

## Build
`gradle --no-daemon :musiclove:assembleDebug`

APK output: `musiclove/build/outputs/apk/debug/musiclove-debug.apk`

The automated debug APK is for personal testing. A store-ready release requires the owner's private signing key.
