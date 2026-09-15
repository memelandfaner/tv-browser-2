# -*- coding: utf-8 -*-
"""Zaznavalnika morata prepoznati oba hrosca na pravih posnetkih zaslona.

Vzorci v tests/vzorci/ so resnicni posnetki televizorja: trije pred popravkom
in trije po njem. Ce kdo zaznavalnik omehca toliko, da hrosca ne vidi vec,
ta test pade -- in prav to je njegov namen.
"""
import os
import sys
import unittest

from PIL import Image

KOREN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(KOREN, "tools"))

import zaznaj  # noqa: E402

VZORCI = os.path.join(KOREN, "tests", "vzorci")


def slika(ime):
    return Image.open(os.path.join(VZORCI, ime))


class SiviPlakat(unittest.TestCase):

    def test_plakat_pred_popravkom_je_zaznan(self):
        self.assertTrue(zaznaj.plakat_prisoten(slika("plakat-pred.png")),
                        "plakata ne prepozna vec: " + zaznaj.opis(slika("plakat-pred.png")))

    def test_crn_prehod_po_popravku_ni_plakat(self):
        self.assertFalse(zaznaj.plakat_prisoten(slika("plakat-po.png")),
                         "crn prehod zmotno steje za plakat")

    def test_slika_videa_ni_plakat(self):
        self.assertFalse(zaznaj.plakat_prisoten(slika("video.png")),
                         "navadna slika videa zmotno steje za plakat")


class OznakaFokusa(unittest.TestCase):

    def test_pred_popravkom_stran_ni_bila_zatemnjena(self):
        self.assertFalse(
            zaznaj.stran_zatemnjena(slika("oznaka-povrnjeno.png"), slika("oznaka-pred.png")),
            "stanje pred popravkom zmotno steje za zatemnjeno")

    def test_po_popravku_je_stran_zatemnjena(self):
        self.assertTrue(
            zaznaj.stran_zatemnjena(slika("oznaka-povrnjeno.png"), slika("oznaka-po.png")),
            "zatemnitve ne prepozna vec")

    def test_povrnitev_ni_zatemnitev(self):
        self.assertFalse(
            zaznaj.stran_zatemnjena(slika("oznaka-povrnjeno.png"), slika("oznaka-povrnjeno.png")),
            "ista slika ne sme veljati za zatemnjeno")


if __name__ == "__main__":
    unittest.main()
