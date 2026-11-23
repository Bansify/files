# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Email **security@bansify.com** with:

- what you found and where (screen, endpoint or file)
- steps to reproduce
- the app version and Android version
- what an attacker could do with it

You will get an acknowledgement within **3 working days** and a decision within **10 days**.
Please give us a reasonable window to ship a fix before disclosing publicly.

## Scope

In scope:

- this Android app and its release artefacts
- the update and download path used by the app
- anything that lets data leave the device without the user's knowledge

Out of scope:

- issues that require a rooted device or a malicious app with the same permissions
- findings in third-party libraries that are already publicly known (report upstream)

## Supported versions

The latest release and the one before it receive fixes. Older builds are not patched.

## What we will never do

- ask for your password, keystore or API keys by email
- publish a build that is not produced by this repository's CI
