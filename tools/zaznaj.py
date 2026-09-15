# -*- coding: utf-8 -*-
"""Zaznavalnika dveh napak, ki smo ju popravili rocno in ju ne smemo dobiti nazaj.

Obe pravili nista ugibanje: mejne vrednosti so izmerjene na pravih posnetkih
zaslona televizorja pred popravkom in po njem (glej tests/vzorci/).

  plakat pred videom  -- Android je pred zacetkom predvajanja sam narisal sivo
                         ploskev z ogromnim gumbom. Podpis: skoraj vsi piksli so
                         sivi (R=G=B), slika ima le nekaj barv, svetlost okoli 100.
                         Po popravku je prehod crn: ena sama barva, svetlost 0.

  dve oznaki fokusa   -- ko fokus prevzame nasa orodna vrstica, se mora stran
                         zatemniti in YouTubova bela tablica izginiti. Podpis:
                         svetlost strani pade vsaj 2,5-krat, stevilo barv mocno
                         upade.

Modul namenoma nima odvisnosti razen Pillow in ne ve nicesar o adb -- zato ga je
mogoce preizkusiti na shranjenih posnetkih, brez naprave.
"""
from __future__ import annotations

from PIL import Image

# Zgornji pas je nasa orodna vrstica; zanima nas stran pod njo.
VRH_STRANI = 0.13
VZOREC = (160, 90)


def _piksli(slika: Image.Image, vrh: float = None, dno: float = 1.0):
    w, h = slika.size
    vrh = VRH_STRANI if vrh is None else vrh
    stran = slika.convert("RGB").crop((0, int(h * vrh), w, int(h * dno))).resize(VZOREC)
    bajti = stran.tobytes()
    return [tuple(bajti[i:i + 3]) for i in range(0, len(bajti), 3)]


def izmeri(slika: Image.Image, vrh: float = None, dno: float = 1.0) -> dict:
    """Stiri stevilke, ki opisejo stran pod orodno vrstico."""
    px = _piksli(slika, vrh, dno)
    n = len(px)
    return {
        "svetlost": sum(sum(p) for p in px) / (3 * n),
        "nasicenost": sum(max(p) - min(p) for p in px) / n,
        "delez_sivih": sum(1 for p in px if max(p) - min(p) <= 8) / n,
        "barv": len({(r // 24, g // 24, b // 24) for r, g, b in px}),
    }


def plakat_prisoten(slika: Image.Image, vrh: float = None, dno: float = 1.0) -> bool:
    """Ali je na zaslonu privzeti sivi plakat Androida?

    Izmerjeno: plakat 98,4 / sivi 1,00 / 6 barv. Crn prehod po popravku
    0,0 / 1,00 / 1 barva. Prava slika videa 50,8 / 0,63 / 403 barve.
    """
    m = izmeri(slika, vrh, dno)
    return (m["delez_sivih"] >= 0.98
            and 60 <= m["svetlost"] <= 180
            and m["barv"] <= 12)


def stran_zatemnjena(svetla: Image.Image, temna: Image.Image) -> bool:
    """Ali je druga slika ista stran, le zatemnjena (fokus je odsel v vrstico)?

    Izmerjeno: nezatemnjeno 59,6 -> zatemnjeno 13,7 (4,3-krat).
    Meja 2,5-krat pusti dovolj prostora za razlicno svetle strani.
    """
    a = izmeri(svetla)["svetlost"]
    b = izmeri(temna)["svetlost"]
    return a > 5 and b * 2.5 <= a


def opis(slika: Image.Image, vrh: float = None, dno: float = 1.0) -> str:
    m = izmeri(slika, vrh, dno)
    return ("svetlost %.1f, nasicenost %.1f, delez sivih %.2f, barv %d"
            % (m["svetlost"], m["nasicenost"], m["delez_sivih"], m["barv"]))
