# Release

## Versioning

- `versionName` follows `MAJOR.MINOR.PATCH`
- `versionCode` increments on every published build (CI uses the commit count by default)
- both can be overridden when starting the release workflow manually

## Secrets

Four repository secrets drive signing:

| Secret | Notes |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.jks` output |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias inside the keystore |
| `KEY_PASSWORD` | key password |

The keystore itself is **never** committed. `Quality checks` fails if a `.jks` or `.keystore`
file ever appears in the tree.

If the keystore is lost, Play Store updates become impossible — keep an offline copy in at
least two places.

## Publishing

Either push a tag:

```bash
git tag v1.0.1
git push origin v1.0.1
```

or run **Build Signed Release APK & Publish GitHub Release** from the Actions tab and leave
the version fields empty for an automatic bump.

The workflow publishes:

| File | Use |
|---|---|
| `BansifyFiles-v<version>-Release.apk` | direct install |
| `BansifyFiles-v<version>-PlayStore.aab` | Play Console upload |
| `SHA256SUMS.txt` | integrity check |

## Checklist before tagging

1. `Android CI` and `Quality checks` are green on `main`
2. `CHANGELOG.md` has an entry for the version
3. the version bump matches the size of the change
4. after publishing, install the APK once on a real device

## Download links

Public pages must not hard-code a versioned asset name — those break on the next release.
Link the release page, or an endpoint that resolves the latest asset.
