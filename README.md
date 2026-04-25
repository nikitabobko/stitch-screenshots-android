# Stitch Screenshots [![Build](https://github.com/nikitabobko/stitch-screenshots-android/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/nikitabobko/stitch-screenshots-android/actions/workflows/build.yml)

Simple Android app for stitching multiple screenshots into a single long screenshot.

## Installation

- Download the APK file directly from [GitHub releases](https://github.com/nikitabobko/stitch-screenshots-android/releases) (no automatic updates)
- [Install via Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/nikitabobko/stitch-screenshots-android) (Obtainium keeps track of updates)

## Screenshots

<img src="./screenshot.jpg" width="300">

## Building

```
# Debug
./gradlew assembleDebug # ./app/build/outputs/apk/debug/app-debug.apk

# Release
keytool -genkey -v -keystore ./release.jks -keyalg RSA -validity 36500 -storepass ******
echo 'storePassword=******' >> ./local.properties
echo 'keyAlias=mykey >> ./local.properties
./gradlew assembleRelease # ./app/build/outputs/apk/release/app-release.apk
./build-release.sh # ./stitch-screenshots-android-v*.apk
```

## License

MIT
