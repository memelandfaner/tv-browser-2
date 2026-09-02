# TV Browser 2 (Android TV)

**Trenutna različica: 2.1.70** — Xplore TV kiosk z nativnim Media3 ExoPlayerjem (DASH + Widevine), HydraHD D-Pad izbira polj, celozaslonski predvajalnik.

## Prenos in namestitev APK

| Kaj | Povezava |
|---|---|
| GitHub | https://github.com/memelandfaner/tv-browser-2 |
| APK (neposredno) | https://github.com/memelandfaner/tv-browser-2/raw/main/TV-Browser-2.apk |
| Kratka povezava (APK) | https://tinyurl.com/27w3uxob |
| Kratka povezava (da.gd) | https://da.gd/8fziT |
| 1-vrstica (PC → TV prek ADB) | `curl -sL https://raw.githubusercontent.com/memelandfaner/tv-browser-2/main/install_tv_browser.sh \| bash` |

Na TV dovoli namestitev iz neznanih virov, nato odpri preneseni `TV-Browser-2.apk`.

Ista datoteka je tudi v `Release/Artifacts/tv-browser-2-release.apk`.

Paket: `com.example.safeerbrowser`. Gradnja: `./build_tv_apk.sh`.

```bash
adb connect 192.168.0.77:5555
adb -s 192.168.0.77:5555 install -r TV-Browser-2.apk
```

## HydraHD (daljinec)

Na `hydrahd.ws` D-Pad **ne** uporablja Chromiumove izvorne prostorske navigacije. Polja so `button.slidebtn` (Watch Now), `div.tab` (Movies/Series) in `a.hthis` (plakati z `height:0`). JS v `assets/tv_spatial.js` označi samo kartice, ne logotipa, logina ali puščic carousela.

- Začetna stran: Watch Now → Movies → prvi plakat → levo/desno po vrsti.
- Film / serija: Predvajaj, nato sezone in epizode.
- Zeleni gumb na daljincu vklopi kazalec, če stran nima pravih polj.

Orodna vrstica brskalnika je skrita samo na predvajalniku (`/movie/`, `/tv/`, `/watch`).

## Xplore TV

Katalog, prijava in EPG ostaneta v WebView. Video je **AndroidX Media3** na SurfaceView, ne Castlabs Android SDK. Podrobnosti: skill `tv-browser-2-xplore-drm`.

Xplore gesel **ne** committaj. Lokalno: `xplore_auth.local.js` (glej `xplore_auth.local.js.example`).

## Daljinec

- D-Pad: prostorska izbira polj (cyan obroč)
- GOR na vrhu strani → URL vrstica (razen Xplore kiosk / Hydra predvajalnik)
- RDEČA / MENI → portali
- ZELENA → kazalec
- RUMENA → zaznamki
- BACK med Xplore predvajanjem zapre Exo, ne `history.back()`
