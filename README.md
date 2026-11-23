# Bansify Files

The file manager for the Bansify ecosystem — browse, clean and organise storage on Android,
signed in with the same Bansify Account as Mail, Drive and the rest.

<p align="center">
  <img src="https://bansify.com/assets/brand/logo.png" alt="Bansify" width="72" />
</p>

| | |
|---|---|
| **Package** | `com.bansify.files` |
| **Platform** | Android 7.0+ (`minSdk 24`), `targetSdk 36` |
| **UI** | Jetpack Compose, Material 3 |
| **Language** | Kotlin |
| **Download** | [Latest release](https://github.com/Bansify/files/releases/latest) |
| **Web** | [bansify.com](https://bansify.com) |

## What it does

- **Browse** — storage overview, folders, recent files, and a full directory browser
- **Clean** — find large files, duplicates and junk, then clear them in a couple of taps
- **Tools** — the everyday utilities that belong next to a file manager
- **Collections** — quick entry points for the file groups people actually look for
- **Preview** — open images, text and media without leaving the app

## Install

Grab the signed APK from the [releases page](https://github.com/Bansify/files/releases/latest):

```
BansifyFiles-v<version>-Release.apk
```

Every release ships `SHA256SUMS.txt`; verify before installing. The Play Store build is
published as `BansifyFiles-v<version>-PlayStore.aab`.

## Build from source

```bash
git clone https://github.com/Bansify/files.git
cd files
./gradlew :app:assembleDebug
```

Requirements: JDK 17 and the Android SDK (API 36). Full instructions in
[docs/BUILD.md](docs/BUILD.md).

## Project layout

```
app/src/main/java/com/example/
├── MainActivity.kt            entry point, navigation host
├── model/                     file and storage models
└── ui/
    ├── components/            shared file widgets
    ├── screens/               Browse, Clean, Tools, DirectoryBrowser, Collections
    └── theme/                 colour, type and theme tokens
```

More detail — including how storage access works and where each screen lives — is in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Continuous integration

| Workflow | Trigger | Does |
|---|---|---|
| `Android CI` | push / PR to `main` | builds the debug APK and uploads it |
| `Quality checks` | push / PR to `main` | lint, unit tests, and a docs/hygiene gate |
| `Build Signed Release APK` | tag `v*` or manual | signs, publishes the APK + AAB + checksums |

## Release signing

CI signs with four repository secrets:

| Secret | Purpose |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the `.jks`, base64-encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias inside the keystore |
| `KEY_PASSWORD` | key password |

No keystore is ever committed; `Quality checks` fails the build if one appears. Details in
[docs/RELEASE.md](docs/RELEASE.md).

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | modules, screens, storage access, data flow |
| [docs/BUILD.md](docs/BUILD.md) | local setup, SDK versions, common build errors |
| [docs/RELEASE.md](docs/RELEASE.md) | signing, versioning, publishing |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | symptom → cause → fix |
| [CONTRIBUTING.md](CONTRIBUTING.md) | branch, commit and review rules |
| [SECURITY.md](SECURITY.md) | how to report a vulnerability |
| [CHANGELOG.md](CHANGELOG.md) | release history |

## Licence

MIT — see [LICENSE](LICENSE). "Bansify" and the Bansify logo are trademarks of Bansify;
do not use them to endorse a derived product.
