# Safeer TV 2.1.82 — public distribution

Default home tiles, bookmarks and portal lists contain public services only. No pirate catalog is preselected or advertised. Users can enter their own addresses and manage their own bookmarks; this is not a website blocklist. Existing user bookmarks are retained.

Production assets exclude authentication files, local developer files and screenshots. The public build cannot embed Xplore credentials. Users sign in themselves to services they subscribe to. Developer command broadcasts and diagnostic logging through SafeerDbg are disabled in release builds. Android backup is disabled for browser data. Web pages are not automatically granted camera, microphone or location access; protected-media playback remains supported.

## Packages

- `./build_tv_apk.sh`: compatibility APK, retains the previous TV signing identity for existing installations. This uses the previous debug certificate and is **not** the Google Play upload package.
- `./build_store_packages.sh`: production-signed APK for APK distributors and AAB for Play Console, in `Release/Store/`. Defaults to the existing Safeer publisher production key stored outside this project in the mobile project's ignored keystore directory. Override RELEASE_KEYSTORE, RELEASE_KEY_ALIAS and RELEASE_KEY_PASS or RELEASE_PASS_FILE as needed. Never commit keys or passwords.

The production certificate differs from the old debug-signed TV package. Android will not install it over that old package as an in-place update. Keep the same production/upload key for all future store releases. Do not uninstall an existing browser without first preserving the user's bookmarks and other data.

## Submission information

Name: Safeer TV Browser

Short description: Web browsing for Android TV with remote navigation and bookmarks.

Description: Browse websites on Android TV using your remote. Safeer includes tabs, editable bookmarks, a search/address bar, local ad and threat filters, and video playback for supported websites. No films, channels, subscriptions or third-party accounts are included. Access to external services may require your own account or subscription. Website compatibility and filtering coverage vary.

Before submission, complete the store's privacy/data-safety, content rating and app-access forms based on actual behavior, supply screenshots from public neutral pages, and run the store's device testing. Store acceptance is decided by its reviewer; these packages do not represent approval.

Target API 34 meets the currently published Android TV exception: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en
