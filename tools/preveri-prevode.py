#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Preveri, da noben jezik ne zaostaja za privzetim.

Isto vrzel smo v enem dnevu nasli trikrat rocno: v brskalniku za televizor,
v brskalniku za telefon in v namiznem. Vsakic je tujec dobil prazno polje ali
slovenscino. Ta skripta to ujame v eni sekundi in brez cloveka.

Zna tri oblike, ki jih uporabljamo:
  - Android:  res/values*/  (strings.xml, ui_strings.xml ...)
  - Safeer Link: assets/link/link.js  (var BESEDILA = { sl: {...}, en: {...} })
  - namizni:  core/i18n.py  (TRANSLATIONS = { "en": {...} })

Zazene se brez odvisnosti. Vrne 0, ce je vse pokrito, sicer 1 in izpise,
kateri kljuci manjkajo kje.

  python3 tools/preveri-prevode.py [pot-do-repozitorija]
"""
from __future__ import annotations

import ast
import os
import re
import sys

# Nizi, ki jih Android ne prevaja (lastna imena, oznake), so lahko samo v privzetem.
DOVOLJENO_SAMO_PRIVZETO = {"app_name"}


def zberi_android(koren: str) -> dict[str, dict[str, set[str]]]:
    """{ime datoteke: {jezik: {kljuci}}} za vsako mapo values*."""
    res = None
    for kandidat in (os.path.join(koren, "res"), os.path.join(koren, "src", "main", "res")):
        if os.path.isdir(kandidat):
            res = kandidat
            break
    if res is None:
        return {}
    najdeno: dict[str, dict[str, set[str]]] = {}
    for mapa in sorted(os.listdir(res)):
        if not mapa.startswith("values"):
            continue
        jezik = "privzeto" if mapa == "values" else mapa[len("values-"):]
        polna = os.path.join(res, mapa)
        if not os.path.isdir(polna):
            continue
        for dat in sorted(os.listdir(polna)):
            if not dat.endswith(".xml"):
                continue
            with open(os.path.join(polna, dat), encoding="utf-8") as f:
                vsebina = f.read()
            kljuci = set(re.findall(r'<string\s+name="([^"]+)"', vsebina))
            if kljuci:
                najdeno.setdefault(dat, {})[jezik] = kljuci
    return najdeno


def zberi_linkjs(koren: str) -> dict[str, set[str]]:
    pot = os.path.join(koren, "assets", "link", "link.js")
    if not os.path.isfile(pot):
        return {}
    with open(pot, encoding="utf-8") as f:
        vsebina = f.read()
    if "var BESEDILA" not in vsebina:
        return {}
    zacetek = vsebina.index("var BESEDILA")
    odsek = vsebina[zacetek:]
    konec = odsek.index("\n  };")
    odsek = odsek[:konec]
    jeziki: dict[str, set[str]] = {}
    for ujem in re.finditer(r"^    (\w+):\s*\{", odsek, re.M):
        jezik = ujem.group(1)
        od = ujem.end()
        do = odsek.index("\n    }", od)
        jeziki[jezik] = set(re.findall(r"^\s{6}(\w+):", odsek[od:do], re.M))
    return jeziki


def zberi_i18n_py(koren: str) -> dict[str, set[str]]:
    pot = os.path.join(koren, "core", "i18n.py")
    if not os.path.isfile(pot):
        return {}
    with open(pot, encoding="utf-8") as f:
        drevo = ast.parse(f.read())
    for vozlisce in drevo.body:
        if not isinstance(vozlisce, ast.Assign):
            continue
        imena = [t.id for t in vozlisce.targets if isinstance(t, ast.Name)]
        if "TRANSLATIONS" not in imena or not isinstance(vozlisce.value, ast.Dict):
            continue
        jeziki: dict[str, set[str]] = {}
        for kljuc, vrednost in zip(vozlisce.value.keys, vozlisce.value.values):
            if isinstance(kljuc, ast.Constant) and isinstance(vrednost, ast.Dict):
                jeziki[str(kljuc.value)] = {
                    str(k.value) for k in vrednost.keys if isinstance(k, ast.Constant)
                }
        return jeziki
    return {}


def primerjaj(naslov: str, jeziki: dict[str, set[str]], osnovni: str) -> list[str]:
    """Vrne seznam napak; prazen seznam pomeni, da je vse pokrito."""
    napake = []
    if osnovni not in jeziki:
        return napake
    pricakovano = jeziki[osnovni] - DOVOLJENO_SAMO_PRIVZETO
    print("  %s (osnova: %s, %d kljucev)" % (naslov, osnovni, len(pricakovano)))
    for jezik in sorted(jeziki):
        if jezik == osnovni:
            continue
        manjka = sorted(pricakovano - jeziki[jezik])
        odvec = sorted(jeziki[jezik] - jeziki[osnovni] - DOVOLJENO_SAMO_PRIVZETO)
        stanje = "v redu"
        if manjka:
            stanje = "MANJKA %d: %s" % (len(manjka), ", ".join(manjka[:6]) +
                                        (" ..." if len(manjka) > 6 else ""))
            napake.append("%s / %s: manjka %d kljucev" % (naslov, jezik, len(manjka)))
        elif odvec:
            stanje = "odvec %d: %s" % (len(odvec), ", ".join(odvec[:4]))
        print("     %-10s %4d  %s" % (jezik, len(jeziki[jezik]), stanje))
    return napake


def main(koren: str) -> int:
    koren = os.path.abspath(koren)
    print("Preverjam prevode v %s\n" % koren)
    napake: list[str] = []
    nasel_kaj = False

    android = zberi_android(koren)
    if android:
        nasel_kaj = True
        print("Android viri:")
        for dat, jeziki in sorted(android.items()):
            napake += primerjaj(dat, jeziki, "privzeto")
        print()

    link = zberi_linkjs(koren)
    if link:
        nasel_kaj = True
        print("Safeer Link (assets/link/link.js):")
        napake += primerjaj("link.js", link, "sl" if "sl" in link else "en")
        print()

    namizni = zberi_i18n_py(koren)
    if namizni:
        nasel_kaj = True
        print("Namizni brskalnik (core/i18n.py):")
        napake += primerjaj("i18n.py", namizni, "en")
        print()

    if not nasel_kaj:
        print("V tem repozitoriju ni prevodov, ki bi jih znal preveriti.")
        return 0

    if napake:
        print("NAPAKA: nepopolni prevodi")
        for v in napake:
            print("   - %s" % v)
        print("\nTujec bi na teh mestih videl prazno polje ali slovenscino.")
        return 1

    print("V redu: vsak jezik ima vse kljuce.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "."))
