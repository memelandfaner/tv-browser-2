# Safeer Browser for Android TV 2.1.86

New application id: the package is now `si.safeer.tv` (until 2.1.85 it was `com.example.safeerbrowser` from a template) and the APK is signed with Safeer's own release key. Android treats it as a new app: uninstall the old Safeer Browser first, then install 2.1.86 (settings and bookmarks of the old version are not carried over). From now on updates install over each other again.

Fewer ads, sponsor segments skipped and a wider net against web traps:

- Ads: the full EasyList list of ad rules (about 52,000 network rules) is compiled into an indexed filter engine inside the browser, so ad scripts, trackers and empty ad placeholders are blocked before they load. The list is refreshed in the background together with the threat lists.
- SponsorBlock: sponsor, self-promotion and "subscribe" segments in YouTube videos are skipped with a short notice. The lookup sends only the first characters of a hash of the video id, never the video itself (k-anonymity); it can be switched off in the menu (all seven interface languages).
- Threat lists: HaGeZi Threat Intelligence Feeds (mini) and HaGeZi Fake (fake shops and scams) and the SI-CERT list of phishing domains confirmed in Slovenia join the existing lists.
- Fake banks: pages that ask for a card number under the guise of a fine, a parcel fee or a tax refund are stopped as a card trap; tax-number and PIN fields count as credentials; every warning reminds you that a bank never calls to ask for codes or to install remote-access software.

All checks run on the TV; no address or page content is sent anywhere.

Slovensko: Novo ime paketa `si.safeer.tv` in pravi podpisni ključ – staro različico je treba najprej odstraniti in 2.1.86 namestiti na novo (nastavitve in zaznamki stare različice se ne prenesejo), naprej se posodobitve spet nameščajo ena čez drugo. Manj oglasov (vgrajen celoten seznam EasyList), preskok sponzorskih odsekov na YouTubu (SponsorBlock, izklop v meniju), novi seznami nevarnih strani (HaGeZi, SI-CERT) in past za podatke kartice pod pretvezo kazni, paketa ali vračila davka.
