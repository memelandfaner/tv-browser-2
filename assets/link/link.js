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
      napHubNiZnan: "Huba še ne poznam. Najprej ga poišči.",
      napIskanje: "Iskanja ni bilo mogoče zagnati.",
      napSeznanitev: "Seznanitve ni bilo mogoče začeti.",
      napPovezava: "Povezava ni uspela. Preveri, ali je Hub prižgan.",
      napStranNiPrimerna: "Te strani ni mogoče poslati.",
      napSamoHttp: "Poslati je mogoče samo naslove http in https.",
      napPosiljanje: "Pošiljanje ni uspelo. Poskusi znova.",
      napUkaz: "Ukaz ni uspel.",
      napZaznamki: "Zaznamkov ni bilo mogoče poslati.",
      napSyncStart: "Sinhronizacije ni bilo mogoče začeti.",
      napSyncNastavi: "Sinhronizacije ni bilo mogoče nastaviti.",
      napZdruzevanje: "Združevanja zaznamkov ni bilo mogoče končati.",
      napHubNeTece: "Hub ne teče.",
      napHubNiZagnan: "Huba ni bilo mogoče zagnati.",
      napHubNiUstavljen: "Huba ni bilo mogoče ustaviti.",
      napPrijavaPotekla: "Prijave ni več ali pa je potekla.",
      napTvJeZaslon: "Televizor je zaslon in ne pošilja.",
      napTvNeUpravlja: "Televizor ne upravlja drugih zaslonov.",
      napSyncTvNiNaVoljo: "Sinhronizacija zaznamkov na televizorju še ni na voljo.",
      preverjam: "Preverjam …",
      zapri: "Zapri",
      povezano: "Povezano z domačim Safeer Linkom",
      cakaNaPotrditev: "Čaka na tvojo potrditev",
      niVklopljen: "Ni povezano",
      brezHubaNaslov: "Safeer Link še ni vklopljen",
      brezHubaOpis: "Safeer Link pošlje odprto stran na televizor in poveže tvoje naprave doma — brez oblaka in brez računa. Za to mora ena naprava v hiši prevzeti vlogo središča; ostale se povežejo nanjo.",
      brezHubaPomirilo: "Brskalnik deluje povsem normalno tudi brez njega. Ničesar ne izgubiš, če to okno zapreš.",
      poisci: "Poišči v mojem omrežju",
      kakoDobim: "Kako to vklopim",
      iscem: "Iščem …",
      niNajden: "V tem omrežju ga nisem našel. Preveri, ali Safeer Link teče na kateri od tvojih naprav, in poskusi znova.",
      povežiNaslov: "Poveži to napravo",
      hubNajdenNa: "Safeer Link je na naslovu",
      zakajPotrditi: "Da ti sme pošiljati in sinhronizirati, ga moraš enkrat potrditi.",
      potrdiKodo: "Kodo potrdi na napravi, kjer teče Safeer Link:",
      kodaVelja: "Koda velja 5 minut.",
      poveziSSafeerLink: "Poveži s Safeer Link",
      cakamNaPotrditev: "Čakam na potrditev …",
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
      tezava: "Nekaj ni v redu. Poskusi znova.",
      preseljenNaslov: "Safeer Link je na novem naslovu",
      preseljenOpis: "Doma se javlja z drugega naslova kot doslej — običajno zato, ker mu je usmerjevalnik podelil novega. Potrdi, da je to tvoj Safeer Link.",
      daPovezi: "Da, poveži",
      preverjamNaslov: "Povezujem se na nov naslov …"
    },
    en: {
      napHubNiZnan: "The hub is not known yet. Find it first.",
      napIskanje: "The search could not be started.",
      napSeznanitev: "Pairing could not be started.",
      napPovezava: "The connection failed. Check that the hub is on.",
      napStranNiPrimerna: "This page cannot be sent.",
      napSamoHttp: "Only http and https addresses can be sent.",
      napPosiljanje: "Sending failed. Try again.",
      napUkaz: "The command failed.",
      napZaznamki: "The bookmarks could not be sent.",
      napSyncStart: "Sync could not be started.",
      napSyncNastavi: "Sync could not be set up.",
      napZdruzevanje: "Merging the bookmarks could not be finished.",
      napHubNeTece: "The hub is not running.",
      napHubNiZagnan: "The hub could not be started.",
      napHubNiUstavljen: "The hub could not be stopped.",
      napPrijavaPotekla: "The request is gone or has expired.",
      napTvJeZaslon: "The television is a screen; it does not send.",
      napTvNeUpravlja: "The television does not control other screens.",
      napSyncTvNiNaVoljo: "Bookmark sync is not available on the television yet.",
      preverjam: "Checking …",
      zapri: "Close",
      povezano: "Connected to your home Safeer Link",
      cakaNaPotrditev: "Waiting for your approval",
      niVklopljen: "Not connected",
      brezHubaNaslov: "Safeer Link is not set up yet",
      brezHubaOpis: "Safeer Link sends the open page to your television and connects the devices in your home — no cloud, no account. One device in the house takes the role of the hub; the others connect to it.",
      brezHubaPomirilo: "The browser works exactly as before without it. You lose nothing by closing this window.",
      poisci: "Look on my network",
      kakoDobim: "How do I turn this on",
      iscem: "Looking …",
      niNajden: "I could not find it on this network. Check that Safeer Link is running on one of your devices and try again.",
      povežiNaslov: "Connect this device",
      hubNajdenNa: "Safeer Link is at",
      zakajPotrditi: "To let it send and sync to you, approve it once.",
      potrdiKodo: "Approve this code on the device running Safeer Link:",
      kodaVelja: "The code is valid for 5 minutes.",
      poveziSSafeerLink: "Connect to Safeer Link",
      cakamNaPotrditev: "Waiting for approval …",
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
      tezava: "Something went wrong. Please try again.",
      preseljenNaslov: "Safeer Link has a new address",
      preseljenOpis: "It is announcing itself from a different address than before — usually because the router gave it a new one. Confirm that this is your Safeer Link.",
      daPovezi: "Yes, connect",
      preverjamNaslov: "Connecting to the new address …"
    },
    de: {
      napHubNiZnan: "Der Hub ist noch nicht bekannt. Suche ihn zuerst.",
      napIskanje: "Die Suche konnte nicht gestartet werden.",
      napSeznanitev: "Die Kopplung konnte nicht gestartet werden.",
      napPovezava: "Die Verbindung ist fehlgeschlagen. Prüfe, ob der Hub eingeschaltet ist.",
      napStranNiPrimerna: "Diese Seite kann nicht gesendet werden.",
      napSamoHttp: "Es können nur http- und https-Adressen gesendet werden.",
      napPosiljanje: "Senden fehlgeschlagen. Versuche es noch einmal.",
      napUkaz: "Der Befehl ist fehlgeschlagen.",
      napZaznamki: "Die Lesezeichen konnten nicht gesendet werden.",
      napSyncStart: "Die Synchronisierung konnte nicht gestartet werden.",
      napSyncNastavi: "Die Synchronisierung konnte nicht eingerichtet werden.",
      napZdruzevanje: "Das Zusammenführen der Lesezeichen konnte nicht beendet werden.",
      napHubNeTece: "Der Hub läuft nicht.",
      napHubNiZagnan: "Der Hub konnte nicht gestartet werden.",
      napHubNiUstavljen: "Der Hub konnte nicht gestoppt werden.",
      napPrijavaPotekla: "Die Anfrage gibt es nicht mehr oder sie ist abgelaufen.",
      napTvJeZaslon: "Der Fernseher ist ein Bildschirm; er sendet nicht.",
      napTvNeUpravlja: "Der Fernseher steuert keine anderen Bildschirme.",
      napSyncTvNiNaVoljo: "Die Lesezeichen-Synchronisierung ist auf dem Fernseher noch nicht verfügbar.",
      preverjam: "Prüfe …",
      zapri: "Schließen",
      povezano: "Mit deinem Safeer Link zu Hause verbunden",
      cakaNaPotrditev: "Warte auf deine Bestätigung",
      niVklopljen: "Nicht verbunden",
      brezHubaNaslov: "Safeer Link ist noch nicht eingerichtet",
      brezHubaOpis: "Safeer Link sendet die geöffnete Seite an deinen Fernseher und verbindet die Geräte bei dir zu Hause — ohne Cloud, ohne Konto. Ein Gerät im Haus übernimmt die Rolle des Hubs, die anderen verbinden sich damit.",
      brezHubaPomirilo: "Ohne ihn funktioniert der Browser genau wie vorher. Du verlierst nichts, wenn du dieses Fenster schließt.",
      poisci: "In meinem Netzwerk suchen",
      kakoDobim: "Wie schalte ich das ein",
      iscem: "Suche …",
      niNajden: "Ich konnte ihn in diesem Netzwerk nicht finden. Prüfe, ob Safeer Link auf einem deiner Geräte läuft, und versuche es erneut.",
      povežiNaslov: "Dieses Gerät verbinden",
      hubNajdenNa: "Safeer Link ist unter",
      zakajPotrditi: "Damit er dir senden und synchronisieren darf, bestätige ihn einmal.",
      potrdiKodo: "Bestätige diesen Code auf dem Gerät, auf dem Safeer Link läuft:",
      kodaVelja: "Der Code gilt 5 Minuten.",
      poveziSSafeerLink: "Mit Safeer Link verbinden",
      cakamNaPotrditev: "Warte auf Bestätigung …",
      niPotrjeno: "Der Code wurde nicht bestätigt. Bitte versuche es erneut.",
      posljiStran: "Diese Seite senden",
      odprtoVBrskalniku: "Im Browser öffnen",
      domacaStran: "Startseite — kann nicht gesendet werden",
      predvajaSeNa: "Läuft auf",
      nazaj10: "⏪ 10 s",
      pavza: "⏸ Pause",
      predvajaj: "▶ Wiedergabe",
      naprej10: "10 s ⏩",
      povezaneNaprave: "Verbundene Geräte",
      osvezi: "Aktualisieren",
      povezujem: "Verbinde …",
      taNaprava: "Dieses Gerät",
      povezanaZLinkom: "Mit Safeer Link verbunden",
      domace: "Heim-Hub",
      zaslon: "Bildschirm",
      televizor: "Fernseher",
      posljiNaZaslon: "An diesen Bildschirm senden",
      poslji: "Senden",
      povezan: "Verbunden",
      brezZaslonov: "Es ist noch kein Bildschirm aufgetaucht. Öffne den Safeer-Browser auf deinem Fernseher und bestätige seinen Code.",
      poslanoNa: "An {ime} gesendet.",
      niDosegljiv: "{ime} ist gerade nicht erreichbar. Prüfe, ob das Gerät an ist, und versuche es erneut.",
      neMorePoslati: "Diese Seite kann nicht gesendet werden. Öffne eine Website und versuche es erneut.",
      povezaveNi: "Es besteht keine Verbindung zu Safeer Link. Bitte versuche es erneut.",
      pozabiNapravo: "Dieses Gerät vergessen",
      pozabiPotrdi: "Sicher? Tippe noch einmal — dieses Gerät wird getrennt.",
      pozabljeno: "Das Gerät ist getrennt. Du kannst es jederzeit wieder verbinden.",
      sinhronizacija: "Synchronisierung",
      syncOpis: "Deine Lesezeichen wandern über deinen Safeer Link zu Hause zwischen deinen Geräten. Nichts geht in die Cloud.",
      syncPrivzeto: "Die Synchronisierung startet, sobald du sie bestätigst — bis dahin wird nichts gesendet.",
      zaznamki: "Lesezeichen",
      syncVklopljena: "Ein",
      syncIzklopljena: "Aus",
      syncPotrdi: "Bestätigen",
      syncVklopljenaOpis: "Ein — {n} Lesezeichen auf diesem Gerät",
      syncNiNaVoljo: "Auf diesem Gerät noch nicht verfügbar",
      syncVprasanje: "Deine {n} Lesezeichen werden an alle deine Geräte gesendet. Tippe noch einmal zum Bestätigen.",
      syncPovabilo: "Zum Einschalten tippen. Bis dahin wird nichts gesendet.",
      syncVklapljam: "Schalte ein …",
      syncIzklapljam: "Schalte aus …",
      syncTece: "Die Synchronisierung läuft im Hintergrund.",
      syncPrejeto: "Empfangen: {n} neue Lesezeichen.",
      syncUgasnjena: "Die Synchronisierung ist aus. Es wird nichts gesendet.",
      nastavitve: "Einstellungen",
      nastavitveOpis: "Aussehen, Suchmaschine, Schutzfunktionen",
      filtri: "Filterlisten",
      filtriOpis: "Der gleiche Schutz auf jedem Gerät",
      kmalu: "Bald",
      tezava: "Etwas ist schiefgelaufen. Bitte versuche es erneut.",
      preseljenNaslov: "Safeer Link hat eine neue Adresse",
      preseljenOpis: "Er meldet sich von einer anderen Adresse als zuvor — meist weil der Router ihm eine neue gegeben hat. Bestätige, dass das dein Safeer Link ist.",
      daPovezi: "Ja, verbinden",
      preverjamNaslov: "Verbinde mit der neuen Adresse …"
    },
    es: {
      napHubNiZnan: "El hub todavía no se conoce. Búscalo primero.",
      napIskanje: "No se pudo iniciar la búsqueda.",
      napSeznanitev: "No se pudo iniciar el emparejamiento.",
      napPovezava: "La conexión ha fallado. Comprueba que el hub esté encendido.",
      napStranNiPrimerna: "Esta página no se puede enviar.",
      napSamoHttp: "Solo se pueden enviar direcciones http y https.",
      napPosiljanje: "El envío ha fallado. Inténtalo de nuevo.",
      napUkaz: "El comando ha fallado.",
      napZaznamki: "No se pudieron enviar los marcadores.",
      napSyncStart: "No se pudo iniciar la sincronización.",
      napSyncNastavi: "No se pudo configurar la sincronización.",
      napZdruzevanje: "No se pudo terminar de combinar los marcadores.",
      napHubNeTece: "El hub no está funcionando.",
      napHubNiZagnan: "No se pudo iniciar el hub.",
      napHubNiUstavljen: "No se pudo detener el hub.",
      napPrijavaPotekla: "La solicitud ya no existe o ha caducado.",
      napTvJeZaslon: "El televisor es una pantalla; no envía.",
      napTvNeUpravlja: "El televisor no controla otras pantallas.",
      napSyncTvNiNaVoljo: "La sincronización de marcadores todavía no está disponible en el televisor.",
      preverjam: "Comprobando …",
      zapri: "Cerrar",
      povezano: "Conectado a tu Safeer Link de casa",
      cakaNaPotrditev: "Esperando tu aprobación",
      niVklopljen: "Sin conexión",
      brezHubaNaslov: "Safeer Link todavía no está configurado",
      brezHubaOpis: "Safeer Link envía la página abierta a tu televisor y conecta los dispositivos de tu casa: sin nube, sin cuenta. Un dispositivo de la casa asume el papel de hub y los demás se conectan a él.",
      brezHubaPomirilo: "Sin él, el navegador funciona exactamente igual que antes. No pierdes nada al cerrar esta ventana.",
      poisci: "Buscar en mi red",
      kakoDobim: "Cómo activo esto",
      iscem: "Buscando …",
      niNajden: "No lo he encontrado en esta red. Comprueba que Safeer Link esté funcionando en alguno de tus dispositivos e inténtalo de nuevo.",
      povežiNaslov: "Conectar este dispositivo",
      hubNajdenNa: "Safeer Link está en",
      zakajPotrditi: "Para que pueda enviarte y sincronizar, apruébalo una vez.",
      potrdiKodo: "Aprueba este código en el dispositivo donde funciona Safeer Link:",
      kodaVelja: "El código es válido durante 5 minutos.",
      poveziSSafeerLink: "Conectar con Safeer Link",
      cakamNaPotrditev: "Esperando la aprobación …",
      niPotrjeno: "El código no se aprobó. Inténtalo de nuevo.",
      posljiStran: "Enviar esta página",
      odprtoVBrskalniku: "Abrir en el navegador",
      domacaStran: "Página de inicio: no se puede enviar",
      predvajaSeNa: "Reproduciéndose en",
      nazaj10: "⏪ 10 s",
      pavza: "⏸ Pausa",
      predvajaj: "▶ Reproducir",
      naprej10: "10 s ⏩",
      povezaneNaprave: "Dispositivos conectados",
      osvezi: "Actualizar",
      povezujem: "Conectando …",
      taNaprava: "Este dispositivo",
      povezanaZLinkom: "Conectado a Safeer Link",
      domace: "Hub de casa",
      zaslon: "Pantalla",
      televizor: "Televisor",
      posljiNaZaslon: "Enviar a esta pantalla",
      poslji: "Enviar",
      povezan: "Conectado",
      brezZaslonov: "Todavía no ha aparecido ninguna pantalla. Abre el navegador Safeer en tu televisor y aprueba su código.",
      poslanoNa: "Enviado a {ime}.",
      niDosegljiv: "Ahora mismo no se puede llegar a {ime}. Comprueba que esté encendido e inténtalo de nuevo.",
      neMorePoslati: "Esta página no se puede enviar. Abre un sitio web e inténtalo de nuevo.",
      povezaveNi: "No hay conexión con Safeer Link. Inténtalo de nuevo.",
      pozabiNapravo: "Olvidar este dispositivo",
      pozabiPotrdi: "¿Seguro? Toca una vez más: este dispositivo se desconectará.",
      pozabljeno: "El dispositivo está desconectado. Puedes volver a conectarlo cuando quieras.",
      sinhronizacija: "Sincronización",
      syncOpis: "Tus marcadores viajan entre tus dispositivos a través de tu Safeer Link de casa. Nada va a la nube.",
      syncPrivzeto: "La sincronización empieza cuando la confirmes; hasta entonces no se envía nada.",
      zaznamki: "Marcadores",
      syncVklopljena: "Activada",
      syncIzklopljena: "Desactivada",
      syncPotrdi: "Confirmar",
      syncVklopljenaOpis: "Activada: {n} marcadores en este dispositivo",
      syncNiNaVoljo: "Todavía no disponible en este dispositivo",
      syncVprasanje: "Tus {n} marcadores se enviarán a todos tus dispositivos. Toca una vez más para confirmar.",
      syncPovabilo: "Toca para activar. Hasta entonces no se envía nada.",
      syncVklapljam: "Activando …",
      syncIzklapljam: "Desactivando …",
      syncTece: "La sincronización funciona en segundo plano.",
      syncPrejeto: "Recibidos: {n} marcadores nuevos.",
      syncUgasnjena: "La sincronización está desactivada. No se envía nada.",
      nastavitve: "Ajustes",
      nastavitveOpis: "Aspecto, buscador, protecciones",
      filtri: "Listas de filtros",
      filtriOpis: "Las mismas protecciones en todos los dispositivos",
      kmalu: "Pronto",
      tezava: "Algo ha salido mal. Inténtalo de nuevo.",
      preseljenNaslov: "Safeer Link tiene una dirección nueva",
      preseljenOpis: "Se anuncia desde una dirección distinta a la anterior, normalmente porque el router le ha dado una nueva. Confirma que este es tu Safeer Link.",
      daPovezi: "Sí, conectar",
      preverjamNaslov: "Conectando con la nueva dirección …"
    },
    fr: {
      napHubNiZnan: "Le hub n\'est pas encore connu. Cherche-le d\'abord.",
      napIskanje: "La recherche n\'a pas pu démarrer.",
      napSeznanitev: "L\'association n\'a pas pu démarrer.",
      napPovezava: "La connexion a échoué. Vérifie que le hub est allumé.",
      napStranNiPrimerna: "Cette page ne peut pas être envoyée.",
      napSamoHttp: "Seules les adresses http et https peuvent être envoyées.",
      napPosiljanje: "L\'envoi a échoué. Réessaie.",
      napUkaz: "La commande a échoué.",
      napZaznamki: "Les favoris n\'ont pas pu être envoyés.",
      napSyncStart: "La synchronisation n\'a pas pu démarrer.",
      napSyncNastavi: "La synchronisation n\'a pas pu être configurée.",
      napZdruzevanje: "La fusion des favoris n\'a pas pu être terminée.",
      napHubNeTece: "Le hub ne fonctionne pas.",
      napHubNiZagnan: "Le hub n\'a pas pu être démarré.",
      napHubNiUstavljen: "Le hub n\'a pas pu être arrêté.",
      napPrijavaPotekla: "La demande n\'existe plus ou a expiré.",
      napTvJeZaslon: "Le téléviseur est un écran ; il n\'envoie pas.",
      napTvNeUpravlja: "Le téléviseur ne contrôle pas les autres écrans.",
      napSyncTvNiNaVoljo: "La synchronisation des favoris n\'est pas encore disponible sur le téléviseur.",
      preverjam: "Vérification …",
      zapri: "Fermer",
      povezano: "Connecté à ton Safeer Link à la maison",
      cakaNaPotrditev: "En attente de ton approbation",
      niVklopljen: "Non connecté",
      brezHubaNaslov: "Safeer Link n\'est pas encore configuré",
      brezHubaOpis: "Safeer Link envoie la page ouverte vers ton téléviseur et relie les appareils de ta maison — sans cloud, sans compte. Un appareil de la maison joue le rôle du hub, les autres s\'y connectent.",
      brezHubaPomirilo: "Sans lui, le navigateur fonctionne exactement comme avant. Tu ne perds rien en fermant cette fenêtre.",
      poisci: "Chercher sur mon réseau",
      kakoDobim: "Comment activer cela",
      iscem: "Recherche …",
      niNajden: "Je ne l\'ai pas trouvé sur ce réseau. Vérifie que Safeer Link fonctionne sur un de tes appareils et réessaie.",
      povežiNaslov: "Connecter cet appareil",
      hubNajdenNa: "Safeer Link se trouve à",
      zakajPotrditi: "Pour qu\'il puisse t\'envoyer et synchroniser, approuve-le une fois.",
      potrdiKodo: "Approuve ce code sur l\'appareil où fonctionne Safeer Link :",
      kodaVelja: "Le code est valable 5 minutes.",
      poveziSSafeerLink: "Se connecter à Safeer Link",
      cakamNaPotrditev: "En attente de l\'approbation …",
      niPotrjeno: "Le code n\'a pas été approuvé. Réessaie.",
      posljiStran: "Envoyer cette page",
      odprtoVBrskalniku: "Ouvrir dans le navigateur",
      domacaStran: "Page d\'accueil — ne peut pas être envoyée",
      predvajaSeNa: "Lecture sur",
      nazaj10: "⏪ 10 s",
      pavza: "⏸ Pause",
      predvajaj: "▶ Lecture",
      naprej10: "10 s ⏩",
      povezaneNaprave: "Appareils connectés",
      osvezi: "Actualiser",
      povezujem: "Connexion …",
      taNaprava: "Cet appareil",
      povezanaZLinkom: "Connecté à Safeer Link",
      domace: "Hub de la maison",
      zaslon: "Écran",
      televizor: "Téléviseur",
      posljiNaZaslon: "Envoyer vers cet écran",
      poslji: "Envoyer",
      povezan: "Connecté",
      brezZaslonov: "Aucun écran n\'est encore apparu. Ouvre le navigateur Safeer sur ton téléviseur et approuve son code.",
      poslanoNa: "Envoyé vers {ime}.",
      niDosegljiv: "{ime} est injoignable pour le moment. Vérifie qu\'il est allumé et réessaie.",
      neMorePoslati: "Cette page ne peut pas être envoyée. Ouvre un site web et réessaie.",
      povezaveNi: "Il n\'y a pas de connexion à Safeer Link. Réessaie.",
      pozabiNapravo: "Oublier cet appareil",
      pozabiPotrdi: "Sûr ? Appuie encore une fois — cet appareil sera déconnecté.",
      pozabljeno: "L\'appareil est déconnecté. Tu peux le reconnecter quand tu veux.",
      sinhronizacija: "Synchronisation",
      syncOpis: "Tes favoris circulent entre tes appareils via ton Safeer Link à la maison. Rien ne part vers le cloud.",
      syncPrivzeto: "La synchronisation démarre dès que tu la confirmes — jusque-là rien n\'est envoyé.",
      zaznamki: "Favoris",
      syncVklopljena: "Activée",
      syncIzklopljena: "Désactivée",
      syncPotrdi: "Confirmer",
      syncVklopljenaOpis: "Activée — {n} favoris sur cet appareil",
      syncNiNaVoljo: "Pas encore disponible sur cet appareil",
      syncVprasanje: "Tes {n} favoris seront envoyés à tous tes appareils. Appuie encore une fois pour confirmer.",
      syncPovabilo: "Appuie pour activer. Jusque-là rien n\'est envoyé.",
      syncVklapljam: "Activation …",
      syncIzklapljam: "Désactivation …",
      syncTece: "La synchronisation fonctionne en arrière-plan.",
      syncPrejeto: "Reçus : {n} nouveaux favoris.",
      syncUgasnjena: "La synchronisation est désactivée. Rien n\'est envoyé.",
      nastavitve: "Paramètres",
      nastavitveOpis: "Apparence, moteur de recherche, protections",
      filtri: "Listes de filtres",
      filtriOpis: "Les mêmes protections sur chaque appareil",
      kmalu: "Bientôt",
      tezava: "Quelque chose a mal tourné. Réessaie.",
      preseljenNaslov: "Safeer Link a une nouvelle adresse",
      preseljenOpis: "Il s\'annonce depuis une adresse différente — le plus souvent parce que le routeur lui en a donné une nouvelle. Confirme que c\'est bien ton Safeer Link.",
      daPovezi: "Oui, connecter",
      preverjamNaslov: "Connexion à la nouvelle adresse …"
    },
    it: {
      napHubNiZnan: "L\'hub non è ancora noto. Cercalo prima.",
      napIskanje: "Non è stato possibile avviare la ricerca.",
      napSeznanitev: "Non è stato possibile avviare l\'associazione.",
      napPovezava: "La connessione non è riuscita. Controlla che l\'hub sia acceso.",
      napStranNiPrimerna: "Questa pagina non può essere inviata.",
      napSamoHttp: "Si possono inviare solo indirizzi http e https.",
      napPosiljanje: "Invio non riuscito. Riprova.",
      napUkaz: "Il comando non è riuscito.",
      napZaznamki: "Non è stato possibile inviare i preferiti.",
      napSyncStart: "Non è stato possibile avviare la sincronizzazione.",
      napSyncNastavi: "Non è stato possibile impostare la sincronizzazione.",
      napZdruzevanje: "Non è stato possibile completare l\'unione dei preferiti.",
      napHubNeTece: "L\'hub non è in esecuzione.",
      napHubNiZagnan: "Non è stato possibile avviare l\'hub.",
      napHubNiUstavljen: "Non è stato possibile arrestare l\'hub.",
      napPrijavaPotekla: "La richiesta non esiste più o è scaduta.",
      napTvJeZaslon: "Il televisore è uno schermo; non invia.",
      napTvNeUpravlja: "Il televisore non controlla altri schermi.",
      napSyncTvNiNaVoljo: "La sincronizzazione dei preferiti non è ancora disponibile sul televisore.",
      preverjam: "Controllo …",
      zapri: "Chiudi",
      povezano: "Connesso al tuo Safeer Link di casa",
      cakaNaPotrditev: "In attesa della tua approvazione",
      niVklopljen: "Non connesso",
      brezHubaNaslov: "Safeer Link non è ancora configurato",
      brezHubaOpis: "Safeer Link invia la pagina aperta al televisore e collega i dispositivi di casa tua: niente cloud, niente account. Un dispositivo in casa assume il ruolo di hub, gli altri si collegano a esso.",
      brezHubaPomirilo: "Senza di esso il browser funziona esattamente come prima. Non perdi nulla chiudendo questa finestra.",
      poisci: "Cerca nella mia rete",
      kakoDobim: "Come lo attivo",
      iscem: "Ricerca …",
      niNajden: "Non l\'ho trovato in questa rete. Controlla che Safeer Link sia in esecuzione su uno dei tuoi dispositivi e riprova.",
      povežiNaslov: "Collega questo dispositivo",
      hubNajdenNa: "Safeer Link si trova su",
      zakajPotrditi: "Perché possa inviarti contenuti e sincronizzare, approvalo una volta.",
      potrdiKodo: "Approva questo codice sul dispositivo su cui è in esecuzione Safeer Link:",
      kodaVelja: "Il codice è valido per 5 minuti.",
      poveziSSafeerLink: "Collegati a Safeer Link",
      cakamNaPotrditev: "In attesa dell\'approvazione …",
      niPotrjeno: "Il codice non è stato approvato. Riprova.",
      posljiStran: "Invia questa pagina",
      odprtoVBrskalniku: "Apri nel browser",
      domacaStran: "Pagina iniziale: non può essere inviata",
      predvajaSeNa: "In riproduzione su",
      nazaj10: "⏪ 10 s",
      pavza: "⏸ Pausa",
      predvajaj: "▶ Riproduci",
      naprej10: "10 s ⏩",
      povezaneNaprave: "Dispositivi collegati",
      osvezi: "Aggiorna",
      povezujem: "Connessione …",
      taNaprava: "Questo dispositivo",
      povezanaZLinkom: "Connesso a Safeer Link",
      domace: "Hub di casa",
      zaslon: "Schermo",
      televizor: "Televisore",
      posljiNaZaslon: "Invia a questo schermo",
      poslji: "Invia",
      povezan: "Connesso",
      brezZaslonov: "Non è ancora comparso nessuno schermo. Apri il browser Safeer sul televisore e approva il suo codice.",
      poslanoNa: "Inviato a {ime}.",
      niDosegljiv: "{ime} non è raggiungibile in questo momento. Controlla che sia acceso e riprova.",
      neMorePoslati: "Questa pagina non può essere inviata. Apri un sito web e riprova.",
      povezaveNi: "Non c\'è connessione a Safeer Link. Riprova.",
      pozabiNapravo: "Dimentica questo dispositivo",
      pozabiPotrdi: "Sicuro? Tocca ancora una volta: questo dispositivo verrà scollegato.",
      pozabljeno: "Il dispositivo è scollegato. Puoi ricollegarlo quando vuoi.",
      sinhronizacija: "Sincronizzazione",
      syncOpis: "I tuoi preferiti viaggiano tra i tuoi dispositivi attraverso il Safeer Link di casa. Niente finisce nel cloud.",
      syncPrivzeto: "La sincronizzazione parte quando la confermi: fino ad allora non viene inviato nulla.",
      zaznamki: "Preferiti",
      syncVklopljena: "Attiva",
      syncIzklopljena: "Disattivata",
      syncPotrdi: "Conferma",
      syncVklopljenaOpis: "Attiva — {n} preferiti su questo dispositivo",
      syncNiNaVoljo: "Non ancora disponibile su questo dispositivo",
      syncVprasanje: "I tuoi {n} preferiti verranno inviati a tutti i tuoi dispositivi. Tocca ancora una volta per confermare.",
      syncPovabilo: "Tocca per attivare. Fino ad allora non viene inviato nulla.",
      syncVklapljam: "Attivazione …",
      syncIzklapljam: "Disattivazione …",
      syncTece: "La sincronizzazione funziona in background.",
      syncPrejeto: "Ricevuti: {n} nuovi preferiti.",
      syncUgasnjena: "La sincronizzazione è disattivata. Non viene inviato nulla.",
      nastavitve: "Impostazioni",
      nastavitveOpis: "Aspetto, motore di ricerca, protezioni",
      filtri: "Liste di filtri",
      filtriOpis: "Le stesse protezioni su ogni dispositivo",
      kmalu: "Presto",
      tezava: "Qualcosa è andato storto. Riprova.",
      preseljenNaslov: "Safeer Link ha un nuovo indirizzo",
      preseljenOpis: "Si annuncia da un indirizzo diverso da prima, di solito perché il router gliene ha assegnato uno nuovo. Conferma che questo è il tuo Safeer Link.",
      daPovezi: "Sì, collega",
      preverjamNaslov: "Connessione al nuovo indirizzo …"
    }
  };

  /**
   * Stabilne kode napak iz mostu. Most poslje kodo in besedilo; stran pokaze prevod
   * kode, besedilo pa uporabi le, ce kode ne pozna (starejsi most, nova koda).
   */
  var NAPAKE = {
    hub_ni_znan: "napHubNiZnan",
    iskanje_ni_steklo: "napIskanje",
    seznanitev_ni_stekla: "napSeznanitev",
    povezava_ni_uspela: "napPovezava",
    stran_ni_primerna: "napStranNiPrimerna",
    samo_http: "napSamoHttp",
    posiljanje_ni_uspelo: "napPosiljanje",
    ukaz_ni_uspel: "napUkaz",
    zaznamki_niso_poslani: "napZaznamki",
    sync_ni_stekla: "napSyncStart",
    sync_ni_nastavljena: "napSyncNastavi",
    zdruzevanje_ni_koncano: "napZdruzevanje",
    hub_ne_tece: "napHubNeTece",
    hub_ni_zagnan: "napHubNiZagnan",
    hub_ni_ustavljen: "napHubNiUstavljen",
    prijava_potekla: "napPrijavaPotekla",
    tv_je_zaslon: "napTvJeZaslon",
    tv_ne_upravlja: "napTvNeUpravlja",
    sync_tv_ni_na_voljo: "napSyncTvNiNaVoljo"
  };

  var jezik = (function () {
    var oznaka = "";
    try {
      if (most && most.jezik) oznaka = String(most.jezik() || "");
    } catch (e) {}
    if (!oznaka) oznaka = (navigator.language || navigator.userLanguage || "en");
    oznaka = oznaka.toLowerCase().slice(0, 2);
    return BESEDILA[oznaka] ? oznaka : "en";
  })();

  function t(kljuc, nadomestki) {
    var niz = (BESEDILA[jezik] && BESEDILA[jezik][kljuc]);
    if (niz === undefined) niz = (BESEDILA.en && BESEDILA.en[kljuc]);
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

  // Besedila za sredisce na televizorju. Dodana so tu na kupu, da se v obeh jezikih
  // vidijo skupaj -- kar je treba prevesti, je na enem mestu.
  var BESEDILA_HUB = {
    sl: {
      tuOpis: "Če doma nimaš računalnika, lahko središče prevzame ta televizor.",
      vklopiLink: "Vklopi Safeer Link",
      izklopiLink: "Izklopi Safeer Link",
      tuNaslov: "Ta televizor je središče",
      tuPojasnilo: "Naprave v lokalnem omrežju se povezujejo na televizor.",
      tuSredisce: "Poveži napravo na televizor",
      povezana: "Povezana",
      sePotrdi: "Še enkrat pritisni, da ji odvzameš dostop",
      cakaPrijava: "Naprava se želi povezati",
      primerjajKodo: "Na napravi mora pisati ista koda",
      potrdi: "Potrdi",
      zavrni: "Zavrni",
      odstrani: "Odstrani",
      povezanNaTv: "Sme pošiljati na ta televizor",
      nobeneNaprave: "Povežite naprave za lažje delo",
      prizigam: "Prižigam …"
    },
    en: {
      tuOpis: "If you have no computer at home, this television can be the hub.",
      vklopiLink: "Turn on Safeer Link",
      izklopiLink: "Turn off Safeer Link",
      tuNaslov: "This television is the hub",
      tuPojasnilo: "Devices on the local network connect to this television.",
      tuSredisce: "Connect a device to the television",
      povezana: "Connected",
      sePotrdi: "Press again to revoke access",
      cakaPrijava: "A device wants to connect",
      primerjajKodo: "The same code must show on the device",
      potrdi: "Approve",
      zavrni: "Decline",
      odstrani: "Remove",
      povezanNaTv: "May send to this television",
      nobeneNaprave: "Connect your devices to make work easier",
      prizigam: "Turning on …"
    }
  };
  for (var _jezik in BESEDILA_HUB) {
    if (!BESEDILA[_jezik]) BESEDILA[_jezik] = {};
    for (var _kljuc in BESEDILA_HUB[_jezik]) BESEDILA[_jezik][_kljuc] = BESEDILA_HUB[_jezik][_kljuc];
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
    tezava: false,
    preseljen: false,
    hubTece: false,
    hubPovezanih: 0,
    prijave: [],
    hubNaprave: []
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
    if (stanje.hubTece) {
      barva = "zelena";
      napis = t("tuSredisce");
    } else if (stanje.tezava) {
      barva = "rumena";
      napis = t("tezava");
    } else if (stanje.znan && stanje.seznanjen) {
      barva = stanje.povezan ? "zelena" : "rumena";
      napis = stanje.povezan ? t("povezano") : t("povezujem");
    } else if (stanje.preseljen) {
      barva = "rumena";
      napis = t("preseljenNaslov");
    } else if (stanje.znan) {
      barva = "rumena";
      napis = t("cakaNaPotrditev");
    }
    if (pika) pika.className = "pika " + barva;
    besedilo("podnaslov", napis);
  }

  function narisiZaslon() {
    // Ce sredisce tece tu, je to edina zgodba na zaslonu: televizor ne isce sam sebe.
    var tuSredisce = stanje.hubTece;
    var brezHuba = !tuSredisce && !stanje.znan && !stanje.preseljen;
    var caka = !tuSredisce && stanje.znan && !stanje.seznanjen && !stanje.preseljen;
    pokazi("zaslonHubTu", tuSredisce);
    pokazi("zaslonBrezHuba", brezHuba);
    pokazi("zaslonPreseljen", !tuSredisce && stanje.preseljen);
    pokazi("zaslonSeznanitev", caka);
    pokazi("zaslonPovezan", !tuSredisce && !brezHuba && !caka && !stanje.preseljen);
    pokazi("gumbPozabi", !tuSredisce && !brezHuba && !caka && !!(most && most.pozabiNapravo));
    pokazi("hubStikalo", podpiraHub);
    var vklopi = el("gumbHubVklopi");
    var izklopi = el("gumbHubIzklopi");
    if (vklopi) vklopi.disabled = tuSredisce;
    if (izklopi) izklopi.disabled = !tuSredisce;
    besedilo("opombaHubVklop", tuSredisce ? "" : t("tuOpis"));
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
  // Sredisce na tem televizorju
  // ----------------------------------------------------------------

  var podpiraHub = !!(most && most.hubStanje);
  var hubUra = null;
  var hubPodpis = "";
  var hubPrejPrijav = 0;
  var odvzemam = "";

  /** Prebere stanje sredisca pri mostu. Na napravah brez te podpore ne naredi nicesar. */
  function hubOsvezi() {
    if (!podpiraHub) return;
    try {
      var s = JSON.parse(most.hubStanje() || "{}");
      stanje.hubTece = !!s.tece;
      stanje.hubPovezanih = s.naprav || 0;
    } catch (e) {}
    try {
      stanje.prijave = JSON.parse(most.hubPrijave() || "[]");
    } catch (e) {
      stanje.prijave = [];
    }
    try {
      stanje.hubNaprave = JSON.parse(most.hubSeznanjene() || "[]");
    } catch (e) {
      stanje.hubNaprave = [];
    }
    narisiHub();
    narisiZaslon();
    hubUraNastavi();
  }

  /** Medtem ko sredisce tece, stanje osvezujemo sami -- nova prijava se mora pokazati sama. */
  function hubUraNastavi() {
    if (stanje.hubTece && !hubUra) hubUra = setInterval(hubOsvezi, 4000);
    if (!stanje.hubTece && hubUra) {
      clearInterval(hubUra);
      hubUra = null;
    }
  }

  function narisiHub() {
    // Seznama ne prerisujemo, ce se ni nic spremenilo: na daljincu bi vsako risanje
    // odneslo fokus z gumba, ki ga ima uporabnik ravno pod prstom.
    var podpis = JSON.stringify([stanje.prijave, stanje.hubNaprave, odvzemam]);
    if (podpis === hubPodpis) return;
    hubPodpis = podpis;

    // Kje je bil fokus? Po izrisu ga vrnemo na isto mesto: brez tega drugi pritisk
    // na daljincu pade v prazno, ker je element, ki ga je uporabnik gledal, nov.
    var prejsnjiFokus = null;
    try {
      prejsnjiFokus = document.activeElement &&
        document.activeElement.getAttribute && document.activeElement.getAttribute("data-fokus");
    } catch (e) {}

    var prijave = el("seznamPrijav");
    if (prijave) {
      prijave.innerHTML = "";
      stanje.prijave.forEach(function (p) { prijave.appendChild(vrsticaPrijave(p)); });
    }
    pokazi("panelPrijave", stanje.prijave.length > 0);

    var naprave = el("seznamHubNaprav");
    if (naprave) {
      naprave.innerHTML = "";
      stanje.hubNaprave.forEach(function (n) {
        // Dostop se odvzame v dveh korakih: en sam pritisk na daljincu je prehitro
        // storjen, naprava pa se mora potem znova seznaniti.
        var odvzemamTo = odvzemam === n.id;
        var vrsticaNaprave = vrstica(
          ikonaNaprave(n),
          n.ime || t("zaslon"),
          odvzemamTo ? t("sePotrdi") : t("povezanNaTv"),
          odvzemamTo ? t("odstrani") : t("povezana"),
          odvzemamTo ? "" : "zivo",
          function () {
            if (!odvzemamTo) {
              odvzemam = n.id;
              narisiHub();
              return;
            }
            odvzemam = "";
            if (most && most.hubPreklici) most.hubPreklici(n.id);
          });
        vrsticaNaprave.setAttribute("data-fokus", "naprava:" + n.id);
        naprave.appendChild(vrsticaNaprave);
      });
    }
    besedilo("opombaHub", stanje.hubNaprave.length ? "" : t("nobeneNaprave"));

    // Fokus nazaj na isto mesto; ce ga ni vec, na cakajoco prijavo.
    var nicNiFokusirano = !document.activeElement || document.activeElement === document.body;
    var vrnjen = false;
    if (prejsnjiFokus) {
      var isti = document.querySelector('[data-fokus="' + prejsnjiFokus + '"]');
      if (isti) {
        try { isti.focus(); vrnjen = true; } catch (e) {}
      }
    }
    // Nova prijava: fokus gre na Potrdi, da je dovolj en pritisk na V redu.
    // Prav tako takrat, kadar fokus ni nikjer -- daljinec mora vedno imeti kam.
    if (!vrnjen && stanje.prijave.length &&
        (stanje.prijave.length > hubPrejPrijav || nicNiFokusirano)) {
      setTimeout(fokusirajPotrditev, 80);
    }
    hubPrejPrijav = stanje.prijave.length;
  }

  /**
   * Ikona pove, kaj se povezuje. Vrsto naprave uganemo iz imena, ki ga naprava pove o sebi --
   * racunalnik naj bo racunalnik in ne telefon, sicer uporabnik ne ve, katera naprava je katera.
   */
  function ikonaNaprave(naprava) {
    var opis = ((naprava && (naprava.ime || "")) + " " + (naprava && (naprava.id || ""))).toLowerCase();
    if (/(televizor|tv|philips|android tv)/.test(opis)) return "📺";
    if (/(racunaln|računaln|computer|namizn|desktop|laptop|prenosn|linux|windows|mac|pc\b)/.test(opis)) return "💻";
    if (/(tablic|tablet|ipad)/.test(opis)) return "📱";
    return "📱";
  }

  function vrsticaPrijave(p) {
    var li = document.createElement("li");
    li.className = "prijava";

    var telo = document.createElement("div");
    telo.className = "telo";
    var ime = document.createElement("div");
    ime.className = "ime";
    ime.textContent = (ikonaNaprave(p) + " " + (p.ime || t("zaslon"))).trim();
    var pod = document.createElement("div");
    pod.className = "pod";
    pod.textContent = t("primerjajKodo");
    telo.appendChild(ime);
    telo.appendChild(pod);

    var koda = document.createElement("div");
    koda.className = "stevilke";
    koda.textContent = p.koda || "------";

    var tipke = document.createElement("div");
    tipke.className = "tipke";
    var potrdi = document.createElement("button");
    potrdi.className = "glavni";
    potrdi.setAttribute("data-potrdi", "1");
    potrdi.setAttribute("data-fokus", "prijava:" + p.id + ":potrdi");
    potrdi.textContent = t("potrdi");
    potrdi.addEventListener("click", function () {
      if (most && most.hubPotrdi) most.hubPotrdi(p.id);
    });
    var zavrni = document.createElement("button");
    zavrni.className = "drugotni tanek";
    zavrni.setAttribute("data-fokus", "prijava:" + p.id + ":zavrni");
    zavrni.textContent = t("zavrni");
    zavrni.addEventListener("click", function () {
      if (most && most.hubZavrni) most.hubZavrni(p.id);
    });
    tipke.appendChild(potrdi);
    tipke.appendChild(zavrni);

    li.appendChild(telo);
    li.appendChild(koda);
    li.appendChild(tipke);
    return li;
  }

  function fokusirajPotrditev() {
    var gumb = document.querySelector("#seznamPrijav button[data-potrdi]");
    if (gumb) {
      try { gumb.focus(); } catch (e) {}
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
          stanje.preseljen = false;
          besedilo("naslovHuba", prijaznaHisa(podatki.naslov));
          osveziStanje();
        } else {
          besedilo("opombaIskanje", t("niNajden"));
          narisiStanje();
        }
      } else if (vrsta === "hub-tu") {
        stanje.hubTece = !!(podatki && podatki.tece);
        stanje.hubPovezanih = (podatki && podatki.naprav) || 0;
        hubPodpis = "";
        hubOsvezi();
      } else if (vrsta === "hub-prijave") {
        stanje.prijave = podatki || [];
        narisiHub();
        narisiZaslon();
      } else if (vrsta === "hub-seznanjene") {
        stanje.hubNaprave = podatki || [];
        narisiHub();
      } else if (vrsta === "preseljen") {
        stanje.preseljen = true;
        besedilo("noviNaslov", prijaznaHisa(podatki && podatki.naslov));
        narisiZaslon();
      } else if (vrsta === "koda") {
        pokazi("kodaBlok", true);
        besedilo("kodaStevilke", String(podatki));
        besedilo("opombaSeznanitev", t("cakamNaPotrditev"));
        var g = el("gumbSeznani");
        if (g) g.disabled = true;
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
        var sporocilo = izNapake(podatki);
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
  /** Napaka pride kot besedilo ali kot {koda, sporocilo}; koda ima prednost. */
  function izNapake(podatki) {
    if (podatki && typeof podatki === "object") {
      var kljuc = NAPAKE[String(podatki.koda || "")];
      if (kljuc) {
        var niz = t(kljuc);
        if (niz) return niz;
      }
      return clovesko(String(podatki.sporocilo || ""));
    }
    return clovesko(String(podatki));
  }

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

    naKlik("gumbOsvezi", poveziSe);

    naKlik("gumbHubVklopi", function () {
      besedilo("opombaHubVklop", t("prizigam"));
      if (most && most.hubVklopi) most.hubVklopi();
    });

    naKlik("gumbHubIzklopi", function () {
      if (most && most.hubIzklopi) most.hubIzklopi();
    });

    naKlik("gumbHubOsvezi", function () {
      hubPodpis = "";
      hubOsvezi();
    });

    naKlik("gumbPotrdiNaslov", function () {
      if (!most || !most.potrdiNovNaslov) return;
      besedilo("opombaPreselitev", t("preverjamNaslov"));
      most.potrdiNovNaslov();
    });

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
    pazljivNaSmerneTipke();
    osveziStanje();
    hubOsvezi();
    // Na daljincu prvi fokus odloca, kaj uporabnik potrdi: naj bo glavno dejanje,
    // ne krizec za zapiranje.
    setTimeout(fokusirajGlavno, 150);
  });

  /**
   * Zasilni izhod za daljinec. Ce fokus ni na nobenem gumbu (to se na televizorju zgodi,
   * kadar se stran na novo izrise), prvi pritisk na smerno tipko ne premakne nicesar --
   * zato ga porabimo za to, da fokus postavimo na glavno dejanje.
   */
  function pazljivNaSmerneTipke() {
    document.addEventListener("keydown", function (e) {
      var smerna = e.key === "ArrowUp" || e.key === "ArrowDown" ||
                   e.key === "ArrowLeft" || e.key === "ArrowRight";
      if (!smerna) return;
      var kje = document.activeElement;
      if (!kje || kje === document.body || kje === document.documentElement) {
        e.preventDefault();
        fokusirajGlavno();
      }
    }, true);
  }

  function fokusirajGlavno() {
    // Ce kdo caka na potrditev, je to najpomembnejse na zaslonu.
    var potrdi = document.querySelector("#seznamPrijav button[data-potrdi]");
    if (potrdi) {
      try { potrdi.focus(); } catch (err) {}
      return;
    }
    var kandidati = ["gumbSeznani", "gumbPoisci", "gumbHubVklopi", "gumbHubIzklopi",
                     "gumbOsvezi", "gumbHubOsvezi"];
    // Onemogocen gumb ni cilj za daljinec.
    for (var i = 0; i < kandidati.length; i++) {
      var e = el(kandidati[i]);
      if (e && e.offsetParent !== null && !e.disabled) {
        try { e.focus(); } catch (err) {}
        return;
      }
    }
  }
})();
