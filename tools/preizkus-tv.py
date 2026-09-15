#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Preizkus na pravem televizorju: oba popravka morata se vedno drzati.

Zaznavalnika sta v tools/zaznaj.py in ju loci preizkus na shranjenih posnetkih
(tests/test_zaznaj.py). Tukaj ju uporabimo na zivi napravi.

  python3 tools/preizkus-tv.py [IP:vrata]

Brez naprave se konca s kodo 0 in jasno pove, da ni preizkusal nicesar -- tako
ga je varno klicati tudi tam, kjer televizorja ni.
"""
from __future__ import annotations

import io
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zaznaj  # noqa: E402

from PIL import Image  # noqa: E402

PAKET = "si.safeer.tv"
PRIVZETA_NAPRAVA = "192.168.0.77:5555"
YOUTUBE = "https://www.youtube.com/tv"


class Naprava:
    def __init__(self, naslov):
        self.naslov = naslov

    def adb(self, *a, timeout=60):
        return subprocess.run(["adb", "-s", self.naslov] + list(a),
                              capture_output=True, text=True, timeout=timeout)

    def ziva(self):
        r = self.adb("get-state", timeout=15)
        return r.returncode == 0 and "device" in r.stdout

    def tipka(self, koda, pavza=0.5):
        self.adb("shell", "input", "keyevent", str(koda))
        time.sleep(pavza)

    def zaslon(self) -> Image.Image:
        # Naprava lahko zaspi sredi preizkusa (lasten casovnik): tedaj bi bili vsi
        # posnetki crni in preizkus bi padel po krivem.
        if self.zaslon_spi():
            self.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            time.sleep(3)
        p = subprocess.run(["adb", "-s", self.naslov, "exec-out", "screencap", "-p"],
                           capture_output=True, timeout=60)
        return Image.open(io.BytesIO(p.stdout))

    def zaslon_spi(self) -> bool:
        r = self.adb("shell", "dumpsys", "power", timeout=30)
        return "mWakefulness=Asleep" in r.stdout or "mWakefulness=Dozing" in r.stdout

    def prebudi(self):
        """Vrne True, ce je bil zaslon prej ugasnjen (in ga je treba na koncu vrniti)."""
        if not self.zaslon_spi():
            return False
        self.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        time.sleep(3)
        return True

    def uspavaj(self):
        self.adb("shell", "input", "keyevent", "KEYCODE_SLEEP")

    def odpri(self, url):
        self.adb("shell", "am", "force-stop", PAKET)
        time.sleep(1)
        self.adb("shell", "am", "start", "-a", "android.intent.action.VIEW",
                 "-d", url, PAKET)


def preizkus_plakat(n: Naprava) -> tuple[bool, str]:
    """Po zagonu videa ne sme biti sive ploskve z gumbom."""
    n.odpri(YOUTUBE)
    time.sleep(18)
    n.tipka("KEYCODE_DPAD_CENTER", pavza=0.2)
    najdeni = []
    for i in range(12):
        s = n.zaslon()
        if zaznaj.plakat_prisoten(s):
            najdeni.append((i, zaznaj.opis(s)))
        time.sleep(0.3)
    if najdeni:
        return False, "sivi plakat v %d kadrih, npr. kader %d: %s" % (
            len(najdeni), najdeni[0][0], najdeni[0][1])
    return True, "v 12 kadrih po zagonu videa ni sivega plakata"


def preizkus_oznaka(n: Naprava) -> tuple[bool, str]:
    """Ko fokus prevzame nasa vrstica, se mora stran zatemniti in spet povrniti."""
    n.odpri(YOUTUBE)
    time.sleep(18)
    n.tipka("KEYCODE_DPAD_LEFT")      # v stransko letev
    n.tipka("20")                     # en korak dol
    time.sleep(0.8)
    svetla = n.zaslon()

    n.tipka("KEYCODE_PROG_RED")       # fokus v nase hitre portale
    time.sleep(1.6)
    temna = n.zaslon()

    n.tipka("20")                     # nazaj v stran
    time.sleep(1.6)
    povrnjena = n.zaslon()

    if not zaznaj.stran_zatemnjena(svetla, temna):
        return False, ("stran se ob prevzemu fokusa ni zatemnila: pred [%s] med [%s]"
                       % (zaznaj.opis(svetla), zaznaj.opis(temna)))
    if zaznaj.stran_zatemnjena(svetla, povrnjena):
        return False, ("zatemnitev je ostala, ko se je fokus vrnil v stran: [%s]"
                       % zaznaj.opis(povrnjena))
    return True, "zatemnitev se vklopi in izklopi ob pravem trenutku"


def main() -> int:
    naslov = sys.argv[1] if len(sys.argv) > 1 else PRIVZETA_NAPRAVA
    n = Naprava(naslov)
    if not n.ziva():
        print("Televizorja %s ni; nic nisem preizkusil." % naslov)
        return 0

    spal = n.prebudi()
    if spal:
        print("Zaslon je spal; prebudil sem ga in ga bom na koncu spet ugasnil.")

    print("Preizkusam na %s\n" % naslov)
    padli = 0
    for ime, preizkus in (("sivi plakat pred videom", preizkus_plakat),
                          ("ena sama oznaka fokusa", preizkus_oznaka)):
        try:
            v_redu, pojasnilo = preizkus(n)
        except Exception as e:  # naprava se lahko odklopi sredi preizkusa
            v_redu, pojasnilo = False, "preizkus se ni izsel: %s" % e
        print("  %-26s %s" % (ime, "V REDU" if v_redu else "PADLO"))
        print("      %s" % pojasnilo)
        padli += 0 if v_redu else 1

    if spal:
        n.uspavaj()

    print()
    if padli:
        print("PADLO: %d od 2. Popravek, ki smo ga ze naredili, se je vrnil." % padli)
        return 1
    print("Oba popravka se drzita.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
