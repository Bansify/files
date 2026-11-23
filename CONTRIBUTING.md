# Contributing to Bansify Files

## Ground rules

1. **One change per pull request.** Unrelated edits make review and rollback harder.
2. **Never commit a keystore or a key.** Signing material lives in GitHub secrets only.
3. **The repository is the source of truth.** A fix that exists only in a CI dashboard or on
   a machine will be lost on the next deploy.
4. **Supplied artwork is copied byte-for-byte.** Icons are never redrawn or resampled.

## Getting set up

```bash
git clone https://github.com/Bansify/files.git
cd files
./gradlew :app:assembleDebug
```

You need JDK 17 and Android SDK 36. See [docs/BUILD.md](docs/BUILD.md).

## Branch and commit

- branch from `main`: `feat/…`, `fix/…`, `docs/…`, `chore/…`
- commit subjects in the imperative: `fix: stop the browser crashing on an empty folder`
- keep the body short and factual — what changed and why

## Before opening a pull request

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest
```

Both workflows (`Android CI`, `Quality checks`) must be green. The documentation gate checks
that the root documents and `docs/` pages still exist.

## Review

- a pull request is squash-merged once CI is green
- release-relevant changes are followed by a version tag, which publishes the APK
- if a change alters behaviour, update the matching page in `docs/`
