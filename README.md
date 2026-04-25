# Stitch Screenshots [![Build](https://github.com/nikitabobko/stitch-screenshots-android/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/nikitabobko/stitch-screenshots-android/actions/workflows/build.yml)

Simple Android app for stitching multiple screenshots into a single long screenshot.

<img src="./screenshot.jpg" width="300">

## Installation

- Download APK file from GitHub releases https://github.com/nikitabobko/stitch-screenshots-android/releases

## Building

```
# Debug
./gradlew assembleDebug # ./app/build/intermediates/apk/debug/app-debug.apk

# Release
keytool -genkey -v -keystore release.jks -keyalg RSA -validity 36500 -storepass ******
echo 'storePassword=******' >> ./local.properties
echo 'keyAlias=mykey >> ./local.properties
./gradlew assembleRelease # ./app/build/outputs/apk/release/app-release.apk
./build-release.sh # ./stitch-screenshots-android-v*.apk
```

## License

MIT
