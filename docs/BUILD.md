# Build

## Requirements

| Tool | Version |
|---|---|
| JDK | 17 |
| Android SDK | API 36 platform, build-tools 36.x |
| Gradle | via the wrapper (`./gradlew`) |
| Kotlin | as declared in `gradle/libs.versions.toml` |

## First build

```bash
git clone https://github.com/Bansify/files.git
cd files
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Useful tasks

| Task | What it does |
|---|---|
| `./gradlew :app:assembleDebug` | debug APK |
| `./gradlew :app:assembleRelease` | release APK (needs signing env vars) |
| `./gradlew :app:bundleRelease` | Play Store bundle |
| `./gradlew :app:lintDebug` | Android lint |
| `./gradlew :app:testDebugUnitTest` | unit tests |
| `./gradlew clean` | clear build outputs |

## Signing environment

The release build reads three variables; CI sets them from secrets:

```bash
export KEYSTORE_PATH=/absolute/path/to/release.jks
export STORE_PASSWORD=…
export KEY_PASSWORD=…
export KEY_ALIAS=upload        # optional, defaults to "upload"
./gradlew :app:assembleRelease
```

Without them a release build fails on purpose — an unsigned "release" is not a release.

## Common errors

| Error | Cause | Fix |
|---|---|---|
| `SDK location not found` | no `local.properties` | add `sdk.dir=/path/to/Android/Sdk` |
| `Unsupported class file major version` | wrong JDK | use JDK 17 |
| `Keystore file not found` | `KEYSTORE_PATH` unset | export it, or let CI handle it |
| `Permission denied` on `gradlew` | lost the exec bit | `chmod +x gradlew` |
