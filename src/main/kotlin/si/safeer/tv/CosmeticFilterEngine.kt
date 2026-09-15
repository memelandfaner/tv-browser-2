package si.safeer.tv

/**
 * 🎨 CosmeticFilterEngine
 * Generira in injicira EasyList CSS element hiding pravila v spletne strani za popolno odstranitev praznih oglasnih okvirjev,
 * lažnih gumbov za prenos (Fake Download Buttons), oglasnih srcdoc okvirjev in lebdečih promocijskih pripomočkov (Reward Zone / Floating Popups).
 */
object CosmeticFilterEngine {

    var isEnabled: Boolean = true

    // Splošna EasyList pravila za skrivanje oglasnih elementov
    private val GENERIC_ELEMENT_HIDING_RULES = listOf(
        // Google Ads & DFP
        ".adsbygoogle", "[id^='google_ads']", "[id^='div-gpt-ad']", "[class*='google-ad']",
        ".a4bIc-ad", ".commercial-unit", ".ad-slot", ".ad-container", ".ad-wrapper",
        
        // Sponzorirane vsebine & Native Ads (Taboola, Outbrain)
        ".taboola", ".outbrain", ".trc_rbox_container", "[data-ad]", "[data-ad-unit]",
        ".sponsored-post", ".sponsored-content", ".promoted-tweet", ".promoted-post",
        
        // Popunder, Banner, In-Page Push & Floating Ad elementi
        ".pop-under", ".ad-banner", ".banner-ad", ".sticky-ad", ".floating-banner",
        ".monetag-banner", ".richpush-banner", ".interstitial-ad",
        "[class*='reward-zone']", "[id*='reward-zone']", "[class*='floating-ad']",
        "[class*='gamify-ad']", "[class*='coin-chest']", "[class*='ad-floating']",
        
        // Lažni gumbi za prenos (Fake Download Ads)
        "[class*='fake-download']", "[id*='fake-download']", ".download-button-ad",
        "div[class*='download-arrow']",
        
        // YouTube elementi & samodejni vklop zvoka (skrit mute gumb & popolnoma odstranjen Odpri aplikacijo gumb)
        ".ytp-ad-overlay-container", ".ytp-ad-message-container", ".ytp-ad-text",
        ".ytd-ad-slot-renderer", "ytd-in-feed-ad-layout-renderer", "ytd-banner-promo-renderer",
        "ytd-player-legacy-desktop-watch-ads-renderer", ".ytd-display-ad-renderer",
        ".ytp-unmute", ".ytp-unmute-inner", ".ytp-unmute-animated", ".ytp-unmute-box", "button[aria-label*='Vklopite zvok']", "button[aria-label*='Unmute']",
        "ytm-open-app-button", "ytm-app-promo-renderer", "ytm-mealbar-promo-renderer", "ytm-upsell-dialog-renderer",
        "button[aria-label*='Odpri aplikacijo']", "button[aria-label*='Odpri v aplikaciji']", "button[aria-label*='Odpri']",
        "button[aria-label*='Open app']", "button[aria-label*='Open in app']",
        ".topbar-action-buttons", ".topbar-action-buttons ytm-open-app-button", ".topbar-action-buttons button",
        "ytm-mobile-topbar-renderer .topbar-action-buttons", "ytm-mobile-topbar-renderer ytm-open-app-button",
        "ytm-mobile-topbar-renderer [aria-label*='Odpri']", "ytm-mobile-topbar-renderer [aria-label*='Open']",
        "a[href*='app_redirect']", "a[href*='open_in_app']", "button[title*='Odpri']", "button[title*='Open']",

        // 🎵 YouTube Mix / Playlist Obstructing Popup Overlays, Autonav & Dimming Backdrops (Bugfix)
        ".engagement-panel-backdrop", "ytm-bottom-sheet-renderer", "ytm-bottom-sheet-renderer.backdrop",
        "ytm-playlist-panel-renderer", "ytm-macro-markers-list-item-renderer", "ytm-chapter-renderer",
        "ytm-engagement-panel-section-list-renderer[target-id='engagement-panel-playlist-panel']",
        "ytm-engagement-panel-section-list-renderer .engagement-panel-header-container",
        "ytm-autonav-bar-renderer", ".ytm-autonav-bar", "ytm-autonav-toggle",
        ".ytp-pause-overlay", ".ytp-pause-overlay-container",
        "ytm-fullscreen-engagement-overlay-renderer", "ytm-companion-ad-renderer",
        "ytm-promoted-sparkles-web-renderer", "ytm-paid-content-overlay-renderer",

        // 🎬 In-Video Preroll & Streaming Ad Overlays (Pornhub, HTML5, Casino)
        ".mgp_adOverlay", ".mgp_adSkip", ".mgp_adMarker", ".mgp_commercial", ".mgp_adContainer",
        ".mgp_adPlaying", ".adBlockContainer", "div[class*='adOverlay']", "div[class*='adSkip']",
        ".mgp_skipAdButton", "a[class*='adLink']", "div[class*='adInformation']", ".adInformation",
        "div[class*='mgp_ad']", ".removeAds", "a[href*='casino']", ".topAd", ".bottomAd",
        ".wideBanner", ".underPlayerAd", ".commercial-unit", ".ad-zone", ".player-ad",
        "[class*='ad-banner']", "[class*='player-advertisement']", "[id*='player-advertisement']",
        ".ad-banner-overlay", ".jw-ad-container", ".plyr__ad", ".vjs-ad", ".video-ad-overlay",
        
        // AdBlock opozorila in prekrivna okna
        ".fc-ab-root", ".adblock-overlay", "#adblock-modal", ".ad-block-warning"
    )

    /**
     * Zgradi strnjen CSS niz za injiciranje v spletno stran.
     */
    fun buildCosmeticCss(pageUrl: String? = null): String {
        if (!isEnabled) return ""
        // Pravila iz seznamov (EasyList in drugi), vezana na to domeno. Doslej smo jih
        // prenesli in zavrgli; zaradi njih so na tujih straneh ostajale prazne luknje.
        val listRules = try {
            AdBlockEngine.cosmeticSelectors(pageUrl)
        } catch (_: Exception) {
            emptyList()
        }
        val selectors = (GENERIC_ELEMENT_HIDING_RULES + listRules).distinct().joinToString(", ")
        return """
            $selectors {
                display: none !important;
                visibility: hidden !important;
                height: 0 !important;
                min-height: 0 !important;
                max-height: 0 !important;
                opacity: 0 !important;
                pointer-events: none !important;
                position: absolute !important;
                left: -9999px !important;
            }
        """.trimIndent()
    }

    /**
     * Zgradi JavaScript ukaz za takojšnjo uveljavitev CSS pravil.
     */
    fun buildInjectionScript(): String {
        val css = buildCosmeticCss().replace("\n", " ").replace("\"", "\\\"")
        return """
            (function() {
                try {
                    var styleId = 'safeer-cosmetic-filter';
                    var existing = document.getElementById(styleId);
                    if (!existing) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.type = 'text/css';
                        style.innerHTML = "$css";
                        (document.head || document.documentElement).appendChild(style);
                    }
                } catch(_) {}
            })();
        """.trimIndent()
    }
}
