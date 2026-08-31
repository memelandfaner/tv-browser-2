# 📺 TV Browser 2 (Android TV Remote Control Edition)

**Trenutna različica: 2.1.09** — Xplore TV kiosk, Castlabs/Widevine predvajalnik, D-Pad izbira programov, celozaslonski HydraHD predvajalnik.

## ⬇️ Prenos APK (namestitev na Android / Android TV)

1. Prenesi: **[TV-Browser-2.apk](https://github.com/memelandfaner/tv-browser-2/raw/main/TV-Browser-2.apk)**
2. Na napravi dovoli namestitev iz neznanih virov.
3. Odpri preneseno datoteko in namesti.

Ista datoteka je tudi v `Release/Artifacts/tv-browser-2-release.apk`.

![HydraHD celozaslonski predvajalnik](docs/hydrahd-celozaslonsko.png)

Na hydrahd.ws se film odpre čez cel TV zaslon (skrita orodna vrstica brskalnika in meni strani). Črne obrobe zgoraj/spodaj so filmsko razmerje slike.

---

**TV Browser 2** je napreden, visoko-varen spletni brskalnik za Android TV in pametne televizorje, optimiziran za upravljanje z **daljinskim upravljalnikom (D-Pad Navigation)** ter s vgrajeno kibernetsko zaščito pred Botnet C2 strežniki, zlonamerno kodo (Malware) in oglasnimi mrežami.

---

## 🎮 Značilnosti za Android TV (Daljinski Upravljalnik)
- 🎯 **D-Pad Upravljanje**: Popolna podpora za smerne tipke (GOR, DOL, LEVO, DESNO, OK/ENTER) na TV daljincu.
- 🔍 **Avtomatski Fokus Barve**: Brskalnik samodejno označi in obrobi fokusirane elemente na spletni strani z svetlo modro (Cyan) barvo (`00e5ff`).
- ⚡ **Hitre Tipke na Daljincu**:
  - `GOR` na vrhu strani -> Skok v Iskalnik / URL polje (`editUrl`).
  - `DOL` v URL polju -> Povratek na vsebino spletne strani.
  - `MENI` -> Odpre stranski meni brskalnika.
  - `RDEČA tipka` / `ISKANJE` -> Hitro iskanje.
  - `RUMENA tipka` -> Zaznamki.
  - `KANAL UP/DOWN` ali `PAGE UP/DOWN` -> Hitro pomikanje po strani.
  - `PREDVAJAJ/PAVZA` -> Nadzor video posnetkov na spletu.

---

## 🛑 Kibernetska Zaščita & AdBlock
- **abuse.ch Feodo Tracker, URLhaus & ThreatFox**: Samodejna blokada nevarne C2 botnet in malware infrastrukture.
- **Phishing Army & StevenBlack Hosts**: Zaščita pred lažnim predstavljanjem.
- **SmartTube & Brave Shield Technology**: Preskok oglasov in delovanje zeliščnega predvajanja v ozadju.

---

## 🛠️ Gradnja in Namestitev

### Gradnja APK paketa:
```bash
./build_tv_apk.sh
```

### Namestitev na Android TV prek ADB:
```bash
adb connect 192.168.0.77:5555
adb -s 192.168.0.77:5555 install -r TV-Browser-2.apk
```
