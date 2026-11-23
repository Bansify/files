# Troubleshooting

Symptom → cause → fix.

## The app shows no files

**Cause:** "All files access" has not been granted. On Android 11+ the normal storage
permission is not enough to browse other apps' folders.

**Fix:** Settings → Apps → Bansify Files → Permissions → *All files access* → allow. The app
opens the right screen from its own prompt.

## Folders are visible but empty

**Cause:** scoped storage. A folder may exist while its contents are not readable without
access to that specific volume (for example a secondary SD card or a work profile).

**Fix:** check which volume the folder lives on; work-profile files are not reachable from a
personal-profile app by design.

## Clean suggests deleting something important

**Cause:** the clean flow lists candidates by size and age, not by importance.

**Fix:** always review the list — nothing is deleted until you confirm. If a folder should
never appear, note it and open an issue so it can be excluded.

## Preview does not open for a large file

**Cause:** previews are size-capped on purpose so the app does not allocate hundreds of
megabytes.

**Fix:** open the file in an external viewer from the overflow menu.

## Release build fails in CI with "keystore not found"

**Cause:** `RELEASE_KEYSTORE_BASE64` is missing or not valid base64.

**Fix:** re-encode with `base64 -w0 release.jks` (single line, no wrapping) and update the
secret.

## Release build fails with "signingConfig … password"

**Cause:** `KEYSTORE_PASSWORD` or `KEY_PASSWORD` does not match the keystore.

**Fix:** verify locally with
`keytool -list -keystore release.jks -storepass <password>`.

## Where to look

| Question | Where |
|---|---|
| Did CI fail on build or on tests? | the failing job's log in Actions |
| What did lint complain about? | the `lint-report` artefact |
| Which version is installed? | app → About, or the APK filename |
| Is the download intact? | `sha256sum BansifyFiles-*.apk` against `SHA256SUMS.txt` |
