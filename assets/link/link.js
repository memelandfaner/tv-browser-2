/**
 * Safeer Link — vgrajen zaslon brskalnika.
 *
 * Oblika je namenoma brez zavihkov: en sam zaslon, ki ga uporabnik prelista.
 * Zgoraj je dejanje (poslji to stran), pod njim stanje (naprave), na dnu nastavitev
 * (sinhronizacija). Kdor odpre Link, najveckrat nekaj posilja -- to naj bo prvo.
 *
 * Stran sama nikoli ne govori z omrezjem in nikoli ne vidi zetona: za vse prosi most
 * (window.SafeerLink), ki ga aplikacija pripne samo temu pogledu. Odgovori pridejo
 * nazaj v window.safeerLinkOdziv, ker most ne sme cakati na omrezje.
 */
(function () {
  "use strict";

  var most = window.SafeerLink || null;

  var el = function (id) { return document.getElementById(id); };

  var stanje = {
    znan: false,
    seznanjen: false,
    povezan: false,
    televizor: false,
    hub: "",
    imeNaprave: "",
    naprave: [],
    prejemnik: null,
    predvajanje: null
  };

  // ----------------------------------------------------------------
  // Pomozno
  // ----------------------------------------------------------------

  function besedilo(id, vsebina) {
    var e = el(id);
    if (e) e.textContent = vsebina;
  }

  function pokazi(id, ali) {
    var e = el(id);
    if (e) e.hidden = !ali;
  }

  // Ena preimenovana oznaka v HTML ne sme podreti celotne inicializacije zaslona.
  function naKlik(id, funkcija) {
    var e = el(id);
    if (e) e.addEventListener("click", funkcija);
  }

  function cas(sekunde) {
    if (!isFinite(sekunde) || sekunde < 0) return "0:00";
    var s = Math.floor(sekunde % 60);
    var m = Math.floor(sekunde / 60);
    return m + ":" + (s < 10 ? "0" : "") + s;
  }

  function vrstica(ikonaZnak, ime, pod, znackaBesedilo, zivo, obKliku) {
    var li = document.createElement("li");
    if (obKliku) {
      li.className = "klikljiv";
      li.tabIndex = 0;
    }

    var ikona = document.createElement("span");
    ikona.className = "ikona";
    ikona.textContent = ikonaZnak;

    var telo = document.createElement("div");
    telo.className = "telo";
    var i = document.createElement("div");
    i.className = "ime";
    i.textContent = ime;
    var p = document.createElement("div");
    p.className = "pod";
    p.textContent = pod;
    telo.appendChild(i);
    telo.appendChild(p);

    var z = document.createElement("span");
    z.className = zivo ? "znacka zivo" : "znacka";
    z.textContent = znackaBesedilo;

    li.appendChild(ikona);
    li.appendChild(telo);
    li.appendChild(z);

    if (obKliku) {
      li.addEventListener("click", obKliku);
      li.addEventListener("keydown", function (e) {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); obKliku(); }
      });
    }
    return li;
  }

  // ----------------------------------------------------------------
  // Izris
  // ----------------------------------------------------------------

  function narisiZaslon() {
    var brezHuba = !stanje.znan;
    var caka = stanje.znan && !stanje.seznanjen;
    pokazi("zaslonBrezHuba", brezHuba);
    pokazi("zaslonSeznanitev", caka);
    pokazi("zaslonPovezan", !brezHuba && !caka);
    // Televizor je zaslon in nima komu posiljati.
    pokazi("panelCast", !brezHuba && !caka && !stanje.televizor);

    if (brezHuba) besedilo("podnaslov", "Hub ni najden");
    else if (caka) besedilo("podnaslov", "Čaka na potrditev");
    else besedilo("podnaslov", "Povezano z domačim Hubom");
  }

  function zasloni() {
    return stanje.naprave.filter(function (n) { return n.vloga === "receiver"; });
  }

  function narisiNaprave() {
    var seznam = el("seznamNaprav");
    if (!seznam) return;
    seznam.innerHTML = "";

    // Hub javlja samo zaslone, zato to napravo in Hub narisemo sama -- uporabnik
    // mora vedno videti, kje je, tudi kadar televizorja se ni.
    seznam.appendChild(vrstica(
      stanje.televizor ? "📺" : "📱",
      stanje.imeNaprave || "Ta naprava",
      stanje.povezan ? "Povezana s Hubom" : "Povezujem se …",
      "Ta naprava",
      stanje.povezan,
      null
    ));
    if (stanje.hub) {
      seznam.appendChild(vrstica(
        "🏠",
        "Safeer Hub",
        stanje.hub.replace(/^wss?:\/\//, "").replace(/\/.*$/, ""),
        "Domači",
        true,
        null
      ));
    }

    zasloni().forEach(function (n) {
      seznam.appendChild(vrstica("📺", n.ime || n.id, "Zaslon", "Povezan", true, null));
    });

    besedilo("opombaNaprave", zasloni().length || stanje.televizor
      ? ""
      : "Noben zaslon se še ni javil. Na televizorju odpri Safeer brskalnik in potrdi njegovo kodo v Safeer Controlu.");
  }

  function narisiPrejemnike() {
    var seznam = el("seznamPrejemnikov");
    if (!seznam) return;
    seznam.innerHTML = "";

    var prejemniki = zasloni();
    if (!prejemniki.length) {
      besedilo("opombaCast", "Noben zaslon se še ni javil, zato pošiljanje še ni mogoče.");
      return;
    }
    besedilo("opombaCast", "");

    prejemniki.forEach(function (n) {
      seznam.appendChild(vrstica("📺", n.ime || n.id, "Pošlji na ta zaslon", "Pošlji", true, function () {
        stanje.prejemnik = n;
        besedilo("imePrejemnika", n.ime || n.id);
        if (most) most.posljiTrenutno(n.id);
      }));
    });
  }

  function narisiPredvajanje() {
    var p = stanje.predvajanje;
    pokazi("predvajalnik", !!p && !stanje.televizor);
    if (!p) return;
    besedilo("naslovPredvajanja", p.naslov || p.url || "—");
    besedilo("casPolozaj", cas(p.polozaj));
    besedilo("casTrajanje", cas(p.trajanje));
    var crta = el("crtaNapolnjena");
    if (crta) {
      var delez = p.trajanje > 0 ? Math.min(100, (p.polozaj / p.trajanje) * 100) : 0;
      crta.style.width = delez + "%";
    }
  }

  function narisiTrenutnoStran() {
    if (!most) return;
    var podatki;
    try {
      podatki = JSON.parse(most.trenutnaStranJson());
    } catch (e) {
      return;
    }
    besedilo("naslovStrani", podatki.naslov || podatki.url || "—");
    besedilo("urlStrani", podatki.posljiva ? podatki.url : "Domača stran — pošiljanje ni mogoče");
  }

  // Vklop odda vse zaznamke vsem napravam na Hubu. To je premalo za en sam dotik,
  // zato prvi dotik samo vprasa, drugi pa res vklopi.
  var syncPotrjujem = false;

  function narisiSync() {
    var seznam = el("seznamSync");
    if (!seznam) return;
    seznam.innerHTML = "";

    var zaznamki = { vklopljena: false, stevilo: 0, nadvoljo: true };
    if (most && most.sinhronizacijaStanje) {
      try {
        var s = JSON.parse(most.sinhronizacijaStanje());
        if (s && s.zaznamki) {
          zaznamki.vklopljena = !!s.zaznamki.vklopljena;
          zaznamki.stevilo = s.zaznamki.stevilo || 0;
          if (s.zaznamki.nadvoljo === false) zaznamki.nadvoljo = false;
        }
      } catch (e) {}
    }

    var pod;
    if (zaznamki.vklopljena) {
      pod = "Vklopljena — " + zaznamki.stevilo + " zaznamkov na tej napravi";
    } else if (!zaznamki.nadvoljo) {
      pod = "Na tej napravi še ni na voljo";
    } else if (syncPotrjujem) {
      pod = "Tvojih " + zaznamki.stevilo + " zaznamkov bo poslanih vsem napravam "
          + "na Hubu. Dotakni se še enkrat, da potrdiš.";
    } else {
      pod = "Dotakni se, da vklopiš. Do takrat se ne pošlje nič.";
    }

    var znacka = zaznamki.vklopljena
      ? "Vklopljeno"
      : (syncPotrjujem ? "Potrdi" : "Izklopljeno");

    seznam.appendChild(vrstica(
      "⭐",
      "Zaznamki",
      pod,
      znacka,
      zaznamki.vklopljena,
      zaznamki.nadvoljo ? function () {
        if (!most) return;
        if (zaznamki.vklopljena) {
          syncPotrjujem = false;
          besedilo("opombaSync", "Izklapljam …");
          most.nastaviSinhronizacijo(false);
          return;
        }
        if (!syncPotrjujem) {
          syncPotrjujem = true;
          besedilo("opombaSync", "");
          narisiSync();
          return;
        }
        syncPotrjujem = false;
        besedilo("opombaSync", "Vklapljam …");
        most.nastaviSinhronizacijo(true);
      } : null
    ));

    [
      { ikona: "⚙️", ime: "Nastavitve", pod: "Videz, iskalnik, zaščite", stanje: "Kmalu" },
      { ikona: "🛡️", ime: "Seznami filtrov", pod: "Iste zaščite na vseh napravah", stanje: "Kmalu" }
    ].forEach(function (v) {
      seznam.appendChild(vrstica(v.ikona, v.ime, v.pod, v.stanje, false, null));
    });
  }

  function narisiVse() {
    narisiZaslon();
    narisiNaprave();
    if (!stanje.televizor) {
      narisiPrejemnike();
      narisiTrenutnoStran();
      narisiPredvajanje();
    }
  }

  // ----------------------------------------------------------------
  // Odzivi mostu
  // ----------------------------------------------------------------

  window.safeerLinkOdziv = function (vrsta, podatki) {
    try {
      if (vrsta === "hub") {
        if (podatki && podatki.najden) {
          stanje.znan = true;
          besedilo("naslovHuba", podatki.naslov || "");
          osveziStanje();
        } else {
          besedilo("opombaIskanje", "Huba ni v tem omrežju. Preveri, ali Safeer Control teče.");
        }
      } else if (vrsta === "koda") {
        pokazi("kodaBlok", true);
        besedilo("kodaStevilke", String(podatki));
        besedilo("opombaSeznanitev", "Čakam na potrditev v Safeer Controlu …");
        var g = el("gumbSeznani");
        if (g) g.disabled = true;
        pokazi("gumbKonzola", true);
      } else if (vrsta === "seznanitev") {
        var gumb = el("gumbSeznani");
        if (gumb) gumb.disabled = false;
        if (podatki) {
          stanje.seznanjen = true;
          narisiZaslon();
          poveziSe();
        } else {
          pokazi("kodaBlok", false);
          besedilo("opombaSeznanitev", "Koda ni bila potrjena. Poskusi znova.");
        }
      } else if (vrsta === "naprave") {
        stanje.naprave = podatki || [];
        narisiNaprave();
        if (!stanje.televizor) narisiPrejemnike();
      } else if (vrsta === "predvajanje") {
        stanje.predvajanje = podatki;
        narisiPredvajanje();
      } else if (vrsta === "poslano") {
        besedilo("opombaCast", "Poslano.");
      } else if (vrsta === "sinhronizacija") {
        narisiSync();
        if (podatki && podatki.vklopljena) {
          besedilo("opombaSync", podatki.dodanih
            ? "Prejeto: " + podatki.dodanih + " novih zaznamkov."
            : "Sinhronizacija je vklopljena.");
        } else {
          besedilo("opombaSync", "Sinhronizacija je izklopljena. Nič se ne pošilja.");
        }
      } else if (vrsta === "povezava") {
        stanje.povezan = !!podatki;
        narisiNaprave();
      } else if (vrsta === "napaka") {
        besedilo("opombaNaprave", String(podatki));
        besedilo("opombaIskanje", String(podatki));
      }
    } catch (e) {
      // Stran nikoli ne sme pasti zaradi odziva.
    }
  };

  // ----------------------------------------------------------------
  // Dejanja
  // ----------------------------------------------------------------

  function osveziStanje() {
    if (!most) {
      besedilo("podnaslov", "Most ni na voljo");
      return;
    }
    var s;
    try {
      s = JSON.parse(most.stanje());
    } catch (e) {
      s = { znan: false, seznanjen: false };
    }
    stanje.znan = !!s.znan;
    stanje.seznanjen = !!s.seznanjen;
    stanje.hub = s.hub || "";
    stanje.imeNaprave = s.naprava || "";
    besedilo("naslovHuba", s.hub || "");
    narisiVse();
    if (stanje.znan && stanje.seznanjen) poveziSe();
  }

  function poveziSe() {
    if (!most) return;
    try {
      var zadnje = JSON.parse(most.naprave() || "[]");
      if (zadnje.length) stanje.naprave = zadnje;
      most.poveziSe();
    } catch (e) {}
    narisiVse();
  }

  // ----------------------------------------------------------------
  // Zacetek
  // ----------------------------------------------------------------

  document.addEventListener("DOMContentLoaded", function () {
    try {
      if (most && most.jeTelevizor && most.jeTelevizor()) stanje.televizor = true;
    } catch (e) {}

    naKlik("gumbZapri", function () {
      if (most) most.zapri();
    });

    naKlik("gumbPoisci", function () {
      besedilo("opombaIskanje", "Iščem …");
      if (most) most.poisciHub();
    });

    naKlik("gumbSeznani", function () {
      besedilo("opombaSeznanitev", "");
      if (most) most.seznani();
    });

    // Ta gumb je za tiste, ki Huba sploh nimajo -- brez njega je zaslon slepa ulica.
    naKlik("gumbNavodila", function () {
      if (most && most.odpri) most.odpri("https://safeer.si/");
    });

    naKlik("gumbKonzola", function () {
      if (!most) return;
      var naslov = most.naslovKonzole();
      if (naslov) most.odpri(naslov);
    });

    naKlik("gumbOsvezi", poveziSe);

    var tipke = document.querySelectorAll(".tipke button");
    for (var j = 0; j < tipke.length; j++) {
      (function (t) {
        t.addEventListener("click", function () {
          if (!most || !stanje.prejemnik) return;
          var ukaz = t.getAttribute("data-ukaz");
          var p = stanje.predvajanje;
          if (ukaz === "nazaj" || ukaz === "naprej") {
            var osnova = p ? p.polozaj : 0;
            var cilj = Math.max(0, osnova + (ukaz === "naprej" ? 10 : -10));
            most.nadzor(stanje.prejemnik.id, "seek", cilj);
          } else {
            most.nadzor(stanje.prejemnik.id, ukaz, 0);
          }
        });
      })(tipke[j]);
    }

    narisiSync();
    osveziStanje();
    // Na daljincu prvi fokus odloca, kaj uporabnik potrdi: naj bo glavno dejanje,
    // ne krizec za zapiranje.
    setTimeout(fokusirajGlavno, 150);
  });

  function fokusirajGlavno() {
    var kandidati = ["gumbPoisci", "gumbSeznani", "gumbOsvezi"];
    for (var i = 0; i < kandidati.length; i++) {
      var e = el(kandidati[i]);
      if (e && e.offsetParent !== null && !e.disabled) {
        try { e.focus(); } catch (err) {}
        return;
      }
    }
  }
})();
