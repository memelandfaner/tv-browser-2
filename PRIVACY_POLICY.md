# Safeer TV Browser — Privacy

Updated: 8 September 2026

Safeer is a general-purpose browser. It does not include a user account, media subscription, private credentials or a developer's browsing data.

Bookmarks, history, browser settings, website cookies and website storage are stored on the device. Websites receive requests, your IP address and any information you submit to them. Your selected search service receives your searches. Browser data is excluded from Android backup in this release.

The browser downloads filter lists from external security-list providers; those providers receive the normal network information required for that download. Local filtering cannot identify every threat or advertisement. Safeer is not a VPN and does not promise anonymity.

Downloads go to the device's Downloads area through Android DownloadManager. On Android 9, saving public downloads may require storage permission. Microphone permission is used for user-initiated voice features where supported; web pages are not automatically granted microphone, camera or location access. Protected-media identifiers may be used by DRM-enabled playback requested by the user. External providers control their own accounts, cookies and privacy policies.

No analytics SDK is included. Release builds disable the developer command receiver and SafeerDbg diagnostic logging. The project does not operate a browser-history collection service.

Privacy questions and issue reports: https://github.com/memelandfaner/tv-browser-2/issues
