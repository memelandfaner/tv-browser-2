# Remote control refresh

Based on b87bc4f (2.1.80), preserving the current player, kiosk dimensions and site navigation contracts.

- Stronger focus outlines on toolbar icons, tab badge and inactive tab cards.
- Native menu rows are focusable, with their child labels grouped into one target.
- Menu opening focuses New Tab; the tab overlay opens with a focused control.
- Back from the tab overlay restores focus to the tab-count button.
- Tab/find overlays receive both DOWN and UP before site handlers, preventing page handlers from swallowing button releases. Back release is consumed after dismissing the overlay.

Validation: Gradle release build, signed APK verification and Media3 ExoPlayer/DashMediaSource dex checks passed. XML parsed successfully. Live remote navigation and clear DASH playback remain untested; TV was playing television and was not interrupted. No GitHub publication. Package version remains 2.1.80 for this local preview.

Device check 2026-09-07: incremental installation on the test television succeeded. Browser home opened; OK opened the menu with rowMenuNewTab focused; D-pad moved to another menu row; the tab overlay opened with btnNewTabInSwitcher focused. Clear DASH broadcast produced no first-frame evidence, so playback is not represented as verified.
