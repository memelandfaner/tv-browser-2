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
 *
 * Nacela vmesnika (Matejeve smernice):
 *  - uporabnik ne vidi ne IP-jev ne vrat ne nastavitev,
 *  - stanje je barva: zelena povezano, siva ni Safeer Linka, rumena tezava,
 *  - imena naprav so cloveska, nikoli tehnicni ID,
 *  - sporocila so v jeziku uporabnika in v navadnih besedah, vedno z naslednjim korakom.
 */
(function () {
  "use strict";

  var most = window.SafeerLink || null;

  var el = function (id) { return document.getElementById(id); };

  // ----------------------------------------------------------------
  // Jezik uporabnika
  // ----------------------------------------------------------------

  var BESEDILA = {
    sl: {
      preverjam: "Preverjam …",
      zapri: "Zapri",
      povezano: "Povezano z domačim Safeer Linkom",
      cakaNaPotrditev: "Čaka na tvojo potrditev",
      niVklopljen: "Ni povezano",
      brezHubaNaslov: "Safeer Link še ni vklopljen",
      brezHubaOpis: "Safeer Link pošlje odprto stran na televizor in poveže tvoje naprave doma — brez oblaka in brez računa. Za to potrebuješ Safeer Control, ki teče na računalniku ali Raspberry Pi-ju in je domače središče vseh naprav.",
      brezHubaPomirilo: "Brskalnik deluje povsem normalno tudi brez njega. Ničesar ne izgubiš, če to okno zapreš.",
      poisci: "Poišči v mojem omrežju",
      kakoDobim: "Kako dobim Safeer Control",
      iscem: "Iščem …",
      niNajden: "V tem omrežju ga nisem našel. Preveri, ali Safeer Control teče, in poskusi znova.",
      povežiNaslov: "Poveži to napravo",
      hubNajdenNa: "Safeer Link je na naslovu",
      zakajPotrditi: "Da ti sme pošiljati in sinhronizirati, ga moraš enkrat potrditi.",
      potrdiKodo: "V Safeer Controlu (zavihek Naprave) potrdi to kodo:",
      kodaVelja: "Koda velja 5 minut.",
      poveziSSafeerLink: "Poveži s Safeer Link",
      odpriKonzolo: "Odpri konzolo",
      cakamNaPotrditev: "Čakam na potrditev v Safeer Controlu …",
      niPotrjeno: "Koda ni bila potrjena. Poskusi znova.",
      posljiStran: "Pošlji to stran",
      odprtoVBrskalniku: "Odprto v brskalniku",
      domacaStran: "Domača stran — pošiljanje ni mogoče",
      predvajaSeNa: "Predvaja se na",
      nazaj10: "⏪ 10 s",
      pavza: "⏸ Pavza",
      predvajaj: "▶ Predvajaj",
      naprej10: "10 s ⏩",
      povezaneNaprave: "Povezane naprave",
      osvezi: "Osveži",
      povezujem: "Povezujem se …",
      taNaprava: "Ta naprava",
      povezanaZLinkom: "Povezana s Safeer Linkom",
      domace: "Domače središče",
      zaslon: "Zaslon",
      televizor: "Televizor",
      posljiNaZaslon: "Pošlji na ta zaslon",
      poslji: "Pošlji",
      povezan: "Povezan",
      brezZaslonov: "Noben zaslon se še ni javil. Na televizorju odpri Safeer brskalnik in potrdi njegovo kodo.",
      poslanoNa: "Poslano na {ime}.",
      niDosegljiv: "{ime} trenutno ni dosegljiv. Preveri, ali je prižgan, in poskusi znova.",
      neMorePoslati: "Te strani ni mogoče poslati. Odpri spletno stran in poskusi znova.",
      povezaveNi: "Povezave s Safeer Linkom ni. Poskusi znova.",
      pozabiNapravo: "Pozabi to napravo",
      pozabiPotrdi: "Res? Dotakni se še enkrat — ta naprava se bo odklopila.",
      pozabljeno: "Naprava je odklopljena. Znova jo lahko povežeš kadar koli.",
      sinhronizacija: "Sinhronizacija",
      syncOpis: "Zaznamki potujejo med tvojimi napravami prek domačega Safeer Linka. Nič ne gre v oblak.",
      syncPrivzeto: "Sinhronizacija se vklopi, ko jo potrdiš — do takrat se ne pošlje nič.",
      zaznamki: "Zaznamki",
      syncVklopljena: "Vklopljeno",
      syncIzklopljena: "Izklopljeno",
      syncPotrdi: "Potrdi",
      syncVklopljenaOpis: "Vklopljena — {n} zaznamkov na tej napravi",
      syncNiNaVoljo: "Na tej napravi še ni na voljo",
      syncVprasanje: "Tvojih {n} zaznamkov bo poslanih vsem tvojim napravam. Dotakni se še enkrat, da potrdiš.",
      syncPovabilo: "Dotakni se, da vklopiš. Do takrat se ne pošlje nič.",
      syncVklapljam: "Vklapljam …",
      syncIzklapljam: "Izklapljam …",
      syncTece: "Sinhronizacija teče v ozadju.",
      syncPrejeto: "Prejeto: {n} novih zaznamkov.",
      syncUgasnjena: "Sinhronizacija je izklopljena. Nič se ne pošilja.",
      nastavitve: "Nastavitve",
      nastavitveOpis: "Videz, iskalnik, zaščite",
      filtri: "Seznami filtrov",
      filtriOpis: "Iste zaščite na vseh napravah",
      kmalu: "Kmalu",
      tezava: "Nekaj ni v redu. Poskusi znova."
    },
    en: {
      preverjam: "Checking …",
      zapri: "Close",
      povezano: "Connected to your home Safeer Link",
      cakaNaPotrditev: "Waiting for your approval",
      niVklopljen: "Not connected",
      brezHubaNaslov: "Safeer Link is not set up yet",
      brezHubaOpis: "Safeer Link sends the open page to your television and connects the devices in your home — no cloud, no account. It needs Safeer Control, which runs on a computer or a Raspberry Pi and is the home hub for all your devices.",
      brezHubaPomirilo: "The browser works exactly as before without it. You lose nothing by closing this window.",
      poisci: "Look on my network",
      kakoDobim: "How do I get Safeer Control",
      iscem: "Looking …",
      niNajden: "I could not find it on this network. Check that Safeer Control is running and try again.",
      povežiNaslov: "Connect this device",
      hubNajdenNa: "Safeer Link is at",
      zakajPotrditi: "To let it send and sync to you, approve it once.",
      potrdiKodo: "In Safeer Control (Devices tab) approve this code:",
      kodaVelja: "The code is valid for 5 minutes.",
      poveziSSafeerLink: "Connect to Safeer Link",
      odpriKonzolo: "Open the console",
      cakamNaPotrditev: "Waiting for approval in Safeer Control …",
      niPotrjeno: "The code was not approved. Please try again.",
      posljiStran: "Send this page",
      odprtoVBrskalniku: "Open in the browser",
      domacaStran: "Home page — cannot be sent",
      predvajaSeNa: "Playing on",
      nazaj10: "⏪ 10 s",
      pavza: "⏸ Pause",
      predvajaj: "▶ Play",
      naprej10: "10 s ⏩",
      povezaneNaprave: "Connected devices",
      osvezi: "Refresh",
      povezujem: "Connecting …",
      taNaprava: "This device",
      povezanaZLinkom: "Connected to Safeer Link",
      domace: "Home hub",
      zaslon: "Screen",
      televizor: "Television",
      posljiNaZaslon: "Send to this screen",
      poslji: "Send",
      povezan: "Connected",
      brezZaslonov: "No screen has appeared yet. Open the Safeer browser on your television and approve its code.",
      poslanoNa: "Sent to {ime}.",
      niDosegljiv: "{ime} cannot be reached right now. Check that it is on and try again.",
      neMorePoslati: "This page cannot be sent. Open a website and try again.",
      povezaveNi: "There is no connection to Safeer Link. Please try again.",
      pozabiNapravo: "Forget this device",
      pozabiPotrdi: "Sure? Tap once more — this device will be disconnected.",
      pozabljeno: "The device is disconnected. You can connect it again any time.",
      sinhronizacija: "Sync",
      syncOpis: "Your bookmarks travel between your devices through your home Safeer Link. Nothing goes to the cloud.",
      syncPrivzeto: "Sync starts once you confirm it — until then nothing is sent.",
      zaznamki: "Bookmarks",
      syncVklopljena: "On",
      syncIzklopljena: "Off",
      syncPotrdi: "Confirm",
      syncVklopljenaOpis: "On — {n} bookmarks on this device",
      syncNiNaVoljo: "Not available on this device yet",
      syncVprasanje: "Your {n} bookmarks will be sent to all your devices. Tap once more to confirm.",
      syncPovabilo: "Tap to turn on. Until then nothing is sent.",
      syncVklapljam: "Turning on …",
      syncIzklapljam: "Turning off …",
      syncTece: "Sync runs in the background.",
      syncPrejeto: "Received: {n} new bookmarks.",
      syncUgasnjena: "Sync is off. Nothing is being sent.",
      nastavitve: "Settings",
      nastavitveOpis: "Look, search engine, protections",
      filtri: "Filter lists",
      filtriOpis: "The same protections on every device",
      kmalu: "Soon",
      tezava: "Something went wrong. Please try again."
    }
  };

  var jezik = (function () {
    var oznaka = "";
    try {
      if (most && most.jezik) oznaka = String(most.jezik() || "");
    } catch (e) {}
    if (!oznaka) oznaka = (navigator.language || navigator.userLanguage || "sl");
    oznaka = oznaka.toLowerCase().slice(0, 2);
    return BESEDILA[oznaka] ? oznaka : "en";
  })();

  function t(kljuc, nadomestki) {
    var niz = (BESEDILA[jezik] && BESEDILA[jezik][kljuc]);
    if (niz === undefined) niz = BESEDILA.sl[kljuc];
    if (niz === undefined) return "";
    if (nadomestki) {
      for (var k in nadomestki) {
        if (Object.prototype.hasOwnProperty.call(nadomestki, k)) {
          niz = niz.split("{" + k + "}").join(String(nadomestki[k]));
        }
      }
    }
    return niz;
  }

  function prevediStran() {
    document.documentElement.lang = jezik;
    var vsi = document.querySelectorAll("[data-t]");
    for (var i = 0; i < vsi.length; i++) {
      var kljuc = vsi[i].getAttribute("data-t");
      var niz = t(kljuc);
      if (niz) vsi[i].textContent = niz;
    }
    var naslovi = document.querySelectorAll("[data-t-naslov]");
    for (var j = 0; j < naslovi.length; j++) {
      var n = t(naslovi[j].getAttribute("data-t-naslov"));
      if (n) naslovi[j].setAttribute("aria-label", n);
    }
  }

  // ----------------------------------------------------------------
  // Stanje
  // ----------------------------------------------------------------

  var stanje = {
    znan: false,
    seznanjen: false,
    povezan: false,
    televizor: false,
    hub: "",
    imeNaprave: "",
    naprave: [],
    prejemnik: null,
    predvajanje: null,
    tezava: false
  };

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

  /** Naslov Huba brez vrat in brez sheme; uporabnik ne rabi videti ne enega ne drugega. */
  function prijaznaHisa(naslov) {
    if (!naslov) return "";
    var golo = String(naslov).replace(/^wss?:\/\//, "").replace(/\/.*$/, "");
    return golo.replace(/:\d+$/, "");
  }

  /** Ime naprave, kot ga razume clovek. Tehnicnega ID nikoli ne pokazemo. */
  function prijaznoIme(naprava) {
    if (!naprava) return t("zaslon");
    var ime = (naprava.ime || "").trim();
    if (ime && !/^[a-z0-9]+-[a-z0-9-]{4,}$/i.test(ime)) return ime;
    return naprava.vloga === "receiver" ? t("televizor") : t("zaslon");
  }

  function vrstica(ikonaZnak, ime, pod, znackaBesedilo, barva, obKliku) {
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
    z.className = "znacka" + (barva ? " " + barva : "");
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

  /** Stanje je barva, ne stavek: zelena povezano, siva ni ga, rumena tezava. */
  function narisiStanje() {
    var pika = el("pika");
    var barva = "siva";
    var napis = t("niVklopljen");
    if (stanje.tezava) {
      barva = "rumena";
      napis = t("tezava");
    } else if (stanje.znan && stanje.seznanjen) {
      barva = stanje.povezan ? "zelena" : "rumena";
      napis = stanje.povezan ? t("povezano") : t("povezujem");
    } else if (stanje.znan) {
      barva = "rumena";
      napis = t("cakaNaPotrditev");
    }
    if (pika) pika.className = "pika " + barva;
    besedilo("podnaslov", napis);
  }

  function narisiZaslon() {
    var brezHuba = !stanje.znan;
    var caka = stanje.znan && !stanje.seznanjen;
    pokazi("zaslonBrezHuba", brezHuba);
    pokazi("zaslonSeznanitev", caka);
    pokazi("zaslonPovezan", !brezHuba && !caka);
    pokazi("gumbPozabi", !brezHuba && !caka && !!(most && most.pozabiNapravo));
    narisiStanje();
  }

  function zasloni() {
    return stanje.naprave.filter(function (n) { return n.vloga === "receiver"; });
  }

  function narisiNaprave() {
    var seznam = el("seznamNaprav");
    if (!seznam) return;
    seznam.innerHTML = "";

    // Hub javlja samo zaslone, zato to napravo in Safeer Link narisemo sama --
    // uporabnik mora vedno videti, kje je, tudi kadar televizorja se ni.
    seznam.appendChild(vrstica(
      stanje.televizor ? "📺" : "💻",
      stanje.imeNaprave || t("taNaprava"),
      stanje.povezan ? t("povezanaZLinkom") : t("povezujem"),
      t("taNaprava"),
      stanje.povezan ? "zivo" : "",
      null
    ));
    if (stanje.hub) {
      seznam.appendChild(vrstica(
        "🏠", "Safeer Link", prijaznaHisa(stanje.hub), t("domace"), "zivo", null));
    }

    zasloni().forEach(function (n) {
      seznam.appendChild(vrstica("📺", prijaznoIme(n), t("zaslon"),
                                 t("povezan"), "zivo", null));
    });

    besedilo("opombaNaprave",
             (zasloni().length || stanje.televizor) ? "" : t("brezZaslonov"));
  }

  function narisiPrejemnike() {
    var seznam = el("seznamPrejemnikov");
    if (!seznam) return;
    seznam.innerHTML = "";

    var prejemniki = zasloni();
    // Smernica: gumb za posiljanje naj obstaja samo, ko je kam poslati.
    pokazi("panelCast", prejemniki.length > 0 && !stanje.televizor);
    if (!prejemniki.length) return;

    besedilo("opombaCast", "");
    prejemniki.forEach(function (n) {
      var ime = prijaznoIme(n);
      seznam.appendChild(vrstica("📺", ime, t("posljiNaZaslon"), t("poslji"), "zivo",
        function () {
          stanje.prejemnik = n;
          besedilo("imePrejemnika", ime);
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
    besedilo("urlStrani", podatki.posljiva ? podatki.url : t("domacaStran"));
  }

  // Vklop odda vse zaznamke vsem napravam. To je premalo za en sam dotik,
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
      pod = t("syncVklopljenaOpis", { n: zaznamki.stevilo });
    } else if (!zaznamki.nadvoljo) {
      pod = t("syncNiNaVoljo");
    } else if (syncPotrjujem) {
      pod = t("syncVprasanje", { n: zaznamki.stevilo });
    } else {
      pod = t("syncPovabilo");
    }

    var znacka = zaznamki.vklopljena ? t("syncVklopljena")
               : (syncPotrjujem ? t("syncPotrdi") : t("syncIzklopljena"));

    seznam.appendChild(vrstica(
      "⭐", t("zaznamki"), pod, znacka,
      zaznamki.vklopljena ? "zivo" : (syncPotrjujem ? "opozorilo" : ""),
      zaznamki.nadvoljo ? function () {
        if (!most) return;
        if (zaznamki.vklopljena) {
          syncPotrjujem = false;
          besedilo("opombaSync", t("syncIzklapljam"));
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
        besedilo("opombaSync", t("syncVklapljam"));
        most.nastaviSinhronizacijo(true);
      } : null
    ));

    [
      { ikona: "⚙️", ime: t("nastavitve"), pod: t("nastavitveOpis") },
      { ikona: "🛡️", ime: t("filtri"), pod: t("filtriOpis") }
    ].forEach(function (v) {
      seznam.appendChild(vrstica(v.ikona, v.ime, v.pod, t("kmalu"), "", null));
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
          stanje.tezava = false;
          besedilo("naslovHuba", prijaznaHisa(podatki.naslov));
          osveziStanje();
        } else {
          besedilo("opombaIskanje", t("niNajden"));
          narisiStanje();
        }
      } else if (vrsta === "koda") {
        pokazi("kodaBlok", true);
        besedilo("kodaStevilke", String(podatki));
        besedilo("opombaSeznanitev", t("cakamNaPotrditev"));
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
          besedilo("opombaSeznanitev", t("niPotrjeno"));
        }
      } else if (vrsta === "naprave") {
        stanje.naprave = podatki || [];
        narisiNaprave();
        if (!stanje.televizor) narisiPrejemnike();
      } else if (vrsta === "predvajanje") {
        stanje.predvajanje = podatki;
        narisiPredvajanje();
      } else if (vrsta === "poslano") {
        var kam = stanje.prejemnik ? prijaznoIme(stanje.prejemnik) : t("televizor");
        besedilo("opombaCast", t("poslanoNa", { ime: kam }));
      } else if (vrsta === "pozabljeno") {
        besedilo("opombaPozabi", t("pozabljeno"));
        stanje.seznanjen = false;
        stanje.povezan = false;
        narisiVse();
      } else if (vrsta === "sinhronizacija") {
        narisiSync();
        if (podatki && podatki.vklopljena) {
          besedilo("opombaSync", podatki.dodanih
            ? t("syncPrejeto", { n: podatki.dodanih })
            : t("syncTece"));
        } else {
          besedilo("opombaSync", t("syncUgasnjena"));
        }
      } else if (vrsta === "povezava") {
        stanje.povezan = !!podatki;
        stanje.tezava = false;
        narisiNaprave();
        narisiStanje();
      } else if (vrsta === "napaka") {
        // Tehnicnega besedila uporabniku ne kazemo: povemo, kaj to pomeni zanj.
        stanje.tezava = true;
        var sporocilo = clovesko(String(podatki));
        besedilo("opombaNaprave", sporocilo);
        besedilo("opombaIskanje", sporocilo);
        besedilo("opombaCast", sporocilo);
        narisiStanje();
      }
    } catch (e) {
      // Stran nikoli ne sme pasti zaradi odziva.
    }
  };

  /** Iz tehnicne napake naredi poved, ki uporabniku pove, kaj naj naredi. */
  function clovesko(sporocilo) {
    var m = (sporocilo || "").toLowerCase();
    if (m.indexOf("unauthorized") >= 0 || m.indexOf("401") >= 0 ||
        m.indexOf("ni povezan") >= 0) {
      return t("povezaveNi");
    }
    if (m.indexOf("websocket") >= 0 || m.indexOf("connection") >= 0 ||
        m.indexOf("povezava") >= 0 || m.indexOf("timeout") >= 0) {
      return t("povezaveNi");
    }
    if (m.indexOf("http") >= 0 && m.indexOf("naslov") >= 0) {
      return t("neMorePoslati");
    }
    // Ce sporocila ne prepoznamo, je ze napisano po slovensko iz mostu --
    // a le kadar ni videti tehnicno.
    if (/[<>{}]|error|exception|traceback|failed/i.test(sporocilo)) {
      return t("tezava");
    }
    return sporocilo || t("tezava");
  }

  // ----------------------------------------------------------------
  // Dejanja
  // ----------------------------------------------------------------

  function osveziStanje() {
    if (!most) {
      stanje.tezava = true;
      narisiStanje();
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
    besedilo("naslovHuba", prijaznaHisa(s.hub));
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
    prevediStran();

    try {
      if (most && most.jeTelevizor && most.jeTelevizor()) stanje.televizor = true;
    } catch (e) {}

    naKlik("gumbZapri", function () {
      if (most) most.zapri();
    });

    naKlik("gumbPoisci", function () {
      besedilo("opombaIskanje", t("iscem"));
      if (most) most.poisciHub();
    });

    naKlik("gumbNavodila", function () {
      if (most && most.odpri) most.odpri("https://safeer.si/");
    });

    naKlik("gumbSeznani", function () {
      besedilo("opombaSeznanitev", "");
      if (most) most.seznani();
    });

    naKlik("gumbKonzola", function () {
      if (!most) return;
      var naslov = most.naslovKonzole();
      if (naslov) most.odpri(naslov);
    });

    naKlik("gumbOsvezi", poveziSe);

    var pozabiPotrjujem = false;
    naKlik("gumbPozabi", function () {
      if (!most || !most.pozabiNapravo) return;
      if (!pozabiPotrjujem) {
        pozabiPotrjujem = true;
        besedilo("opombaPozabi", t("pozabiPotrdi"));
        return;
      }
      pozabiPotrjujem = false;
      most.pozabiNapravo();
    });

    var tipke = document.querySelectorAll(".tipke button");
    for (var j = 0; j < tipke.length; j++) {
      (function (tipka) {
        tipka.addEventListener("click", function () {
          if (!most || !stanje.prejemnik) return;
          var ukaz = tipka.getAttribute("data-ukaz");
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
