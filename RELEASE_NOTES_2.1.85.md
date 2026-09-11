# Safeer Browser for Android TV 2.1.85

Update of the protection against web traps (Safeer Threat Shield):

- Fake online banks: an address that imitates a bank and a page on a foreign address that presents itself as a bank while showing a password, SMS code or card field get a warning (in all six interface languages) with "Back", "Open the real site" and "Continue for this session". Back from the warning skips the fake page.
- Real banks work undisturbed: Slovenian banks, their banking groups, PayPal, Revolut, N26, Wise and the pages used for logins and card payments are never blocked by ad rules or phishing lists and get no cosmetic filters or pop-up shield. Invalid TLS certificates are no longer accepted for bank pages or anything a bank page loads.
- Threat lists on every start without slowing the TV: Feodo Tracker, URLhaus and Phishing Army lists saved by the previous run load in the background at once and are swapped in as a whole; about 12 seconds after start the TV checks for newer lists with a conditional request, then every 6 hours. Before, all lists were downloaded in full at every start and inserted into the live lookup while browsing.
- Prepared layer for the verified, signed Safeer threat list (Ed25519), checked in the background after every start.

Slovensko: Posodobljena zaščita pred spletnimi pastmi. Opozorilo pred lažnimi spletnimi bankami z gumbom za pravo stran banke, prave banke delujejo nemoteno (za bančne strani TV ne sprejme več neveljavnih certifikatov), seznami nevarnih strani pa se ob vsakem zagonu naložijo v ozadju in preverijo brez upočasnitve.
